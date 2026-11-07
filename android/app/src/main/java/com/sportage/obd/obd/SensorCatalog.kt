package com.sportage.obd.obd

import java.util.Locale

data class SensorValue(val label: String, val value: Double, val unit: String, val description: String? = null) {
    fun formatted(): String = description ?: "${String.format(Locale.ENGLISH, "%.2f", value).trimEnd('0').trimEnd('.')} $unit".trim()
}
data class SensorSample(val pid: String, val values: List<SensorValue>, val timestamp: Long) {
    fun stale(now: Long) = now - timestamp > 90_000
}
data class SensorDefinition(
    val pid: String, val title: String, val category: String, val bytes: Int,
    val decode: (IntArray) -> List<SensorValue>
)

/**
 * Read-only SAE Mode 01 parameters. Actual support is discovered from the ECU.
 * Formulas: https://en.wikipedia.org/wiki/OBD-II_PIDs
 * Sensor indices deliberately do not guess an oxygen sensor's bank assignment.
 */
object SensorCatalog {
    private fun word(a: IntArray, offset: Int = 0) = a[offset] * 256 + a[offset + 1]
    val entries: List<SensorDefinition> = buildList {
        fun scalar(pid: String, title: String, category: String, bytes: Int, unit: String,
            formula: (IntArray) -> Double) {
            add(SensorDefinition(pid, title, category, bytes) { listOf(SensorValue(title, formula(it), unit)) })
        }
        fun pct(pid: String, title: String, category: String) = scalar(pid, title, category, 1, "%") { it[0] * 100.0 / 255 }
        fun temp(pid: String, title: String) = scalar(pid, title, "Temperatures", 1, "°C") { it[0] - 40.0 }
        pct("04", "Calculated engine load", "Engine")
        // PID 03 fuel system status: bit-encoded (open loop, closed loop, etc.).
        add(SensorDefinition("03", "Fuel system status", "Fuel", 1) {
            val bits = it[0]
            val status = when {
                bits and 0x02 != 0 -> "Closed loop (O2 feedback)"
                bits and 0x04 != 0 -> "Open loop (load)"
                bits and 0x08 != 0 -> "Open loop (failure)"
                bits and 0x10 != 0 -> "Closed loop (fault)"
                else -> "Open loop (warm-up)"
            }
            listOf(SensorValue("Status", 0.0, status))
        })
        temp("05", "Coolant temperature")
        for ((pid, title) in listOf("06" to "Short fuel trim bank 1", "07" to "Long fuel trim bank 1",
            "08" to "Short fuel trim bank 2", "09" to "Long fuel trim bank 2"))
            scalar(pid, title, "Fuel", 1, "%") { (it[0] - 128) * 100.0 / 128 }
        scalar("0A", "Fuel pressure", "Fuel", 1, "kPa") { it[0] * 3.0 }
        scalar("0B", "Intake manifold pressure", "Air & boost", 1, "kPa") { it[0].toDouble() }
        scalar("0C", "Engine speed", "Engine", 2, "rpm") { word(it) / 4.0 }
        scalar("0D", "Vehicle speed", "Engine", 1, "km/h") { it[0].toDouble() }
        scalar("0E", "Ignition timing advance", "Engine", 1, "°BTDC") { it[0] / 2.0 - 64 }
        temp("0F", "Intake air temperature")
        scalar("10", "Mass air flow", "Air & boost", 2, "g/s") { word(it) / 100.0 }
        pct("11", "Throttle position", "Throttle & pedals")
        for (pid in 0x14..0x1B) {
            val sensor = pid - 0x13
            add(SensorDefinition("%02X".format(pid), "Oxygen sensor $sensor", "Oxygen sensors", 2) {
                buildList {
                    add(SensorValue("Voltage", it[0] / 200.0, "V"))
                    if (it[1] != 255) add(SensorValue("Short fuel trim", (it[1] - 128) * 100.0 / 128, "%"))
                }
            })
        }
        scalar("1F", "Engine run time", "Counters", 2, "s") { word(it).toDouble() }
        scalar("21", "Distance with MIL on", "Counters", 2, "km") { word(it).toDouble() }
        scalar("22", "Fuel rail relative pressure", "Fuel", 2, "kPa") { word(it) * 0.079 }
        scalar("23", "Fuel rail gauge pressure", "Fuel", 2, "kPa") { word(it) * 10.0 }
        for (pid in 0x24..0x2B) add(SensorDefinition("%02X".format(pid),
            "Wideband oxygen ${pid - 0x23} · voltage", "Oxygen sensors", 4) {
            listOf(SensorValue("Lambda", word(it) / 32768.0, "λ"),
                SensorValue("Voltage", word(it, 2) / 8192.0, "V"))
        })
        pct("2C", "Commanded EGR", "Emissions")
        scalar("2D", "EGR error", "Emissions", 1, "%") { (it[0] - 128) * 100.0 / 128 }
        pct("2E", "Evaporative purge command", "Emissions")
        pct("2F", "Fuel tank level", "Fuel")
        scalar("30", "Warm-ups since codes cleared", "Counters", 1, "cycles") { it[0].toDouble() }
        scalar("31", "Distance since codes cleared", "Counters", 2, "km") { word(it).toDouble() }
        scalar("32", "EVAP vapor pressure", "Emissions", 2, "Pa") { word(it).toShort() / 4.0 }
        scalar("33", "Barometric pressure", "Air & boost", 1, "kPa") { it[0].toDouble() }
        for (pid in 0x34..0x3B) add(SensorDefinition("%02X".format(pid),
            "Wideband oxygen ${pid - 0x33} · current", "Oxygen sensors", 4) {
            listOf(SensorValue("Lambda", word(it) / 32768.0, "λ"),
                SensorValue("Current", word(it, 2) / 256.0 - 128, "mA"))
        })
        for ((pid, location) in listOf(0x3C to "B1 S1", 0x3D to "B2 S1", 0x3E to "B1 S2", 0x3F to "B2 S2"))
            scalar("%02X".format(pid), "Catalyst temperature $location", "Temperatures", 2, "°C") { word(it) / 10.0 - 40 }
        scalar("42", "ECU supply voltage", "Electrical", 2, "V") { word(it) / 1000.0 }
        scalar("43", "Absolute engine load", "Engine", 2, "%") { word(it) * 100.0 / 255 }
        scalar("44", "Commanded lambda", "Fuel", 2, "λ") { word(it) / 32768.0 }
        pct("45", "Relative throttle position", "Throttle & pedals")
        temp("46", "Ambient air temperature")
        for ((pid, title) in listOf("47" to "Throttle position B", "48" to "Throttle position C",
            "49" to "Accelerator pedal D", "4A" to "Accelerator pedal E", "4B" to "Accelerator pedal F",
            "4C" to "Commanded throttle actuator", "5A" to "Relative accelerator pedal"))
            pct(pid, title, "Throttle & pedals")
        scalar("4D", "Engine time with MIL on", "Counters", 2, "min") { word(it).toDouble() }
        scalar("4E", "Time since codes cleared", "Counters", 2, "min") { word(it).toDouble() }
        pct("52", "Ethanol fuel content", "Fuel")
        scalar("53", "Absolute EVAP vapor pressure", "Emissions", 2, "kPa") { word(it) / 200.0 }
        scalar("54", "EVAP vapor pressure · signed", "Emissions", 2, "Pa") { word(it).toShort().toDouble() }
        scalar("59", "Fuel rail absolute pressure", "Fuel", 2, "kPa") { word(it) * 10.0 }
        pct("5B", "Hybrid battery remaining life", "Electrical")
        temp("5C", "Engine oil temperature")
        scalar("5D", "Fuel injection timing", "Fuel", 2, "°") { word(it) / 128.0 - 210 }
        scalar("5E", "Engine fuel rate", "Fuel", 2, "L/h") { word(it) / 20.0 }
        scalar("61", "Driver demand torque", "Engine", 1, "%") { it[0] - 125.0 }
        scalar("62", "Actual engine torque", "Engine", 1, "%") { it[0] - 125.0 }
        scalar("63", "Reference engine torque", "Engine", 2, "N·m") { word(it).toDouble() }
        add(SensorDefinition("64", "Torque operating points", "Engine", 5) {
            it.mapIndexed { index, value -> SensorValue(if (index == 0) "Idle" else "Point $index", value - 125.0, "%") }
        })
        scalar("A6", "ECU odometer", "Counters", 4, "km") {
            ((it[0].toLong() shl 24) + (it[1].toLong() shl 16) + (it[2].toLong() shl 8) + it[3]) / 10.0
        }
    }.sortedBy { it.pid }
    val byPid = entries.associateBy { it.pid }
    val corePids = setOf("04", "05", "06", "07", "0C", "0D", "0E", "0F", "10", "11", "2F")

    fun decode(pid: String, response: String, timestamp: Long = System.currentTimeMillis()): SensorSample? {
        val definition = byPid[pid] ?: return null
        // Match the exact response PID and expected byte count, never an echoed request.
        val marker = Regex("""41[ \t]*${pid[0]}[ \t]*${pid[1]}((?:[ \t]*[0-9A-F]{2}){${definition.bytes}})""",
            RegexOption.IGNORE_CASE)
        val match = marker.find(response) ?: return null
        val data = match.groupValues[1].replace(Regex("\\s"), "").chunked(2).map { it.toInt(16) }.toIntArray()
        return SensorSample(pid, definition.decode(data), timestamp)
    }

    fun extraPids(supported: Set<String>): List<String> =
        entries.map { it.pid }.filter { it in supported && it !in corePids }
}
