package com.gachigayo.feature.drive

import com.gachigayo.data.route.RouteLine
import com.gachigayo.data.route.RouteStep

/** 화면에 띄울 다음 안내 */
data class NextTurn(
    val distanceM: Double,
    val instruction: String,
    val arrowDegrees: Float, // 0 = 직진, -90 = 좌, 90 = 우, 180 = 유턴
)

private val SKIP = setOf("depart", "new name", "continue", "notification")

/**
 * 출발점에서 alongM 만큼 왔을 때 다음으로 안내할 지점.
 * 도로 이름만 바뀌는 단계는 건너뛴다.
 */
fun nextTurn(line: RouteLine, alongM: Double): NextTurn? {
    val steps = line.route.steps
    for (i in 1 until steps.size) {
        val at = line.stepAlongM[i]
        if (at <= alongM + 5 || steps[i].type in SKIP) continue
        return NextTurn(at - alongM, instructionOf(steps[i]), arrowOf(steps[i]))
    }
    return null
}

private fun directionOf(modifier: String?): String = when (modifier) {
    "left" -> "좌회전"
    "right" -> "우회전"
    "slight left" -> "왼쪽 방향"
    "slight right" -> "오른쪽 방향"
    "sharp left" -> "급좌회전"
    "sharp right" -> "급우회전"
    "uturn" -> "유턴"
    else -> "직진"
}

private fun sideOf(modifier: String?): String =
    if (modifier?.contains("left") == true) "왼쪽" else "오른쪽"

fun instructionOf(step: RouteStep): String {
    val road = step.road.ifBlank { null }
    val toward = road?.let { " · $it" } ?: ""
    return when (step.type) {
        "arrive" -> "목적지 도착"
        "off ramp" -> "${sideOf(step.modifier)} 출구로 나가기$toward"
        "on ramp" -> "${sideOf(step.modifier)} 진입로로 들어가기$toward"
        "fork" -> "${sideOf(step.modifier)} 길로 가기$toward"
        "merge" -> "합류$toward"
        "roundabout", "rotary" -> "회전교차로$toward"
        "end of road" -> "${directionOf(step.modifier)}$toward"
        else -> "${directionOf(step.modifier)}$toward"
    }
}

private fun arrowOf(step: RouteStep): Float = when (step.modifier) {
    "left" -> -90f
    "right" -> 90f
    "slight left" -> -40f
    "slight right" -> 40f
    "sharp left" -> -135f
    "sharp right" -> 135f
    "uturn" -> 180f
    else -> 0f
}

fun formatDistance(m: Double): String =
    if (m < 1000) "${(m / 10).toInt() * 10} m" else "%.1f km".format(m / 1000)

fun formatDuration(s: Double): String {
    val min = (s / 60).toInt()
    return if (min < 60) "${min}분" else "${min / 60}시간 ${min % 60}분"
}
