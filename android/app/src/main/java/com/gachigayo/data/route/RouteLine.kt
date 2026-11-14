package com.gachigayo.data.route

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/** 위치를 경로 위에 맞춘 결과 */
data class Snap(val alongM: Double, val offM: Double)

/**
 * 경로선 위의 거리 계산 도구.
 * "출발점에서 몇 m 왔는지(alongM)"를 기준으로 위치·안내·남은 거리를 계산한다.
 */
class RouteLine(val route: Route) {

    val points: List<LngLat> = route.points
    val lengthM: Double = route.distanceM
    private val cum: DoubleArray

    /** 각 안내 지점이 출발점에서 몇 m 떨어져 있는지 */
    val stepAlongM: DoubleArray

    init {
        val raw = DoubleArray(points.size)
        for (i in 1 until points.size) raw[i] = raw[i - 1] + meters(points[i - 1], points[i])
        // OSRM이 알려준 실제 거리와 맞추기
        val k = if (raw.last() > 0) route.distanceM / raw.last() else 1.0
        cum = DoubleArray(raw.size) { raw[it] * k }
        stepAlongM = DoubleArray(route.steps.size) { snap(route.steps[it].location).alongM }
    }

    /** 경로 위 alongM 지점의 좌표 */
    fun pointAt(alongM: Double): LngLat {
        val s = alongM.coerceIn(0.0, lengthM)
        val i = segmentIndex(s)
        val a = points[i - 1]
        val b = points[i]
        val seg = (cum[i] - cum[i - 1]).takeIf { it > 0 } ?: 1.0
        val t = (s - cum[i - 1]) / seg
        return LngLat(a.lng + (b.lng - a.lng) * t, a.lat + (b.lat - a.lat) * t)
    }

    /** 진행 방향 (도, 북쪽 0 · 시계방향) */
    fun bearingAt(alongM: Double): Float {
        val a = pointAt(alongM - 30)
        val b = pointAt(alongM + 30)
        val k = cos(Math.toRadians((a.lat + b.lat) / 2))
        val deg = Math.toDegrees(atan2((b.lng - a.lng) * k, b.lat - a.lat))
        return ((deg + 360) % 360).toFloat()
    }

    /** 임의의 좌표(GPS)를 경로에 가장 가까운 지점으로 맞춘다 */
    fun snap(p: LngLat, hintAlongM: Double? = null): Snap {
        // 힌트가 있으면 그 근처(앞 3km, 뒤 500m)만 찾는다
        val from = hintAlongM?.let { segmentIndex((it - 500).coerceAtLeast(0.0)) } ?: 1
        val to = hintAlongM?.let { segmentIndex((it + 3000).coerceAtMost(lengthM)) } ?: (points.size - 1)
        val k = cos(Math.toRadians(p.lat))
        var best = Double.MAX_VALUE
        var bestAlong = 0.0
        for (i in from..to) {
            val a = points[i - 1]
            val b = points[i]
            val ax = (a.lng - p.lng) * k; val ay = a.lat - p.lat
            val bx = (b.lng - p.lng) * k; val by = b.lat - p.lat
            val dx = bx - ax; val dy = by - ay
            val len2 = (dx * dx + dy * dy).takeIf { it > 0 } ?: 1e-18
            val t = (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val x = ax + dx * t; val y = ay + dy * t
            val d = x * x + y * y
            if (d < best) {
                best = d
                bestAlong = cum[i - 1] + (cum[i] - cum[i - 1]) * t
            }
        }
        return Snap(bestAlong, meters(p, pointAt(bestAlong)))
    }

    /** 지나온 구간 / 남은 구간 */
    fun split(alongM: Double): Pair<List<LngLat>, List<LngLat>> {
        val s = alongM.coerceIn(0.0, lengthM)
        val i = segmentIndex(s)
        val here = pointAt(s)
        val passed = points.subList(0, i) + here
        val remaining = listOf(here) + points.subList(i, points.size)
        return passed to remaining
    }

    private fun segmentIndex(s: Double): Int {
        var lo = 1
        var hi = cum.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cum[mid] < s) lo = mid + 1 else hi = mid
        }
        return lo.coerceIn(1, cum.size - 1)
    }

    companion object {
        fun meters(a: LngLat, b: LngLat): Double {
            val k = cos(Math.toRadians((a.lat + b.lat) / 2))
            return hypot((b.lng - a.lng) * 111_320.0 * k, (b.lat - a.lat) * 110_540.0)
        }
    }
}
