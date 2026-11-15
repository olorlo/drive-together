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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.gachigayo.feature.group.CarKind
import com.gachigayo.feature.group.GroupDemo
import com.gachigayo.feature.group.Member
import com.gachigayo.feature.group.Proposal
import com.gachigayo.feature.group.ProposalStatus
import com.gachigayo.feature.group.Reason
import com.gachigayo.feature.group.RestArea
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Shape = RoundedCornerShape(16.dp)
private val Small = RoundedCornerShape(12.dp)
private val Dim = Color(0x6B1B2430)
private val TealSoft = Color(0xFFE3F4F1)

/** 그룹 화면에서 쓰는 동작 모음 */
data class GroupActions(
    val onOpenSheet: () -> Unit,
    val onCloseSheet: () -> Unit,
    val onReason: (Reason) -> Unit,
    val onPropose: (String) -> Unit,
    val onVote: (Boolean) -> Unit,
    val onCancel: () -> Unit,
    val onCloseAsk: () -> Unit,
    val onOpenAsk: () -> Unit,
    val onIncoming: () -> Unit,
    val onNudgeYes: () -> Unit,
    val onNudgeNo: () -> Unit,
    val onCreate: () -> Unit,
    val onGo: () -> Unit,
    val onEnd: () -> Unit,
    val onResume: () -> Unit,
    val restOptions: (DriveUi) -> List<Pair<RestArea, String?>>,
)

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
    group: GroupActions,
) {
    val drive = ui.phase == GroupPhase.DRIVE
    var zoom by remember { mutableStateOf<ZoomRequest?>(null) }
    var zoomSeq by remember { mutableIntStateOf(0) }

    val proposal = ui.proposal
    val askingFrom = proposal?.takeIf { it.status == ProposalStatus.OPEN && it.from != GroupDemo.ME.id && it.votes[GroupDemo.ME.id] == null }?.from
    val cars = buildList {
        ui.me?.let { add(MapCar("A", it, ui.line?.bearingAt(ui.alongM) ?: 90f, GroupDemo.ME.color.toInt(), "${GroupDemo.ME.alias} · 나", me = true)) }
        ui.cars.forEach { c ->
            val pos = c.position ?: return@forEach
            add(
                MapCar(
                    c.member.id, pos, c.line?.bearingAt(c.alongM) ?: 90f, c.member.color.toInt(), c.member.alias, me = false,
                    bubble = if (askingFrom == c.member.id) proposal?.reason?.label else null,
                ),
            )
        }
    }
    val otherLines = remember(ui.cars.map { it.line }) {
        ui.cars.mapNotNull { c -> c.line?.let { MapLine(it.points, c.member.colorHex) } }
    }
    val pins = remember(ui.stopName, ui.restName) {
        GroupDemo.RESTS.map { MapPin(it.at, it.name, planned = it.name == ui.stopName || it.name == ui.restName) }
    }

    Box(Modifier.fillMaxSize()) {
        RouteMap(
            line = ui.line,
            alongM = ui.alongM,
            me = ui.me,
            follow = ui.follow,
            padding = MapPadding(left = 420, top = 150, right = 400, bottom = 150),
            modifier = Modifier.fillMaxSize(),
            others = cars,
            otherLines = otherLines,
            pins = pins,
            zoomRequest = zoom,
        )

        StatusBar(ui, onMode, Modifier.align(Alignment.TopCenter))

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
            drive -> {
                if (!ui.arrived) {
                    Box(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 72.dp)) {
                        if (ui.resting) RestCard(ui) else ui.next?.let { TurnCard(it) }
                    }
                }
                GroupPanel(ui, askingFrom, group, Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 72.dp))
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 76.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ui.toast?.let { ToastCard(it.title, it.sub) }
                    if (ui.rerouting && ui.line != null) StatusChip("모든 차의 경로를 다시 찾는 중…")
                    ui.gpsMessage?.let { StatusChip(it) }
                }
                ZoomColumn(
                    follow = ui.follow,
                    onZoom = { d -> zoomSeq++; zoom = ZoomRequest(zoomSeq, d) },
                    onToggleFollow = onToggleFollow,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 128.dp),
                )
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 96.dp, top = 40.dp)) {
                    when {
                        ui.sheetOpen -> ProposeSheet(ui, group)
                        proposal != null && ui.askOpen -> AskCard(ui, proposal, group)
                        ui.nudge && proposal == null -> NudgeCard(ui, group)
                    }
                }
                BottomBar(ui, destinationName, askingFrom != null, onTogglePlay, onSpeed, group, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                if (ui.arrived) Cover { ArrivedCover(ui, destinationName, group) }
            }
            ui.phase == GroupPhase.CREATE -> Cover { CreateCover(destinationName, group) }
            ui.phase == GroupPhase.INVITE -> Cover { InviteCover(ui, group) }
            ui.phase == GroupPhase.ENDED -> Cover { EndedCover(onRestart) }
        }
    }
}

