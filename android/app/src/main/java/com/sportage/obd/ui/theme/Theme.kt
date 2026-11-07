package com.sportage.obd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The complete color identity of the cockpit. Every screen reads its colors from here
 * through the [LocalCockpitColors] composition local, so a single theme change
 * restyles the whole app at once.
 */
data class CockpitThemeColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    /** Slightly deeper shade than the background, used for inset boxes and graph plots. */
    val deepShade: Color,
    val accent: Color,
    val secondary: Color,
    val warning: Color,
    val critical: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Text/icon color that sits on top of [accent] filled buttons. */
    val onAccent: Color
)

data class CockpitThemePreset(
    val id: String,
    val title: String,
    val colors: CockpitThemeColors
)

object CockpitThemes {
    val defaultId = "cockpit-dark"

    val presets: List<CockpitThemePreset> = listOf(
        CockpitThemePreset(
            "cockpit-dark", "Cockpit Dark",
            CockpitThemeColors(
                isDark = true,
                background = Color(0xFF101417), surface = Color(0xFF1B2228), surfaceElevated = Color(0xFF2A353E),
                deepShade = Color(0xFF0A0F17), accent = Color(0xFF8BD8E7), secondary = Color(0xFF92D7BC),
                warning = Color(0xFFEFC681), critical = Color(0xFFFF8D97),
                textPrimary = Color(0xFFF0F4F6), textSecondary = Color(0xFFAFBEC8), onAccent = Color(0xFF05070B)
            )
        ),
        CockpitThemePreset(
            "midnight", "Midnight Blue",
            CockpitThemeColors(
                isDark = true,
                background = Color(0xFF0B1020), surface = Color(0xFF141C33), surfaceElevated = Color(0xFF22304F),
                deepShade = Color(0xFF070B16), accent = Color(0xFF7FB4FF), secondary = Color(0xFF8FD0C8),
                warning = Color(0xFFEFC681), critical = Color(0xFFFF8D97),
                textPrimary = Color(0xFFEEF3FA), textSecondary = Color(0xFFA9B7CC), onAccent = Color(0xFF05070B)
            )
        ),
        CockpitThemePreset(
            "emerald", "Emerald Night",
            CockpitThemeColors(
                isDark = true,
                background = Color(0xFF0C1512), surface = Color(0xFF16241E), surfaceElevated = Color(0xFF24382F),
                deepShade = Color(0xFF071009), accent = Color(0xFF7FE0A8), secondary = Color(0xFF9AD8E0),
                warning = Color(0xFFEFC681), critical = Color(0xFFFF8D97),
                textPrimary = Color(0xFFEDF5F0), textSecondary = Color(0xFFA8BFB4), onAccent = Color(0xFF05070B)
            )
        ),
        CockpitThemePreset(
            "sunset", "Sunset Drive",
            CockpitThemeColors(
                isDark = true,
                background = Color(0xFF171210), surface = Color(0xFF261B18), surfaceElevated = Color(0xFF3A2A24),
                deepShade = Color(0xFF100B09), accent = Color(0xFFEB9E6B), secondary = Color(0xFFE8C07D),
                warning = Color(0xFFEFC681), critical = Color(0xFFFF8D97),
                textPrimary = Color(0xFFF7EFE9), textSecondary = Color(0xFFC4B0A4), onAccent = Color(0xFF140B06)
            )
        ),
        CockpitThemePreset(
            "rose", "Rose Sport",
            CockpitThemeColors(
                isDark = true,
                background = Color(0xFF171013), surface = Color(0xFF251820), surfaceElevated = Color(0xFF3A2531),
                deepShade = Color(0xFF100A0D), accent = Color(0xFFFF9EB5), secondary = Color(0xFFD9A7E0),
                warning = Color(0xFFEFC681), critical = Color(0xFFFF8D97),
                textPrimary = Color(0xFFF9EEF2), textSecondary = Color(0xFFC7AEB9), onAccent = Color(0xFF14080C)
            )
        ),
        CockpitThemePreset(
            "violet", "Violet Night",
            CockpitThemeColors(
                isDark = true,
                background = Color(0xFF12101A), surface = Color(0xFF1D1A2B), surfaceElevated = Color(0xFF2E2A42),
                deepShade = Color(0xFF0B0912), accent = Color(0xFFB79CFF), secondary = Color(0xFF8FD0C8),
                warning = Color(0xFFEFC681), critical = Color(0xFFFF8D97),
                textPrimary = Color(0xFFF1EEF9), textSecondary = Color(0xFFB0AAC7), onAccent = Color(0xFF0F0A1A)
            )
        ),
        CockpitThemePreset(
            "arctic", "Arctic Light",
            CockpitThemeColors(
                isDark = false,
                background = Color(0xFFF2F5F7), surface = Color(0xFFFFFFFF), surfaceElevated = Color(0xFFE1E8ED),
                deepShade = Color(0xFFE4EAEE), accent = Color(0xFF0E7490), secondary = Color(0xFF0F766E),
                warning = Color(0xFFB45309), critical = Color(0xFFBE123C),
                textPrimary = Color(0xFF11181C), textSecondary = Color(0xFF52616B), onAccent = Color(0xFFFFFFFF)
            )
        ),
        CockpitThemePreset(
            "slate", "Slate Light",
            CockpitThemeColors(
                isDark = false,
                background = Color(0xFFEEF1F4), surface = Color(0xFFFFFFFF), surfaceElevated = Color(0xFFDFE5EB),
                deepShade = Color(0xFFE3E8ED), accent = Color(0xFF3B5BDB), secondary = Color(0xFF0F766E),
                warning = Color(0xFFB45309), critical = Color(0xFFBE123C),
                textPrimary = Color(0xFF141A21), textSecondary = Color(0xFF55616E), onAccent = Color(0xFFFFFFFF)
            )
        )
    )

