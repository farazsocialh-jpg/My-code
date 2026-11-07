package com.sportage.obd.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

sealed interface ConnectionState {
    object Disconnected : ConnectionState
    /** Carries the address of the ONE device being connected, so the UI can mark just that row. */
    data class Connecting(val address: String? = null) : ConnectionState
    data class Connected(val deviceName: String, val address: String, val protocol: String = "ISO 15765-4 CAN") : ConnectionState
    data class Error(val message: String) : ConnectionState
}

class BluetoothTransport : ElmTransport {
    // Standard Bluetooth Serial Port Profile (SPP) UUID used by ELM327 adapters
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var pendingSocket: BluetoothSocket? = null
    @Volatile private var stopped = false
    private val commandMutex = Mutex()
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    /**
     * Connects to an ELM327 Bluetooth adapter with fallbacks for aftermarket head units.
     * Bluetooth permission and device/ROM failures are reported through [connectionState]
     * instead of escaping the service coroutine and crashing the APK.
     */
    @SuppressLint("MissingPermission")
    override suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        stopped = false
        try {
            _connectionState.value = ConnectionState.Connecting(address = device.address)
            // Discovery must stop before an RFCOMM connection, but some Android versions
            // throw here when Bluetooth permission was revoked while the dialog was open.
            try {
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            } catch (_: SecurityException) {
                // Continue; socket.connect() below will produce the permission error if needed.
            }

            var connectedSocket: BluetoothSocket? = null
            var lastError: Exception? = null

            // Method 1: Standard Secure RFCOMM Socket
            if (connectedSocket == null && !stopped) {
                try {
                    val candidate = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    lastError = runInterruptible { connectSocketWithTimeout(candidate) } ?: run { connectedSocket = candidate; null }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    lastError = error
                }
            }

            // Method 2: Insecure RFCOMM Socket (compatibility fallback)
            currentCoroutineContext().ensureActive()
            if (connectedSocket == null && !stopped) {
                try {
                    val candidate = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    lastError = runInterruptible { connectSocketWithTimeout(candidate) } ?: run { connectedSocket = candidate; null }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    lastError = error
                }
            }

            // Method 3: Hidden RFCOMM channel 1, used by some aftermarket head units
            currentCoroutineContext().ensureActive()
            if (connectedSocket == null && !stopped) {
                try {
                    val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    val candidate = method.invoke(device, 1) as BluetoothSocket
                    lastError = runInterruptible { connectSocketWithTimeout(candidate) } ?: run { connectedSocket = candidate; null }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    lastError = error
                }
            }

            val activeSocket = connectedSocket
            currentCoroutineContext().ensureActive()
            if (activeSocket != null && activeSocket.isConnected && !stopped) {
                socket = activeSocket
                inputStream = activeSocket.inputStream
                outputStream = activeSocket.outputStream
                pendingSocket = null

                // Read metadata only after a successful connection and guard against permission
                // revocation between the connection request and completion.
                val deviceName = try { device.name ?: "ELM327 OBD2" } catch (_: SecurityException) { "ELM327 OBD2" }
                val deviceAddress = try { device.address } catch (_: SecurityException) { "Unknown address" }
                _connectionState.value = ConnectionState.Connected(
                    deviceName = deviceName,
                    address = deviceAddress,
                    protocol = "ISO 15765-4 (CAN 11/500)"
                )
                true
            } else {
                disconnect()
                val message = lastError?.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Could not establish an RFCOMM connection with the adapter"
                _connectionState.value = ConnectionState.Error(message)
                false
            }
        } catch (error: CancellationException) {
            disconnect()
            throw error
        } catch (error: SecurityException) {
            disconnect()
            _connectionState.value = ConnectionState.Error(
                "Bluetooth permission is required. Grant Nearby devices permission and try again."
            )
            false
        } catch (error: Exception) {
            disconnect()
            _connectionState.value = ConnectionState.Error(
                error.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Bluetooth connection failed"
            )
            false
        }
    }

    override suspend fun sendCommand(command: String, timeoutMs: Long): String = commandMutex.withLock { withContext(Dispatchers.IO) {
        val out = outputStream ?: throw IllegalStateException("Socket is not connected")
        val inStream = inputStream ?: throw IllegalStateException("Socket is not connected")

        // Clear any old bytes in buffer
        while (inStream.available() > 0) {
            inStream.read()
        }

        // ELM327 commands must terminate with a carriage return (0x0D), not the two characters
        // backslash + 'r'. Elm327Command.format() appends the real CR byte.
        out.write(Elm327Command.format(command).toByteArray(Charsets.US_ASCII))
        out.flush()

        // Read until prompt '>' is received
        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()
        val byteBuffer = ByteArray(256)

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            currentCoroutineContext().ensureActive()
            if (inStream.available() > 0) {
                val readCount = inStream.read(byteBuffer)
                if (readCount > 0) {
                    val chunk = String(byteBuffer, 0, readCount, Charsets.US_ASCII)
                    buffer.append(chunk)
                    if (buffer.contains(">")) {
                        break
                    }
                } else if (readCount < 0) {
                    throw java.io.EOFException("Bluetooth adapter closed the link")
                }
            } else {
                delay(8)
            }
        }
        if (!buffer.contains(">")) throw java.io.IOException("OBD adapter response timed out")
        Elm327Command.stripPrompt(buffer.toString())
    } }

    /**
     * socket.connect() BLOCKS with no timeout of its own - on an unreachable device
     * (out of range, powered off, or a non-SPP device like a laptop) it can hang for
     * 20+ seconds per attempt, which looked like the app "connecting forever and doing
     * nothing". Run it on a worker thread and force-close the socket after a deadline.
     * Returns null on success, or the failure.
     */
    private fun connectSocketWithTimeout(
        socket: BluetoothSocket,
        timeoutMs: Long = 12_000L
    ): Exception? {
        pendingSocket = socket
        if (stopped) {
            runCatching { socket.close() }
            return java.io.IOException("Connection cancelled")
        }
        var error: Exception? = null
        val worker = Thread {
            try {
                socket.connect()
            } catch (e: SecurityException) {
                // BLUETOOTH_CONNECT may be revoked while the worker is running.
                synchronized(this) { error = e }
            } catch (e: Exception) {
                synchronized(this) { error = e }
            }
        }
        worker.isDaemon = true
        worker.start()
        try {
            worker.join(timeoutMs)
            if (worker.isAlive) {
                runCatching { socket.close() }
                return java.io.IOException("Timed out after ${timeoutMs / 1000}s - device not responding")
            }
            val failure = synchronized(this) { error }
            if (failure != null) runCatching { socket.close() }
            return failure
        } catch (e: InterruptedException) {
            runCatching { socket.close() }
            throw e
        }
    }

    override fun disconnect() {
        stopped = true
        runCatching { pendingSocket?.close() }
        runCatching { socket?.close() }
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        pendingSocket = null
        inputStream = null
        outputStream = null
        socket = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
