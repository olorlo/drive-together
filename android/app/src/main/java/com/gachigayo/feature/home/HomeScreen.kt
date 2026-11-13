package com.gachigayo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gachigayo.core.designsystem.GachiColors
import com.gachigayo.core.designsystem.GachiTheme

sealed interface VehicleStatus {
    data object Connecting : VehicleStatus
    data class Connected(val odometerKm: Float?) : VehicleStatus
    data class Error(val message: String) : VehicleStatus
}

@Composable
fun HomeScreen(status: VehicleStatus, onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("같이 가요", fontSize = 40.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("Vehicle SDK 연결 확인", fontSize = 18.sp, color = GachiColors.Muted)

            val (title, detail) = when (status) {
                VehicleStatus.Connecting -> "연결하는 중…" to ""
                is VehicleStatus.Connected -> "연결됨 ✅" to
                    (status.odometerKm?.let { "누적 주행거리 %.1f km".format(it) } ?: "주행거리 값 없음")
                is VehicleStatus.Error -> "연결 실패 ⚠️" to status.message
            }
            Text(title, fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
            if (detail.isNotEmpty()) Text(detail, fontSize = 20.sp, color = GachiColors.Muted)

            Button(onClick = onRefresh, modifier = Modifier.height(64.dp)) {
                Text("다시 읽기", fontSize = 20.sp)
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 640)
@Composable
private fun HomeScreenPreview() {
    GachiTheme { HomeScreen(VehicleStatus.Connected(1234.5f), onRefresh = {}) }
}
