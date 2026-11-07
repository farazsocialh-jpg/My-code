package com.sportage.obd.map

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.android.module.http.HttpRequestUtil
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Native GL map. The Mapsforge view beneath it stays available during network/style failures. */
internal class LibreTrackerView(context: Context, private val lifecycle: Lifecycle,
    theme: MapTheme, private val onReady: () -> Unit, private val onFailure: () -> Unit
) : FrameLayout(context) {
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var fix: LatLng? = null
    private var bearing = 0f
    private var lastRecenter = -1
    private var observer: LifecycleEventObserver? = null
    private var started = false
    private var resumed = false
    private var released = false
    private var styleLoaded = false
    private val watchdog = Handler(Looper.getMainLooper())
    private var waitingForTiles = false
    private val renderTimeout = Runnable {
        if (!released && waitingForTiles) onFailure()
    }

    init {
        try {
            MapLibre.getInstance(context.applicationContext)
            HttpRequestUtil.setOkHttpClient(httpClient)
            // TextureView respects Compose clipping and can reveal the fallback underneath.
            val view = MapView(context, MapLibreMapOptions.createFromAttributes(context)
                .textureMode(true).attributionEnabled(true).logoEnabled(true))
            mapView = view
            addView(view, LayoutParams(-1, -1))
            view.onCreate(null)
            observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> start()
                    Lifecycle.Event.ON_RESUME -> resume()
                    Lifecycle.Event.ON_PAUSE -> pause()
                    Lifecycle.Event.ON_STOP -> stop()
                    Lifecycle.Event.ON_DESTROY -> release()
                    else -> Unit
                }
            }.also { lifecycle.addObserver(it) }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
            view.addOnDidFailLoadingMapListener { onFailure() }
            view.addOnDidFinishRenderingFrameListener { fully, _, _ ->
                if (styleLoaded && !released) {
                    if (fully) {
                        waitingForTiles = false
                        watchdog.removeCallbacks(renderTimeout)
                        onReady()
                    } else if (!waitingForTiles) {
                        waitingForTiles = true
                        watchdog.postDelayed(renderTimeout, 18_000)
                    }
                }
            }
            view.getMapAsync { ready ->
                if (released) return@getMapAsync
                map = ready
                ready.uiSettings.isRotateGesturesEnabled = false
                ready.uiSettings.isTiltGesturesEnabled = false
                ready.setStyle(Style.Builder().fromUri(requireNotNull(theme.url))) { style ->
                    if (released) return@setStyle
                    // Prefer actual English, then Latin transliteration. Never append Arabic labels.
                    val english = Expression.raw(MapLabels.ENGLISH)
                    style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
                        // Inspect the actual field, not PropertyValue.toString() whose wrapper
                        // always contains "name". Keep road shields and house numbers intact.
                        val field = layer.textField
                        val value = if (field.isExpression) field.expression.toString() else field.value?.toString().orEmpty()
                        if (MapLabels.shouldLocalize(value)) layer.setProperties(textField(english))
                    }
                    style.addSource(GeoJsonSource("sportage-gps"))
                    // Direction-of-travel arrow instead of a plain dot: the symbol is
                    // rotated to the GPS bearing so it shows where the vehicle is heading.
                    style.addImage("sportage-gps-arrow", arrowIcon())
                    style.addLayer(SymbolLayer("sportage-gps-dot", "sportage-gps").withProperties(
                        iconImage("sportage-gps-arrow"),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                        iconSize(1.15f),
                        iconRotate(Expression.get("bearing"))))
                    styleLoaded = true
                    fix?.let { updateMarker(it); center(it) }
                }
            }
        } catch (_: Exception) { onFailure() }
        catch (_: LinkageError) { onFailure() }
    }

    fun updateFix(latitude: Double, longitude: Double, recenter: Int, headingBearing: Float = 0f) {
        if (released || !TrackerMapPolicy.valid(latitude, longitude)) return
        val next = LatLng(latitude, longitude)
        if (next != fix || recenter != lastRecenter || headingBearing != bearing) {
            fix = next
            bearing = headingBearing
            lastRecenter = recenter
            if (styleLoaded) { updateMarker(next); center(next) }
        }
    }
    private fun center(point: LatLng) {
        val zoom = map?.cameraPosition?.zoom?.takeIf { it >= 3.0 } ?: 15.5
        map?.cameraPosition = CameraPosition.Builder().target(point).zoom(zoom).bearing(0.0).tilt(0.0).build()
    }
    private fun updateMarker(point: LatLng) {
        map?.style?.getSourceAs<GeoJsonSource>("sportage-gps")?.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))
                .also { it.addNumberProperty("bearing", bearing.toDouble()) })
    }

    /** Solid triangular arrow pointing up (north) with a light outline, rotated by the layer. */
    private fun arrowIcon(): android.graphics.Bitmap {
        val size = 72
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0088BE.toInt() }
        val outline = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.STROKE; strokeWidth = 5f }
        val tip = android.graphics.PointF(size * 0.5f, size * 0.10f)
        val left = android.graphics.PointF(size * 0.20f, size * 0.86f)
        val right = android.graphics.PointF(size * 0.80f, size * 0.86f)
        val tail = android.graphics.PointF(size * 0.5f, size * 0.70f)
        val path = android.graphics.Path().apply {
            moveTo(tip.x, tip.y); lineTo(right.x, right.y); lineTo(tail.x, tail.y)
            lineTo(left.x, left.y); close()
        }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, outline)
        return bmp
    }
    private fun start() { if (!started && !released) { mapView?.onStart(); started = true } }
    private fun resume() { start(); if (!resumed && !released) { mapView?.onResume(); resumed = true } }
    private fun pause() { if (resumed) { mapView?.onPause(); resumed = false } }
    private fun stop() { pause(); if (started) { mapView?.onStop(); started = false } }
    fun release() {
        if (released) return
        observer?.let { lifecycle.removeObserver(it) }
        watchdog.removeCallbacksAndMessages(null)
        runCatching { stop() }
        released = true
        runCatching { mapView?.onDestroy() }
        mapView = null
        map = null
        removeAllViews()
    }
    companion object {
        private val httpClient by lazy {
            OkHttpClient.Builder()
                .dispatcher(okhttp3.Dispatcher().apply { maxRequestsPerHost = 4 })
                .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain -> chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", "SportageOBD/1.1.0 (Android; github.com/farazsocialh-jpg/sportageobd)")
                    .build()) }.build()
        }
    }
}

