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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gachigayo.data.route.LngLat
import com.gachigayo.data.route.RouteLine
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
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
private const val OTHERS_SRC = "others"
private const val OTHER_LINES_SRC = "other-lines"
private const val PINS_SRC = "pins"
private val LABEL_FONT = arrayOf("Noto Sans Bold")

private const val TEAL = "#087F70"
private const val INK = "#1B2430"
private const val PASSED = "#B4BCC4"

/** 따라가기 줌 (고속도로 주행에 맞춘 값) */
private const val FOLLOW_ZOOM = 13.2

/** 화면 가장자리 UI(안내 카드, 하단 바)에 가리지 않도록 지도에 주는 여백 (dp) */
data class MapPadding(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** 지도 위의 차 (내 차 포함). bearing = 진행 방향(북 0°), bubble = 질문 말풍선 글자 */
data class MapCar(
    val id: String,
    val at: LngLat,
    val bearing: Float,
    val color: Int,
    val label: String,
    val me: Boolean,
    val bubble: String? = null,
)

/** + / − 버튼으로 지도 확대·축소 (id가 바뀔 때마다 한 번) */
data class ZoomRequest(val id: Int, val delta: Double)

/** 다른 차의 경로선 */
data class MapLine(val points: List<LngLat>, val color: String)

/** 휴게소 핀 (planned = 다 같이 들르기로 한 곳) */
data class MapPin(val at: LngLat, val name: String, val planned: Boolean)

@Composable
fun RouteMap(
    line: RouteLine?,
    alongM: Double,
    me: LngLat?,
    follow: Boolean,
    padding: MapPadding,
    modifier: Modifier = Modifier,
    others: List<MapCar> = emptyList(),
    otherLines: List<MapLine> = emptyList(),
    pins: List<MapPin> = emptyList(),
    zoomRequest: ZoomRequest? = null,
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
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var followZoom by remember { mutableStateOf(FOLLOW_ZOOM) }

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
                view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    viewSize = IntSize(v.width, v.height)
                }
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

    // 함께 가는 차들의 경로선 (경로가 바뀔 때만)
    LaunchedEffect(otherLines, style) {
        val s = style ?: return@LaunchedEffect
        (s.getSource(OTHER_LINES_SRC) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(
                otherLines.filter { it.points.size >= 2 }.map { l ->
                    Feature.fromGeometry(LineString.fromLngLats(l.points.map { Point.fromLngLat(it.lng, it.lat) }))
                        .also { it.addStringProperty("color", l.color) }
                },
            ),
        )
    }

    // 차들의 위치 (차 아이콘 + 이름표 + 질문 말풍선)
    LaunchedEffect(others, style) {
        val s = style ?: return@LaunchedEffect
        others.forEach { c ->
            val car = "car-${c.id}"
            val tag = "tag-${c.id}"
            if (s.getImage(car) == null) s.addImage(car, MapIcons.car(c.color, c.me))
            if (s.getImage(tag) == null) s.addImage(tag, MapIcons.tag(c.label, c.color, c.me))
            c.bubble?.let { b -> if (s.getImage("bubble-$b") == null) s.addImage("bubble-$b", MapIcons.bubble(b)) }
        }
        (s.getSource(OTHERS_SRC) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(
                others.sortedBy { it.me }.map { c ->
                    Feature.fromGeometry(Point.fromLngLat(c.at.lng, c.at.lat)).also {
                        it.addStringProperty("car", "car-${c.id}")
                        it.addStringProperty("tag", "tag-${c.id}")
                        it.addNumberProperty("rot", c.bearing - 90f)
                        c.bubble?.let { b -> it.addStringProperty("bubble", "bubble-$b") }
                    }
                },
            ),
        )
    }

    // + / − 확대·축소
    LaunchedEffect(zoomRequest) {
        val z = zoomRequest ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        if (follow) followZoom = (followZoom + z.delta).coerceIn(8.0, 16.0)
        else m.animateCamera(CameraUpdateFactory.zoomBy(z.delta), 300)
    }

    // 휴게소 핀
    LaunchedEffect(pins, style) {
        val s = style ?: return@LaunchedEffect
        (s.getSource(PINS_SRC) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(
                pins.map { p ->
                    Feature.fromGeometry(Point.fromLngLat(p.at.lng, p.at.lat)).also {
                        it.addStringProperty("name", p.name)
                        it.addStringProperty("color", if (p.planned) TEAL else "#FFFFFF")
                    }
                },
            ),
        )
    }

    // 내 차 위치 + 따라가기 카메라
    LaunchedEffect(me, follow, style, followZoom) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        (s.getSource(ME_SRC) as? GeoJsonSource)?.setGeoJson(pointOrEmpty(me))
        if (follow && me != null) {
            m.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(me.lat, me.lng))
                        .zoom(followZoom)
                        .padding(px(padding.left), px(padding.top), px(padding.right), px(padding.bottom))
                        .build(),
                ),
            )
        }
    }

    // 전체 보기: 경로가 바뀌거나 따라가기를 끄면 경로 전체가 보이게
    // 지도 뷰 크기가 정해지기 전(0이거나 여백보다 작을 때)에 맞추면 엉뚱한 곳을 비추므로, 크기가 정해지거나 바뀔 때 다시 맞춘다
    LaunchedEffect(line, follow, style, viewSize) {
        val m = map ?: return@LaunchedEffect
        if (style == null || follow || line == null || line.points.size < 2) return@LaunchedEffect
        val left = px(padding.left).toInt()
        val top = px(padding.top).toInt()
        val right = px(padding.right).toInt()
        val bottom = px(padding.bottom).toInt()
        if (viewSize.width <= left + right || viewSize.height <= top + bottom) return@LaunchedEffect
        val bounds = LatLngBounds.Builder().includes(line.points.map { LatLng(it.lat, it.lng) }).build()
        // 지도 엔진(GL 화면)이 새 크기를 반영하는 시점이 뷰 크기보다 늦을 수 있어, 잠시 뒤 맞추고 한 번 더 맞춘다
        delay(300)
        m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, left, top, right, bottom))
        delay(900)
        m.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, left, top, right, bottom), 400)
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
    listOf(OTHER_LINES_SRC, PASSED_SRC, REMAIN_SRC, PINS_SRC, ME_SRC, DEST_SRC, OTHERS_SRC).forEach { style.addSource(GeoJsonSource(it)) }

    val round = arrayOf(
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    // 함께 가는 차들의 경로: 각 차 색으로 얇게
    style.addLayer(
        LineLayer("other-lines", OTHER_LINES_SRC).withProperties(
            PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineOpacity(0.45f),
            *round,
        ),
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
    // 휴게소 핀
    style.addLayer(
        CircleLayer("pin-dot", PINS_SRC).withProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor(TEAL),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
    style.addLayer(
        SymbolLayer("pin-label", PINS_SRC).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(LABEL_FONT),
            PropertyFactory.textSize(14f),
            PropertyFactory.textColor(INK),
            PropertyFactory.textHaloColor(AndroidColor.WHITE),
            PropertyFactory.textHaloWidth(2f),
            PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
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
    // 차: 진행 방향으로 회전하는 차 아이콘 + 아래 이름표 + 위 질문 말풍선 (웹 데모와 같은 모양)
    style.addLayer(
        SymbolLayer("cars-icon", OTHERS_SRC).withProperties(
            PropertyFactory.iconImage(Expression.get("car")),
            PropertyFactory.iconRotate(Expression.get("rot")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ),
    )
    style.addLayer(
        SymbolLayer("cars-tag", OTHERS_SRC).withProperties(
            PropertyFactory.iconImage(Expression.get("tag")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_TOP),
            PropertyFactory.iconOffset(arrayOf(0f, 26f)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ),
    )
    style.addLayer(
        SymbolLayer("cars-bubble", OTHERS_SRC).withProperties(
            PropertyFactory.iconImage(Expression.get("bubble")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconOffset(arrayOf(0f, -28f)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ),
    )
}
