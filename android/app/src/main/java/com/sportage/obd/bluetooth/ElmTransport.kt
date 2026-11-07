package com.sportage.obd.bluetooth

import kotlinx.coroutines.flow.StateFlow

/**
 * Common contract for any ELM327 link: classic Bluetooth SPP, Bluetooth LE
 * GATT or (later) WiFi/TCP. ObdService talks to this interface only.
 */
interface ElmTransport {
    val connectionState: StateFlow<ConnectionState>

    /** Establishes the link. Returns true when connected. */
    suspend fun connect(device: android.bluetooth.BluetoothDevice): Boolean

    /** Sends one ELM327 command and returns the response (prompt stripped). */
    suspend fun sendCommand(command: String, timeoutMs: Long = 2500): String

    fun disconnect()
}

/**
 * ELM327 wire framing shared by every transport.
 *
 * A command must end with a real carriage return (0x0D). A literal backslash followed by 'r'
 * leaves the line unterminated, so the adapter never answers and every read times out.
 */
object Elm327Command {

    /** Terminates [command] with the carriage return the ELM327 expects. */
    fun format(command: String): String = command.trim() + "\r"

    /** Strips the ELM prompt and collapses line endings into single spaces. */
    fun cleanResponse(raw: String): String = raw
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace(">", "")
        .trim()

    /**
     * Strips only the ELM prompt and normalizes line endings to '\n'; keeps
     * line boundaries intact so frame-based parsers (e.g. DTC frame decoders) can
     * still split the response by line rather than seeing one long space blob.
     */
    fun stripPrompt(raw: String): String = raw
        .replace(Regex("\r\n?"), "\n")
        .replace(">", "")
        .trim()
}
