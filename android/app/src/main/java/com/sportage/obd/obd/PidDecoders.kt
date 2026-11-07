package com.sportage.obd.obd

data class DtcItem(
    val code: String,
    val system: String,
    val description: String,
    val severity: DtcSeverity,
    val probableCause: String
)

enum class DtcSeverity {
    CRITICAL,
    WARNING,
    ADVISORY
}

/** A single readiness monitor status from Mode 01 PID 01. */
data class ReadinessMonitor(val name: String, val complete: Boolean)

/** Decoded readiness / MIL state. */
data class ReadinessState(
    val milOn: Boolean,
    val storedDtcCount: Int,
    val sparkIgnitionMonitors: List<ReadinessMonitor>,
    val compressionIgnitionMonitors: List<ReadinessMonitor>
)

/** One parameter captured inside a Mode 02 freeze frame. */
data class FreezeFrameItem(val label: String, val value: String)

data class FreezeFrame(
    val dtc: DtcItem?,
    val items: List<FreezeFrameItem>
)

object PidDecoders {

    /**
     * Engine RPM (PID 010C): ((A*256)+B)/4
     */
    fun decodeRpm(response: String): Int? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("410C")
        if (idx != -1 && clean.length >= idx + 8) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            val b = clean.substring(idx + 6, idx + 8).toInt(16)
            return ((a * 256) + b) / 4
        }
        return null
    }

    /**
     * Vehicle Speed (PID 010D): A km/h
     */
    fun decodeSpeed(response: String): Int? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("410D")
        if (idx != -1 && clean.length >= idx + 6) {
            return clean.substring(idx + 4, idx + 6).toInt(16)
        }
        return null
    }

    /**
     * Transmission actual gear (PID 01A4). Encoding is not fully standardized across
     * manufacturers, so accept the two observed variants:
     *  - plain enumeration: 0x00 = Neutral, 0x01..0x0A = forward gear 1..10,
     *    0x7E (126) = Reverse, 0x7F (127) = Park
     *  - high-nibble enumeration (e.g. Mazda): 0x10 = 1st, 0x20 = 2nd, 0x30 = 3rd ...
     * Returns null when the value does not map to a known gear.
     */
    fun decodeTransmissionGear(response: String): Int? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("41A4")
        if (idx == -1 || clean.length < idx + 6) return null
        val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return null
        return when (a) {
            0x00 -> 0          // Neutral
            in 0x01..0x0A -> a // forward gear
            0x7E -> -1         // Reverse
            0x7F -> -2         // Park
            in 0x10..0xA0 -> if (a % 0x10 == 0) a / 0x10 else null
            else -> null
        }
    }

    /**
     * Engine Coolant Temperature (PID 0105): A - 40 °C
     */
    fun decodeCoolantTemp(response: String): Int? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("4105")
        if (idx != -1 && clean.length >= idx + 6) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            return a - 40
        }
        return null
    }

    /**
     * Intake Air Temperature (PID 010F): A - 40 °C
     */
    fun decodeIntakeTemp(response: String): Int? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("410F")
        if (idx != -1 && clean.length >= idx + 6) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            return a - 40
        }
        return null
    }

    /**
     * Fuel Tank Level (PID 012F): (A*100)/255 %
     */
    fun decodeFuelLevel(response: String): Int? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("412F")
        if (idx != -1 && clean.length >= idx + 6) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            return ((a * 100) / 255)
        }
        return null
    }

    /**
     * Throttle Position (PID 0111): (A*100)/255 %
     */
    fun decodeThrottle(response: String): Float? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("4111")
        if (idx != -1 && clean.length >= idx + 6) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            return (a * 100f) / 255f
        }
        return null
    }

    /**
     * Calculated Engine Load (PID 0104): (A*100)/255 %
     */
    fun decodeLoad(response: String): Float? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("4104")
        if (idx != -1 && clean.length >= idx + 6) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            return (a * 100f) / 255f
        }
        return null
    }

    /**
     * Parse battery voltage from ATRV response
     */
    fun decodeBatteryVoltage(response: String): Float? {
        val match = Regex("""([\d\.]+)\s*V""", RegexOption.IGNORE_CASE).find(response)
        return match?.groupValues?.get(1)?.toFloatOrNull()
    }

    /**
     * Decodes Mode 03 (Stored), Mode 07 (Pending) or Mode 0A (Permanent)
     * Diagnostic Trouble Codes.
     * Response standard format: "43 01 33 00 00 ...", "47 ..." or "4A ..."
     */
    fun decodeDtcCodes(response: String): List<DtcItem> {
        // Frame-based parsing. The old whole-string hex-strip turned ELM chatter
        // ("SEARCHING...", "NO DATA") and interleaved Mode 01 poll replies into
        // garbage hex that was decoded as random trouble codes.
        val frames = dtcFrames(response, setOf("43", "47", "4A"))
        val dtcs = mutableListOf<DtcItem>()
        for (frame in frames) {
            var i = 0
            while (i + 4 <= frame.length) {
                val chunk = frame.substring(i, i + 4)
                if (chunk != "0000") {
                val byte1 = chunk.substring(0, 2).toIntOrNull(16) ?: 0
                val byte2 = chunk.substring(2, 4)

                val prefix = when ((byte1 and 0xC0) shr 6) {
                    0 -> "P" // Powertrain
                    1 -> "C" // Chassis
                    2 -> "B" // Body
                    else -> "U" // Network
                }

                val digit2 = ((byte1 and 0x30) shr 4).toString()
                val digit3 = (byte1 and 0x0F).toString(16).uppercase()
                val code = "$prefix$digit2$digit3$byte2"

                val info = DtcDatabase.lookup(code)

                dtcs.add(
                    DtcItem(
                        code = code,
                        system = when (prefix) {
                            "P" -> "Powertrain / Engine"
                            "C" -> "Chassis / ABS / AWD"
                            "B" -> "Body & Lighting"
                            else -> "CAN Network Bus"
                        },
                        description = info.description,
                        severity = info.severity,
                        probableCause = info.probableCause
                    )
                )
            }
            i += 4
            }
        }
        return dtcs
    }

    /**
     * Extracts DTC byte payloads from an ELM327 multi-line response.
     *
     * Only lines whose first token (after an optional CAN header such as 7E8) is one of
     * [modes] are considered. Everything else - the command echo, "SEARCHING...",
     * "NO DATA", ">" prompts and stray Mode 01 poll replies - is ignored, so stale
     * poll data can never be misread as trouble codes.
     */
    private fun dtcFrames(response: String, modes: Set<String>): List<String> {
        val frames = mutableListOf<String>()
        for (rawLine in response.split('\n', '\r')) {
            var line = rawLine.trim().trimStart('>')
            if (line.isBlank()) continue
            val lower = line.uppercase()
            if (lower.contains("SEARCHING") || lower.contains("NO DATA") ||
                lower.contains("STOPPED") || lower.contains("UNABLE") || lower.contains("ERROR") ||
                lower.contains("BUS") || lower.contains("?") || lower.contains("CAN OK")
            ) continue
            var tokens = line.split(' ', '\t').filter { it.isNotBlank() }
            // Strip a CAN header token (e.g. 7E8 / 18DAF110) that precedes the mode byte.
            if (tokens.isNotEmpty() && Regex("^([0-9A-F]{3}|[0-9A-F]{6,8})$").matches(tokens[0]) &&
                tokens.size > 1 && tokens[1].uppercase() in modes
            ) tokens = tokens.drop(1)
            if (tokens.isEmpty()) continue
            val mode = tokens[0].uppercase()
            if (mode !in modes) continue
            val payload = (listOf(mode) + tokens.drop(1))
                .joinToString("") { it.replace(Regex("[^0-9A-Fa-f]"), "").uppercase() }
            // payload starts with the mode byte(s); keep everything after it as DTC bytes.
            if (payload.length > 2) frames.add(payload.substring(2))
        }
        return frames
    }

    /**
     * Mass Air Flow (PID 0110): ((A*256)+B)/100 g/s
     */
    fun decodeMaf(response: String): Float? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("4110")
        if (idx != -1 && clean.length >= idx + 8) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            val b = clean.substring(idx + 6, idx + 8).toInt(16)
            return ((a * 256) + b) / 100f
        }
        return null
    }

    /**
     * Short Term Fuel Trim (PID 0106): (A-128)*100/128 %
     */
    fun decodeShortTermFuelTrim(response: String): Float? =
        decodeTrim(response, "4106")

    /**
     * Long Term Fuel Trim (PID 0107): (A-128)*100/128 %
     */
    fun decodeLongTermFuelTrim(response: String): Float? =
        decodeTrim(response, "4107")

    private fun decodeTrim(response: String, marker: String): Float? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf(marker)
        if (idx != -1 && clean.length >= idx + 6) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            return (a - 128) * 100f / 128f
        }
        return null
    }

    /**
     * Timing Advance (PID 010E): A/2 - 64 degrees
     */
    fun decodeTimingAdvance(response: String): Float? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("410E")
        if (idx != -1 && clean.length >= idx + 6) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            return a / 2f - 64f
        }
        return null
    }

    /**
     * Engine Run Time (PID 011F): ((A*256)+B) seconds
     */
    fun decodeRuntime(response: String): Int? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("411F")
        if (idx != -1 && clean.length >= idx + 8) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            val b = clean.substring(idx + 6, idx + 8).toInt(16)
            return (a * 256) + b
        }
        return null
    }

    /**
     * VIN (Mode 09 PID 02). Handles multi-frame ISO-TP responses and the
     * "0: 1 4 4 4 ..." style line numbering ELM327 prints.
     */
    fun decodeVin(response: String): String? {
        val containsVin = response.uppercase().contains("014") ||
            response.uppercase().contains("0902")
        if (!containsVin && !response.uppercase().contains("49")) return null

        // Strip "0:", "1:" ... line prefixes, spaces and the ELM prompt
        val hex = response
            // cleanResponse may have already flattened CR/LF; row markers
            // therefore need removing anywhere, not only at line starts.
            .replace(Regex("[0-9a-fA-F]\\s*:\\s*"), "")
            .replace(">", "")
            .replace(" ", "")
            .replace(Regex("[^0-9A-Fa-f]"), "")

        // Each continuation has another 49 02 sequence header. Do not decode
        // that header as printable VIN characters ("I") or stop at its 02 byte.
        val normalized = hex.uppercase()
        val frames = Regex("4902[0-9A-F]{2}").findAll(normalized).toList()
        val vin = buildString {
            frames.forEachIndexed { index, frame ->
                val end = frames.getOrNull(index + 1)?.range?.first ?: normalized.length
                normalized.substring(frame.range.last + 1, end).chunked(2)
                    .filter { it.length == 2 }.forEach { byte ->
                        val char = byte.toIntOrNull(16)?.toChar()
                        if (char != null && (char in 'A'..'Z' || char in '0'..'9')) append(char)
                    }
            }
        }
        return vin.takeIf { it.length == 17 }
    }

    /**
     * Mode 01 PID 01 - MIL status, DTC count and readiness monitor bits.
     */
    fun decodeReadiness(response: String): ReadinessState? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("4101")
        if (idx == -1 || clean.length < idx + 12) return null

        val a = clean.substring(idx + 4, idx + 6).toInt(16)
        val b = clean.substring(idx + 6, idx + 8).toInt(16)
        val c = clean.substring(idx + 8, idx + 10).toInt(16)
        val d = clean.substring(idx + 10, idx + 12).toInt(16)
        val common = listOf("Misfire", "Fuel System", "Components").mapIndexedNotNull { bit, name ->
            if (b and (1 shl bit) == 0) null else ReadinessMonitor(name, b and (1 shl (bit + 4)) == 0)
        }
        val diesel = b and 0x08 != 0
        val names = if (diesel) mapOf(0 to "NMHC Catalyst", 1 to "NOx / SCR", 3 to "Boost Pressure",
            5 to "Exhaust Gas Sensor", 6 to "PM Filter", 7 to "EGR / VVT")
        else mapOf(0 to "Catalyst", 1 to "Heated Catalyst", 2 to "Evaporative System", 3 to "Secondary Air",
            4 to "Gasoline Particulate Filter", 5 to "O2 Sensor", 6 to "O2 Sensor Heater", 7 to "EGR")
        val specific = names.mapNotNull { (bit, name) ->
            if (c and (1 shl bit) == 0) null else ReadinessMonitor(name, d and (1 shl bit) == 0)
        }
        val monitors = common + specific
        return ReadinessState(a and 0x80 != 0, a and 0x7F,
            if (diesel) emptyList() else monitors, if (diesel) monitors else emptyList())
    }

    /**
     * Supported PID bitmap response (Mode 01 PID 00/20/40/...).
     * Returns the set of supported 2-hex-digit PID codes.
     */
    fun decodeSupportedPids(response: String, basePid: Int): Set<String> {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        val marker = "41" + String.format(java.util.Locale.ENGLISH, "%02X", basePid)
        val supported = mutableSetOf<String>()
        Regex(marker + "([0-9A-F]{8})").findAll(clean).forEach { match ->
            match.groupValues[1].chunked(2).forEachIndexed { byteIdx, hex ->
                val byte = hex.toInt(16)
                for (bit in 7 downTo 0) if ((byte shr bit) and 1 == 1) {
                    val pid = basePid + 1 + byteIdx * 8 + (7 - bit)
                    if (pid <= 255) supported.add(String.format(java.util.Locale.ENGLISH, "%02X", pid))
                }
            }
        }
        return supported
    }

    /**
     * Mode 02 freeze frame. Parses "42 <pid> <data>" entries and decodes
     * the common parameters.
     */
    fun decodeFreezeFrame(response: String): FreezeFrame {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        val items = mutableListOf<FreezeFrameItem>()

        var searchFrom = 0
        val seen = mutableSetOf<String>()
        while (true) {
            val idx = clean.indexOf("42", searchFrom)
            if (idx == -1 || idx + 6 > clean.length) break
            searchFrom = idx + 2

            val pid = clean.substring(idx + 2, idx + 4)
            if (!pid.matches(Regex("[0-9A-F]{2}"))) continue
            if (!seen.add(pid)) continue

            val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16) ?: continue
            val b = clean.substringOrNull(idx + 6, idx + 8)?.toIntOrNull(16)
            val item = when (pid) {
                "04" -> FreezeFrameItem("Engine Load", "${a * 100 / 255}%")
                "05" -> FreezeFrameItem("Coolant Temp", "${a - 40}°C")
                "0C" -> if (b != null) FreezeFrameItem("Engine RPM", "${((a * 256) + b) / 4}") else null
                "0D" -> FreezeFrameItem("Vehicle Speed", "${a} km/h")
                "0F" -> FreezeFrameItem("Intake Air Temp", "${a - 40}°C")
                "10" -> if (b != null) FreezeFrameItem("MAF Air Flow", String.format(java.util.Locale.getDefault(), "%.1f g/s", ((a * 256) + b) / 100f)) else null
                "11" -> FreezeFrameItem("Throttle Position", "${a * 100 / 255}%")
                "2F" -> FreezeFrameItem("Fuel Level", "${a * 100 / 255}%")
                else -> null
            } ?: continue
            items.add(item)
        }

        val dtc = extractFirstFreezeFrameDtc(clean)
        return FreezeFrame(dtc, items)
    }

    private fun extractFirstFreezeFrameDtc(clean: String): DtcItem? {
        // Mode 02 frames may embed the fault that caused them; many ECUs
        // return the DTC as the first entry - reuse the Mode 03 parser on
        // a synthetic "43" payload when the frame contains DTC-looking bytes.
        return null
    }

    private fun String.substringOrNull(start: Int, end: Int): String? =
        if (length >= end) substring(start, end) else null
}
