package com.sportage.obd

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.sportage.obd.ui.theme.CockpitThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Theme registry: presets, persistence keys and accent overrides. */
class ThemeRegistryTest {

    @Test fun presetIdsAreUniqueAndDefaultExists() {
        assertEquals(CockpitThemes.presets.size, CockpitThemes.presets.map { it.id }.toSet().size)
        assertNotNull(CockpitThemes.byId(CockpitThemes.defaultId))
        assertEquals("Cockpit Dark", CockpitThemes.byId(CockpitThemes.defaultId).title)
    }

    @Test fun unknownIdFallsBackToDefault() {
        assertEquals(CockpitThemes.byId(CockpitThemes.defaultId), CockpitThemes.byId("does-not-exist"))
        assertEquals(CockpitThemes.byId(CockpitThemes.defaultId), CockpitThemes.byId(null))
    }

    @Test fun lightAndDarkPresetsAreBothAvailable() {
        assertTrue(CockpitThemes.presets.any { !it.colors.isDark })
        assertTrue(CockpitThemes.presets.any { it.colors.isDark })
    }

    @Test fun accentOverrideRestylesAccentAndKeepsPresetSurfaces() {
        val base = CockpitThemes.apply("cockpit-dark", null)
        val overridden = CockpitThemes.apply("cockpit-dark", "FF3B82F6")
        assertEquals(Color(0xFF3B82F6), overridden.accent)
        assertEquals(base.background, overridden.background)
        assertEquals(base.surface, overridden.surface)
    }

    @Test fun accentOverridePicksReadableOnAccentColor() {
        // Light accents get dark text; dark accents get white text.
        val lightAccent = CockpitThemes.apply("cockpit-dark", "FFFBBF24")
        val darkAccent = CockpitThemes.apply("cockpit-dark", "FF1D4ED8")
        assertTrue(lightAccent.accent.luminance() > 0.5f)
        assertEquals(Color(0xFF0A0F14), lightAccent.onAccent)
        assertTrue(darkAccent.accent.luminance() < 0.5f)
        assertEquals(Color(0xFFFFFFFF), darkAccent.onAccent)
    }

    @Test fun malformedAccentFallsBackToPresetAccent() {
        val base = CockpitThemes.apply("cockpit-dark", null)
        assertEquals(base.accent, CockpitThemes.apply("cockpit-dark", "zzz").accent)
        assertEquals(base.accent, CockpitThemes.apply("cockpit-dark", "").accent)
    }

    @Test fun presetsCarryACompletePalette() {
        CockpitThemes.presets.forEach { preset ->
            val c = preset.colors
            // Surfaces must be layered: background darker/different from surface and elevated.
            assertNotEquals(c.background, c.surface)
            assertNotEquals(c.surface, c.surfaceElevated)
            assertNotEquals(c.accent, c.secondary)
            // Text must be readable on its own background.
            if (c.isDark) assertTrue(c.textPrimary.luminance() > 0.5f)
            else assertTrue(c.textPrimary.luminance() < 0.5f)
            assertTrue(c.textSecondary != c.textPrimary)
        }
    }
}
