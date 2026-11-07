package com.sportage.obd.service

import android.content.Context
import android.util.Log
import com.sportage.obd.service.ObdTelemetry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rolls CSV telemetry logs to app-accessible external storage so the user
 * can share them with ACTION_SEND (email, Drive, Telegram...).
 */
class CsvLogger(private val context: Context) {

    private var currentFile: File? = null
    private var lastFile: File? = null
    private var bytesWritten = 0L

    val activeFile: File? get() = currentFile ?: lastFile

    val isLogging: Boolean get() = currentFile != null

    @Synchronized fun start(): File {
        stop()
        val dir = File(context.getExternalFilesDir(null), "obd_logs").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File.createTempFile("session_${ts}_", ".csv", dir)
        file.appendText(CSV_HEADER)
        bytesWritten = CSV_HEADER.length.toLong()
        currentFile = file
        lastFile = file
        return file
    }

    @Synchronized fun stop() {
        currentFile = null
        bytesWritten = 0
    }

    /** Appends one telemetry row. Returns false when not logging. */
    @Synchronized fun log(telemetry: ObdTelemetry): Boolean {
        val file = currentFile ?: return false
        return try {
            val row = buildString {
                append(telemetry.timestamp).append(',')
                    .append(telemetry.rpm).append(',')
                    .append(telemetry.speedKmh).append(',')
                    .append(telemetry.coolantTempC).append(',')
                    .append(telemetry.intakeTempC).append(',')
                    .append(telemetry.throttlePct).append(',')
                    .append(telemetry.engineLoadPct).append(',')
                    .append(telemetry.fuelLevelPct).append(',')
                    .append(telemetry.batteryVoltage).append(',')
                    .append(telemetry.mafGs).append(',')
                    .append(telemetry.shortTermFuelTrimPct).append(',')
                    .append(telemetry.longTermFuelTrimPct).append(',')
                    .append(telemetry.timingAdvanceDeg).append(',')
                    .append(telemetry.fuelEconomyL100).append(',')
                val extra = org.json.JSONObject()
                telemetry.sensors.forEach { (pid, sample) ->
                    val values = org.json.JSONArray()
                    sample.values.forEach { value ->
                        values.put(org.json.JSONObject().put("label", value.label)
                            .put("value", value.value).put("unit", value.unit))
                    }
                    extra.put(pid, org.json.JSONObject().put("timestamp", sample.timestamp).put("values", values))
                }
                append('"').append(extra.toString().replace("\"", "\"\"")).append('"').append('\n')
            }
            file.appendText(row)
            bytesWritten += row.toByteArray(Charsets.UTF_8).size
            if (bytesWritten > MAX_FILE_BYTES) {
                roll()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "CSV log write failed", e)
            false
        }
    }

    private fun roll() {
        currentFile?.let { old ->
            Log.i(TAG, "Rolling CSV log at ${old.length()} bytes")
        }
        start()
    }

    companion object {
        private const val TAG = "CsvLogger"
        private const val MAX_FILE_BYTES = 5L * 1024 * 1024 // 5 MB per session file

        private const val CSV_HEADER =
            "timestamp,rpm,speed_kmh,coolant_c,intake_c,throttle_pct,load_pct,fuel_pct,battery_v,maf_gs,stft_pct,ltft_pct,timing_deg,l_per_100km,extended_sensor_samples_json\n"
    }
}
