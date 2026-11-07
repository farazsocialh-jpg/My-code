package com.sportage.obd

import com.sportage.obd.bluetooth.BleTransport
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.bluetooth.TransportStateRelay
import com.sportage.obd.bluetooth.rowState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BluetoothStateTest {
    private val devices = (1..12).map { "AA:BB:CC:DD:EE:%02X".format(it) }

    @Test fun onlySelectedOneOfTwelveRowsShowsConnecting() {
        val state = ConnectionState.Connecting(devices[5])
        val rows = devices.map { state.rowState(it) }
        assertEquals(1, rows.count { it.label == "Connecting..." })
        assertEquals("Connecting...", rows[5].label)
        assertEquals(11, rows.count { it.label == "Connect" })
        assertTrue(rows.none { it.enabled })
    }

    @Test fun addressComparisonIgnoresCase() {
        assertEquals("Connecting...", ConnectionState.Connecting(devices[0].lowercase()).rowState(devices[0]).label)
    }

    @Test fun unknownConnectingAddressNeverMarksAllRows() {
        assertTrue(devices.none { ConnectionState.Connecting().rowState(it).label == "Connecting..." })
        assertFalse(ConnectionState.Connecting().rowState(null).enabled)
    }

    @Test fun onlyConnectedRowShowsDisconnect() {
        val state = ConnectionState.Connected("OBD", devices[2])
        assertEquals(1, devices.count { state.rowState(it).connected })
        assertEquals("Disconnect", state.rowState(devices[2]).label)
        assertEquals("Connect", state.rowState(devices[0]).label)
    }

    @Test fun failedAttemptReenablesEveryValidRow() {
        assertTrue(devices.all { ConnectionState.Error("Timeout").rowState(it).enabled })
        assertFalse(ConnectionState.Disconnected.rowState(null).enabled)
    }

    @Test fun serviceFlowIdentitySurvivesTransportReplacement() = runBlocking {
        val relay = TransportStateRelay(this)
        val observedByRepository = relay.state
        val classic = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        relay.attach(classic, devices[0])
        assertEquals(ConnectionState.Connecting(devices[0]), observedByRepository.value)
        classic.value = ConnectionState.Connected("SPP", devices[0])
        yield()
        assertTrue(observedByRepository.value is ConnectionState.Connected)
        val ble = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        relay.attach(ble, devices[1])
        assertSame(observedByRepository, relay.state)
        assertEquals(ConnectionState.Connecting(devices[1]), observedByRepository.value)
        ble.value = ConnectionState.Connected("BLE", devices[1])
        yield()
        assertEquals(devices[1], (observedByRepository.value as ConnectionState.Connected).address)
        relay.detach()
    }

    @Test fun oldTransportCannotOverwriteNewSelection() = runBlocking {
        val relay = TransportStateRelay(this)
        val old = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val next = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        relay.attach(old, devices[0])
        relay.attach(next, devices[1])
        old.value = ConnectionState.Error("Late callback from old socket")
        yield()
        assertEquals(ConnectionState.Connecting(devices[1]), relay.state.value)
        relay.detach()
    }

    @Test fun cancellationCannotReconnectFromLateCallback() = runBlocking {
        val relay = TransportStateRelay(this)
        val old = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        relay.attach(old, devices[0])
        relay.detach()
        old.value = ConnectionState.Connected("Late", devices[0])
        yield()
        assertEquals(ConnectionState.Disconnected, relay.state.value)
    }

    @Test fun normalizesStandardBluetoothBaseUuids() {
        assertTrue(BleTransport.isElmServiceUuid(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")))
        assertTrue(BleTransport.isElmServiceUuid(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")))
    }

    @Test fun acceptsNordicAndMicrochipUartsButNotHeartRate() {
        assertTrue(BleTransport.isElmServiceUuid(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")))
        assertTrue(BleTransport.isElmServiceUuid(UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455")))
        assertFalse(BleTransport.isElmServiceUuid(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")))
    }
}
