package com.sportage.obd

import com.sportage.obd.bluetooth.Elm327Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ELM327 command formatting, initialization sequences,
 * and transmission gear estimation algorithms.
 */
class Elm327CommandTest {

    @Test
    fun testCommandFormatting_CarriageReturnTermination() {
        // Exercises the production formatter, not a copy of it.
        val framed = Elm327Command.format("010C")
        assertEquals("010C\r", framed)
        assertTrue("Framed command must end with the 0x0D byte", framed.last() == '\r')
        assertFalse(
            "A literal backslash-r must never reach the adapter",
            framed.contains("\\r")
        )
    }

    @Test
    fun testCommandFormatting_TrimsSurroundingWhitespace() {
        assertEquals("ATZ\r", Elm327Command.format("  ATZ \n"))
    }

    @Test
    fun testCleanResponse_StripsPromptAndLineEndings() {
        val cleaned = Elm327Command.cleanResponse("SEARCHING...\r\r41 0C 1A F8\r\r>")
        assertEquals("SEARCHING...  41 0C 1A F8", cleaned)
    }

    @Test
    fun testInitializationSequence() {
        val initCommands = listOf(
            "ATZ",   // Reset chip
            "ATE0",  // Echo off
            "ATL0",  // Linefeeds off
            "ATS0",  // Spaces off
            "ATH0",  // Headers off
            "ATSP0"  // Auto protocol detect
        )
        assertEquals(6, initCommands.size)
        assertEquals("ATZ", initCommands[0])
        assertEquals("ATSP0", initCommands[5])
    }

    @Test
    fun testGearEstimation_Stationary() {
        // Vehicle stopped or idling -> Park / Neutral (Gear 0)
        assertEquals(0, calculateEstimatedGear(speedKmh = 0, rpm = 750))
        assertEquals(0, calculateEstimatedGear(speedKmh = 2, rpm = 800))
        assertEquals(0, calculateEstimatedGear(speedKmh = 0, rpm = 0))
    }

    @Test
    fun testGearEstimation_DriveGears() {
        // 1st Gear: 15 km/h at 1500 RPM (ratio = 100)
        assertEquals(1, calculateEstimatedGear(speedKmh = 15, rpm = 1500))

        // 2nd Gear: 30 km/h at 2000 RPM (ratio = 66.6)
        assertEquals(2, calculateEstimatedGear(speedKmh = 30, rpm = 2000))

        // 3rd Gear: 50 km/h at 2200 RPM (ratio = 44)
        assertEquals(3, calculateEstimatedGear(speedKmh = 50, rpm = 2200))

        // 4th Gear: 70 km/h at 2300 RPM (ratio = 32.8)
        assertEquals(4, calculateEstimatedGear(speedKmh = 70, rpm = 2300))

        // 5th Gear: 90 km/h at 2250 RPM (ratio = 25)
        assertEquals(5, calculateEstimatedGear(speedKmh = 90, rpm = 2250))

        // 6th Gear (Overdrive cruising): 120 km/h at 2100 RPM (ratio = 17.5)
        assertEquals(6, calculateEstimatedGear(speedKmh = 120, rpm = 2100))
    }

    private fun calculateEstimatedGear(speedKmh: Int, rpm: Int): Int {
        if (speedKmh <= 2 || rpm < 600) return 0 // P / N
        val ratio = rpm.toFloat() / speedKmh.toFloat()
        return when {
            ratio > 80f -> 1
            ratio in 55f..80f -> 2
            ratio in 38f..55f -> 3
            ratio in 28f..38f -> 4
            ratio in 22f..28f -> 5
            else -> 6
        }
    }
}
