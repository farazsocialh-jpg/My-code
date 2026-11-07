package com.sportage.obd.obd

/**
 * Decoded SAE J1979 Service 06 on-board monitor results, based on the documented
 * in-use performance tracking monitor-component sets rather than a single universal PID table.
 *
 * The app treats this as interpretation support only. Where the ECU exposes Service 06
 * data in the known SAE J1979 component shape, the decoder labels the component and the
 * completion-vs-condition counts; where a raw response does not match a known component
 * ordering or contains manufacturer-specific test IDs, it is preserved as raw text.
 */
data class Mode06MonitorResult(
    /** True when the decode used the known SAE J1979 in-use performance tracking shape. */
    val standardShape: Boolean,
    /** Spark-ignition monitor component results, in the documented SAE J1979 order. */
    val sparkIgnitionComponents: List<MonitorComponent>,
    /** Compression-ignition monitor component results, if any are reported. */
    val compressionIgnitionComponents: List<MonitorComponent>,
    /** Raw ECU text, kept when the response is not fully consumed by the known decoder. */
    val rawResponse: String
) {
    /** Best-effort summary row for UI display. Not a diagnosis. */
    val hasMonitoredSparkIgnition: Boolean get() = sparkIgnitionComponents.isNotEmpty()
    val hasMonitoredCompressionIgnition: Boolean get() = compressionIgnitionComponents.isNotEmpty()
}

data class MonitorComponent(
    /** SAE J1979 component mnemonic, e.g. CATCOMP1. */
    val componentId: String,
    /** Human label, e.g. Catalyst Monitor - Bank 1. */
    val name: String,
    /** Number of completed monitor cycles. */
    val completionCount: Int,
    /** Number of condition encounters. */
    val conditionsEncountered: Int,
    /** Completion fraction in 0..1 when conditions > 0, otherwise null. */
    val completionFraction: Float? = if (conditionsEncountered > 0) completionCount.toFloat() / conditionsEncountered.toFloat() else null,
    /** Raw one-line display of the two count bytes, if useful to the user. */
    val rawCounts: String = "$completionCount / $conditionsEncountered"
)

/**
 * Minimal SAE J1979 Service 06 decoder.
 *
 * The decoder is intentionally conservative:
 *  - It only interprets the documented monitor-component pairs (two-byte completion count
 *    followed by the same two-byte condition-encountered count).
 *  - It keeps the full raw text so an unknown response is never silently renamed.
 *  - It does not assign pass/fail limits or vehicle-specific thresholds.
 */
object Mode06Decoders {

    /**
     * Order used in the documented SAE J1979 in-use performance tracking for spark-ignition
     * engines. Each component is followed by its condition-encountered count.
     */
    private val sparkIgnitionOrder = listOf(
        MonitorDefinition("OBDCOND", "OBD Monitoring Conditions Encountered"),
        MonitorDefinition("IGNCNTR", "Ignition Counter"),
        MonitorDefinition("CATCOMP1", "Catalyst Monitor - Bank 1"),
        MonitorDefinition("CATCOND1", "Catalyst Conditions Encountered - Bank 1"),
        MonitorDefinition("CATCOMP2", "Catalyst Monitor - Bank 2"),
        MonitorDefinition("CATCOND2", "Catalyst Conditions Encountered - Bank 2"),
        MonitorDefinition("O2SCOMP1", "O2 Sensor Monitor - Bank 1"),
        MonitorDefinition("O2SCOND1", "O2 Sensor Conditions Encountered - Bank 1"),
        MonitorDefinition("O2SCOMP2", "O2 Sensor Monitor - Bank 2"),
        MonitorDefinition("O2SCOND2", "O2 Sensor Conditions Encountered - Bank 2"),
        MonitorDefinition("EGRCOMP", "EGR Monitor"),
        MonitorDefinition("EGRCOND", "EGR Conditions Encountered"),
        MonitorDefinition("AIRCOMP", "Secondary Air Monitor"),
        MonitorDefinition("AIRCOND", "Secondary Air Conditions Encountered"),
        MonitorDefinition("EVAPCOMP", "EVAP Monitor"),
        MonitorDefinition("EVAPCOND", "EVAP Conditions Encountered"),
        MonitorDefinition("SO2SCOMP1", "Secondary O2 Sensor Monitor - Bank 1"),
        MonitorDefinition("SO2SCOND1", "Secondary O2 Sensor Conditions Encountered - Bank 1"),
        MonitorDefinition("SO2SCOMP2", "Secondary O2 Sensor Monitor - Bank 2"),
        MonitorDefinition("SO2SCOND2", "Secondary O2 Sensor Conditions Encountered - Bank 2")
    )

