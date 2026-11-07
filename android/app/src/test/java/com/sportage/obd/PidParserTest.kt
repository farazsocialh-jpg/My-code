package com.sportage.obd

import com.sportage.obd.obd.DtcSeverity
import com.sportage.obd.obd.PidDecoders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for OBD-II Mode 01 PID and Mode 03 / 07 DTC parsers.
 * Validates real vehicle responses, whitespace tolerance, edge conditions,
 * and error strings ("NO DATA", "UNABLE TO CONNECT").
 */
class PidParserTest {

    @Test
    fun testDecodeRpm_StandardResponse() {
        // ((0x1A * 256) + 0xF8) / 4 = ((26 * 256) + 248) / 4 = 1726 RPM
        val response = "41 0C 1A F8"
        val rpm = PidDecoders.decodeRpm(response)
        assertNotNull(rpm)
        assertEquals(1726, rpm)
    }

    @Test
    fun testDecodeRpm_IdleRpmWithPromptAndSpaces() {
        // ((0x0B * 256) + 0xB8) / 4 = ((11 * 256) + 184) / 4 = 750 RPM
        val response = "SEARCHING...\r\n41 0C 0B B8\r\n>"
        val rpm = PidDecoders.decodeRpm(response)
        assertNotNull(rpm)
        assertEquals(750, rpm)
    }

    @Test
    fun testDecodeRpm_ZeroRpm() {
        val response = "41 0C 00 00"
        val rpm = PidDecoders.decodeRpm(response)
        assertEquals(0, rpm)
    }

    @Test
    fun testDecodeRpm_NoData() {
        assertNull(PidDecoders.decodeRpm("NO DATA"))
        assertNull(PidDecoders.decodeRpm("UNABLE TO CONNECT"))
        assertNull(PidDecoders.decodeRpm(""))
    }

    @Test
    fun testDecodeSpeed_ValidSpeed() {
        // 0x3C = 60 km/h
        val response = "41 0D 3C"
        assertEquals(60, PidDecoders.decodeSpeed(response))

        // 0x78 = 120 km/h
        assertEquals(120, PidDecoders.decodeSpeed("410D78"))

        // 0x00 = 0 km/h
        assertEquals(0, PidDecoders.decodeSpeed("41 0D 00\r\n>"))
    }

    @Test
    fun testDecodeSpeed_NoData() {
        assertNull(PidDecoders.decodeSpeed("NO DATA"))
        assertNull(PidDecoders.decodeSpeed("CAN ERROR"))
    }

    @Test
    fun testDecodeCoolantTemp_NormalOperating() {
        // 0x82 = 130 -> 130 - 40 = 90 °C
        val response = "41 05 82"
        assertEquals(90, PidDecoders.decodeCoolantTemp(response))

        // 0x28 = 40 -> 40 - 40 = 0 °C
        assertEquals(0, PidDecoders.decodeCoolantTemp("41 05 28"))

        // 0x1E = 30 -> 30 - 40 = -10 °C
        assertEquals(-10, PidDecoders.decodeCoolantTemp("41 05 1E"))
    }

    @Test
    fun testDecodeIntakeTemp() {
        // 0x46 = 70 -> 70 - 40 = 30 °C
        val response = "41 0F 46"
        assertEquals(30, PidDecoders.decodeIntakeTemp(response))
    }

    @Test
    fun testDecodeFuelLevel() {
        // 0x7F = 127 -> (127 * 100) / 255 = 49%
        val response = "41 2F 7F"
        assertEquals(49, PidDecoders.decodeFuelLevel(response))

        // Full tank: 0xFF = 255 -> 100%
        assertEquals(100, PidDecoders.decodeFuelLevel("41 2F FF"))

        // Empty: 0x00 -> 0%
        assertEquals(0, PidDecoders.decodeFuelLevel("41 2F 00"))
    }

    @Test
    fun testDecodeThrottle() {
        // 0x33 = 51 -> (51 * 100) / 255 = 20.0%
        val throttle = PidDecoders.decodeThrottle("41 11 33")
        assertNotNull(throttle)
        assertEquals(20.0f, throttle!!, 0.1f)
    }

    @Test
    fun testDecodeLoad() {
        // 0x40 = 64 -> (64 * 100) / 255 = 25.09%
        val load = PidDecoders.decodeLoad("41 04 40")
        assertNotNull(load)
        assertEquals(25.1f, load!!, 0.2f)
    }

    @Test
    fun testDecodeBatteryVoltage() {
        assertEquals(14.2f, PidDecoders.decodeBatteryVoltage("14.2V")!!, 0.01f)
        assertEquals(12.6f, PidDecoders.decodeBatteryVoltage("12.6 V\r>")!!, 0.01f)
        assertEquals(13.8f, PidDecoders.decodeBatteryVoltage("SEARCHING... 13.8V")!!, 0.01f)
        assertNull(PidDecoders.decodeBatteryVoltage("ERR"))
    }

    @Test
    fun testDecodeDtcCodes_SingleDtc() {
        // Mode 03 response with P0133
        val response = "43 01 33 00 00 00 00"
        val dtcs = PidDecoders.decodeDtcCodes(response)
        assertEquals(1, dtcs.size)
        assertEquals("P0133", dtcs[0].code)
        assertEquals("Powertrain / Engine", dtcs[0].system)
        assertEquals(DtcSeverity.WARNING, dtcs[0].severity)
    }

    @Test
    fun testDecodeDtcCodes_MultipleDtcs() {
        // Mode 03 response with P0133, P0300, and P0420
        val response = "43 01 33 03 00 04 20"
        val dtcs = PidDecoders.decodeDtcCodes(response)
        assertEquals(3, dtcs.size)
        assertEquals("P0133", dtcs[0].code)
        assertEquals("P0300", dtcs[1].code)
        assertEquals(DtcSeverity.CRITICAL, dtcs[1].severity)
        assertEquals("P0420", dtcs[2].code)
    }

    @Test
    fun testDecodeDtcCodes_PendingMode07() {
        // Mode 07 response with P0171
        val response = "47 01 71 00 00"
        val dtcs = PidDecoders.decodeDtcCodes(response)
        assertEquals(1, dtcs.size)
        assertEquals("P0171", dtcs[0].code)
    }

    @Test
    fun testDecodeDtcCodes_CleanEcu() {
        // No stored trouble codes
        val response = "43 00 00 00 00 00 00"
        val dtcs = PidDecoders.decodeDtcCodes(response)
        assertTrue(dtcs.isEmpty())
    }

    @Test
    fun testDecodeDtcCodes_NoData() {
        val dtcs = PidDecoders.decodeDtcCodes("NO DATA")
        assertTrue(dtcs.isEmpty())
    }
}
