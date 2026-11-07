package com.sportage.obd

import com.sportage.obd.ui.boards.BoardLayout
import com.sportage.obd.ui.boards.BoardLimits
import com.sportage.obd.ui.boards.BoardWidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Board layout persistence: JSON codec, limits and fallbacks. */
class BoardsTest {

    @Test fun defaultLayoutIsSensible() {
        val layout = BoardLayout.DEFAULT
        assertTrue(layout.boards.isNotEmpty())
        assertTrue(layout.boards[0].contains(BoardWidgetType.SPEED))
        assertTrue(layout.boards[0].contains(BoardWidgetType.MAP))
    }

    @Test fun jsonRoundTripPreservesWidgets() {
        val layout = BoardLayout(listOf(
            listOf(BoardWidgetType.SPEED, BoardWidgetType.RPM, BoardWidgetType.CLOCK),
            listOf(BoardWidgetType.MAP, BoardWidgetType.GPS)
        ))
        val restored = BoardLayout.fromJson(BoardLayout.toJson(layout))
        assertEquals(layout, restored)
    }

    @Test fun malformedJsonFallsBackToDefault() {
        assertEquals(BoardLayout.DEFAULT, BoardLayout.fromJson("not json at all {"))
        assertEquals(BoardLayout.DEFAULT, BoardLayout.fromJson("[[\"unknown_widget\"]]"))
        assertEquals(BoardLayout.DEFAULT, BoardLayout.fromJson(null))
    }

    @Test fun unknownWidgetIdsAreDroppedWithoutLosingBoard() {
        val restored = BoardLayout.fromJson("speed|bogus|voltage")
        assertEquals(listOf(BoardWidgetType.SPEED, BoardWidgetType.VOLTAGE), restored.boards[0])
    }

    @Test fun limitsAreEnforcedWhenParsing() {
        val manyBoards = (1..(BoardLimits.MAX_BOARDS + 3)).joinToString(";;") { "speed|rpm" }
        val restored = BoardLayout.fromJson(manyBoards)
        assertTrue(restored.boards.size <= BoardLimits.MAX_BOARDS)
    }

    @Test fun everyWidgetTypeSurvivesARoundTrip() {
        val all = BoardWidgetType.entries.toList()
        // Split across two boards so the per-board widget limit is respected.
        val layout = BoardLayout(listOf(all.take(10), all.drop(10)))
        val restored = BoardLayout.fromJson(BoardLayout.toJson(layout))
        assertEquals(layout, restored)
        // Every type must resolve by its persisted id.
        BoardWidgetType.entries.forEach { w -> assertNotNull(BoardWidgetType.byId(w.id)) }
    }
}
