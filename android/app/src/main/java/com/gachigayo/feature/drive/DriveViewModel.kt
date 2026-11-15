package com.gachigayo.feature.drive

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gachigayo.data.location.DeviceLocation
import com.gachigayo.data.route.LngLat
import com.gachigayo.data.route.RouteLine
import com.gachigayo.data.route.RouteRepository
import com.gachigayo.feature.group.GroupCar
import com.gachigayo.feature.group.GroupDemo
import com.gachigayo.feature.group.GroupToast
import com.gachigayo.feature.group.Proposal
import com.gachigayo.feature.group.ProposalStatus
import com.gachigayo.feature.group.Reason
import com.gachigayo.feature.group.RestArea
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

/** 위치를 어디서 받는지 */
enum class LocationMode { SIMULATION, GPS }

/** 그룹 단계: 만들기 → 초대(참여 대기) → 함께 주행 → 끝 */
enum class GroupPhase { CREATE, INVITE, DRIVE, ENDED }

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
    // ---------- 그룹 (시연) ----------
    val cars: List<GroupCar> = GroupDemo.OTHERS.map { GroupCar(it) },
    val battery: Int = GroupDemo.ME.energy,
    val plan: Int = 1,
    val stopAlongM: Double? = null,
    val stopName: String? = null,
    val stayS: Double = 0.0,
    val restLeftS: Double = 0.0,
    val restName: String? = null,
    val proposalReasonCharge: Boolean = false,
    val proposal: Proposal? = null,
    val sheetOpen: Boolean = false,
    val reason: Reason = Reason.TOILET,
    val nudge: Boolean = false,
    val toast: GroupToast? = null,
    val phase: GroupPhase = GroupPhase.CREATE,
    val joined: Set<String> = setOf(GroupDemo.ME.id),
    val askOpen: Boolean = false,
    val stopsVisited: Int = 0,
    val simElapsedS: Double = 0.0,
    val lastDiffMin: Int? = null,
) {
    val remainingM: Double get() = line?.let { (it.lengthM - alongM).coerceAtLeast(0.0) } ?: 0.0
    val remainingS: Double
        get() {
            val drive = line?.let { if (it.lengthM > 0) it.route.durationS * remainingM / it.lengthM else 0.0 } ?: 0.0
            val pendingStay = if (stopAlongM != null && alongM < stopAlongM) stayS else 0.0
            return drive + pendingStay + restLeftS
        }
    val next: NextTurn? get() = line?.let { nextTurn(it, alongM, stopName) }
    val resting: Boolean get() = restLeftS > 0
}

/** 시연용 여정: 서울 → 강릉 */
object DemoTrip {
    val start = GroupDemo.ME.start                  // 서울 강남역 부근
    val destination = LngLat(128.8761, 37.7519)     // 강릉 시내
    const val DESTINATION_NAME = "강릉 할머니댁"
}

class DriveViewModel(app: Application) : AndroidViewModel(app) {

    private val routes = RouteRepository()
    private val device = DeviceLocation(app)

    private val _ui = MutableStateFlow(DriveUi())
    val ui: StateFlow<DriveUi> = _ui