@Composable
fun HybridLocationMap(latitude: Double, longitude: Double, recenter: Int, bearing: Float = 0f) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val prefs = remember { context.getSharedPreferences("map_preferences", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(MapTheme.saved(prefs.getString("theme", null))) }
    var menu by remember { mutableStateOf(false) }
    var failed by remember(theme) { mutableStateOf(false) }
    var rendered by remember(theme) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val manager = remember { context.getSystemService(ConnectivityManager::class.java) }
    fun connected() = manager?.getNetworkCapabilities(manager.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    var internet by remember { mutableStateOf(connected()) }
    val graphics = remember {
        context.getSystemService(ActivityManager::class.java)?.deviceConfigurationInfo?.reqGlEsVersion
            ?.let { it >= 0x20000 } == true
    }
    DisposableEffect(manager) {
        val handler = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun refresh() { handler.post {
                val next = connected()
                if (next && !internet) { failed = false; rendered = false; retry++ }
                internet = next
            } }
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) }; handler.removeCallbacksAndMessages(null) }
    }
    val online = MapAvailability.useOnline(theme, internet, graphics, failed)
    LaunchedEffect(theme, retry, online) {
        if (online) {
            rendered = false
            delay(18_000)
            if (!rendered) failed = true
        }
    }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        // Keep native offline content visible until GL reports a complete frame.
        if (!online || !rendered) {
            val forceOffline = !internet || theme == MapTheme.OFFLINE
            key(forceOffline) { NativeLocationMap(latitude, longitude, recenter, forceOffline) }
        }
        if (online) key(theme, retry) {
            AndroidView(
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (rendered) 1f else 0f },
                factory = { ctx -> LibreTrackerView(ctx, lifecycle, theme,
                    onReady = { rendered = true }, onFailure = { failed = true }) },
                update = { it.updateFix(latitude, longitude, recenter, bearing) },
                onRelease = { it.release() }
            )
        }
        Column(Modifier.align(Alignment.TopStart).padding(6.dp)) {
            FilledTonalIconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Layers, "Choose map theme")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MapTheme.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.title + if (option == theme) " · selected" else "") },
                        onClick = {
                            theme = option; failed = false; rendered = false; retry++; menu = false
                            prefs.edit().putString("theme", option.name).apply()
                        })
                }
            }
        }
        if (online && rendered) Text("OpenFreeMap · ${theme.title}", color = Color.Black, fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopEnd).background(Color(0xEEFFFFFF)).padding(4.dp))
        if (failed && internet && graphics) {
            Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp), color = Color(0xF2FFFFFF)) {
                TextButton(onClick = { failed = false; rendered = false; retry++ }, enabled = internet && graphics) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Text(if (internet) "Offline fallback · retry" else "Offline fallback", color = Color.Black, fontSize = 11.sp)
                }
            }
        }
    }
}