// ---------- 상단 바 ----------

@Composable
private fun StatusBar(ui: DriveUi, onMode: (LocationMode) -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(5_000)
        }
    }
    val moving = ui.phase == GroupPhase.DRIVE && ui.running && !ui.resting
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, GachiColors.Line)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
            Text("${GroupDemo.ME.alias} · 전기차", fontSize = 15.sp, color = GachiColors.Muted)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("같이 가요", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
            Text(GroupDemo.NAME, fontSize = 13.sp, color = GachiColors.Muted)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Pill("${if (moving) ui.speedKmh else 0} km/h")
            Pill(if (moving) "D" else "P")
            Pill(if (ui.mode == LocationMode.SIMULATION) "시뮬레이션" else "실제 위치") {
                onMode(if (ui.mode == LocationMode.SIMULATION) LocationMode.GPS else LocationMode.SIMULATION)
            }
            Row(
                Modifier.background(GachiColors.Soft, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF16A34A)))
                Text("연결됨", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = GachiColors.Ink)
            }
        }
    }
}

@Composable
private fun Pill(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GachiColors.Soft)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = GachiColors.Ink,
    )
}

// ---------- 다음 안내 / 쉬는 중 ----------

@Composable
private fun TurnCard(turn: NextTurn) {
    Row(
        modifier = Modifier
            .width(340.dp)
            .background(GachiColors.Primary, Shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(60.dp).background(Color.White, Small), contentAlignment = Alignment.Center) {
            if (turn.instruction.contains("같이 쉬어요")) CupIcon(GachiColors.Primary, Modifier.size(32.dp))
            else TurnArrow(turn.arrowDegrees, GachiColors.Primary, Modifier.size(34.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatDistance(turn.distanceM), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(turn.instruction, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        }
    }
}

@Composable
private fun RestCard(ui: DriveUi) {
    Row(
        modifier = Modifier
            .width(340.dp)
            .background(MaterialTheme.colorScheme.surface, Shape)
            .border(2.dp, GachiColors.Primary, Shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(60.dp).background(TealSoft, Small), contentAlignment = Alignment.Center) {
            CupIcon(GachiColors.Primary, Modifier.size(32.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ui.restName ?: "휴게소", color = GachiColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("쉬는 중 · 약 ${(ui.restLeftS / 60).toInt() + 1}분 뒤 출발", color = GachiColors.Muted, fontSize = 15.sp)
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

/** 컵 아이콘 (휴게소) — 24 단위 격자 기준 */
@Composable
private fun CupIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.width / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val cup = Path().apply {
            moveTo(4 * u, 9 * u); lineTo(17 * u, 9 * u); lineTo(17 * u, 14 * u)
            cubicTo(17 * u, 17 * u, 15 * u, 19 * u, 12 * u, 19 * u)
            lineTo(9 * u, 19 * u)
            cubicTo(6 * u, 19 * u, 4 * u, 17 * u, 4 * u, 14 * u)
            close()
        }
        val handle = Path().apply {
            moveTo(17 * u, 11 * u); lineTo(18.5f * u, 11 * u)
            cubicTo(21.5f * u, 11 * u, 21.5f * u, 16 * u, 18.5f * u, 16 * u)
            lineTo(17 * u, 16 * u)
        }
        val steam = Path().apply { moveTo(8 * u, 3 * u); lineTo(8 * u, 6 * u); moveTo(12 * u, 3 * u); lineTo(12 * u, 6 * u) }
        drawPath(cup, color, style = stroke)
        drawPath(handle, color, style = stroke)
        drawPath(steam, color, style = stroke)
    }
}

/** 번개(배터리) / 주유기(연료) 작은 아이콘 */
@Composable
private fun EnergyIcon(kind: CarKind, color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(width = 2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (kind == CarKind.EV) {
            val p = Path().apply {
                moveTo(13 * u, 2 * u); lineTo(4 * u, 14 * u); lineTo(11 * u, 14 * u); lineTo(10 * u, 22 * u)
                lineTo(19 * u, 10 * u); lineTo(12 * u, 10 * u); close()
            }
            drawPath(p, color, style = stroke)
        } else {
            val p = Path().apply {
                moveTo(4 * u, 21 * u); lineTo(4 * u, 4 * u); lineTo(14 * u, 4 * u); lineTo(14 * u, 21 * u)
                moveTo(3 * u, 21 * u); lineTo(15 * u, 21 * u)
                moveTo(14 * u, 9 * u); lineTo(17 * u, 9 * u); lineTo(18 * u, 10 * u); lineTo(18 * u, 16 * u)
                lineTo(20 * u, 16 * u); lineTo(20 * u, 8 * u); lineTo(17 * u, 5 * u)
            }
            drawPath(p, color, style = stroke)
        }
    }
}

// ---------- 오른쪽 위: 같이 가는 차 ----------

@Composable
private fun Avatar(m: Member, size: Int = 36) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(m.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(m.short, color = Color.White, fontSize = (size * 0.42).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GroupPanel(ui: DriveUi, askingFrom: String?, a: GroupActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(320.dp)
            .background(MaterialTheme.colorScheme.surface, Shape)
            .border(1.dp, GachiColors.Line, Shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("같이 가는 차", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
            Spacer(Modifier.weight(1f))
            Text("계획 v${ui.plan}", fontSize = 12.sp, color = GachiColors.Muted)
        }
        val myStatus = when {
            ui.arrived -> "도착"
            ui.resting -> "${ui.restName}에서 쉬는 중"
            ui.running -> "${ui.speedKmh} km/h로 달리는 중"
            else -> "출발 준비 완료"
        }
        MemberRow(GroupDemo.ME, "${GroupDemo.ME.alias} (나)", myStatus, "${ui.battery}%", low = ui.battery <= 25)
        ui.cars.forEach { c ->
            if (askingFrom == c.member.id) {
                MemberRow(c.member, c.member.alias, c.status, "", low = false, onAsk = a.onOpenAsk)
            } else {
                MemberRow(c.member, c.member.alias, c.status, "${c.energy}%", low = false)
            }
        }
        Text(
            "시연 · 큰집 요청 받기",
            modifier = Modifier
                .clip(Small)
                .clickable(role = Role.Button, onClick = a.onIncoming)
                .padding(vertical = 4.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = GachiColors.Primary,
        )
    }
}

@Composable
private fun MemberRow(m: Member, name: String, status: String, energy: String, low: Boolean, onAsk: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(m)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
            Text(status, fontSize = 13.sp, color = GachiColors.Muted, maxLines = 1)
        }
        if (onAsk != null) {
            Text(
                "질문 1",
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GachiColors.Accent)
                    .clickable(role = Role.Button, onClick = onAsk)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GachiColors.Ink,
            )
        } else {
            val color = if (low) Color(0xFFC2410C) else GachiColors.Muted
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                EnergyIcon(m.kind, color)
                Text(energy, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

// ---------- 지도 조작 ----------

@Composable
private fun ZoomColumn(follow: Boolean, onZoom: (Double) -> Unit, onToggleFollow: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("+" to 1.0, "−" to -1.0).forEach { (label, d) ->
            Box(
                Modifier
                    .size(56.dp)
                    .clip(Small)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, GachiColors.Line, Small)
                    .clickable(role = Role.Button) { onZoom(d) },
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink) }
        }
        Box(
            Modifier
                .size(56.dp)
                .clip(Small)
                .background(if (follow) MaterialTheme.colorScheme.surface else GachiColors.Primary)
                .border(1.dp, if (follow) GachiColors.Line else GachiColors.Primary, Small)
                .clickable(role = Role.Button, onClick = onToggleFollow),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (follow) "전체" else "내 차", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (follow) GachiColors.Ink else Color.White)
        }
    }
}

// ---------- 아래: 도착 시간 · 버튼 ----------

@Composable
private fun BottomBar(
    ui: DriveUi,
    destinationName: String,
    hasQuestion: Boolean,
    onTogglePlay: () -> Unit,
    onSpeed: (Int) -> Unit,
    a: GroupActions,
    modifier: Modifier = Modifier,
) {
    val eta = LocalTime.now().plusSeconds(ui.remainingS.toLong())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN))
    val open = ui.proposal?.status == ProposalStatus.OPEN
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(MaterialTheme.colorScheme.surface, Shape)
            .border(1.dp, GachiColors.Line, Shape)
            .padding(start = 24.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(eta, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
                    Text(" 도착", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = GachiColors.Ink)
                }
                ui.lastDiffMin?.let { d ->
                    Text(
                        "경로 변경 ${if (d >= 0) "+" else ""}${d}분",
                        Modifier.background(GachiColors.Accent, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GachiColors.Ink,
                    )
                }
            }
            Text(
                "${formatDistance(ui.remainingM)} · ${formatDuration(ui.remainingS)} 남음 · $destinationName",
                fontSize = 14.sp,
                color = GachiColors.Muted,
            )
        }
        Spacer(Modifier.weight(1f))
        if (ui.resting) {
            ActionButton("출발 준비 완료", filled = true, onClick = a.onResume, height = 60)
        } else {
            TintButton("휴게소 들르자", enabled = !open, onClick = a.onOpenSheet)
        }
        if (open) {
            val mine = ui.proposal?.from == GroupDemo.ME.id
            Box(
                Modifier
                    .height(60.dp)
                    .clip(Small)
                    .background(GachiColors.Accent)
                    .clickable(role = Role.Button, onClick = a.onOpenAsk)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (mine) "대답 보기" else if (hasQuestion) "질문 보기" else "투표 보기",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = GachiColors.Ink,
                )
            }
        }
        if (ui.mode == LocationMode.SIMULATION) {
            Box(Modifier.width(1.dp).height(48.dp).background(GachiColors.Line))
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
        }
    }
}

@Composable
private fun TintButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(60.dp)
            .clip(Small)
            .background(TealSoft.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val c = GachiColors.Primary.copy(alpha = if (enabled) 1f else 0.45f)
        CupIcon(c, Modifier.size(22.dp))
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c)
    }
}

