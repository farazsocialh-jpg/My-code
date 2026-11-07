package com.sportage.obd.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

/** Serialized BLE UART; Connected means the notification subscription is acknowledged. */
@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
class BleTransport(private val context: Context) : ElmTransport {
    private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = mutableState
    @Volatile private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var ready = CompletableDeferred<Unit>()
    @Volatile private var response: CompletableDeferred<String>? = null
    @Volatile private var writeResult: CompletableDeferred<Unit>? = null
    private val buffer = StringBuilder()
    private val commandMutex = Mutex()

    private fun fail(message: String) {
        val error = IOException(message)
        mutableState.value = ConnectionState.Error(message)
        ready.completeExceptionally(error)
        response?.completeExceptionally(error)
        writeResult?.completeExceptionally(error)
    }

    private fun markReady(g: BluetoothGatt) {
        mutableState.value = ConnectionState.Connected(
            BluetoothDeviceInfo.displayName(context, g.device, "ELM327 BLE"),
            BluetoothDeviceInfo.address(context, g.device) ?: "unknown",
            "ELM327 BLE"
        )
        ready.complete(Unit)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) return
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) fail("BLE link error (status $status)")
                else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!g.discoverServices()) fail("Could not start BLE service discovery")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    fail("BLE adapter disconnected")
                }
            } catch (_: SecurityException) { fail("Nearby devices permission was revoked") }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("BLE service discovery failed ($status)")
                    return
                }
                val pair = findUart(g.services)
                if (pair == null) {
                    fail("No supported ELM327 BLE UART. Try a classic Bluetooth connection.")
                    return
                }
                writeChar = pair.first
                notifyChar = pair.second
                if (!g.setCharacteristicNotification(pair.second, true)) {
                    fail("Could not enable BLE notifications")
                    return
                }
                val ccc = pair.second.getDescriptor(CCC)
                if (ccc == null) {
                    fail("BLE UART has no notification descriptor")
                    return
                }
                ccc.value = if (pair.second.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                if (!g.writeDescriptor(ccc)) fail("BLE notification subscription was rejected")
                // Do not send ATZ until onDescriptorWrite confirms the CCC write.
            } catch (_: SecurityException) { fail("Nearby devices permission was revoked") }
            catch (e: Exception) { fail("BLE setup failed: ${e.message}") }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (g !== gatt || descriptor.uuid != CCC) return
            if (status == BluetoothGatt.GATT_SUCCESS) markReady(g)
            else fail("BLE notification subscription failed ($status)")
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            if (g !== gatt || ch.uuid != writeChar?.uuid) return
            if (status == BluetoothGatt.GATT_SUCCESS) writeResult?.complete(Unit)
            else writeResult?.completeExceptionally(IOException("BLE write failed ($status)"))
        }

        // Android 9–12 dispatch this overload, Android 13+ dispatches the value overload.
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            receive(g, ch, ch.value ?: return)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            receive(g, ch, value)
        }
    }

    private fun receive(g: BluetoothGatt, ch: BluetoothGattCharacteristic, bytes: ByteArray) {
        if (g !== gatt || ch.uuid != notifyChar?.uuid) return
        synchronized(buffer) {
            if (response == null) return
            buffer.append(String(bytes, Charsets.US_ASCII))
            if (buffer.length > 65536) {
                response?.completeExceptionally(IOException("BLE response too large"))
                buffer.setLength(0)
            } else if (buffer.contains(">")) response?.complete(buffer.toString())
        }
    }

    override suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        ready = CompletableDeferred()
        try {
            mutableState.value = ConnectionState.Connecting(device.address)
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw IOException("Could not open BLE adapter")
            withTimeout(15_000L) { ready.await() }
            true
        } catch (e: CancellationException) {
            disconnect()
            // A timeout is a failed attempt; explicit caller cancellation must propagate.
            if (e is kotlinx.coroutines.TimeoutCancellationException) {
                mutableState.value = ConnectionState.Error("BLE connection timed out")
                false
            } else throw e
        } catch (e: Exception) {
            disconnect()
            mutableState.value = ConnectionState.Error(e.message ?: "BLE connection failed")
            false
        }
    }

    override suspend fun sendCommand(command: String, timeoutMs: Long): String =
        commandMutex.withLock {
            withContext(Dispatchers.IO) {
                val g = gatt ?: throw IOException("BLE is not connected")
                val ch = writeChar ?: throw IOException("BLE UART is not ready")
                val reply = CompletableDeferred<String>()
                synchronized(buffer) { buffer.setLength(0); response = reply }
                try {
                    withTimeout(timeoutMs) {
                        val data = Elm327Command.format(command).toByteArray(Charsets.US_ASCII)
                        // Default MTU is 23, leaving 20 bytes for characteristic data.
                        for (chunk in data.toList().chunked(20)) {
                            val written = CompletableDeferred<Unit>()
                            writeResult = written
                            ch.writeType = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            ch.value = chunk.toByteArray()
                            if (!g.writeCharacteristic(ch)) throw IOException("BLE write was rejected")
                            written.await()
                        }
                        Elm327Command.stripPrompt(reply.await())
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    // A deadline is a link failure, not the user cancelling polling.
                    throw IOException("BLE adapter response timed out", e)
                } finally {
                    writeResult = null
                    synchronized(buffer) { response = null; buffer.setLength(0) }
                }
            }
        }

    override fun disconnect() {
        val old = gatt
        gatt = null // Ignore late callbacks from an obsolete connection.
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
        ready.cancel()
        response?.cancel()
        writeResult?.cancel()
        writeChar = null
        notifyChar = null
        mutableState.value = ConnectionState.Disconnected
    }

    companion object {
        private val CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private fun shortId(uuid: UUID) = uuid.toString().take(8).lowercase()
        private fun writable(ch: BluetoothGattCharacteristic) =
            ch.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        private fun readable(ch: BluetoothGattCharacteristic) =
            ch.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

        private fun findUart(services: List<BluetoothGattService>):
            Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
            for (service in services) {
                val chars = service.characteristics
                val write = chars.firstOrNull {
                    shortId(it.uuid) in listOf("6e400002", "0000fff2", "0000ffe1") && writable(it)
                }
                val notify = chars.firstOrNull {
                    shortId(it.uuid) in listOf("6e400003", "0000fff1", "0000ffe1") && readable(it)
                }
                if (write != null && notify != null) return write to notify
                if (shortId(service.uuid) == "49535343") {
                    val w = chars.firstOrNull { writable(it) }
                    val n = chars.firstOrNull { readable(it) }
                    if (w != null && n != null) return w to n
                }
            }
            return null
        }

        fun looksLikeElmBle(uuids: List<ParcelUuid>): Boolean =
            uuids.any { isElmServiceUuid(it.uuid) }

        fun isElmServiceUuid(uuid: UUID): Boolean =
            shortId(uuid) in listOf("0000fff0", "0000ffe0", "6e400001", "49535343")
    }
}
