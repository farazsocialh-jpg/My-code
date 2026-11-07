package com.sportage.obd

import com.sportage.obd.map.TrackerMapPolicy
import com.sportage.obd.map.EnglishMapFile
import org.junit.Assert.*
import org.junit.Test
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.reader.MapFile
import java.io.File

class NativeTrackerMapTest {
    private fun asset() = File("src/main/assets/${TrackerMapPolicy.MAP_ASSET}")

    @Test fun bundledMapHasExpectedLengthAndValidHeader() {
        assertEquals(TrackerMapPolicy.MAP_BYTES, asset().length())
        asset().inputStream().use {
            assertEquals("mapsforge binary OSM", String(it.readNBytes(20), Charsets.US_ASCII))
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        asset().inputStream().use { input ->
            val bytes = ByteArray(8192)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        assertEquals("f2bf3f4c005bc07ca7e8fa8ca9545b8558c7abfb703320b055e7c107dd9c6f18",
            digest.digest().joinToString("") { "%02x".format(it) })
    }
    @Test fun bundledCoverageIncludesBothUserScreenshotsAndCentralDoha() {
        val map = MapFile(asset())
        try {
            assertTrue(map.boundingBox().contains(LatLong(25.1712, 51.5670)))
            assertTrue(map.boundingBox().contains(LatLong(25.2854, 51.5310)))
            assertFalse(map.boundingBox().contains(LatLong(51.5074, -0.1278)))
        } finally { map.close() }
    }
    @Test fun bundledMapActuallyContainsStreetGeometryAtUserFix() {
        val map = MapFile(asset())
        try {
            val zoom: Byte = 16
            val pixelSize = 256
            val x = org.mapsforge.core.util.MercatorProjection.longitudeToTileX(51.567, zoom)
            val y = org.mapsforge.core.util.MercatorProjection.latitudeToTileY(25.1712, zoom)
            val data = map.readMapData(org.mapsforge.core.model.Tile(x, y, zoom, pixelSize))
            assertNotNull(data)
            assertTrue("Expected actual street geometry, not an empty basemap", data.ways.isNotEmpty())
        } finally { map.close() }
    }
    @Test fun invalidLocationFixesAreRejected() {
        assertTrue(TrackerMapPolicy.valid(25.1712, 51.567))
        assertFalse(TrackerMapPolicy.valid(Double.NaN, 51.5))
        assertFalse(TrackerMapPolicy.valid(25.1, Double.POSITIVE_INFINITY))
        assertFalse(TrackerMapPolicy.valid(91.0, 51.5))
        assertFalse(TrackerMapPolicy.valid(25.1, 181.0))
    }

    @Test fun EnglishLabelsDoNotFallBackToArabicOrLeakLanguageMarkers() {
        assertEquals("Al Mashaf Street", TrackerMapPolicy.englishLabel("Al Mashaf Street"))
        assertEquals("Ezdan Village 9", TrackerMapPolicy.englishLabel("en\u0008Ezdan Village 9"))
        assertEquals("", TrackerMapPolicy.englishLabel("\u0634\u0627\u0631\u0639"))
        assertEquals("", TrackerMapPolicy.englishLabel(null))
    }

    @Test fun bundledStreetNamesAreReadInEnglish() {
        val map = EnglishMapFile(asset())
        try {
            assertTrue(map.mapFileInfo.languagesPreference.split(",").contains("en"))
            val zoom: Byte = 16
            val x = org.mapsforge.core.util.MercatorProjection.longitudeToTileX(51.567, zoom)
            val y = org.mapsforge.core.util.MercatorProjection.latitudeToTileY(25.1712, zoom)
            val names = map.readMapData(org.mapsforge.core.model.Tile(x, y, zoom, 256))
                .ways.flatMap { it.tags }.filter { it.key == "name" }.map { it.value }
            assertTrue(names.contains("Al Mashaf Street"))
            assertTrue(names.contains("Al Hurriya Street"))
            assertTrue(names.none { it.any { char -> char in '\u0600'..'\u06FF' || char == '\u0008' } })
        } finally { map.close() }
    }
}
