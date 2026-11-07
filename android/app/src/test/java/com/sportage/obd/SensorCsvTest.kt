package com.sportage.obd

import androidx.test.core.app.ApplicationProvider
import com.sportage.obd.obd.SensorCatalog
import com.sportage.obd.service.CsvLogger
import com.sportage.obd.service.ObdTelemetry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SensorCsvTest {
    @Test fun extendedCsvHasSampleTimestampsEscapedJsonAndRemainsShareableAfterStop() {
        val logger = CsvLogger(ApplicationProvider.getApplicationContext())
        val file = logger.start()
        val sample = SensorCatalog.decode("42", "414235E8", 1230)!!
        assertTrue(logger.log(ObdTelemetry(timestamp = 1234, sensors = mapOf("42" to sample))))
        val text = file.readText()
        assertTrue(text.lineSequence().first().endsWith("extended_sensor_samples_json"))
        val row = text.lines()[1]
        assertTrue(row.startsWith("1234,"))
        val json = row.substring(row.indexOf(",\"{") + 1).removeSurrounding("\"").replace("\"\"", "\"")
        val data = JSONObject(json).getJSONObject("42")
        assertEquals(1230L, data.getLong("timestamp"))
        assertEquals(13.8, data.getJSONArray("values").getJSONObject(0).getDouble("value"), 0.001)
        logger.stop()
        assertFalse(logger.isLogging)
        assertEquals(file, logger.activeFile)
        assertFalse(logger.log(ObdTelemetry()))
        val second = logger.start()
        assertNotEquals(file, second)
        logger.stop()
    }
}
