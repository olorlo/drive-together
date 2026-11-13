package com.gachigayo.data.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LngLat(val lng: Double, val lat: Double)

data class RouteStep(
    val type: String,       // turn, fork, off ramp, merge, arrive ...
    val modifier: String?,  // left, right, slight left, straight ...
    val road: String,       // 도로명 또는 도로 번호
    val distanceM: Double,  // 이 단계(현재 안내 지점 → 다음 안내 지점) 거리
    val location: LngLat,   // 안내 지점 좌표
)

data class Route(
    val points: List<LngLat>,
    val distanceM: Double,
    val durationS: Double,
    val steps: List<RouteStep>,
)

/**
 * 경로 계산 (OSRM 공개 서버).
 * TODO: 그룹 기능이 붙으면 서버(FastAPI)가 경로를 계산해 모든 차에 같은 경로를 내려주도록 옮긴다.
 */
class RouteRepository(
    private val baseUrl: String = "https://router.project-osrm.org",
) {
    suspend fun fetch(from: LngLat, to: LngLat, via: List<LngLat> = emptyList()): Route =
        withContext(Dispatchers.IO) {
            val coords = (listOf(from) + via + to).joinToString(";") { "${it.lng},${it.lat}" }
            val url = URL("$baseUrl/route/v1/driving/$coords?overview=full&geometries=geojson&steps=true")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "gachigayo-app")
            }
            try {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(JSONObject(body))
            } finally {
                conn.disconnect()
            }
        }

    private fun parse(json: JSONObject): Route {
        require(json.optString("code") == "Ok") { "경로를 찾지 못했어요 (${json.optString("code")})" }
        val route = json.getJSONArray("routes").getJSONObject(0)

        val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coords.length()).map {
            val c = coords.getJSONArray(it)
            LngLat(c.getDouble(0), c.getDouble(1))
        }

        val steps = mutableListOf<RouteStep>()
        val legs = route.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val arr = legs.getJSONObject(l).getJSONArray("steps")
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val m = s.getJSONObject("maneuver")
                val loc = m.getJSONArray("location")
                val name = s.optString("name")
                val ref = s.optString("ref")
                steps += RouteStep(
                    type = m.getString("type"),
                    modifier = m.optString("modifier").ifEmpty { null },
                    road = name.ifEmpty { ref },
                    distanceM = s.getDouble("distance"),
                    location = LngLat(loc.getDouble(0), loc.getDouble(1)),
                )
            }
        }
        return Route(points, route.getDouble("distance"), route.getDouble("duration"), steps)
    }
}
