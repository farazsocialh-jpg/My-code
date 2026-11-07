package com.sportage.obd

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import com.sportage.obd.ui.boards.BoardData
import com.sportage.obd.ui.boards.BoardLayout
import com.sportage.obd.ui.boards.BoardScreen
import com.sportage.obd.ui.boards.BoardStore
import com.sportage.obd.ui.boards.BoardWidgetType
import com.sportage.obd.ui.theme.SportageObdTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the swipeable boards screen with real Compose layout, not mockups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoardsVisualTest {
    @get:Rule val compose = createComposeRule()

    private class MemoryStore(var layout: BoardLayout = BoardLayout.DEFAULT) : BoardStore {
        override fun load(): BoardLayout = layout
        override fun save(layout: BoardLayout) { this.layout = layout }
    }

    private fun data() = BoardData(
        speed = 88, speedUnit = "km/h", rpm = 2100, coolant = 89, intakeTemp = 32,
        fuelLevel = 68, throttle = 24f, load = 37f, voltage = 13.8f, gear = 4,
        currentLat = 25.2854, currentLon = 51.5310, gpsStatus = "GPS · ±6 m",
        tripDistanceKm = 24.7, tripDurationSeconds = 1800, connectionLabel = "Connected · ELM327",
        connectionOk = true,
        speedHistory = listOf(1000L to 0, 2000L to 18, 3000L to 32, 4000L to 47, 5000L to 56, 6000L to 88),
        rpmHistory = listOf(1000L to 800, 2000L to 1500, 3000L to 1900, 4000L to 2400, 5000L to 1800, 6000L to 2100)
    )

    private fun mount(store: MemoryStore, themeId: String = "cockpit-dark") {
        compose.setContent {
            SportageObdTheme(themeId = themeId) {
                BoardScreen(data = data(), store = store)
            }
        }
        compose.waitForIdle()
    }

    @Test fun defaultBoardRendersItsWidgets() {
        val store = MemoryStore()
        mount(store)
        compose.onNodeWithTag("boards_header").assertExists()
        compose.onNodeWithTag("boards_pager").assertExists()
        compose.onNodeWithTag("board_widget_speed").assertExists()
        compose.onNodeWithTag("board_widget_map").assertExists()
        compose.onNodeWithTag("board_widget_clock").assertExists()
        compose.onNodeWithText("88").assertExists()
    }

    @Test fun driverCanAddAWidgetFromThePicker() {
        val store = MemoryStore()
        mount(store)
        compose.onNodeWithTag("board_add_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("widget_picker_list").performScrollToNode(hasTestTag("picker_fuel"))
        compose.onNodeWithTag("picker_fuel").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        // The new widget may sit below the fold of the lazy grid, so scroll to it.
        compose.onNodeWithTag("board_grid_0").performScrollToNode(hasTestTag("board_widget_fuel"))
        compose.onNodeWithTag("board_widget_fuel").assertExists()
        assertTrue(BoardWidgetType.FUEL in store.layout.boards[0])
    }

    @Test fun duplicateWidgetsAreRefused() {
        val store = MemoryStore()
        mount(store)
        compose.onNodeWithTag("board_add_button").performClick()
        compose.waitForIdle()
        // Speed is already on the default board; its picker row is disabled, so tapping
        // it must not add a second speed widget.
        compose.onNodeWithTag("picker_speed").performClick()
        compose.waitForIdle()
        assertEquals(1, store.layout.boards[0].count { it == BoardWidgetType.SPEED })
    }

    @Test fun editModeRemovesWidgets() {
        val store = MemoryStore(BoardLayout(listOf(listOf(BoardWidgetType.SPEED, BoardWidgetType.CLOCK))))
        mount(store)
        compose.onNodeWithTag("board_edit_toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("board_widget_clock").assertExists()
        // The clock is the second widget, so its remove control is the second match.
        compose.onAllNodesWithContentDescription("Remove widget")[1].performClick()
        compose.waitForIdle()
        assertFalse(BoardWidgetType.CLOCK in store.layout.boards[0])
        assertTrue(BoardWidgetType.SPEED in store.layout.boards[0])
    }

    @Test fun editModeReordersWidgets() {
        val store = MemoryStore(BoardLayout(listOf(listOf(BoardWidgetType.SPEED, BoardWidgetType.RPM))))
        mount(store)
        compose.onNodeWithTag("board_edit_toggle").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Move right")[0].performClick()
        compose.waitForIdle()
        assertEquals(
            listOf(BoardWidgetType.RPM, BoardWidgetType.SPEED),
            store.layout.boards[0]
        )
    }

    @Test fun editModeAddsAndRemovesBoards() {
        val store = MemoryStore()
        mount(store)
        compose.onNodeWithTag("board_edit_toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add board").performClick()
        compose.waitForIdle()
        assertEquals(2, store.layout.boards.size)
        compose.onNodeWithContentDescription("Remove this board").performClick()
        compose.waitForIdle()
        assertEquals(1, store.layout.boards.size)
    }

    @Test fun lightThemeRendersBoardsToo() {
        val store = MemoryStore()
        mount(store, themeId = "arctic")
        compose.onNodeWithTag("board_widget_speed").assertExists()
        compose.onNodeWithText("88").assertExists()
    }

    @Test fun emptyBoardShowsGuidance() {
        val store = MemoryStore(BoardLayout(listOf(emptyList())))
        mount(store)
        compose.onNodeWithTag("board_empty_0").assertExists()
        compose.onNodeWithText("Board 1 is empty").assertExists()
    }
}
