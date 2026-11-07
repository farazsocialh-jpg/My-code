package com.sportage.obd

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.sportage.obd.map.NativeTrackerMap
import com.sportage.obd.map.TrackerMapPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** Exercises the real native View/Canvas renderer, not a browser or a fake map. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeMapRenderTest {
    @Test fun nativeCanvasDrawsBundledQatarStreets() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val view = NativeTrackerMap(activity, File("src/main/assets/${TrackerMapPolicy.MAP_ASSET}"))
        val bitmap = Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888)
        try {
            activity.setContentView(view)
            assertTrue("Screenshot location must use offline data", view.updateFix(25.1712, 51.5670, 0))
            view.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 360, 360)
            // Rendering happens on the library's tile workers. Wait for real map
            // pixels in a corner, away from the center GPS marker and controls.
            val deadline = System.nanoTime() + 30_000_000_000L
            var distinct = 0
            do {
                ShadowLooper.idleMainLooper()
                bitmap.eraseColor(android.graphics.Color.WHITE)
                view.draw(Canvas(bitmap))
                val colors = HashSet<Int>()
                for (y in 20 until 140) for (x in 20 until 140) colors.add(bitmap.getPixel(x, y))
                distinct = colors.size
                if (distinct >= 20) {
                    // Let neighboring tile jobs finish before recording the evidence image.
                    Thread.sleep(750)
                    ShadowLooper.idleMainLooper()
                    break
                }
                Thread.sleep(100)
            } while (System.nanoTime() < deadline)
            val output = File("build/reports/native-map-android-canvas.png")
            output.parentFile?.mkdirs()
            val snapshot = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(snapshot))
            output.outputStream().use { snapshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            snapshot.recycle()
            assertTrue("Native map must draw street detail, not just a marker/blank background; got $distinct colors", distinct >= 20)
            // The token does NOT change: a new GPS fix alone must move the map.
            assertTrue("Live GPS following must stay in offline coverage", view.updateFix(25.2854, 51.531, 0))
            val map = view.getChildAt(0) as org.mapsforge.map.android.view.MapView
            assertEquals(25.2854, map.model.mapViewPosition.center.latitude, 0.000001)
            assertEquals(51.531, map.model.mapViewPosition.center.longitude, 0.000001)
            assertTrue("Manual Follow GPS remains available", view.updateFix(25.2854, 51.531, 1))
        } finally {
            view.release()
            bitmap.recycle()
            controller.pause().stop().destroy()
        }
    }
}
