package com.example.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandSky
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

enum class MarkerKind { PICKUP, DROP, CAR, DRIVER, ME }

data class MapMarker(val lat: Double, val lng: Double, val kind: MarkerKind, val title: String? = null)

private val INDIA_CENTER = GeoPoint(20.5937, 78.9629)

/**
 * OpenStreetMap view (no API key). The camera re-fits whenever [fitKey] changes: one point is centred,
 * several are framed together, and with no markers the map centres on [focus]. [onCenterChanged] reports where the map settles after the user pans it.
 */
@Composable
fun OsmMap(
    markers: List<MapMarker>,
    modifier: Modifier = Modifier,
    fitKey: Any? = markers.map { it.kind to (it.lat to it.lng) }.firstOrNull { it.first != MarkerKind.CAR },
    focusZoom: Double = 16.0,
    focus: Pair<Double, Double>? = null,
    onCenterChanged: ((Double, Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { createMap(context) }
    val centerCallback = rememberUpdatedState(onCenterChanged)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.addMapListener(DelayedMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean = report()
            override fun onZoom(event: ZoomEvent?): Boolean = report()
            private fun report(): Boolean {
                val c = mapView.mapCenter
                centerCallback.value?.invoke(c.latitude, c.longitude)
                return true
            }
        }, 350))
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(fitKey) {
        val points = markers.filter { it.kind != MarkerKind.CAR }.map { GeoPoint(it.lat, it.lng) }
            .ifEmpty { listOfNotNull(focus?.let { GeoPoint(it.first, it.second) }) }
        mapView.post {
            when {
                points.size >= 2 && mapView.width > 0 -> {
                    val box = BoundingBox.fromGeoPointsSafe(points)
                    mapView.zoomToBoundingBox(box.increaseByScale(1.5f), false, 80)
                }
                points.isNotEmpty() -> {
                    mapView.controller.setZoom(focusZoom)
                    mapView.controller.setCenter(points.first())
                }
                else -> {
                    mapView.controller.setZoom(5.0)
                    mapView.controller.setCenter(INDIA_CENTER)
                }
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.removeAll { it is Marker }
            markers.forEach { m ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(m.lat, m.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = dot(context, m.kind)
                    title = m.title
                    setInfoWindow(null)
                })
            }
            map.invalidate()
        },
    )
}

private fun createMap(context: Context) = MapView(context).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    isTilesScaledToDpi = true
    minZoomLevel = 4.0
    maxZoomLevel = 19.0
    // Dark map to match the app.
    overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
    controller.setZoom(5.0)
    controller.setCenter(INDIA_CENTER)
}

private fun dot(context: Context, kind: MarkerKind): GradientDrawable {
    val density = context.resources.displayMetrics.density
    val (color, sizeDp) = when (kind) {
        MarkerKind.PICKUP -> SuccessGreen to 18
        MarkerKind.DROP -> DangerRed to 18
        MarkerKind.CAR -> BrandAmber to 12
        MarkerKind.DRIVER -> BrandAmber to 22
        MarkerKind.ME -> BrandSky to 16
    }
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color.toArgb())
        setStroke((3 * density).toInt(), TextPrimary.toArgb())
        val px = (sizeDp * density).toInt()
        setSize(px, px)
    }
}