    /** Accent colors the driver can layer on top of any preset. */
    val accentChoices: List<Color> = listOf(
        Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF34D399), Color(0xFFA3E635),
        Color(0xFFFBBF24), Color(0xFFFB923C), Color(0xFFF87171), Color(0xFFFB7185),
        Color(0xFFA78BFA), Color(0xFFC084FC), Color(0xFF2DD4BF), Color(0xFF8BD8E7)
    )

    fun byId(id: String?): CockpitThemePreset =
        presets.firstOrNull { it.id == id } ?: presets.first()

    /** Applies an optional accent override (ARGB hex like "FF22D3EE") on top of a preset. */
    fun apply(presetId: String?, accentOverride: String?): CockpitThemeColors {
        val preset = byId(presetId)
        val parsed = parseAccent(accentOverride)
        return if (parsed == null) preset.colors else preset.colors.copy(
            accent = parsed,
            onAccent = if (parsed.luminance() > 0.5f) Color(0xFF0A0F14) else Color(0xFFFFFFFF)
        )
    }

    fun parseAccent(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        return try {
            val cleaned = hex.removePrefix("#").takeLast(8)
            if (cleaned.length == 8) Color(cleaned.toLong(16).toInt()) else null
        } catch (_: Exception) { null }
    }
}

/** Colors for the current theme; defaults keep non-preview tooling renders sensible. */
val LocalCockpitColors = staticCompositionLocalOf { CockpitThemes.apply(CockpitThemes.defaultId, null) }

private val CockpitTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
)

/**
 * Themed color accessors. They keep the historical names so every existing screen
 * picks up the driver-selected theme without hardcoded palette values.
 */
@Composable fun DarkBackground(): Color = LocalCockpitColors.current.background
@Composable fun DarkSurface(): Color = LocalCockpitColors.current.surface
@Composable fun DarkSurfaceElevated(): Color = LocalCockpitColors.current.surfaceElevated
@Composable fun DeepShade(): Color = LocalCockpitColors.current.deepShade
@Composable fun ElectricCyan(): Color = LocalCockpitColors.current.accent
@Composable fun TealAccent(): Color = LocalCockpitColors.current.secondary
@Composable fun WarningAmber(): Color = LocalCockpitColors.current.warning
@Composable fun AmberWarning(): Color = LocalCockpitColors.current.warning
@Composable fun CriticalRed(): Color = LocalCockpitColors.current.critical
@Composable fun PrimaryText(): Color = LocalCockpitColors.current.textPrimary
@Composable fun SecondaryText(): Color = LocalCockpitColors.current.textSecondary
@Composable fun OnAccent(): Color = LocalCockpitColors.current.onAccent

@Composable
fun SportageObdTheme(
    themeId: String = CockpitThemes.defaultId,
    accentOverride: String? = null,
    content: @Composable () -> Unit
) {
    val colors = CockpitThemes.apply(themeId, accentOverride)
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent,
            primaryContainer = colors.surfaceElevated,
            onPrimaryContainer = colors.accent,
            secondary = colors.secondary,
            secondaryContainer = colors.surfaceElevated,
            onSecondaryContainer = colors.textPrimary,
            background = colors.background,
            surface = colors.surface,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.textSecondary.copy(alpha = 0.5f),
            outlineVariant = colors.surfaceElevated,
            onPrimary = colors.onAccent,
            onSecondary = colors.onAccent,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            error = colors.critical
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            primaryContainer = colors.surfaceElevated,
            onPrimaryContainer = colors.accent,
            secondary = colors.secondary,
            secondaryContainer = colors.surfaceElevated,
            onSecondaryContainer = colors.textPrimary,
            background = colors.background,
            surface = colors.surface,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.textSecondary.copy(alpha = 0.5f),
            outlineVariant = colors.surfaceElevated,
            onPrimary = colors.onAccent,
            onSecondary = colors.onAccent,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            error = colors.critical
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalCockpitColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CockpitTypography,
            content = content
        )
    }
}
