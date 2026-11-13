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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.gachigayo.data.route.LngLat
import com.gachigayo.data.route.Route
import org.maplibre.android.MapLibre
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

private const val ROUTE_SRC = "route"
private const val ME_SRC = "me"
private const val DEST_SRC = "dest"

private const val TEAL = "#087F70"
private const val INK = "#1B2430"

/** 화면 가장자리 UI(안내 카드, 하단 바)에 가리지 않도록 지도에 주는 여백 (dp) */
data class MapPadding(val left: Int, val top: Int, val right: Int, val bottom: Int)

@Composable
fun RouteMap(
    route: Route?,
    me: LngLat?,
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
                    m.uiSettings.isAttributionEnabled = true
                    m.uiSettings.isLogoEnabled = false
                    m.uiSettings.isCompassEnabled = false
                    m.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                        .target(LatLng(37.6, 128.0)).zoom(7.0).build()
                    m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s ->
                        addRouteLayers(s)
                        map = m
                        style = s
                    }
                }
            }
        },
    )

    LaunchedEffect(route, me, style) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect

        (s.getSource(ROUTE_SRC) as? GeoJsonSource)?.setGeoJson(
            route?.let { r ->
                FeatureCollection.fromFeature(
                    Feature.fromGeometry(LineString.fromLngLats(r.points.map { Point.fromLngLat(it.lng, it.lat) })),
                )
            } ?: FeatureCollection.fromFeatures(emptyList()),
        )
        (s.getSource(ME_SRC) as? GeoJsonSource)?.setGeoJson(pointOrEmpty(me))
        (s.getSource(DEST_SRC) as? GeoJsonSource)?.setGeoJson(pointOrEmpty(route?.points?.lastOrNull()))

        if (route != null && route.points.size >= 2) {
            val bounds = LatLngBounds.Builder()
                .includes(route.points.map { LatLng(it.lat, it.lng) })
                .build()
            m.easeCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds,
                    (padding.left * density).toInt(),
                    (padding.top * density).toInt(),
                    (padding.right * density).toInt(),
                    (padding.bottom * density).toInt(),
                ),
                900,
            )
        }
    }
}

private fun pointOrEmpty(p: LngLat?): FeatureCollection =
    if (p == null) FeatureCollection.fromFeatures(emptyList())
    else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)))

private fun addRouteLayers(style: Style) {
    style.addSource(GeoJsonSource(ROUTE_SRC))
    style.addSource(GeoJsonSource(ME_SRC))
    style.addSource(GeoJsonSource(DEST_SRC))

    // 경로선: 흰 테두리 + 로드 틸
    style.addLayer(
        LineLayer("route-casing", ROUTE_SRC).withProperties(
            PropertyFactory.lineColor(AndroidColor.WHITE),
            PropertyFactory.lineWidth(11f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer("route-line", ROUTE_SRC).withProperties(
            PropertyFactory.lineColor(TEAL),
            PropertyFactory.lineWidth(6.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    // 목적지
    style.addLayer(
        CircleLayer("dest-dot", DEST_SRC).withProperties(
            PropertyFactory.circleColor(INK),
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
    // 내 차
    style.addLayer(
        CircleLayer("me-halo", ME_SRC).withProperties(
            PropertyFactory.circleColor(TEAL),
            PropertyFactory.circleOpacity(0.18f),
            PropertyFactory.circleRadius(22f),
        ),
    )
    style.addLayer(
        CircleLayer("me-dot", ME_SRC).withProperties(
            PropertyFactory.circleColor(TEAL),
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
            PropertyFactory.circleStrokeWidth(3.5f),
        ),
    )
}