    /**
     * Order used in the documented SAE J1979 in-use performance tracking for compression-ignition
     * engines.
     */
    private val compressionIgnitionOrder = listOf(
        MonitorDefinition("OBDCOND", "OBD Monitoring Conditions Encountered"),
        MonitorDefinition("IGNCNTR", "Ignition Counter"),
        MonitorDefinition("HCCATCOMP", "NMHC Catalyst Monitor"),
        MonitorDefinition("HCCATCOND", "NMHC Catalyst Conditions Encountered"),
        MonitorDefinition("NCATCOMP", "NOx / SCR Catalyst Monitor"),
        MonitorDefinition("NCATCOND", "NOx / SCR Catalyst Conditions Encountered"),
        MonitorDefinition("NADSCOMP", "NOx Adsorber Monitor"),
        MonitorDefinition("NADSCOND", "NOx Adsorber Conditions Encountered"),
        MonitorDefinition("PMCOMP", "PM Filter Monitor"),
        MonitorDefinition("PMCOND", "PM Filter Conditions Encountered"),
        MonitorDefinition("EGSCOMP", "Exhaust Gas Sensor Monitor"),
        MonitorDefinition("EGSCOND", "Exhaust Gas Sensor Conditions Encountered"),
        MonitorDefinition("EGRCOMP", "EGR / VVT Monitor"),
        MonitorDefinition("EGRCOND", "EGR / VVT Conditions Encountered"),
        MonitorDefinition("BPCOMP", "Boost Pressure Monitor"),
        MonitorDefinition("BPCOND", "Boost Pressure Conditions Encountered"),
        MonitorDefinition("FUELCOMP", "Fuel Monitor"),
        MonitorDefinition("FUELCOND", "Fuel Monitor Conditions Encountered")
    )

    private data class MonitorDefinition(val componentId: String, val name: String)

    /**
     * Decodes a raw Mode 06 response into a [Mode06MonitorResult].
     *
     * The response is expected to be a hex payload or ELM327 multi-line text from the
     * `06` command. The decoder first tries the documented SAE J1979 in-use performance
     * tracking shape, then preserves whatever it could not consume.
     */
    fun decodeMode06(raw: String): Mode06MonitorResult {
        val text = raw.ifBlank { "No response" }
        val cleaned = cleanedHex(text)
        val shape = decodeShape(cleaned)

        val sparkComponents = if (shape.isNotEmpty()) {
            val ordered = shape.firstOrNull { it.second == sparkIgnitionOrder.size }?.first
            if (ordered != null) {
                sparkIgnitionOrder.mapIndexedNotNull { index, definition ->
                    ordered.getOrNull(index)?.let { (comp, cond) ->
                        MonitorComponent(
                            componentId = definition.componentId,
                            name = definition.name,
                            completionCount = comp,
                            conditionsEncountered = cond
                        )
                    }
                }
            } else emptyList()
        } else emptyList()

        val compIgnitionComponents = if (shape.isNotEmpty()) {
            val ordered = shape.firstOrNull { it.second == compressionIgnitionOrder.size }?.first
            if (ordered != null) {
                compressionIgnitionOrder.mapIndexedNotNull { index, definition ->
                    ordered.getOrNull(index)?.let { (comp, cond) ->
                        MonitorComponent(
                            componentId = definition.componentId,
                            name = definition.name,
                            completionCount = comp,
                            conditionsEncountered = cond
                        )
                    }
                }
            } else emptyList()
        } else emptyList()

        val rawDisplay = if (cleaned.isNotEmpty() && cleaned != text) text else cleaned
        return Mode06MonitorResult(
            standardShape = sparkComponents.isNotEmpty() || compIgnitionComponents.isNotEmpty(),
            sparkIgnitionComponents = sparkComponents,
            compressionIgnitionComponents = compIgnitionComponents,
            rawResponse = rawDisplay.ifBlank { "No response" }
        )
    }

