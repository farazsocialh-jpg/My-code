package com.sportage.obd.fuel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * How a fuel figure was produced. Every reading shown in the UI must carry one of
 * these labels so estimates are never presented as direct ECU measurements.
 */
enum class FuelSource(val label: String) {
    ECU_DIRECT("ECU direct (PID 5E)"),
    CALCULATED("Calculated (MAF estimate)"),
    CALIBRATED("Calibrated estimate"),
    MANUAL("Manual entry")
}

/** One tank-to-tank fill, used to calibrate the OBD-derived fuel usage. */
data class FuelFillEntry(
    val dateMs: Long,
    val litersFilled: Double,
    val kmSinceLastFill: Double,
    val pricePerLiterQar: Double?,
    /** OBD-accumulated liters at the moment of the fill; captured automatically. */
    val obdLitersSinceLastFill: Double,
    /** Trip-odometer km at the moment of the fill; captured automatically. */
    val tripKmAtFill: Double
) {
    /** Actual consumption for the covered distance, L/100 km. */
    fun actualL100(): Double? = if (kmSinceLastFill > 1) litersFilled / kmSinceLastFill * 100.0 else null

    /** OBD estimate for the same period, L/100 km (null before the first fill). */
    fun obdL100(): Double? = if (kmSinceLastFill > 1 && obdLitersSinceLastFill > 0.05)
        obdLitersSinceLastFill / kmSinceLastFill * 100.0 else null

    /** Per-fill correction factor: how much to scale OBD liters to match reality. */
    fun fillFactor(): Double? {
        val obd = obdLitersSinceLastFill
        return if (obd >= 1.0 && litersFilled > 0.5) (litersFilled / obd).coerceIn(0.5, 2.5) else null
    }
}

/** A completed trip, persisted when the user resets the trip computer. */
data class TripRecord(
    val dateMs: Long,
    val distanceKm: Double,
    val durationSeconds: Long,
    val fuelLiters: Double,
    val avgL100: Double?,
    val maxSpeedKmh: Int,
    val avgSpeedKmh: Int
)

object FuelMath {
    /** Stoichiometric AFR for gasoline and density in g/ml, used by the MAF fallback. */
    const val STOICH_AFR = 14.7
    const val GASOLINE_DENSITY_G_PER_ML = 0.745

    /** MAF-based fuel rate, L/h: g/s * 3600 s/h / (AFR * g/ml) / 1000 ml/L. */
    fun litersPerHourFromMaf(mafGs: Double): Double = mafGs * 3.6 / (STOICH_AFR * GASOLINE_DENSITY_G_PER_ML)

    fun l100From(rateLh: Double, speedKmh: Double): Double? =
        if (speedKmh > 3 && rateLh > 0) rateLh / speedKmh * 100.0 else null

    fun kmPerL(rateLh: Double, speedKmh: Double): Double? =
        if (rateLh > 0 && speedKmh > 3) speedKmh / rateLh else null

    /** Weighted overall calibration factor from all usable fills. */
    fun calibrationFactor(fills: List<FuelFillEntry>): Double? {
        val usable = fills.mapNotNull { f -> f.fillFactor()?.let { f.litersFilled to it } }
        if (usable.isEmpty()) return null
        val totalLiters = usable.sumOf { it.first }
        if (totalLiters <= 0) return null
        // Weight each fill by the liters filled: big fills are more reliable.
        return usable.sumOf { (liters, factor) -> factor * liters } / totalLiters
    }

    /** City below 80 km/h, highway at or above. Speed 0..3 counts as idle, not city. */
    fun classify(speedKmh: Double): DriveClass = when {
        speedKmh < 3 -> DriveClass.IDLE
        speedKmh < 80 -> DriveClass.CITY
        else -> DriveClass.HIGHWAY
    }
}

enum class DriveClass { IDLE, CITY, HIGHWAY }

/** Simple JSON-file stores. No new dependencies; org.json ships with Android. */
class FuelHistoryStore(context: Context) {
    private val file: File = File(context.filesDir, "fuel_history.json")

    fun fills(): List<FuelFillEntry> {
        val root = read()
        val arr = root.optJSONArray("fills") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            FuelFillEntry(
                dateMs = o.optLong("dateMs"),
                litersFilled = o.optDouble("liters"),
                kmSinceLastFill = o.optDouble("km"),
                pricePerLiterQar = if (o.has("price")) o.optDouble("price") else null,
                obdLitersSinceLastFill = o.optDouble("obdLiters"),
                tripKmAtFill = o.optDouble("tripKm")
            )
        }
    }

    fun addFill(entry: FuelFillEntry) {
        val root = read()
        val arr = root.optJSONArray("fills") ?: JSONArray()
        arr.put(JSONObject().apply {
            put("dateMs", entry.dateMs)
            put("liters", entry.litersFilled)
            put("km", entry.kmSinceLastFill)
            if (entry.pricePerLiterQar != null) put("price", entry.pricePerLiterQar)
            put("obdLiters", entry.obdLitersSinceLastFill)
            put("tripKm", entry.tripKmAtFill)
        })
        root.put("fills", arr)
        write(root)
    }

    fun clear() = write(JSONObject())

    private fun read(): JSONObject = runCatching {
        if (file.exists()) JSONObject(file.readText()) else JSONObject()
    }.getOrDefault(JSONObject())

    private fun write(root: JSONObject) {
        runCatching { file.writeText(root.toString()) }
    }
}

class TripHistoryStore(context: Context) {
    private val file: File = File(context.filesDir, "trip_history.json")
    private val maxEntries = 200

    fun trips(): List<TripRecord> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            TripRecord(
                dateMs = o.optLong("dateMs"),
                distanceKm = o.optDouble("distanceKm"),
                durationSeconds = o.optLong("durationSeconds"),
                fuelLiters = o.optDouble("fuelLiters"),
                avgL100 = if (o.has("avgL100")) o.optDouble("avgL100") else null,
                maxSpeedKmh = o.optInt("maxSpeedKmh"),
                avgSpeedKmh = o.optInt("avgSpeedKmh")
            )
        }
    }.getOrDefault(emptyList())

    fun addTrip(record: TripRecord) {
        runCatching {
            val arr = if (file.exists()) JSONArray(file.readText()) else JSONArray()
            arr.put(JSONObject().apply {
                put("dateMs", record.dateMs)
                put("distanceKm", record.distanceKm)
                put("durationSeconds", record.durationSeconds)
                put("fuelLiters", record.fuelLiters)
                if (record.avgL100 != null) put("avgL100", record.avgL100)
                put("maxSpeedKmh", record.maxSpeedKmh)
                put("avgSpeedKmh", record.avgSpeedKmh)
            })
            while (arr.length() > maxEntries) arr.remove(0)
            file.writeText(arr.toString())
        }
    }
}

/** Fuel-related user settings, persisted in plain SharedPreferences by the caller. */
data class FuelSettings(
    val fuelPriceQarPerLiter: Double = 2.05,
    val tankCapacityLiters: Double = 62.0
)
