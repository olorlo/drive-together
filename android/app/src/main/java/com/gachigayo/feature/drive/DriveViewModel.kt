package com.gachigayo.feature.drive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gachigayo.data.location.DeviceLocation
import com.gachigayo.data.route.LngLat
import com.gachigayo.data.route.RouteLine
import com.gachigayo.data.route.RouteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 위치를 어디서 받는지 */
enum class LocationMode { SIMULATION, GPS }

data class DriveUi(
    val loading: Boolean = true,
    val error: String? = null,
    val line: RouteLine? = null,
    val alongM: Double = 0.0,
    val me: LngLat? = null,
    val speedKmh: Int = 0,
    val mode: LocationMode = LocationMode.SIMULATION,
    val running: Boolean = false,
    val simSpeed: Int = 30,
    val follow: Boolean = false,
    val arrived: Boolean = false,
    val gpsMessage: String? = null,
    val rerouting: Boolean = false,
) {
    val remainingM: Double get() = line?.let { (it.lengthM - alongM).coerceAtLeast(0.0) } ?: 0.0
    val remainingS: Double
        get() = line?.let { if (it.lengthM > 0) it.route.durationS * remainingM / it.lengthM else 0.0 } ?: 0.0
    val next: NextTurn? get() = line?.let { nextTurn(it, alongM) }
}

/** 시연용 여정: 서울 → 강릉 */
object DemoTrip {
    val start = LngLat(127.0276, 37.4979)      // 서울 강남역 부근
    val destination = LngLat(128.8761, 37.7519) // 강릉 시내
    const val DESTINATION_NAME = "강릉 할머니댁"
}

class DriveViewModel(app: Application) : AndroidViewModel(app) {

    private val routes = RouteRepository()
    private val device = DeviceLocation(app)

    private val _ui = MutableStateFlow(DriveUi())
    val ui: StateFlow<DriveUi> = _ui

    private var driveJob: Job? = null
    private var offRouteCount = 0

    init {
        loadRoute(DemoTrip.start)
    }

    fun loadRoute(from: LngLat = DemoTrip.start) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { routes.fetch(from, DemoTrip.destination) }
                .onSuccess { r ->
                    val line = RouteLine(r)
                    _ui.update {
                        it.copy(loading = false, line = line, alongM = 0.0, me = line.pointAt(0.0), arrived = false, rerouting = false)
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, rerouting = false, error = e.message ?: "경로를 찾지 못했어요") }
                }
        }
    }

    // ---------- 시뮬레이션 ----------

    fun toggleSimulation() {
        val ui = _ui.value
        if (ui.mode != LocationMode.SIMULATION) return
        if (ui.running) stop() else startSimulation()
    }

    fun setSimSpeed(x: Int) = _ui.update { it.copy(simSpeed = x) }

    private fun startSimulation() {
        val line = _ui.value.line ?: return
        if (_ui.value.arrived) _ui.update { it.copy(alongM = 0.0, arrived = false) }
        driveJob?.cancel()
        _ui.update { it.copy(running = true, follow = true) }
        driveJob = viewModelScope.launch {
            val baseMps = line.lengthM / line.route.durationS // 경로 평균 속도
            val tickMs = 100L
            while (isActive) {
                delay(tickMs)
                val s = _ui.value
                val wave = 0.92 + 0.1 * kotlin.math.sin(s.alongM / 900.0) // 속도에 약간의 변화
                val next = (s.alongM + baseMps * wave * s.simSpeed * tickMs / 1000.0).coerceAtMost(line.lengthM)
                moveTo(next, (baseMps * wave * 3.6).toInt())
                if (next >= line.lengthM) {
                    arrive()
                    break
                }
            }
        }
    }

    // ---------- 실제 위치 ----------

    fun useMode(mode: LocationMode, gpsGranted: Boolean = false) {
        stop()
        _ui.update { it.copy(mode = mode, gpsMessage = null) }
        if (mode == LocationMode.GPS) {
            if (!gpsGranted) {
                _ui.update { it.copy(gpsMessage = "위치 권한이 필요해요") }
                return
            }
            startGps()
        }
    }

    private fun startGps() {
        driveJob?.cancel()
        offRouteCount = 0
        _ui.update { it.copy(running = true, follow = true, gpsMessage = "위치를 기다리는 중…") }
        driveJob = viewModelScope.launch {
            runCatching {
                device.fixes().collect { fix ->
                    val s = _ui.value
                    val line = s.line ?: return@collect
                    val snap = line.snap(fix.at, s.alongM.takeIf { it > 0 })
                    val kmh = ((fix.speedMps ?: 0f) * 3.6f).toInt()
                    if (snap.offM > 80) {
                        // 경로를 벗어나면 3번 연속 확인 후 현재 위치에서 다시 찾기
                        offRouteCount++
                        _ui.update { it.copy(me = fix.at, speedKmh = kmh, gpsMessage = "경로를 벗어났어요") }
                        if (offRouteCount >= 3 && !s.rerouting) {
                            offRouteCount = 0
                            _ui.update { it.copy(rerouting = true, gpsMessage = "경로를 다시 찾는 중…") }
                            loadRoute(fix.at)
                        }
                        return@collect
                    }
                    offRouteCount = 0
                    _ui.update { it.copy(gpsMessage = null) }
                    moveTo(snap.alongM, kmh, me = fix.at)
                    if (line.lengthM - snap.alongM < 40) arrive()
                }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) return@onFailure // 직접 멈춘 경우
                _ui.update { it.copy(running = false, gpsMessage = "위치를 받을 수 없어요 (${e.message})") }
            }
        }
    }

    // ---------- 공통 ----------

    fun toggleFollow() = _ui.update { it.copy(follow = !it.follow) }

    fun restart() {
        stop()
        _ui.update { s -> s.copy(alongM = 0.0, arrived = false, me = s.line?.pointAt(0.0), speedKmh = 0) }
    }

    private fun moveTo(alongM: Double, kmh: Int, me: LngLat? = null) {
        _ui.update { s ->
            s.copy(alongM = alongM, speedKmh = kmh, me = me ?: s.line?.pointAt(alongM))
        }
    }

    private fun arrive() {
        driveJob?.cancel()
        _ui.update { it.copy(running = false, arrived = true, speedKmh = 0, follow = false) }
    }

    private fun stop() {
        driveJob?.cancel()
        driveJob = null
        _ui.update { it.copy(running = false, speedKmh = 0) }
    }
}