    /**
     * When the cleaned hex is long enough and looks like SAE J1979 in-use performance
     * tracking, split it into the documented component pairs.
     *
     * Each documented component occupies two bytes of completion count and is immediately
     * followed by two bytes of conditions encountered. The first two-byte pair is the number
     * of monitored items reported, so the decoder uses that to anchor the known orderings.
     */
    private fun decodeShape(hex: String): List<Pair<List<Pair<Int, Int>>, Int>> {
        if (hex.length < 4) return emptyList()
        val pairs = hex.chunked(4).mapNotNull { word ->
            if (word.length == 4) {
                val high = word.substring(0, 2).toIntOrNull(16) ?: return@mapNotNull null
                val low = word.substring(2, 4).toIntOrNull(16) ?: return@mapNotNull null
                high to low
            } else null
        }
        if (pairs.isEmpty()) return emptyList()

        val reportedCount = pairs.first().first
        if (reportedCount <= 0) return emptyList()

        val results = mutableListOf<Pair<List<Pair<Int, Int>>, Int>>()
        for (order in listOf(sparkIgnitionOrder, compressionIgnitionOrder)) {
            if (pairs.size < 1 + order.size * 2) continue
            val componentPairs = pairs.drop(1).take(order.size * 2)
                .windowed(2, 2)
                .map { (comp, cond) -> comp.first to cond.first }
                .toList()
            if (componentPairs.size == order.size) {
                results.add(componentPairs to order.size)
            }
        }
        return results
    }

    /**
     * Strips non-hex characters but keeps the original response text available for display.
     */
    private fun cleanedHex(raw: String): String =
        raw.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
}

// --- Lightweight self-check for the documented SAE J1979 shape. ---
// This runs when the file is executed as a script and is NOT compiled into the app.
fun main() {
    val siRaw = """46 0013 000100020003000400050006000700080009000A000B000C000D000E000F0010001100120013"""
    val si = Mode06Decoders.decodeMode06(siRaw)
    check(si.standardShape) { "SI shape expected to decode" }
    check(si.sparkIgnitionComponents.size == 20) { "SI component count" }
    check(si.sparkIgnitionComponents[0].componentId == "OBDCOND") { "SI first component" }
    check(si.sparkIgnitionComponents[0].rawCounts == "0 / 1") { "SI first counts" }
    check(si.compressionIgnitionComponents.isEmpty()) { "no CI in SI payload" }

    val ciRaw = """46 0012 000100020003000400050006000700080009000A000B000C000D000E000F00100011"""
    val ci = Mode06Decoders.decodeMode06(ciRaw)
    check(ci.standardShape) { "CI shape expected to decode" }
    check(ci.compressionIgnitionComponents.size == 18) { "CI component count" }
    check(ci.compressionIgnitionComponents[0].componentId == "OBDCOND") { "CI first component" }
    check(ci.sparkIgnitionComponents.isEmpty()) { "no SI in CI payload" }

    val rawOnly = "NO DATA"
    val raw = Mode06Decoders.decodeMode06(rawOnly)
    check(!raw.standardShape) { "raw text should not fake a shape" }
    check(raw.rawResponse == "NO DATA") { "raw text preserved" }

    println("Mode06Decoders assertions passed")
}
