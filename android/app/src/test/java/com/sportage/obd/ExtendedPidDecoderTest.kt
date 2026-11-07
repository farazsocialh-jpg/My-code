package com.sportage.obd

import com.sportage.obd.obd.DtcDatabase
import com.sportage.obd.obd.DtcSeverity
import com.sportage.obd.obd.PidDecoders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the extended PID decoders: MAF, fuel trims, timing,
 * runtime, VIN (Mode 09), readiness (Mode 01 PID 01), supported-PID
 * bitmaps, freeze frame (Mode 02) and Mode 0A permanent DTCs.
 */
class ExtendedPidDecoderTest {

    // --- MAF (0110) ---

    @Test
    fun testDecodeMaf_StandardResponse() {
        // ((0x03 * 256) + 0xE8) / 100 = 1000/100 = 10.0 g/s
        val maf = PidDecoders.decodeMaf("41 10 03 E8")
        assertNotNull(maf)
        assertEquals(10.0f, maf!!, 0.01f)
    }

    @Test
    fun testDecodeMaf_NoData() {
        assertNull(PidDecoders.decodeMaf("NO DATA"))
    }

    // --- Fuel trims (0106 / 0107) ---

    @Test
    fun testDecodeShortTermFuelTrim_ZeroTrim() {
        // A = 128 -> 0%
        val trim = PidDecoders.decodeShortTermFuelTrim("41 06 80")
        assertNotNull(trim)
        assertEquals(0f, trim!!, 0.01f)
    }

    @Test
    fun testDecodeLongTermFuelTrim_Positive() {
        // A = 160 -> (160-128)*100/128 = +25%
        val trim = PidDecoders.decodeLongTermFuelTrim("41 07 A0")
        assertNotNull(trim)
        assertEquals(25f, trim!!, 0.01f)
    }

    // --- Timing advance (010E) ---

    @Test
    fun testDecodeTimingAdvance() {
        // A = 100 -> 100/2 - 64 = -14
        val adv = PidDecoders.decodeTimingAdvance("41 0E 64")
        assertNotNull(adv)
        assertEquals(-14f, adv!!, 0.01f)
    }

    // --- Runtime (011F) ---

    @Test
    fun testDecodeRuntime() {
        // ((0x0A * 256) + 0x2C) = 2604 seconds
        val rt = PidDecoders.decodeRuntime("41 1F 0A 2C")
        assertNotNull(rt)
        assertEquals(2604, rt)
    }

    // --- VIN (0902) ---

    @Test
    fun testDecodeVin_MultiFrame() {
        // VIN "KNDPMCAC5N7123456" (17 chars) split across ISO-TP frames
        val response = "014\n0: 49 02 01 4B 4E 44\n1: 50 4D 43 41 43 35\n2: 4E 37 31 32 33 34\n3: 35 36"
        val vin = PidDecoders.decodeVin(response)
        assertNotNull(vin)
        assertEquals("KNDPMCAC5N7123456", vin)
        assertEquals(vin, PidDecoders.decodeVin(response.replace("\n", " ")))
    }

    @Test
    fun testDecodeVin_NoData() {
        assertNull(PidDecoders.decodeVin("NO DATA"))
    }

    // --- Readiness (0101) ---

    @Test
    fun testDecodeReadiness_MilOn() {
        // A = 0x82 -> MIL on, 2 codes; D = 0x00 -> all monitors complete
        val state = PidDecoders.decodeReadiness("41 01 82 07 FF 00")
        assertNotNull(state)
        assertTrue(state!!.milOn)
        assertEquals(2, state.storedDtcCount)
        assertTrue(state.sparkIgnitionMonitors.all { it.complete })
    }

    @Test
    fun testDecodeReadiness_MonitorsIncomplete() {
        // B high bits indicate incomplete continuous monitors; D bit 0 = catalyst incomplete.
        val state = PidDecoders.decodeReadiness("41 01 00 77 FF 01")
        assertNotNull(state)
        assertFalse(state!!.milOn)
        assertEquals(0, state.storedDtcCount)
        val misfire = state.sparkIgnitionMonitors.first { it.name == "Misfire" }
        assertFalse(misfire.complete)
        val catalyst = state.sparkIgnitionMonitors.first { it.name == "Catalyst" }
        assertFalse(catalyst.complete)
        val o2 = state.sparkIgnitionMonitors.first { it.name == "O2 Sensor" }
        assertTrue(o2.complete)
    }

