package com.gachigayo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.gachigayo.core.designsystem.GachiTheme
import com.gachigayo.feature.drive.DemoTrip
import com.gachigayo.feature.drive.DriveScreen
import com.gachigayo.feature.drive.DriveViewModel
import com.gachigayo.feature.drive.LocationMode

class MainActivity : ComponentActivity() {

    private val drive: DriveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ui by drive.ui.collectAsState()
            val askLocation = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { result -> drive.useMode(LocationMode.GPS, gpsGranted = result.values.any { it }) }

            GachiTheme(dark = false) {
                DriveScreen(
                    ui = ui,
                    destinationName = DemoTrip.DESTINATION_NAME,
                    onRetry = { drive.loadRoute() },
                    onTogglePlay = drive::toggleSimulation,
                    onSpeed = drive::setSimSpeed,
                    onMode = { mode ->
                        if (mode == LocationMode.GPS && !hasLocationPermission()) {
                            askLocation.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                        } else {
                            drive.useMode(mode, gpsGranted = hasLocationPermission())
                        }
                    },
                    onToggleFollow = drive::toggleFollow,
                    onRestart = drive::restart,
                )
            }
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
