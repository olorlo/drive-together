package com.gachigayo.feature.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gachigayo.core.designsystem.GachiColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Shape = RoundedCornerShape(16.dp)
private val Small = RoundedCornerShape(12.dp)

@Composable
fun DriveScreen(
    ui: DriveUi,
    destinationName: String,
    onRetry: () -> Unit,
    onTogglePlay: () -> Unit,
    onSpeed: (Int) -> Unit,
    onMode: (LocationMode) -> Unit,
    onToggleFollow: () -> Unit,
    onRestart: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        RouteMap(
            line = ui.line,
            alongM = ui.alongM,
            me = ui.me,
            follow = ui.follow,
            padding = MapPadding(left = 380, top = 80, right = 80, bottom = 150),
            modifier = Modifier.fillMaxSize(),
        )

        when {
            ui.loading && ui.line == null -> StatusChip("경로를 찾는 중…", Modifier.align(Alignment.Center))
            ui.error != null && ui.line == null -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusChip(ui.error)
                ActionButton("다시 찾기", filled = true, onClick = onRetry)
            }
            else -> {
                if (!ui.arrived) ui.next?.let { TurnCard(it, Modifier.align(Alignment.TopStart).padding(16.dp)) }
                TopControls(ui, onMode, onToggleFollow, Modifier.align(Alignment.TopEnd).padding(16.dp))
                ui.gpsMessage?.let { StatusChip(it, Modifier.align(Alignment.TopCenter).padding(top = 20.dp)) }
                BottomBar(ui, destinationName, onTogglePlay, onSpeed, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                if (ui.arrived) ArrivedCard(destinationName, onRestart, Modifier.align(Alignment.Center))
            }
        }
    }
}

// ---------- 다음 안내 ----------

@Composable
private fun TurnCard(turn: NextTurn, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(340.dp)
            .background(GachiColors.Primary, Shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(60.dp).background(Color.White, Small), contentAlignment = Alignment.Center) {
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
        val head = Path().apply { moveTo(w * 0.2f, h * 0.42f); lineTo(w / 2, h * 0.12f); lineTo(w * 0.8f, h * 0.42f) }
        drawPath(shaft, color, style = stroke)
        drawPath(head, color, style = stroke)
    }
}

// ---------- 오른쪽 위: 위치 방식 · 따라가기 ----------

@Composable
private fun TopControls(
    ui: DriveUi,
    onMode: (LocationMode) -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Segmented(
            options = listOf(LocationMode.SIMULATION to "시뮬레이션", LocationMode.GPS to "실제 위치"),
            selected = ui.mode,
            onSelect = onMode,
        )
        ActionButton(if (ui.follow) "전체 보기" else "내 차 따라가기", filled = false, onClick = onToggleFollow)
    }
}

// ---------- 아래: 도착 시간 · 주행 조작 ----------

@Composable
private fun BottomBar(
    ui: DriveUi,
    destinationName: String,
    onTogglePlay: () -> Unit,
    onSpeed: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val eta = LocalTime.now().plusSeconds(ui.remainingS.toLong())
        .format(DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(MaterialTheme.colorScheme.surface, Shape)
            .border(1.dp, GachiColors.Line, Shape)
            .padding(start = 24.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(eta, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(" 도착", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "${formatDistance(ui.remainingM)} · ${formatDuration(ui.remainingS)} 남음 · $destinationName",
                fontSize = 14.sp,
                color = GachiColors.Muted,
            )
        }
        Spacer(Modifier.weight(1f))
        SpeedPill(ui.speedKmh)
        if (ui.mode == LocationMode.SIMULATION) {
            Segmented(
                options = listOf(1 to "×1", 10 to "×10", 30 to "×30", 120 to "×120"),
                selected = ui.simSpeed,
                onSelect = onSpeed,
            )
            ActionButton(
                if (ui.running) "멈춤" else if (ui.alongM > 0 && !ui.arrived) "이어서 주행" else "주행 시작",
                filled = !ui.running,
                onClick = onTogglePlay,
                height = 60,
            )
        } else {
            Text(
                "에뮬레이터: ⋯ → Location → Routes에서 경로 재생",
                fontSize = 13.sp,
                color = GachiColors.Muted,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
    }
}

@Composable
private fun SpeedPill(kmh: Int) {
    Column(
        Modifier.background(GachiColors.Soft, Small).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$kmh", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
        Text("km/h", fontSize = 11.sp, color = GachiColors.Muted)
    }
}

// ---------- 도착 ----------

@Composable
private fun ArrivedCard(destinationName: String, onRestart: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(max = 480.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .border(1.dp, GachiColors.Line, RoundedCornerShape(20.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("$destinationName 도착", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("안전하게 도착했어요.", fontSize = 16.sp, color = GachiColors.Muted)
        ActionButton("처음부터 다시", filled = true, onClick = onRestart)
    }
}

// ---------- 공통 ----------

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .clip(Small)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, GachiColors.Line, Small)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) GachiColors.Primary else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) Color.White else GachiColors.Ink,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, filled: Boolean, onClick: () -> Unit, height: Int = 52) {
    Box(
        Modifier
            .height(height.dp)
            .clip(Small)
            .background(if (filled) GachiColors.Primary else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (filled) GachiColors.Primary else GachiColors.Line, Small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.White else GachiColors.Ink)
    }
}

@Composable
private fun StatusChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, Small)
            .border(1.dp, GachiColors.Line, Small)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        fontSize = 17.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
