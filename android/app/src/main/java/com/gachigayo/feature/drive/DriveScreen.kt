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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.gachigayo.feature.group.GroupDemo
import com.gachigayo.feature.group.Member
import com.gachigayo.feature.group.Proposal
import com.gachigayo.feature.group.ProposalStatus
import com.gachigayo.feature.group.Reason
import com.gachigayo.feature.group.RestArea
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
    group: GroupActions,
) {
    val others = ui.cars.mapNotNull { c -> c.position?.let { MapCar(it, c.member.colorHex, c.member.alias) } }
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
            padding = MapPadding(left = 380, top = 80, right = 80, bottom = 150),
            modifier = Modifier.fillMaxSize(),
            others = others,
            otherLines = otherLines,
            pins = pins,
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
                Column(
                    Modifier.align(Alignment.TopStart).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!ui.arrived && !ui.resting) ui.next?.let { TurnCard(it) }
                    GroupPanel(ui, group)
                }
                TopControls(ui, onMode, onToggleFollow, Modifier.align(Alignment.TopEnd).padding(16.dp))
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ui.toast?.let { ToastCard(it.title, it.sub) }
                    if (ui.rerouting && ui.line != null) StatusChip("모든 차의 경로를 다시 찾는 중…")
                    if (ui.resting) StatusChip("${ui.restName}에서 쉬는 중 · 약 ${(ui.restLeftS / 60).toInt() + 1}분 뒤 다시 출발")
                    ui.gpsMessage?.let { StatusChip(it) }
                }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, bottom = 60.dp)) {
                    when {
                        ui.sheetOpen -> ProposeSheet(ui, group)
                        ui.proposal != null -> AskCard(ui.proposal, group)
                        ui.nudge -> NudgeCard(ui.battery, group)
                    }
                }
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


// ---------- 그룹 (시연: 다른 차의 위치·대답은 앱 안에서 흉내 낸다) ----------

data class GroupActions(
    val onOpenSheet: () -> Unit,
    val onCloseSheet: () -> Unit,
    val onReason: (Reason) -> Unit,
    val onPropose: (String) -> Unit,
    val onVote: (Boolean) -> Unit,
    val onCancel: () -> Unit,
    val onCloseAsk: () -> Unit,
    val onIncoming: () -> Unit,
    val onNudgeYes: () -> Unit,
    val onNudgeNo: () -> Unit,
    val restOptions: (DriveUi) -> List<Pair<RestArea, String?>>,
)

