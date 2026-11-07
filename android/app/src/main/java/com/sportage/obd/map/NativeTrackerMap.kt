package com.sportage.obd.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OpenStreetMapMapnik
import org.mapsforge.map.layer.overlay.FixedPixelCircle
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme
import java.io.File

/** Native Android Canvas rendering. No browser, JavaScript, remote style or API key. */
class NativeTrackerMap(context: Context, file: File, private val offlineOnly: Boolean = false) : FrameLayout(context) {
    private val map = MapView(context)
    private val data = EnglishMapFile(file)
    private val factory = AndroidGraphicFactory.INSTANCE
    private val offline: TileRendererLayer
    private val online: TileDownloadLayer
    private val marker: FixedPixelCircle
    private var lastFix: LatLong? = null
    private var lastRecenter = -1
    private var offlineMode = true
    private var disposed = false

    init {
        setBackgroundColor(android.graphics.Color.WHITE)
        addView(map, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        map.setBuiltInZoomControls(true)
        map.setZoomLevelMin(3)
        map.setZoomLevelMax(18)
        map.setZoomLevel(16)
        map.mapScaleBar?.isVisible = true
        val tileSize = map.model.displayModel.tileSize
        val overdraw = map.model.frameBufferModel.overdrawFactor
        // Distinct caches prevent online images and locally rendered vectors colliding.
        val vectorCache = AndroidUtil.createTileCache(context, "qatar-en-streets-v2", tileSize, 1f, overdraw)
        val onlineCache = AndroidUtil.createTileCache(context, "osm-streets", tileSize, 1f, overdraw)
        offline = TileRendererLayer(vectorCache, data, map.model.mapViewPosition, factory)
        offline.setXmlRenderTheme(AssetsRenderTheme(context.assets, "", "maps/streets.xml"))
        val source = OpenStreetMapMapnik(arrayOf("tile.openstreetmap.org"), 443).apply {
            setUserAgent("SportageOBD/1.0.5 (Android native tracker; github.com/farazsocialh-jpg/sportageobd)")
        }
        online = TileDownloadLayer(onlineCache, map.model.mapViewPosition, source, factory)
        online.cacheTimeToLive = 7L * 24 * 60 * 60 * 1000
        online.isVisible = false
        map.layerManager.layers.add(online)
        map.layerManager.layers.add(offline)
        val fill = factory.createPaint().apply { setColor(0xFF009DDB.toInt()); setStyle(Style.FILL) }
        val stroke = factory.createPaint().apply {
            setColor(android.graphics.Color.WHITE); setStyle(Style.STROKE); setStrokeWidth(3f)
        }
        marker = FixedPixelCircle(null, 8f, fill, stroke)
        map.layerManager.layers.add(marker)
        contentDescription = "Native street map with current GPS position"
    }

    /** Returns the displayed data source so Compose can label offline coverage honestly. */
    fun updateFix(latitude: Double, longitude: Double, recenter: Int): Boolean {
        if (disposed || !TrackerMapPolicy.valid(latitude, longitude)) return offlineMode
        val fix = LatLong(latitude, longitude)
        val useOffline = offlineOnly || data.boundingBox().contains(fix)
        if (useOffline != offlineMode) {
            offlineMode = useOffline
            offline.isVisible = useOffline
            online.isVisible = !useOffline
            if (useOffline) online.onPause() else if (isAttachedToWindow) online.onResume()
        }
        if (fix != lastFix || recenter != lastRecenter) {
            marker.setLatLong(fix)
            map.setCenter(fix)
            marker.requestRedraw()
            lastFix = fix
            lastRecenter = recenter
        }
        return useOffline
    }

    fun covers(latitude: Double, longitude: Double): Boolean =
        TrackerMapPolicy.valid(latitude, longitude) && data.boundingBox().contains(LatLong(latitude, longitude))

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!disposed && !offlineMode) online.onResume()
    }

    override fun onDetachedFromWindow() {
        if (!disposed) online.onPause()
        super.onDetachedFromWindow()
    }

    fun release() {
        if (disposed) return
        disposed = true
        online.onPause()
        map.destroyAll()
        removeAllViews()
    }
}

/** Copies a compressed APK asset on IO, atomically, into private app storage. */
internal fun prepareBundledMap(context: Context): File {
    val destination = File(context.filesDir, TrackerMapPolicy.MAP_ASSET)
    synchronized(TrackerMapPolicy) {
        if (destination.length() == TrackerMapPolicy.MAP_BYTES) return destination
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, destination.name + ".tmp")
        try {
            context.assets.open(TrackerMapPolicy.MAP_ASSET).use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            check(temporary.length() == TrackerMapPolicy.MAP_BYTES) { "Bundled street-map data is incomplete" }
            check(temporary.renameTo(destination)) { "Could not prepare bundled street map" }
        } finally {
            temporary.delete()
        }
    }
    return destination
}

@Composable
fun NativeLocationMap(latitude: Double, longitude: Double, recenter: Int, offlineOnly: Boolean = false) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(true) }
    var covered by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        error = null
        try {
            file = withContext(Dispatchers.IO) { prepareBundledMap(context.applicationContext) }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = "Street map could not open: ${e.message}" }
    }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        val ready = file
        if (ready != null && error == null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    try {
                        NativeTrackerMap(ctx, ready, offlineOnly).apply { updateFix(latitude, longitude, recenter) }
                    } catch (e: Exception) {
                        error = "Native map initialization failed: ${e.message}"
                        FrameLayout(ctx)
                    }
                },
                update = { view ->
                    if (view is NativeTrackerMap) {
                        offline = view.updateFix(latitude, longitude, recenter)
                        covered = view.covers(latitude, longitude)
                    }
                },
                onRelease = { view -> (view as? NativeTrackerMap)?.release() }
            )
        } else if (error == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Preparing bundled Qatar streets…", color = Color.Black, fontSize = 12.sp)
            }
        }
        error?.let { message ->
            Text("$message\nTap to retry", color = Color.Black,
                modifier = Modifier.align(Alignment.Center).background(Color(0xFFFFDE80))
                    .clickable { file = null; retry++ }.padding(12.dp))
        }
        if (ready != null && error == null) {
            Text(
                if (offline) "QATAR STREETS · OFFLINE" else "WORLD STREETS · INTERNET REQUIRED",
                color = Color.Black, fontSize = 9.sp,
                modifier = Modifier.align(Alignment.TopEnd).background(Color(0xDDFFFFFF)).padding(4.dp)
            )
        }
        if (offlineOnly && !covered) Text(
            "Outside bundled Qatar coverage.\nChoose an online theme with an internet connection.",
            color = Color.Black, modifier = Modifier.align(Alignment.Center)
                .background(Color(0xEEFFFFFF)).padding(12.dp))
        Text("© OpenStreetMap contributors", color = Color.Black, fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomStart).background(Color(0xDDFFFFFF))
                .clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.openstreetmap.org/copyright")))
                    }
                }.padding(4.dp))
    }
}