    private var driveJob: Job? = null
    private var offRouteCount = 0
    private var drivenM = 0.0
    private var nudgeShown = false
    private var proposalSeq = 1
    private var toastJob: Job? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        loadRoute(DemoTrip.start)
        loadOtherCars()
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                ttsReady = true
            }
        }
    }

    override fun onCleared() {
        tts?.shutdown()
        super.onCleared()
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
                    refreshStatuses()
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, rerouting = false, error = e.message ?: "경로를 찾지 못했어요") }
                }
        }
    }

    /** 함께 가는 차들의 경로 (시연: 각자 출발지에서 같은 목적지로) */
    private fun loadOtherCars() {
        GroupDemo.OTHERS.forEach { m ->
            viewModelScope.launch {
                runCatching { routes.fetch(m.start, DemoTrip.destination) }.onSuccess { r ->
                    val line = RouteLine(r)
                    _ui.update { s -> s.copy(cars = s.cars.map { if (it.member.id == m.id) it.copy(line = line, alongM = 0.0) else it }) }
                    refreshStatuses()
                }
            }
        }
    }

    // ---------- 그룹 만들기·초대·출발 ----------

    fun createGroup() {
        if (_ui.value.phase != GroupPhase.CREATE) return
        _ui.update { it.copy(phase = GroupPhase.INVITE) }
        // 시연: 초대 코드를 받은 차들이 차례로 참여
        GroupDemo.OTHERS.forEachIndexed { i, m ->
            viewModelScope.launch {
                delay(1600L + i * 1800L)
                _ui.update { it.copy(joined = it.joined + m.id) }
            }
        }
    }

    fun goTogether() {
        val s = _ui.value
        if (s.phase != GroupPhase.INVITE || s.joined.size < 2) return
        _ui.update { it.copy(phase = GroupPhase.DRIVE, joined = GroupDemo.ALL.map { m -> m.id }.toSet()) }
        speak("모두 출발해요. ${DemoTrip.DESTINATION_NAME}까지 같이 가요.")
        if (isSimMode()) startSimulation()
    }

    private fun isSimMode() = _ui.value.mode == LocationMode.SIMULATION

    fun endGroup() {
        stop()
        _ui.update { it.copy(phase = GroupPhase.ENDED, proposal = null, sheetOpen = false, nudge = false) }
    }

    fun openAsk() = _ui.update { it.copy(askOpen = true, sheetOpen = false) }

    /** 쉬는 중에 '출발 준비 완료' */
    fun resumeNow() {
        if (_ui.value.restLeftS > 0) {
            _ui.update { it.copy(restLeftS = 0.0, restName = null) }
            speak("다시 출발해요.")
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
        if (_ui.value.line == null || _ui.value.arrived || _ui.value.phase != GroupPhase.DRIVE) return
        driveJob?.cancel()
        _ui.update { it.copy(running = true, follow = true) }
        driveJob = viewModelScope.launch {
            val tickMs = 100L
            var n = 0
            while (isActive) {
                delay(tickMs)
                val dtSim = tickMs / 1000.0 * _ui.value.simSpeed
                if (!_ui.value.rerouting) {
                    stepMe(dtSim)
                    stepOthers(dtSim)
                }
                if (++n % 3 == 0) refreshStatuses()
                if (_ui.value.arrived) break
            }
        }
    }

    private fun stepMe(dtSim: Double) {
        val s = _ui.value
        val line = s.line ?: return
        _ui.update { it.copy(simElapsedS = it.simElapsedS + dtSim) }
        if (s.restLeftS > 0) {
            val left = s.restLeftS - dtSim
            if (left <= 0) {
                _ui.update { it.copy(restLeftS = 0.0, restName = null) }
                speak("다시 출발해요.")
            } else {
                _ui.update { it.copy(restLeftS = left, speedKmh = 0) }
            }
            return
        }
        val baseMps = line.lengthM / line.route.durationS
        val wave = 0.92 + 0.1 * sin(s.alongM / 900.0)
        var next = (s.alongM + baseMps * wave * dtSim).coerceAtMost(line.lengthM)
        val stop = s.stopAlongM
        var reachedStop = false
        if (stop != null && s.alongM < stop && next >= stop) {
            next = stop
            reachedStop = true
        }
        // 5km 달릴 때마다 배터리 1% (시연 값). 충전하면 그 값에서 다시 줄어든다
        drivenM += next - s.alongM
        var battery = s.battery
        while (drivenM >= 5000.0) {
            drivenM -= 5000.0
            battery = (battery - 1).coerceAtLeast(10)
        }
        moveTo(next, (baseMps * wave * 3.6).toInt())
        _ui.update { it.copy(battery = battery) }
        if (reachedStop) {
            val name = s.stopName ?: "휴게소"
            val charged = if (s.proposalReasonCharge) (battery + 38).coerceAtMost(80) else battery
            _ui.update {
                it.copy(
                    restLeftS = it.stayS, restName = name, stopAlongM = null, speedKmh = 0, battery = charged,
                    proposalReasonCharge = false, stopsVisited = it.stopsVisited + 1,
                )
            }
            speak("${name}에 도착했어요. ${(s.stayS / 60).toInt()}분 쉬어 가요.")
        }
        val chargePlanned = s.proposalReasonCharge && s.stopAlongM != null
        if (battery <= 25 && !nudgeShown && !chargePlanned && !reachedStop) {
            nudgeShown = true
            _ui.update { it.copy(nudge = true) }
            speak("배터리가 ${battery}퍼센트예요. 충전 휴게소를 같이 들를지 물어볼까요?")
        }
        if (next >= line.lengthM) arrive()
    }

    private fun stepOthers(dtSim: Double) {
        _ui.update { s ->
            s.copy(cars = s.cars.map { c ->
                val line = c.line ?: return@map c
                if (c.arrived) return@map c
                if (c.restLeftS > 0) {
                    val left = c.restLeftS - dtSim
                    return@map if (left <= 0) c.copy(restLeftS = 0.0, restName = null) else c.copy(restLeftS = left)
                }
                val baseMps = line.lengthM / line.route.durationS
                val wave = 0.9 + 0.12 * sin(c.alongM / 700.0 + c.member.id.hashCode())
                var next = (c.alongM + baseMps * wave * dtSim).coerceAtMost(line.lengthM)
                val stop = c.stopAlongM
                if (stop != null && c.alongM < stop && next >= stop) {
                    return@map c.copy(alongM = stop, stopAlongM = null, restLeftS = c.stayS, restName = c.stopName)
                }
                if (next >= line.lengthM) next = line.lengthM
                c.copy(alongM = next, arrived = next >= line.lengthM)
            })
        }
    }

    /** 다른 차가 내 경로 기준으로 앞/뒤 어디쯤인지 */
    private fun refreshStatuses() {
        _ui.update { s ->
            val myLine = s.line
            s.copy(cars = s.cars.map { c ->
                val pos = c.position
                val status = when {
                    c.line == null || pos == null -> "경로 찾는 중"
                    c.arrived -> "도착"
                    c.restLeftS > 0 -> "${c.restName ?: "휴게소"}에서 쉬는 중"
                    myLine == null -> ""
                    else -> {
                        val snap = myLine.snap(pos)
                        if (snap.offM > 400) {
                            "다른 길 · ${formatDistance(c.line.lengthM - c.alongM)} 남음"
                        } else {
                            val d = snap.alongM - s.alongM
                            if (abs(d) < 150) "바로 옆" else "${formatDistance(abs(d))} ${if (d > 0) "앞" else "뒤"}"
                        }
                    }
                }
                c.copy(status = status)
            })
        }
    }

    // ---------- 그룹: 휴게소 제안·투표 ----------

    /** 지금 제안할 수 있는 휴게소 (아직 안 지났고, 이미 들르기로 한 곳이 없을 때) */
    fun restOptions(s: DriveUi = _ui.value): List<Pair<RestArea, String?>> {
        val line = s.line ?: return emptyList()
        return GroupDemo.RESTS.map { r ->
            val snap = line.snap(r.at)
            val why = when {
                s.stopAlongM != null || s.resting -> "이미 ${s.stopName ?: s.restName}에 들르기로 했어요"
                snap.offM > 1500 -> "경로에서 멀어요"
                snap.alongM < s.alongM + 2000 -> "이미 지나쳤어요"
                else -> null
            }
            r to why
        }
    }

    fun openSheet(reason: Reason? = null) = _ui.update { it.copy(sheetOpen = true, nudge = false, reason = reason ?: it.reason) }
    fun closeSheet() = _ui.update { it.copy(sheetOpen = false) }
    fun setReason(r: Reason) = _ui.update { it.copy(reason = r) }
    fun dismissNudge() = _ui.update { it.copy(nudge = false) }

    /** 내가 휴게소를 제안 */
    fun propose(restId: String) {
        val s = _ui.value
        if (s.proposal?.status == ProposalStatus.OPEN) return
        val rest = GroupDemo.rest(restId)
        val p = Proposal(
            id = proposalSeq++,
            from = GroupDemo.ME.id,
            restId = restId,
            reason = s.reason,
            text = s.reason.message(rest.name, s.battery),
            votes = GroupDemo.ALL.associate { it.id to if (it.id == GroupDemo.ME.id) true else null },
            at = now(),
        )
        _ui.update { it.copy(proposal = p, sheetOpen = false, askOpen = true) }
        // 시연: 다른 차들이 차례로 '좋아요'
        GroupDemo.OTHERS.forEachIndexed { i, m ->
            viewModelScope.launch {
                delay(1700L + i * 1100L)
                vote(m.id, true)
            }
        }
    }

    /** 시연: 큰집이 화장실 요청을 보낸다 */
    fun simulateIncoming() {
        val s = _ui.value
        if (s.proposal?.status == ProposalStatus.OPEN) return
        val rest = restOptions(s).firstOrNull { it.second == null }?.first
        if (rest == null) {
            showToast("지금은 들를 수 있는 휴게소가 없어요", "이미 정한 휴게소가 있거나 모두 지나쳤어요")
            return
        }
        val from = GroupDemo.OTHERS.first()
        val p = Proposal(
            id = proposalSeq++,
            from = from.id,
            restId = rest.id,
            reason = Reason.TOILET,
            text = Reason.TOILET.message(rest.name, s.battery),
            votes = GroupDemo.ALL.associate { it.id to if (it.id == from.id) true else null },
            at = now(),
        )
        _ui.update { it.copy(proposal = p, sheetOpen = false, askOpen = false) }
        showToast("${GroupDemo.subject(from.alias)} 물어봤어요", "차 옆 말풍선이나 '질문 보기'를 눌러 대답해 주세요")
        speak("${GroupDemo.subject(from.alias)} 물어봤어요. ${p.text}")
        viewModelScope.launch {
            delay(1500)
            vote(GroupDemo.OTHERS[1].id, true)
        }
    }

    /** 내 대답 */
    fun myVote(yes: Boolean) = vote(GroupDemo.ME.id, yes)

    fun cancelProposal() {
        val p = _ui.value.proposal ?: return
        if (p.from == GroupDemo.ME.id && p.status == ProposalStatus.OPEN) _ui.update { it.copy(proposal = null) }
    }

    fun closeProposal() {
        if (_ui.value.proposal?.status != ProposalStatus.OPEN) _ui.update { it.copy(proposal = null, askOpen = false) }
        else _ui.update { it.copy(askOpen = false) }
    }

    private fun vote(id: String, yes: Boolean) {
        val p = _ui.value.proposal ?: return
        if (p.status != ProposalStatus.OPEN || p.votes[id] != null) return
        val votes = p.votes + (id to yes)
        if (!yes) {
            _ui.update { it.copy(proposal = p.copy(votes = votes, status = ProposalStatus.DECLINED)) }
            val who = GroupDemo.member(id)
            showToast("${GroupDemo.subject(who.alias)} 다음에 하재요", "경로는 그대로예요")
            clearProposalLater(p.id)
            return
        }
        val updated = p.copy(votes = votes)
        _ui.update { it.copy(proposal = updated) }
        if (votes.values.all { it == true }) applyPlan(updated)
    }

    /** 모두 동의: 모든 차의 경로에 휴게소를 경유지로 넣어 다시 찾는다 */
    private fun applyPlan(p: Proposal) {
        val rest = GroupDemo.rest(p.restId)
        val stayS = p.reason.stayMin * 60.0
        _ui.update { it.copy(proposal = p.copy(status = ProposalStatus.AGREED), rerouting = true) }
        viewModelScope.launch {
            val s = _ui.value
            val before = s.remainingS
            val myFrom = s.me ?: DemoTrip.start
            // 휴게소 좌표를 그대로 쓰면 반대 차선 쪽으로 잡혀 크게 돌아가는 경로가 나올 수 있어,
            // 내 경로(고속도로 진행 방향) 위에서 휴게소와 가장 가까운 지점을 경유지로 쓴다
            val via = s.line?.let { it.pointAt(it.snap(rest.at).alongM) } ?: rest.at
            val result = runCatching {
                val mine = async { routes.fetch(myFrom, DemoTrip.destination, listOf(via)) }
                val others = s.cars.map { c ->
                    async {
                        val from = c.position
                        if (from == null || c.arrived) null else routes.fetch(from, DemoTrip.destination, listOf(via))
                    }
                }
                mine.await() to others.awaitAll()
            }
            result.onSuccess { (mine, others) ->
                val myLine = RouteLine(mine)
                val plan = _ui.value.plan + 1
                _ui.update { st ->
                    st.copy(
                        line = myLine,
                        alongM = 0.0,
                        me = myLine.pointAt(0.0),
                        stopAlongM = myLine.snap(via).alongM,
                        stopName = rest.name,
                        stayS = stayS,
                        proposalReasonCharge = p.reason == Reason.CHARGE,
                        plan = plan,
                        rerouting = false,
                        cars = st.cars.mapIndexed { i, c ->
                            val r = others.getOrNull(i) ?: return@mapIndexed c
                            val l = RouteLine(r)
                            c.copy(line = l, alongM = 0.0, stopAlongM = l.snap(via).alongM, stopName = rest.name, stayS = stayS)
                        },
                    )
                }
                val diff = ((_ui.value.remainingS - before) / 60).toInt()
                val sign = if (diff >= 0) "+" else ""
                _ui.update { it.copy(lastDiffMin = diff) }
                viewModelScope.launch {
                    delay(15_000)
                    _ui.update { it.copy(lastDiffMin = null) }
                }
                showToast("다 같이 ${rest.name}에 들러요", "모든 차 경로 변경 · 도착 $sign${diff}분 · 계획 v$plan")
                speak("모두 좋대요. 다 같이 ${rest.name}에 들러요. 도착 시간이 ${abs(diff)}분 ${if (diff >= 0) "늦어져요" else "빨라져요"}.")
                refreshStatuses()
            }.onFailure {
                _ui.update { st -> st.copy(rerouting = false) }
                showToast("경로를 바꾸지 못했어요", "인터넷 연결을 확인해 주세요")
            }
            clearProposalLater(p.id)
        }
    }

    private fun clearProposalLater(id: Int) {
        viewModelScope.launch {
            delay(2600)
            _ui.update {
                if (it.proposal?.id == id && it.proposal.status != ProposalStatus.OPEN) it.copy(proposal = null, askOpen = false) else it
            }
        }
    }

    private fun showToast(title: String, sub: String) {
        toastJob?.cancel()
        _ui.update { it.copy(toast = GroupToast(title, sub)) }
        toastJob = viewModelScope.launch {
            delay(5500)
            _ui.update { it.copy(toast = null) }
        }
    }

    private fun speak(text: String) {
        if (ttsReady) runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gachi") }
    }

    private fun now(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

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

    /** 처음부터: 모든 차를 출발지로 되돌리고 원래 경로를 다시 받는다 */
    fun restart() {
        stop()
        drivenM = 0.0
        nudgeShown = false
        _ui.update {
            DriveUi(simSpeed = it.simSpeed, mode = it.mode)
        }
        loadRoute(DemoTrip.start)
        loadOtherCars()
    }

    private fun moveTo(alongM: Double, kmh: Int, me: LngLat? = null) {
        _ui.update { s ->
            s.copy(alongM = alongM, speedKmh = kmh, me = me ?: s.line?.pointAt(alongM))
        }
    }

    private fun arrive() {
        driveJob?.cancel()
        _ui.update { it.copy(running = false, arrived = true, speedKmh = 0, follow = false, nudge = false, sheetOpen = false) }
        speak("${DemoTrip.DESTINATION_NAME}에 도착했어요.")
    }

    private fun stop() {
        driveJob?.cancel()
        driveJob = null
        _ui.update { it.copy(running = false, speedKmh = 0) }
    }
}
