package com.gachigayo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.gachigayo.core.designsystem.GachiTheme
import com.gachigayo.data.route.LngLat
import com.gachigayo.data.route.RouteRepository
import com.gachigayo.feature.drive.DriveScreen
import com.gachigayo.feature.drive.DriveState
import kotlinx.coroutines.launch

/** 시연용 여정: 서울 → 강릉 */
private object DemoTrip {
    val start = LngLat(127.0276, 37.4979)      // 서울 강남역 부근
    val destination = LngLat(128.8761, 37.7519) // 강릉 시내
    const val destinationName = "강릉 할머니댁"
}

class MainActivity : ComponentActivity() {

    private val routes = RouteRepository()
    private var state by mutableStateOf<DriveState>(DriveState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GachiTheme(dark = false) {
                DriveScreen(
                    state = state,
                    me = DemoTrip.start,
                    destinationName = DemoTrip.destinationName,
                    onRetry = ::loadRoute,
                )
            }
        }
        loadRoute()
    }

    private fun loadRoute() {
        state = DriveState.Loading
        lifecycleScope.launch {
            state = runCatching { routes.fetch(DemoTrip.start, DemoTrip.destination) }
                .fold(
                    onSuccess = { DriveState.Ready(it) },
                    onFailure = { DriveState.Error(it.message ?: "경로를 찾지 못했어요") },
                )
        }
    }
}
