package com.sportage.obd

import com.sportage.obd.map.MapAvailability
import com.sportage.obd.map.MapTheme
import com.sportage.obd.map.MapLabels
import org.junit.Assert.*
import org.junit.Test

class MapAvailabilityTest {
    @Test fun offlineChoiceNeverStartsOnlineRenderer() {
        for (internet in listOf(true, false)) for (graphics in listOf(true, false)) for (failed in listOf(true, false))
            assertFalse(MapAvailability.useOnline(MapTheme.OFFLINE, internet, graphics, failed))
    }
    @Test fun rendererRequiresInternetCompatibleGraphicsAndNoFailure() {
        MapTheme.entries.filter { it != MapTheme.OFFLINE }.forEach { theme ->
            for (internet in listOf(true, false)) for (graphics in listOf(true, false)) for (failed in listOf(true, false))
                assertEquals(internet && graphics && !failed, MapAvailability.useOnline(theme, internet, graphics, failed))
        }
    }
    @Test fun missingOrStaleThemePreferencesAreLightByDefault() {
        assertEquals(MapTheme.POSITRON, MapTheme.saved(null))
        assertEquals(MapTheme.POSITRON, MapTheme.saved("unknown"))
        assertEquals(MapTheme.DARK, MapTheme.saved("DARK"))
        assertNull(MapTheme.OFFLINE.url)
        assertTrue(MapTheme.entries.filter { it.url != null }.all { it.url!!.startsWith("https://tiles.openfreemap.org/styles/") })
    }
    @Test fun englishLocalizationPreservesShieldReferencesAndHouseNumbers() {
        assertTrue(MapLabels.shouldLocalize("""["get","name:nonlatin"]"""))
        assertTrue(MapLabels.shouldLocalize("{name}"))
        assertFalse(MapLabels.shouldLocalize("""["get","ref"]"""))
        assertFalse(MapLabels.shouldLocalize("{housenumber}"))
        assertTrue(MapLabels.ENGLISH.contains("name:en"))
        assertFalse(MapLabels.ENGLISH.contains("name:nonlatin"))
    }
}
