package com.gachigayo.feature.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gachigayo.core.designsystem.GachiColors
import com.gachigayo.data.route.LngLat
import com.gachigayo.data.route.Route
import java.time.LocalTime
import java.time.format.DateTimeFormatter

sealed interface DriveState {
    data object Loading : DriveState
    data class Ready(val route: Route) : DriveState
    data class Error(val message: String) : DriveState
}

private val TurnCardWidth = 340.dp
private val BarHeight = 88.dp

@Composable
fun DriveScreen(
    state: DriveState,
    me: LngLat?,
    destinationName: String,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        RouteMap(
            route = (state as? DriveState.Ready)?.route,
            me = me,
            padding = MapPadding(left = 380, top = 60, right = 60, bottom = 140),
            modifier = Modifier.fillMaxSize(),
        )

        when (state) {
            DriveState.Loading -> StatusChip("경로를 찾는 중…", Modifier.align(Alignment.Center))
            is DriveState.Error -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusChip(state.message)
                Button(onClick = onRetry, modifier = Modifier.height(56.dp)) { Text("다시 찾기", fontSize = 18.sp) }
            }
            is DriveState.Ready -> {
                nextTurn(state.route)?.let { TurnCard(it, Modifier.align(Alignment.TopStart).padding(16.dp)) }
                BottomBar(state.route, destinationName, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}

@Composable
private fun TurnCard(turn: NextTurn, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(TurnCardWidth)
            .background(GachiColors.Primary, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(60.dp)
                .background(Color.White, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            TurnArrow(turn.arrowDegrees, GachiColors.Primary, Modifier.size(34.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatDistance(turn.distanceM), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(turn.instruction, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        }
    }
}

/** 위쪽 화살표를 그리고 회전시켜 방향을 표시 */
@Composable
private fun TurnArrow(degrees: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.rotate(degrees)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val shaft = Path().apply { moveTo(w / 2, h * 0.9f); lineTo(w / 2, h * 0.15f) }
        val head = Path().apply {
            moveTo(w * 0.2f, h * 0.42f); lineTo(w / 2, h * 0.12f); lineTo(w * 0.8f, h * 0.42f)
        }
        drawPath(shaft, color, style = stroke)
        drawPath(head, color, style = stroke)
    }
}

@Composable
private fun BottomBar(route: Route, destinationName: String, modifier: Modifier = Modifier) {
    val eta = LocalTime.now().plusSeconds(route.durationS.toLong())
        .format(DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, GachiColors.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(eta, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(" 도착", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "${formatDistance(route.distanceM)} · ${formatDuration(route.durationS)} 남음 · $destinationName",
                fontSize = 14.sp,
                color = GachiColors.Muted,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StatusChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, GachiColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