// ---------- 오른쪽 카드: 질문 · 휴게소 선택 · 충전 제안 ----------

@Composable
private fun RightCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .width(460.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .border(1.dp, GachiColors.Line, RoundedCornerShape(20.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text,
        Modifier.background(GachiColors.Soft, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        fontSize = 13.sp,
        color = GachiColors.Ink,
    )
}

/** 경로 위 휴게소까지 남은 시간(분) */
private fun minutesTo(ui: DriveUi, r: RestArea, id: String): Int? {
    if (id == GroupDemo.ME.id) {
        val l = ui.line ?: return null
        val ahead = l.snap(r.at).alongM - ui.alongM
        return (ahead / (l.lengthM / l.route.durationS) / 60).toInt().coerceAtLeast(1)
    }
    val c = ui.cars.firstOrNull { it.member.id == id } ?: return null
    val l = c.line ?: return null
    val ahead = l.snap(r.at).alongM - c.alongM
    return (ahead / (l.lengthM / l.route.durationS) / 60).toInt().coerceAtLeast(1)
}

@Composable
private fun AskCard(ui: DriveUi, p: Proposal, a: GroupActions) {
    val from = GroupDemo.member(p.from)
    val mine = p.from == GroupDemo.ME.id
    val rest = GroupDemo.rest(p.restId)
    RightCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(from, 36)
            Text(
                if (mine) "내가 물어봤어요" else "${GroupDemo.subject(from.alias)} 물어봤어요",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = GachiColors.Ink,
            )
            Spacer(Modifier.weight(1f))
            Text(p.at, fontSize = 13.sp, color = GachiColors.Muted)
            Text("닫기", Modifier.clip(Small).clickable(onClick = a.onCloseAsk).padding(8.dp), fontSize = 14.sp, color = GachiColors.Muted)
        }
        Text(p.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = GachiColors.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaChip(rest.name)
            minutesTo(ui, rest, GroupDemo.ME.id)?.let { MetaChip("나는 약 ${it}분 뒤 도착") }
            MetaChip("쉬는 시간 ${p.reason.stayMin}분")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupDemo.ALL.forEach { m ->
                val v = p.votes[m.id]
                Row(
                    Modifier
                        .background(if (v == true) TealSoft else GachiColors.Soft, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Avatar(m, 22)
                    val answer = when (v) {
                        true -> "좋아요"
                        false -> "다음에"
                        null -> "대답 전"
                    }
                    Text("${m.alias} $answer", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = GachiColors.Ink)
                }
            }
        }
        when {
            p.status == ProposalStatus.AGREED ->
                Text("모두 좋대요. 모든 차의 경로를 바꿨어요", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GachiColors.Primary)
            p.status == ProposalStatus.DECLINED ->
                Text("이번엔 다음으로 미뤘어요. 경로는 그대로예요", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GachiColors.Muted)
            mine -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("다른 차의 대답을 기다리는 중…", fontSize = 15.sp, color = GachiColors.Muted, modifier = Modifier.weight(1f))
                ActionButton("요청 취소", filled = false, onClick = a.onCancel)
            }
            p.votes[GroupDemo.ME.id] == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("좋아요", filled = true, onClick = { a.onVote(true) }, height = 64, modifier = Modifier.weight(1f))
                    ActionButton("다음에", filled = false, onClick = { a.onVote(false) }, height = 64, modifier = Modifier.weight(1f))
                }
            }
            else -> Text("대답했어요. 다른 차를 기다리는 중이에요", fontSize = 15.sp, color = GachiColors.Muted)
        }
    }
}

