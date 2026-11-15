package com.gachigayo.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.gachigayo.data.route.LngLat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** 위치 한 번 */
data class Fix(val at: LngLat, val speedMps: Float?)

/**
 * 기기 GPS 위치.
 * 에뮬레이터: 옆 메뉴 ⋯ → Location → Routes 에서 경로를 재생하면 이 위치가 움직인다.
 * 호출 전에 ACCESS_FINE_LOCATION 권한이 있어야 한다.
 */
class DeviceLocation(context: Context) {

    private val manager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    fun fixes(): Flow<Fix> = callbackFlow {
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.allProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            else -> LocationManager.NETWORK_PROVIDER
        }
        val listener = LocationListener { loc ->
            trySend(Fix(LngLat(loc.longitude, loc.latitude), if (loc.hasSpeed()) loc.speed else null))
        }
        manager.getLastKnownLocation(provider)?.let {
            trySend(Fix(LngLat(it.longitude, it.latitude), null))
        }
        manager.requestLocationUpdates(provider, 500L, 0f, listener, Looper.getMainLooper())
        awaitClose { manager.removeUpdates(listener) }
    }
}
