package com.gachigayo.feature.drive

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gachigayo.data.route.LngLat
import com.gachigayo.data.route.RouteLine
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** 무료 지도 스타일 (API 키 없음). 밝고 단정한 회색 톤. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

private const val REMAIN_SRC = "route-remain"
private const val PASSED_SRC = "route-passed"
private const val ME_SRC = "me"
private const val DEST_SRC = "dest"

private const val TEAL = "#087F70"
private const val INK = "#1B2430"
private const val PASSED = "#B4BCC4"

/** 따라가기 줌 (고속도로 주행에 맞춘 값) */
private const val FOLLOW_ZOOM = 13.2

/** 화면 가장자리 UI(안내 카드, 하단 바)에 가리지 않도록 지도에 주는 여백 (dp) */
data class MapPadding(val left: Int, val top: Int, val right: Int, val bottom: Int)

@Composable
fun RouteMap(
    line: RouteLine?,
    alongM: Double,
    me: LngLat?,
    follow: Boolean,
    padding: MapPadding,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.also { view ->
                view.getMapAsync { m ->
                    m.uiSettings.isLogoEnabled = false
                    m.uiSettings.isCompassEnabled = false
                    m.uiSettings.isRotateGesturesEnabled = false
                    m.cameraPosition = CameraPosition.Builder().target(LatLng(37.6, 128.0)).zoom(7.0).build()
                    m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s ->
                        addLayers(s)
                        map = m
                        style = s
                    }
                }
            }
        },
    )

    val px = { dp: Int -> (dp * density).toDouble() }

    // 경로선: 100m 단위로만 다시 그린다 (점이 수천 개라 매 프레임 그리면 무겁다)
    val bucket = (alongM / 100).toInt()
    LaunchedEffect(line, bucket, style) {
        val s = style ?: return@LaunchedEffect
        val (passed, remaining) = line?.split(alongM) ?: (emptyList<LngLat>() to emptyList())
        (s.getSource(REMAIN_SRC) as? GeoJsonSource)?.setGeoJson(lineOrEmpty(remaining))
        (s.getSource(PASSED_SRC) as? GeoJsonSource)?.setGeoJson(lineOrEmpty(passed))
        (s.getSource(DEST_SRC) as? GeoJsonSource)?.setGeoJson(pointOrEmpty(line?.points?.lastOrNull()))
    }

    // 내 차 위치 + 따라가기 카메라
    LaunchedEffect(me, follow, style) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        (s.getSource(ME_SRC) as? GeoJsonSource)?.setGeoJson(pointOrEmpty(me))
        if (follow && me != null) {
            m.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(me.lat, me.lng))
                        .zoom(FOLLOW_ZOOM)
                        .padding(px(padding.left), px(padding.top), px(padding.right), px(padding.bottom))
                        .build(),
                ),
            )
        }
    }

    // 전체 보기: 경로가 바뀌거나 따라가기를 끄면 경로 전체가 보이게
    LaunchedEffect(line, follow, style) {
        val m = map ?: return@LaunchedEffect
        if (style == null || follow || line == null || line.points.size < 2) return@LaunchedEffect
        val bounds = LatLngBounds.Builder().includes(line.points.map { LatLng(it.lat, it.lng) }).build()
        m.easeCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds,
                px(padding.left).toInt(), px(padding.top).toInt(), px(padding.right).toInt(), px(padding.bottom).toInt(),
            ),
            700,
        )
    }
}

private fun lineOrEmpty(points: List<LngLat>): FeatureCollection =
    if (points.size < 2) FeatureCollection.fromFeatures(emptyList())
    else FeatureCollection.fromFeature(
        Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.lng, it.lat) })),
    )

private fun pointOrEmpty(p: LngLat?): FeatureCollection =
    if (p == null) FeatureCollection.fromFeatures(emptyList())
    else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)))

private fun addLayers(style: Style) {
    listOf(PASSED_SRC, REMAIN_SRC, ME_SRC, DEST_SRC).forEach { style.addSource(GeoJsonSource(it)) }

    val round = arrayOf(
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    // 지나온 길: 회색
    style.addLayer(
        LineLayer("passed-line", PASSED_SRC).withProperties(
            PropertyFactory.lineColor(PASSED), PropertyFactory.lineWidth(6f), *round,
        ),
    )
    // 남은 길: 흰 테두리 + 로드 틸
    style.addLayer(
        LineLayer("route-casing", REMAIN_SRC).withProperties(
            PropertyFactory.lineColor(AndroidColor.WHITE), PropertyFactory.lineWidth(12f), *round,
        ),
    )
    style.addLayer(
        LineLayer("route-line", REMAIN_SRC).withProperties(
            PropertyFactory.lineColor(TEAL), PropertyFactory.lineWidth(7f), *round,
        ),
    )
    // 목적지
    style.addLayer(
        CircleLayer("dest-dot", DEST_SRC).withProperties(
            PropertyFactory.circleColor(INK),
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
    // 내 차
    style.addLayer(
        CircleLayer("me-halo", ME_SRC).withProperties(
            PropertyFactory.circleColor(TEAL),
            PropertyFactory.circleOpacity(0.18f),
            PropertyFactory.circleRadius(24f),
        ),
    )
    style.addLayer(
        CircleLayer("me-dot", ME_SRC).withProperties(
            PropertyFactory.circleColor(TEAL),
            PropertyFactory.circleRadius(11f),
            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
            PropertyFactory.circleStrokeWidth(4f),
        ),
    )
}
