package com.sportage.obd

import com.sportage.obd.obd.SensorCatalog
import com.sportage.obd.obd.PidDecoders
import org.junit.Assert.*
import org.junit.Test

class SensorCatalogTest {
    private fun value(pid: String, bytes: String) = SensorCatalog.decode(pid, "41$pid$bytes")!!.values.first().value

    @Test fun allDefinitionsDecodeTheirExpectedByteCountWithoutNonfiniteValues() {
        assertTrue(SensorCatalog.entries.size >= 80)
        assertEquals(SensorCatalog.entries.size, SensorCatalog.byPid.size)
        SensorCatalog.entries.forEach { definition ->
            assertTrue(definition.pid.matches(Regex("[0-9A-F]{2}")))
            for (byte in listOf("00", "7F", "FF")) {
                val result = SensorCatalog.decode(definition.pid, "41${definition.pid}${byte.repeat(definition.bytes)}")
                assertNotNull(definition.title, result)
                assertTrue(definition.title, result!!.values.all { it.value.isFinite() })
            }
            assertNull(SensorCatalog.decode(definition.pid, "NO DATA"))
            assertNull(SensorCatalog.decode(definition.pid, "01${definition.pid}"))
            assertNull(SensorCatalog.decode(definition.pid, "41${definition.pid}" + "00".repeat(definition.bytes - 1)))
        }
        println("Read-only catalogue: ${SensorCatalog.entries.size} standard Mode 01 PIDs")
    }

    @Test fun signedVaporPressureAndUnsignedOdometerAreNotCorrupted() {
        assertEquals(-1.0, value("32", "FFFC"), 0.0001)
        assertEquals(-4.0, value("54", "FFFC"), 0.0001)
        assertEquals(429496729.5, value("A6", "FFFFFFFF"), 0.0001)
    }

    @Test fun fuelVoltagePressureTorqueAndTemperatureFormulas() {
        assertEquals(13.8, value("42", "35E8"), 0.0001)
        assertEquals(90.0, value("5C", "82"), 0.0001)
        assertEquals(10.0, value("5E", "00C8"), 0.0001)
        assertEquals(1000.0, value("23", "0064"), 0.0001)
        assertEquals(1.0, value("44", "8000"), 0.0001)
        assertEquals(-25.0, value("62", "64"), 0.0001)
    }

    @Test fun widebandSecondValuesAndUnusedNarrowbandTrim() {
        val voltage = SensorCatalog.decode("24", "41 24 80 00 20 00")!!
        assertEquals(1.0, voltage.values[0].value, 0.0001)
        assertEquals(1.0, voltage.values[1].value, 0.0001)
        val current = SensorCatalog.decode("34", "41 34 80 00 7F 00")!!
        assertEquals(-1.0, current.values[1].value, 0.0001)
        assertEquals(1, SensorCatalog.decode("14", "41 14 80 FF")!!.values.size)
        assertEquals(2, SensorCatalog.decode("14", "41 14 80 80")!!.values.size)
    }

    @Test fun echoedLowercaseHeaderAndPaddingResponses() {
        assertEquals(90.0, SensorCatalog.decode("5C", "015C 7E8 03 41 5c 82")!!.values[0].value, 0.001)
        assertEquals(90.0, SensorCatalog.decode("5C", "415C8200000000")!!.values[0].value, 0.001)
        assertNull(SensorCatalog.decode("0C", "41 0C 1A"))
        // A truncated first ECU must not consume its next line; the second complete ECU is valid.
        assertEquals(0.0, SensorCatalog.decode("0C", "41 0C 1A\n7E9 04 41 0C 00 00")!!.values[0].value, 0.001)
    }

    @Test fun onlyExplicitlyAdvertisedNoncoreSensorsAreInExtraRotation() {
        assertTrue(SensorCatalog.extraPids(emptySet()).isEmpty())
        assertEquals(setOf("42", "5C"), SensorCatalog.extraPids(setOf("0C", "42", "5C", "FE")).toSet())
    }

    @Test fun freshnessUsesTheIndividualSensorsTimestamp() {
        val sample = SensorCatalog.decode("5C", "415C82", 1000)!!
        assertFalse(sample.stale(91_000))
        assertTrue(sample.stale(91_001))
        assertEquals(1000L, sample.timestamp)
    }

    @Test fun capabilityBitmapsMergeMultipleEcusAndHandleHigherPages() {
        val supported = PidDecoders.decodeSupportedPids("7E8 06 41 00 80 00 00 01\r7E9 06 41 00 40 00 00 00", 0)
        assertEquals(setOf("01", "02", "20"), supported)
        assertEquals(setOf("A6"), PidDecoders.decodeSupportedPids("41 a0 04 00 00 00", 0xA0))
    }

    @Test fun readinessDoesNotClaimUnsupportedTestsAreCompleteOrMixEngineTypes() {
        val unsupported = PidDecoders.decodeReadiness("410100000000")!!
        assertTrue(unsupported.sparkIgnitionMonitors.isEmpty())
        assertTrue(unsupported.compressionIgnitionMonitors.isEmpty())
        val diesel = PidDecoders.decodeReadiness("4101000BC202")!!
        assertTrue(diesel.sparkIgnitionMonitors.isEmpty())
        assertTrue(diesel.compressionIgnitionMonitors.any { it.name == "NOx / SCR" && !it.complete })
        assertFalse(diesel.compressionIgnitionMonitors.any { it.name == "O2 Sensor" })
    }
}