    // --- Supported PIDs bitmap (0100) ---

    @Test
    fun testDecodeSupportedPids_Bitmap() {
        // 41 00 BE 3E B8 13 is the standard "supports most PIDs" response
        val pids = PidDecoders.decodeSupportedPids("41 00 BE 3E B8 13", 0x00)
        assertTrue(pids.isNotEmpty())
        // 0xBE = 10111110 -> PIDs 01,03,04,05,06,07 supported; 01 -> RPM? no: 0x0C is in 3E (00111110) -> 0B,0C,0D,0E,0F
        assertTrue(pids.contains("0C")) // RPM
        assertTrue(pids.contains("0D")) // Speed
        assertTrue(pids.contains("05")) // Coolant
        assertFalse(pids.contains("02")) // bit for 02 is 0 in 0xBE
    }

    @Test
    fun testDecodeSupportedPids_Garbage() {
        assertTrue(PidDecoders.decodeSupportedPids("NO DATA", 0x00).isEmpty())
    }

    // --- Freeze frame (Mode 02) ---

    @Test
    fun testDecodeFreezeFrame_Values() {
        val response = "42 04 32 42 05 87 42 0C 1A F8 42 0D 48 42 11 20"
        val frame = PidDecoders.decodeFreezeFrame(response)
        assertTrue(frame.items.isNotEmpty())
        val coolant = frame.items.first { it.label == "Coolant Temp" }
        assertEquals("95°C", coolant.value)
        val rpm = frame.items.first { it.label == "Engine RPM" }
        assertEquals("1726", rpm.value)
        val speed = frame.items.first { it.label == "Vehicle Speed" }
        assertEquals("72 km/h", speed.value)
    }

    // --- Mode 0A permanent DTCs ---

    @Test
    fun testDecodeDtcCodes_Mode0A_Permanent() {
        // 4A 01 33 = P0133 permanent
        val dtcs = PidDecoders.decodeDtcCodes("4A 01 33 00 00")
        assertEquals(1, dtcs.size)
        assertEquals("P0133", dtcs[0].code)
        assertEquals(DtcSeverity.WARNING, dtcs[0].severity)
        assertTrue(dtcs[0].description.contains("O2 Sensor"))
    }

    // --- DTC database ---

    @Test
    fun testDtcDatabase_KnownCodes() {
        val p1326 = DtcDatabase.lookup("P1326")
        assertEquals(DtcSeverity.CRITICAL, p1326.severity)
        assertTrue(p1326.description.contains("Knock Sensor Detection"))

        val p0420 = DtcDatabase.lookup("P0420")
        assertTrue(p0420.probableCause.contains("catalytic converter"))
    }

    @Test
    fun testDtcDatabase_UnknownCodeFallsBack() {
        val unknown = DtcDatabase.lookup("P9999")
        assertEquals(DtcSeverity.WARNING, unknown.severity)
        assertTrue(unknown.description.contains("P9999"))
    }

    @Test
    fun testReadinessRejectsMissingFourthDataByte() {
        assertNull(PidDecoders.decodeReadiness("41 01 00 07 FF"))
        val incomplete = PidDecoders.decodeReadiness("41 01 00 77 FF FF")!!
        assertTrue(incomplete.sparkIgnitionMonitors.none { it.complete })
    }

    @Test
    fun testVinWithRepeatedResponseHeaders() {
        val response = "0: 49 02 01 4B 4E 44\r\n" +
            "1: 49 02 02 50 4D 43 41 43 35\r\n" +
            "2: 49 02 03 4E 37 31 32 33 34\r\n3: 49 02 04 35 36"
        assertEquals("KNDPMCAC5N7123456", PidDecoders.decodeVin(response))
        assertNull(PidDecoders.decodeVin("49 02 01 4B 4E 44"))
    }

    @Test
    fun testFinalSingleByteFreezeFrameParameter() {
        assertTrue(PidDecoders.decodeFreezeFrame("42 0D 64").items.any { it.value == "100 km/h" })
    }
}