@Composable
private fun Avatar(m: Member, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(m.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(m.short, color = Color.White, fontSize = (size * 0.42).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GroupPanel(ui: DriveUi, a: GroupActions) {
    Column(
        Modifier
            .width(340.dp)
            .background(MaterialTheme.colorScheme.surface, Shape)
            .border(1.dp, GachiColors.Line, Shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("같이 가는 차", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
            Spacer(Modifier.weight(1f))
            Text("${GroupDemo.NAME} · 계획 v${ui.plan}", fontSize = 12.sp, color = GachiColors.Muted)
        }
        val myStatus = when {
            ui.arrived -> "도착"
            ui.resting -> "${ui.restName}에서 쉬는 중"
            ui.running -> "${ui.speedKmh} km/h로 달리는 중"
            else -> "출발 준비 완료"
        }
        MemberRow(GroupDemo.ME, me = true, status = myStatus, energy = "배터리 ${ui.battery}%", low = ui.battery <= 25)
        ui.cars.forEach { c ->
            val asking = ui.proposal?.let { it.from == c.member.id && it.status == ProposalStatus.OPEN } == true
            MemberRow(
                c.member,
                me = false,
                status = if (asking) "질문을 보냈어요" else c.status,
                energy = "${c.member.kind.energyLabel} ${c.energy}%",
                low = false,
                asking = asking,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionButton("휴게소 들르자", filled = true, onClick = a.onOpenSheet)
            Text(
                "시연 · 큰집 요청 받기",
                modifier = Modifier
                    .clip(Small)
                    .clickable(role = Role.Button, onClick = a.onIncoming)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = GachiColors.Primary,
            )
        }
    }
}

@Composable
private fun MemberRow(m: Member, me: Boolean, status: String, energy: String, low: Boolean, asking: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(m)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.alias, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
                if (me) Text(" · 나", fontSize = 14.sp, color = GachiColors.Muted)
                if (asking) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "질문",
                        Modifier
                            .background(GachiColors.Accent, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GachiColors.Ink,
                    )
                }
            }
            Text(status, fontSize = 13.sp, color = GachiColors.Muted, maxLines = 1)
        }
        Text(
            energy,
            Modifier
                .background(if (low) Color(0xFFFFE8E0) else GachiColors.Soft, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (low) Color(0xFFC2410C) else GachiColors.Ink,
        )
    }
}

@Composable
private fun RightCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .width(450.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .border(1.dp, GachiColors.Line, RoundedCornerShape(20.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun AskCard(p: Proposal, a: GroupActions) {
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
            if (p.status != ProposalStatus.OPEN) {
                Text(
                    "닫기",
                    Modifier.clip(Small).clickable(onClick = a.onCloseAsk).padding(8.dp),
                    fontSize = 14.sp,
                    color = GachiColors.Muted,
                )
            }
        }
        Text(
            p.text,
            Modifier
                .fillMaxWidth()
                .background(GachiColors.Accent, RoundedCornerShape(14.dp))
                .padding(16.dp),
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            color = GachiColors.Ink,
        )
        Text("${rest.name} · ${p.reason.label} · ${p.reason.stayMin}분 쉬기", fontSize = 14.sp, color = GachiColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupDemo.ALL.forEach { m ->
                val v = p.votes[m.id]
                Row(
                    Modifier
                        .background(if (v == true) Color(0xFFE3F4F1) else GachiColors.Soft, RoundedCornerShape(10.dp))
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
            p.votes[GroupDemo.ME.id] == null -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("좋아요", filled = true, onClick = { a.onVote(true) }, height = 64, modifier = Modifier.weight(1f))
                ActionButton("다음에", filled = false, onClick = { a.onVote(false) }, height = 64, modifier = Modifier.weight(1f))
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
            Text(
                "닫기",
                Modifier.clip(Small).clickable(onClick = a.onCloseSheet).padding(8.dp),
                fontSize = 15.sp,
                color = GachiColors.Muted,
            )
        }
        Text("이유", fontSize = 13.sp, color = GachiColors.Muted)
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
        Text("경로 위 휴게소", fontSize = 13.sp, color = GachiColors.Muted)
        options.forEach { (r, why) ->
            val ahead = ui.line?.let { it.snap(r.at).alongM - ui.alongM } ?: 0.0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Small)
                    .background(GachiColors.Soft)
                    .clickable(enabled = why == null, role = Role.Button) { a.onPropose(r.id) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        r.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (why == null) GachiColors.Ink else GachiColors.Muted,
                    )
                    Text(why ?: "${formatDistance(ahead)} 앞 · ${ui.reason.stayMin}분 쉬기", fontSize = 13.sp, color = GachiColors.Muted)
                }
                if (why == null) Text("물어보기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GachiColors.Primary)
            }
        }
        Text("모두 '좋아요'를 누르면 모든 차의 경로에 휴게소가 함께 추가돼요.", fontSize = 13.sp, color = GachiColors.Muted)
    }
}

@Composable
private fun NudgeCard(battery: Int, a: GroupActions) {
    RightCard {
        Text("배터리가 ${battery}%예요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GachiColors.Ink)
        Text("다음 휴게소에서 다 같이 충전하고 쉬어 가자고 물어볼까요?", fontSize = 15.sp, color = GachiColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("물어보기", filled = true, onClick = a.onNudgeYes)
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