@Composable
private fun ProposeSheet(ui: DriveUi, a: GroupActions) {
    val options = a.restOptions(ui)
    RightCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("어디서 쉬어 갈까요?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
            Spacer(Modifier.weight(1f))
            ActionButton("닫기", filled = false, onClick = a.onCloseSheet, height = 40)
        }
        Text("모두에게 물어봐요. 모두 좋다고 하면 모든 차의 경로에 휴게소가 들어가요.", fontSize = 14.sp, color = GachiColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Reason.entries.forEach { r ->
                val on = r == ui.reason
                Text(
                    r.label,
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) GachiColors.Primary else GachiColors.Soft)
                        .clickable(role = Role.RadioButton) { a.onReason(r) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) Color.White else GachiColors.Ink,
                )
            }
        }
        options.forEach { (r, why) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, GachiColors.Line, Small)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).background(TealSoft, Small), contentAlignment = Alignment.Center) {
                    CupIcon(GachiColors.Primary, Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(r.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (why == null) GachiColors.Ink else GachiColors.Muted)
                    Text(why ?: "모두의 진행 방향 · ${ui.reason.stayMin}분 쉬기", fontSize = 13.sp, color = GachiColors.Muted)
                    if (why == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GroupDemo.ALL.forEach { m ->
                                minutesTo(ui, r, m.id)?.let { min ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(m.color)))
                                        Text("${m.alias} ${min}분", fontSize = 12.sp, color = GachiColors.Muted)
                                    }
                                }
                            }
                        }
                    }
                }
                ActionButton("물어보기", filled = why == null, onClick = { if (why == null) a.onPropose(r.id) })
            }
        }
    }
}

