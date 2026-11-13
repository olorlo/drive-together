package com.gachigayo.data.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle

/**
 * Pleos Vehicle SDK 래퍼.
 * 화면(feature)에서는 Vehicle SDK를 직접 부르지 않고 이 클래스를 통해서만 차량 정보를 읽는다.
 * SDK 콜백은 백그라운드 스레드에서 오므로, 결과는 메인 스레드로 넘겨준다.
 */
class VehicleRepository(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null

    /** SDK 연결. 실패하면 예외 메시지를 돌려준다. */
    fun connect(): Result<Unit> = runCatching {
        if (vehicle == null) {
            vehicle = Vehicle(appContext).also { it.initialize() }
        }
    }

    /** 누적 주행거리(km) 읽기 */
    fun readOdometer(onResult: (Float?) -> Unit, onError: (Exception) -> Unit) {
        val v = vehicle ?: return onError(IllegalStateException("Vehicle SDK가 연결되지 않았어요"))
        try {
            v.getOdometer().getCurrentDistanceDriven(
                { km -> main.post { onResult(km) } },
                { e -> main.post { onError(e) } },
            )
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun release() {
        runCatching { vehicle?.release() }
        vehicle = null
    }
}
