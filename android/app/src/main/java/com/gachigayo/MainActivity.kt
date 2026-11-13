package com.gachigayo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gachigayo.core.designsystem.GachiTheme
import com.gachigayo.data.vehicle.VehicleRepository
import com.gachigayo.feature.home.HomeScreen
import com.gachigayo.feature.home.VehicleStatus

class MainActivity : ComponentActivity() {

    private lateinit var vehicle: VehicleRepository
    private var status by mutableStateOf<VehicleStatus>(VehicleStatus.Connecting)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vehicle = VehicleRepository(this)
        setContent {
            GachiTheme { HomeScreen(status = status, onRefresh = ::refresh) }
        }
        refresh()
    }

    private fun refresh() {
        status = VehicleStatus.Connecting
        vehicle.connect()
            .onSuccess {
                vehicle.readOdometer(
                    onResult = { km -> status = VehicleStatus.Connected(km) },
                    onError = { e -> status = VehicleStatus.Error(e.message ?: e.javaClass.simpleName) },
                )
            }
            .onFailure { e -> status = VehicleStatus.Error(e.message ?: e.javaClass.simpleName) }
    }

    override fun onDestroy() {
        vehicle.release()
        super.onDestroy()
    }
}