@Composable
private fun NudgeCard(ui: DriveUi, a: GroupActions) {
    val next = a.restOptions(ui).firstOrNull { it.second == null }?.first
    RightCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EnergyIcon(CarKind.EV, GachiColors.Primary)
            Text("배터리 ${ui.battery}%", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
        }
        Text(
            next?.let { "${it.name}에서 다 같이 충전하고 쉬어 가자고 물어볼까요?" } ?: "경로 위에 들를 수 있는 휴게소가 없어요.",
            fontSize = 15.sp,
            color = GachiColors.Muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (next != null) ActionButton("같이 충전하자고 하기", filled = true, onClick = a.onNudgeYes)
            ActionButton("괜찮아요", filled = false, onClick = a.onNudgeNo)
        }
    }
}

@Composable
private fun ToastCard(title: String, sub: String) {
    Column(
        Modifier
            .background(GachiColors.Ink, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(sub, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

// ---------- 가운데 덮개: 그룹 만들기 · 초대 · 도착 · 끝 ----------

@Composable
private fun Cover(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(top = 56.dp).background(Dim), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(560.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
                .padding(30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
private fun Field(label: String, value: String, sub: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, color = GachiColors.Muted)
        Row(
            Modifier.fillMaxWidth().border(1.dp, GachiColors.Line, Small).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = GachiColors.Ink)
            sub?.let { Text(it, fontSize = 14.sp, color = GachiColors.Muted) }
        }
    }
}

@Composable
private fun CreateCover(destinationName: String, a: GroupActions) {
    Text("어디로 같이 갈까요?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
    Text("따로 출발해도 한 지도에서 함께 가요.", fontSize = 15.sp, color = GachiColors.Muted)
    Field("목적지", destinationName, "강원 강릉시")
    Field("그룹 이름", GroupDemo.NAME)
    ActionButton("그룹 만들고 초대하기", filled = true, onClick = a.onCreate, height = 60, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun InviteCover(ui: DriveUi, a: GroupActions) {
    Text(GroupDemo.NAME, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
    Text("초대 코드를 가족 단톡방에 보내면 차에서 바로 참여해요.", fontSize = 15.sp, color = GachiColors.Muted)
    Row(
        Modifier.fillMaxWidth().background(GachiColors.Soft, Small).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("초대 코드", fontSize = 13.sp, color = GachiColors.Muted)
            Text("482 913", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink, letterSpacing = 2.sp)
        }
        ActionButton("초대 링크 보내기", filled = false, onClick = {}, height = 44)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GroupDemo.ALL.forEach { m ->
            val joined = m.id in ui.joined
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(m)
                Column(Modifier.weight(1f)) {
                    Text(if (m.id == GroupDemo.ME.id) "${m.alias} (나)" else m.alias, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = GachiColors.Ink)
                    Text(
                        when (m.kind) {
                            CarKind.EV -> "전기차"
                            CarKind.GAS -> "가솔린"
                            CarKind.HYBRID -> "하이브리드"
                        },
                        fontSize = 12.sp,
                        color = GachiColors.Muted,
                    )
                }
                Text(
                    if (joined) "✓ 참여" else "초대 보냄",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (joined) GachiColors.Primary else GachiColors.Muted,
                )
            }
        }
    }
    val n = ui.joined.size
    ActionButton(
        "${n}대 같이 출발",
        filled = n >= 2,
        onClick = { if (n >= 2) a.onGo() },
        height = 60,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ArrivedCover(ui: DriveUi, destinationName: String, a: GroupActions) {
    Text("$destinationName 도착", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
    Text("오늘 함께 달린 기록이에요. 추억 카드로 남겨 둘게요.", fontSize = 15.sp, color = GachiColors.Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            formatDuration(ui.simElapsedS) to "함께 달린 시간",
            "${ui.stopsVisited}곳" to "같이 들른 휴게소",
            "v${ui.plan}" to "함께 바꾼 계획",
        ).forEach { (v, l) ->
            Column(
                Modifier.weight(1f).background(GachiColors.Soft, Small).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(v, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
                Text(l, fontSize = 13.sp, color = GachiColors.Muted)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(GroupDemo.ME, 30)
            Text("${GroupDemo.ME.alias} (나)", Modifier.weight(1f), fontSize = 15.sp, color = GachiColors.Ink)
            Text("✓ 도착", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GachiColors.Primary)
        }
        ui.cars.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(c.member, 30)
                Text(c.member.alias, Modifier.weight(1f), fontSize = 15.sp, color = GachiColors.Ink)
                Text(
                    if (c.arrived) "✓ 도착" else c.status,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (c.arrived) GachiColors.Primary else GachiColors.Muted,
                )
            }
        }
    }
    ActionButton("그룹 끝내고 위치 공유 멈추기", filled = true, onClick = a.onEnd, height = 60, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun EndedCover(onRestart: () -> Unit) {
    Text("그룹이 끝났어요", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
    Text("모든 차의 위치와 차량 상태 공유를 멈췄어요. 즐거운 명절 보내세요.", fontSize = 15.sp, color = GachiColors.Muted)
    ActionButton("처음부터 다시", filled = true, onClick = onRestart, height = 60, modifier = Modifier.fillMaxWidth())
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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) Color.White else GachiColors.Ink,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, filled: Boolean, onClick: () -> Unit, height: Int = 52, modifier: Modifier = Modifier) {
    Box(
        modifier
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
