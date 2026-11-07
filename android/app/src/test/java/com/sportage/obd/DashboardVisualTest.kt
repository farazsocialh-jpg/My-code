package com.sportage.obd

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.CompositionLocalProvider
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.ui.CarLauncherScreen
import com.sportage.obd.ui.theme.SportageObdTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import org.robolectric.Shadows.shadowOf
import android.content.Intent

/** Actual Compose layouts rendered with simulated Android native graphics, not HTML mockups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardVisualTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var activity: Activity
    private var scans = 0
    private var clears = 0
    private var bluetoothOpens = 0
    private var appDrawerOpens = 0

    private fun launch(fontScale: Float = 1f, drivingSpeed: Int = 64, connected: Boolean = true) {
        val page = mutableIntStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
              SportageObdTheme {
                activity = LocalContext.current as Activity
                CarLauncherScreen(
                    connectionState = if (connected) ConnectionState.Connected("ELM327", "00:11:22:33:44:55") else ConnectionState.Disconnected,
                    measuredHz = 8.7f, speed = drivingSpeed, rpm = 2100, coolant = 89, intakeTemp = 32,
                    fuelLevel = 68, throttle = 24f, load = 37f, voltage = 13.8f, gear = 4,
                    speedHistory = listOf(1000L to 0, 2000L to 18, 3000L to 32, 4000L to 47, 5000L to 56, 6000L to 64),
                    rpmHistory = listOf(1000L to 800, 2000L to 1500, 3000L to 1900, 4000L to 2400, 5000L to 1800, 6000L to 2100),
                    currentLat = 25.2854, currentLon = 51.5310,
                    activePage = page.intValue, onNavigatePage = { page.intValue = it },
                    onOpenBluetoothClick = { bluetoothOpens++ },
                    onOpenAppDrawer = { appDrawerOpens++ }, dtcCodes = emptyList(), isScanningDtc = false,
                    onScanDtc = { scans++ }, onClearDtc = { clears++ }, tripDistanceKm = 24.7,
                    tripDurationSeconds = 1800, tripMaxSpeed = 90, tripAvgSpeed = 49,
                    onResetTrip = {}, onSetDefaultLauncher = {}, isDemoMode = false,
                    onToggleDemoMode = {}
                )
              }
            }
        }
    }

    private fun snapshot(name: String) {
        compose.waitForIdle()
        lateinit var bitmap: Bitmap
        // Map tile jobs start during draw. Allow real renderer threads to fill the cache
        // while Compose's controlled clock remains idle; do not fake or overlay map pixels.
        repeat(12) {
            compose.runOnIdle {
                val view = activity.window.decorView
                bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
            }
            Thread.sleep(125)
        }
        val output = File("build/reports/dashboard-$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        if (name in listOf("headunit", "portrait", "phone-landscape", "s24-portrait", "s24-landscape", "wide-headunit")) {
            val rows = listOf("dashboard_header", "home_map_card", "home_map_header", "home_map_viewport", "home_map_footer")
                .map { tag ->
                    val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                    "$tag,${bounds.left},${bounds.top},${bounds.width},${bounds.height}"
                }
            File("build/reports/layout-$name.csv").writeText("component,x,y,width,height\n" + rows.joinToString("\n"))
            val card = compose.onNodeWithTag("home_map_card").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("home_map_viewport").fetchSemanticsNode().boundsInRoot
            val header = compose.onNodeWithTag("home_map_header").fetchSemanticsNode().boundsInRoot
            val footer = compose.onNodeWithTag("home_map_footer").fetchSemanticsNode().boundsInRoot
            assertEquals(card.width, viewport.width, 1f)
            assertEquals(header.bottom, viewport.top, 1f)
            assertEquals(footer.top, viewport.bottom, 1f)
            assertTrue("Viewport must remain inside its card", viewport.top >= card.top && viewport.bottom <= card.bottom)
            compose.onNodeWithTag("dashboard_header").assertHeightIsEqualTo(48.dp)
            compose.onNodeWithTag("home_map_header").assertHeightIsEqualTo(48.dp)
            compose.onNodeWithTag("home_map_footer").assertHeightIsEqualTo(48.dp)
        }
    }

    @Test fun portraitDashboardAndNavigation() {
        launch()
        compose.onNodeWithText("64 km/h", substring = false).assertExists()
        compose.onAllNodesWithContentDescription("Open navigation app").assertCountEquals(1)
        snapshot("portrait")
        compose.onNodeWithText("Trip").performClick()
        compose.onNodeWithText("24.7", substring = false).assertExists()
        compose.onNodeWithText("00:30:00").assertIsDisplayed()
        snapshot("trip-portrait")
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Setup").performClick()
        compose.onNodeWithText("Speed & Distance Units").assertExists()
        val label = compose.onNodeWithText("Android Bluetooth System Settings").fetchSemanticsNode().boundsInRoot
        val control = compose.onNodeWithText("Open Settings").fetchSemanticsNode().boundsInRoot
        assertTrue("Settings labels must not overlap their controls", label.right <= control.left)
        snapshot("settings-portrait")
    }

    @Test
    @Config(qualifiers = "w1024dp-h600dp-land-mdpi")
    fun headUnitDashboardAndSettings() {
        launch()
        compose.onAllNodesWithContentDescription("Open navigation app").assertCountEquals(1)
        snapshot("headunit")
        // The rail now holds 11 destinations; scroll it so Setup is reachable.
        compose.onAllNodes(hasScrollAction())[0].performTouchInput { swipeUp() }
        compose.onNodeWithText("Setup").performClick()
        compose.onNodeWithText("Speed & Distance Units").assertExists()
        snapshot("settings-headunit")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-port-mdpi")
    fun narrowPhoneWithLargeTextRetainsControls() {
        launch(fontScale = 1.3f, drivingSpeed = 127)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("127 km/h").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("Three-digit speed must not clip at enlarged font scale: ${layouts.single()}", layouts.single().didOverflowWidth)
        compose.onNodeWithContentDescription("Open navigation app")
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithText("Connected").assertExists()
        compose.onNodeWithText("More").assertExists()
        snapshot("large-text-phone")
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Setup").performClick()
        compose.onNodeWithText("Open Settings").performScrollTo()
            .assertWidthIsAtLeast(128.dp).assertHeightIsAtLeast(48.dp)
        snapshot("large-text-settings")
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-mdpi")
    fun shortLandscapeRetainsNavigationAndScrollableTelemetry() {
        launch()
        compose.onAllNodesWithContentDescription("Open navigation app").assertCountEquals(1)
        compose.onNodeWithText("64 km/h", substring = false).assertExists()
        // The richer vehicle pane now scrolls on short screens; readings stay reachable.
        compose.onNodeWithText("2100 rpm", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("89°C", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("13.8 V", substring = false).performScrollTo().assertIsDisplayed()
        snapshot("phone-landscape")
        compose.onNodeWithText("24%", substring = false).performScrollTo().assertIsDisplayed()
    }

    @Test fun secondaryPhoneScreensRemainReachable() {
        launch()
        compose.onNodeWithText("Gauges").performClick()
        compose.onNodeWithText("Engine history · 60 seconds").assertExists()
        snapshot("gauges-portrait")
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Diagnostics").performClick()
        snapshot("diagnostics-portrait")
        compose.onNodeWithText("Scan ECU (03)").performClick()
        assertEquals(1, scans)
        compose.onNodeWithText("Clear Codes (04)").performClick()
        compose.onNodeWithText("Clear Fault Codes (Mode 04)?").assertExists()
        assertEquals("Opening a confirmation must not clear ECU codes", 0, clears)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, clears)
        compose.onNodeWithText("Media").performClick()
        snapshot("media-portrait")
    }

    @Test fun mapThemesDockAndSensorWorkspaceAreReachable() {
        launch(connected = false)
        compose.onNodeWithContentDescription("Choose map theme").performClick()
        compose.onNodeWithText("Night").assertExists()
        compose.onNodeWithText("Qatar offline").performClick()
        compose.onNodeWithContentDescription("Open music app").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Play or pause media").assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Quick actions").performClick()
        compose.onNodeWithText("Next track").assertExists()
        compose.onNodeWithText("OBD Bluetooth").performClick()
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Sensors").performClick()
        compose.onNodeWithText("OBD workspace").assertExists()
        compose.onNodeWithText("All", substring = false).performClick()
        compose.onNodeWithText("Search sensors, category or PID").performTextInput("5C")
        compose.onNodeWithText("Engine oil temperature").assertExists()
        compose.onNodeWithContentDescription("Favorite Engine oil temperature").performClick()
        compose.onNodeWithText("Favorites").performClick()
        compose.onNodeWithText("Engine oil temperature").assertExists()
        snapshot("sensors-portrait")
        compose.onNodeWithText("Vehicle / tests").performClick()
        compose.onNodeWithText("VIN", substring = false).assertExists()
        snapshot("vehicle-portrait")
    }

    @Test @Config(qualifiers = "w412dp-h915dp-port-mdpi")
    fun s24SizePortrait() { launch(); snapshot("s24-portrait") }

    @Test @Config(qualifiers = "w915dp-h412dp-land-mdpi")
    fun s24SizeLandscape() { launch(); snapshot("s24-landscape") }

    @Test @Config(qualifiers = "w1920dp-h720dp-land-mdpi")
    fun wideHeadunit() { launch(); snapshot("wide-headunit") }

    @Test fun compactHomeControlsKeepTheirCallbacks() {
        launch()
        compose.onNodeWithText("Connected", substring = false).performClick()
        assertEquals(1, bluetoothOpens)
        compose.onNodeWithText("Alert · 100 km/h", substring = false).performClick()
        compose.onNodeWithText("Alert · 120 km/h", substring = false).assertExists()
        compose.onNodeWithContentDescription("Open installed apps").performClick()
        assertEquals(1, appDrawerOpens)
        compose.onNodeWithContentDescription("Open phone dialer").performClick()
        assertEquals(Intent.ACTION_DIAL, shadowOf(activity).nextStartedActivity.action)
        compose.onNodeWithContentDescription("Open navigation app").performClick()
        val navigation = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, navigation.action)
        assertEquals("geo", navigation.data?.scheme)
    }

    @Test fun swipeableBoardsAndThemeCustomizationAreReachable() {
        launch()
        // Boards page is directly in the bottom navigation.
        compose.onNodeWithText("Boards").performClick()
        compose.onNodeWithTag("boards_header").assertExists()
        compose.onNodeWithTag("board_widget_speed").assertExists()
        compose.onNodeWithTag("board_widget_map").assertExists()
        snapshot("boards-portrait")
        // Add a widget through the picker.
        compose.onNodeWithTag("board_add_button").performClick()
        compose.onNodeWithTag("widget_picker_list").performScrollToNode(hasTestTag("picker_fuel"))
        compose.onNodeWithTag("picker_fuel").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("board_grid_0").performScrollToNode(hasTestTag("board_widget_fuel"))
        compose.onNodeWithTag("board_widget_fuel").assertExists()
        snapshot("boards-with-added-widget")
        // Theme customization lives in Setup and restyles the whole app.
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Setup").performClick()
        compose.onNodeWithText("THEME & COLORS").assertExists()
        compose.onNodeWithTag("theme_preset_arctic").performClick()
        compose.onNodeWithText("Arctic Light").assertExists()
        snapshot("settings-theme-arctic")
        compose.onNodeWithTag("theme_preset_cockpit-dark").performClick()
        compose.onNodeWithText("Cockpit Dark").assertExists()
    }
}
