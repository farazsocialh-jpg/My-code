package com.sportage.obd.ui

import com.sportage.obd.bluetooth.rowState
import kotlinx.coroutines.delay

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sportage.obd.bluetooth.BluetoothDeviceInfo
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.ui.theme.*

data class TerminalLog(
    val direction: String, // "TX", "RX", "INFO", "ERR"
    val text: String,
    val time: String
)

/**
 * Defensive classification for the connect action on a paired (or discovered) device.
 *
 * The Android Bluetooth stack can still list a device as bonded even after it has gone
 * unreachable (powered off, out of range, unpaired on the adapter side). Tapping such a
 * device must not drive the app straight into a connect attempt that can throw or crash.
 *
 * This does not change map behaviour (CarLauncherScreen.kt) — it only makes
 * the Bluetooth dialog refuse to start a connect when the target device is clearly unavailable.
 */
object BluetoothDeviceConnectGuard {
    /** Reasons a device should not be connected from the UI right now. */
    sealed class UnavailableReason {
        data class MissingAddress(val device: BluetoothDevice) : UnavailableReason()
        data class AlreadyConnected(val device: BluetoothDevice, val currentAddress: String) : UnavailableReason()
        data class AlreadyConnecting(val device: BluetoothDevice) : UnavailableReason()
        data class AdapterOff(val device: BluetoothDevice) : UnavailableReason()

        val shortMessage: String
            get() = when (this) {
                is MissingAddress -> "device has no address"
                is AlreadyConnected -> "already connected to this adapter"
                is AlreadyConnecting -> "connection already in progress"
                is AdapterOff -> "Bluetooth is off"
            }

        fun detailMessage(deviceLabel: String): String =
            "$shortMessage (device $deviceLabel)"
    }

    /**
     * True when a paired device is safe to pass into the connect path from the UI.
     *
     * Deliberately does NOT reject devices with a null/blank cached name or a transient
     * bond state: many ELM327 adapters report no name while idle, and refusing them made
     * the "Connect" button permanently disabled for valid paired adapters. A failed connect
     * now surfaces as a ConnectionState.Error in the dialog instead of blocking the button.
     *
     * Requires a non-null [context] because reading a device address needs it (and on
     * Android 12+ needs BLUETOOTH_CONNECT). When the context is unavailable the guard treats
     * the device as not connectable rather than crashing.
     */
    fun canConnectTo(
        device: BluetoothDevice,
        connectionState: ConnectionState,
        context: Context
    ): Pair<Boolean, UnavailableReason?> {
        val address = BluetoothDeviceInfo.address(context, device)
        if (address.isNullOrBlank()) {
            return Pair(false, UnavailableReason.MissingAddress(device))
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || runCatching { !adapter.isEnabled }.getOrDefault(true)) {
            return Pair(false, UnavailableReason.AdapterOff(device))
        }

        if (connectionState is ConnectionState.Connected) {
            val currentAddress = (connectionState as ConnectionState.Connected).address
            if (currentAddress.equals(address, ignoreCase = true)) {
                return Pair(false, UnavailableReason.AlreadyConnected(device, currentAddress))
            }
        }

        if (connectionState is ConnectionState.Connecting) {
            // Do not start a second connect while one is already in flight.
            return Pair(false, UnavailableReason.AlreadyConnecting(device))
        }

        return Pair(true, null)
    }

    /**
     * Human-readable label for an unavailable device, used only in error messages.
     *
     * Uses the same permission-safe accessors as the rest of the dialog, so this never throws
     * even when BLUETOOTH_CONNECT has not been granted yet.
     */
    fun deviceLabel(context: Context, device: BluetoothDevice): String {
        val address = BluetoothDeviceInfo.address(context, device)
        val name = BluetoothDeviceInfo.displayName(context, device, "")
        return buildString {
            if (name.isNotBlank()) append(name)
            if (name.isNotBlank()) append(" (")
            if (!address.isNullOrBlank()) append("MAC: $address")
            if (name.isNotBlank()) append(")")
        }.ifBlank { "unknown device" }
    }
}

private fun ConnectionState.isConnecting(): Boolean =
    this is ConnectionState.Connecting

private fun ConnectionState.currentAddress(): String? =
    if (this is ConnectionState.Connected) (this as ConnectionState.Connected).address else null

@SuppressLint("MissingPermission")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BluetoothDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    connectionState: ConnectionState,
    pairedDevices: List<BluetoothDevice>,
    onConnectDevice: (BluetoothDevice, Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshDevices: () -> Unit,
    onSendCommand: (String) -> Unit,
    terminalLogs: List<TerminalLog>,
    isDemoMode: Boolean,
    onToggleDemoMode: (Boolean) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Devices, 1: Terminal, 2: Help
    var commandInput by remember { mutableStateOf("") }

    // Deferred pairing state for the "Pair & Connect" button.
    var pendingPairDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var pendingPairFailed by remember { mutableStateOf<String?>(null) }

    val discoveredDevices = remember { mutableStateListOf<BluetoothDevice>() }
    var isScanningNearby by remember { mutableStateOf(false) }

    val bleDevices = remember { mutableStateListOf<BluetoothDevice>() }
    var isScanningBle by remember { mutableStateOf(false) }
    val latestConnectionState by rememberUpdatedState(connectionState)
    val latestConnect by rememberUpdatedState(onConnectDevice)
    val bleScanCallback = remember {
        object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                try {
                    val device = result.device ?: return
                    val advertised = result.scanRecord?.deviceName ?: device.name
                    val uuids = result.scanRecord?.serviceUuids
                    val looksElm = advertised != null && listOf("obd", "elm", "vgate", "icar", "carista").any {
                        advertised!!.lowercase().contains(it)
                    } || (uuids != null && com.sportage.obd.bluetooth.BleTransport.looksLikeElmBle(uuids!!))
                    if (looksElm && bleDevices.none { it.address == device.address }) {
                        bleDevices.add(device)
                    }
                } catch (_: SecurityException) {}
            }
        }
    }

    fun startBleScan() {
        if (latestConnectionState is ConnectionState.Connecting || pendingPairDevice != null) return
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            val scanner = adapter.bluetoothLeScanner ?: return
            bleDevices.clear()
            scanner.startScan(null, android.bluetooth.le.ScanSettings.Builder().build(), bleScanCallback)
            isScanningBle = true
        } catch (_: Exception) {}
    }

    fun stopBleScan() {
        try {
            BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(bleScanCallback)
        } catch (_: Exception) {}
        isScanningBle = false
    }

    fun startNearbyScan() {
        if (latestConnectionState is ConnectionState.Connecting || pendingPairDevice != null) return
        stopBleScan()
        // Explicit, user-initiated scan only (never automatic). Any in-flight RFCOMM
        // connect is aborted first: discovery and socket connects cannot run together
        // on most Android Bluetooth stacks and doing both made connects fail instantly.
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                discoveredDevices.clear()
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
                isScanningNearby = adapter.startDiscovery()
            }
        } catch (_: SecurityException) {
            // BLUETOOTH_SCAN not granted: stay silent, the paired list still works.
        } catch (_: Exception) {}
    }

    fun connectSelected(device: BluetoothDevice, ble: Boolean) {
        stopBleScan()
        runCatching { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() }
        isScanningNearby = false
        latestConnect(device, ble)
    }

    LaunchedEffect(isScanningBle) {
        if (isScanningBle) { delay(15_000); stopBleScan() }
    }

    LaunchedEffect(pendingPairDevice) {
        if (pendingPairDevice != null) {
            delay(30_000)
            pendingPairDevice = null
            pendingPairFailed = "Pairing timed out. Check the adapter PIN in Bluetooth Settings."
        }
    }

    DisposableEffect(isOpen) {
        if (!isOpen) return@DisposableEffect onDispose {}

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (device != null) {
                            // Reading name/address needs BLUETOOTH_CONNECT on Android 12+; a raw
                            // read here threw inside a BroadcastReceiver and killed the process.
                            val name = BluetoothDeviceInfo.displayName(context, device, "")
                            val addr = BluetoothDeviceInfo.address(context, device)
                            if (name.isNotBlank() && addr != null &&
                                discoveredDevices.none { BluetoothDeviceInfo.address(context, it) == addr } &&
                                pairedDevices.none { BluetoothDeviceInfo.address(context, it) == addr }
                            ) {
                                discoveredDevices.add(device)
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        isScanningNearby = true
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        isScanningNearby = false
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // RECEIVER_NOT_EXPORTED: some Chinese head-unit ROMs throw SecurityException when a
        // receiver is registered without an export flag on targetSdk 35 - that crash killed
        // the app the moment the Bluetooth dialog opened.
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {}

        // Discovery is NEVER started automatically. On Android head units (TS10 / UIS7862 /
        // MTK) an app-initiated discovery makes the unit's Bluetooth stack refresh and
        // re-pair EVERY cached device, which looked like "pairing with all devices" and
        // crashed the app. Scanning is strictly opt-in via the "Scan Nearby" button, and
        // an error state no longer triggers a background scan that would also kill any
        // RFCOMM connect attempt already in flight.
        // To scan: the driver taps "Scan Nearby" / "Scan for Adapters" explicitly.

        onDispose {
            try {
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            try {
                BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(bleScanCallback)
            } catch (_: Exception) {}
        }
    }

    // Watches the device being paired so the RFCOMM connect only starts once bonding completes.
    DisposableEffect(pendingPairDevice) {
        val device = pendingPairDevice
        if (device == null) return@DisposableEffect onDispose {}

        val bondReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                @Suppress("DEPRECATION")
                val changed = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val expectedAddress = BluetoothDeviceInfo.address(context, device) ?: return
                if (changed == null || !expectedAddress.equals(
                        BluetoothDeviceInfo.address(context, changed), ignoreCase = true)) return

                @Suppress("DEPRECATION")
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)

                when (bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        pendingPairDevice = null
                        pendingPairFailed = null
                        val (canConnect, reason) = BluetoothDeviceConnectGuard.canConnectTo(
                            device = device,
                            connectionState = latestConnectionState,
                            context = context
                        )
                        if (canConnect) {
                            onRefreshDevices()
                            runCatching { connectSelected(device, false) }
                        } else {
                            val label = BluetoothDeviceConnectGuard.deviceLabel(context, device)
                            pendingPairFailed = "Paired, but unavailable to connect: ${reason?.detailMessage(label) ?: "unknown"}"
                        }
                    }
                    BluetoothDevice.BOND_NONE -> {
                        pendingPairDevice = null
                        pendingPairFailed = "Pairing was cancelled or did not complete. Tap to retry."
                    }
                }
            }
        }

        try {
            ContextCompat.registerReceiver(
                context,
                bondReceiver,
                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
            // Register first: a fast bond completion must not race receiver installation.
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                pendingPairDevice = null
                connectSelected(device, false)
            } else if (!device.createBond()) {
                pendingPairDevice = null
                pendingPairFailed = "Pairing request was rejected. Try Android Bluetooth Settings."
            }
        } catch (e: Exception) {
            pendingPairDevice = null
            pendingPairFailed = "Pairing failed: ${e.localizedMessage}"
        }

        onDispose {
            try {
                context.unregisterReceiver(bondReceiver)
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            color = DeepShade(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DarkSurfaceElevated())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = TealAccent().copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = TealAccent(),
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "BLUETOOTH OBD2 INTERFACE",
                                color = PrimaryText(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = when (connectionState) {
                                    is ConnectionState.Connected -> "ACTIVE: ${(connectionState as ConnectionState.Connected).deviceName} (${(connectionState as ConnectionState.Connected).address})"
                                    is ConnectionState.Connecting -> "CONNECTING TO ${(connectionState as ConnectionState.Connecting).address ?: "ADAPTER"} - PLEASE WAIT..."
                                    is ConnectionState.Error -> "ERROR: ${(connectionState as ConnectionState.Error).message}"
                                    else -> if (isDemoMode) "DEMO BENCH TESTING ACTIVE" else "NOT CONNECTED · SELECT ADAPTER"
                                },
                                color = when (connectionState) {
                                    is ConnectionState.Connected -> TealAccent()
                                    is ConnectionState.Connecting -> ElectricCyan()
                                    is ConnectionState.Error -> CriticalRed()
                                    else -> SecondaryText()
                                },
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .background(DarkSurfaceElevated(), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText(), modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (connectionState is ConnectionState.Connecting) {
                    TextButton(onClick = onDisconnect) {
                        Text("Cancel connection", color = CriticalRed(), fontSize = 12.sp)
                    }
                }

                // Segmented Tabs: DEVICES | TERMINAL | TROUBLESHOOTING
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepShade(), RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("PAIRED ADAPTERS", "BLE SCAN", "RAW TERMINAL", "TROUBLESHOOTING").forEachIndexed { index, tabTitle ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = index },
                            color = if (selectedTab == index) ElectricCyan() else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = tabTitle,
                                color = if (selectedTab == index) OnAccent() else SecondaryText(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier
                                    .padding(vertical = 6.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // TAB 0: DEVICES & DISCOVERY
                if (selectedTab == 0) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Error banner if connection failed
                        if (connectionState is ConnectionState.Error) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                color = CriticalRed().copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CriticalRed().copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CriticalRed(), modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ADAPTER CONNECTION FAILED",
                                            color = CriticalRed(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                        Text(
                                            text = (connectionState as ConnectionState.Error).message + " · Select an adapter below to pair and connect.",
                                            color = SecondaryText(),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                    Button(
                                        onClick = { startNearbyScan() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CriticalRed()),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Scan", fontSize = 12.sp, color = DarkBackground(), fontFamily = FontFamily.SansSerif)
                                    }
                                }
                            }
                        }

                        // Toolbar
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "DEVICES: ${pairedDevices.size} PAIRED · ${discoveredDevices.size} DISCOVERED",
                                color = SecondaryText(),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif
                            )

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { startNearbyScan() },
                                    enabled = !isScanningNearby && connectionState !is ConnectionState.Connecting && pendingPairDevice == null,
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    if (isScanningNearby) {
                                        CircularProgressIndicator(color = ElectricCyan(), modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Scanning...", fontSize = 12.sp, color = ElectricCyan(), fontFamily = FontFamily.SansSerif)
                                    } else {
                                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(12.dp), tint = ElectricCyan())
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Scan Nearby", fontSize = 12.sp, color = ElectricCyan(), fontFamily = FontFamily.SansSerif)
                                    }
                                }

                                Button(
                                    onClick = onRefreshDevices,
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = SecondaryText())
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Refresh", fontSize = 12.sp, color = SecondaryText(), fontFamily = FontFamily.SansSerif)
                                }

                                Button(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            })
                                        } catch (_: Exception) {}
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(12.dp), tint = PrimaryText())
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("System Settings", fontSize = 12.sp, color = PrimaryText(), fontFamily = FontFamily.SansSerif)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Section: Paired Devices
                            if (pairedDevices.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "PAIRED BLUETOOTH ADAPTERS",
                                        color = SecondaryText(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                items(pairedDevices) { device ->
                                    val rowState = connectionState.rowState(BluetoothDeviceInfo.address(context, device))
                                    val devName = BluetoothDeviceInfo.displayName(context, device, "Unknown Bluetooth Device")
                                    val isObd = devName.lowercase().contains("obd") ||
                                            devName.lowercase().contains("elm") ||
                                            devName.lowercase().contains("vgate") ||
                                            devName.lowercase().contains("carista")

                                    val deviceAddress = BluetoothDeviceInfo.address(context, device)
                                    val isCurrentConnected = connectionState is ConnectionState.Connected &&
                                            connectionState.currentAddress()?.equals(deviceAddress, ignoreCase = true) == true

                                    val (canConnect, _) = BluetoothDeviceConnectGuard.canConnectTo(
                                        device = device,
                                        connectionState = connectionState,
                                        context = context
                                    )
                                    val connectButtonLabel = rowState.label

                                    Surface(
                                        color = if (isCurrentConnected) DarkSurface() else DarkSurface(),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrentConnected) TealAccent() else if (isObd) ElectricCyan().copy(alpha = 0.5f) else DarkSurfaceElevated()
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    color = if (isCurrentConnected) TealAccent().copy(alpha = 0.2f) else DarkSurfaceElevated(),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DirectionsCar,
                                                        contentDescription = null,
                                                        tint = if (isCurrentConnected) TealAccent() else if (isObd) ElectricCyan() else SecondaryText(),
                                                        modifier = Modifier
                                                            .padding(8.dp)
                                                            .size(20.dp)
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text(
                                                            text = devName,
                                                            color = PrimaryText(),
                                                            fontSize = 13.sp,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false),
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.SansSerif
                                                        )
                                                        if (isObd) {
                                                            Text(
                                                                text = "ELM327 MATCH",
                                                                color = ElectricCyan(),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = FontFamily.SansSerif,
                                                                modifier = Modifier
                                                                    .background(ElectricCyan().copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                                )
                                                        }
                                                    }
                                                    Text(
                                                        text = deviceAddress?.let { "MAC: $it" } ?: "MAC unavailable",
                                                        color = SecondaryText(),
                                                        fontSize = 12.sp,
                                                        fontFamily = FontFamily.SansSerif
                                                    )
                                                }
                                            }

                                            if (isCurrentConnected) {
                                                Button(
                                                    onClick = onDisconnect,
                                                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed()),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Disconnect", color = DarkBackground(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                                                }
                                            } else {
                                                Button(
                                                    // runCatching: even if the connect path throws
                                                    // (aggressive head-unit ROMs), the app must survive.
                                                    onClick = { runCatching { connectSelected(device, device.type == BluetoothDevice.DEVICE_TYPE_LE) } },
                                                    enabled = canConnect && rowState.enabled && pendingPairDevice == null,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isObd) ElectricCyan() else DarkSurfaceElevated()
                                                    ),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = connectButtonLabel,
                                                        color = if (isObd) OnAccent() else PrimaryText(),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.SansSerif
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Section: Discovered Nearby Devices
                            if (discoveredDevices.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "NEARBY DISCOVERED ADAPTERS (TAP TO PAIR & CONNECT)",
                                        color = ElectricCyan(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                items(discoveredDevices) { device ->
                                    val devName = BluetoothDeviceInfo.displayName(context, device, "Nearby Bluetooth Device")
                                    val isObd = devName.lowercase().contains("obd") ||
                                            devName.lowercase().contains("elm") ||
                                            devName.lowercase().contains("vgate") ||
                                            devName.lowercase().contains("carista")

                                    Surface(
                                        color = DarkSurface(),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, if (isObd) ElectricCyan().copy(alpha = 0.6f) else DarkSurfaceElevated())
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    color = DarkSurfaceElevated(),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.BluetoothSearching,
                                                        contentDescription = null,
                                                        tint = if (isObd) ElectricCyan() else SecondaryText(),
                                                        modifier = Modifier
                                                            .padding(8.dp)
                                                            .size(20.dp)
                                                    )
                                                }

                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text(
                                                            text = devName,
                                                            color = PrimaryText(),
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.SansSerif
                                                        )
                                                        Text(
                                                            text = "NEARBY",
                                                            color = AmberWarning(),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.SansSerif,
                                                            modifier = Modifier
                                                                .background(AmberWarning().copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = BluetoothDeviceInfo.address(context, device)?.let { "MAC: $it" } ?: "MAC unavailable",
                                                        color = SecondaryText(),
                                                        fontSize = 12.sp,
                                                        fontFamily = FontFamily.SansSerif
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    pendingPairFailed = null
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                        try {
                                                            val permission = android.Manifest.permission.BLUETOOTH_CONNECT
                                                            @Suppress("DEPRECATION")
                                                            val granted = context.checkCallingOrSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                            if (!granted) {
                                                                pendingPairFailed = "Bluetooth connection permission is required before pairing."
                                                                return@Button
                                                            }
                                                        } catch (_: SecurityException) {
                                                            pendingPairFailed = "Bluetooth connection permission is required before pairing."
                                                            return@Button
                                                        }
                                                    }

                                                    stopBleScan()
                                                    runCatching { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() }
                                                    // The effect registers the receiver before starting the bond.
                                                    pendingPairDevice = device
                                                },
                                                enabled = connectionState !is ConnectionState.Connecting &&
                                                        pendingPairDevice == null,
                                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = when {
                                                        pendingPairFailed != null -> "Retry Pair"
                                                        pendingPairDevice == device -> "Pairing..."
                                                        else -> "Pair & Connect"
                                                    },
                                                    color = OnAccent(),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Empty state if both are empty
                            if (pairedDevices.isEmpty() && discoveredDevices.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(DarkSurface(), RoundedCornerShape(10.dp))
                                            .border(1.dp, DarkSurfaceElevated(), RoundedCornerShape(10.dp))
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            if (isScanningNearby) {
                                                CircularProgressIndicator(color = ElectricCyan(), modifier = Modifier.size(36.dp))
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text("Scanning for Nearby Bluetooth Adapters...", color = PrimaryText(), fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.SansSerif)
                                                Text("Ensure adapter is plugged into OBD2 port with vehicle ignition ON.", color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                                            } else {
                                                Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = SecondaryText(), modifier = Modifier.size(40.dp))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("No OBD2 Adapters Detected", color = PrimaryText(), fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.SansSerif)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Plug ELM327 into the vehicle's OBD2 port and tap Scan Nearby.", color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Button(
                                                    onClick = { startNearbyScan() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Scan for Adapters", color = OnAccent(), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Action Row
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface(), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Bench / Demo Simulation:", color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                                Switch(
                                    checked = isDemoMode,
                                    onCheckedChange = onToggleDemoMode,
                                    colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan(), checkedTrackColor = ElectricCyan().copy(alpha = 0.4f))
                                )
                            }

                            if (connectionState is ConnectionState.Connected) {
                                Text(
                                    text = "ISO 15765-4 CAN 11/500K · 10Hz POLLING",
                                    color = TealAccent(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }

                // TAB 1: BLE SCAN (Low Energy ELM327 adapters)
                if (selectedTab == 1) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BLUETOOTH LOW ENERGY SCAN (VGATE / iCAR / ELM327 BLE)",
                            color = SecondaryText(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { if (isScanningBle) stopBleScan() else startBleScan() },
                                enabled = connectionState !is ConnectionState.Connecting && pendingPairDevice == null,
                                colors = ButtonDefaults.buttonColors(containerColor = if (isScanningBle) CriticalRed() else ElectricCyan()),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isScanningBle) "Stop Scan" else "Scan BLE Adapters",
                                    fontSize = 12.sp,
                                    color = if (isScanningBle) PrimaryText() else OnAccent(),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (bleDevices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurface(), RoundedCornerShape(10.dp))
                                    .border(1.dp, DarkSurfaceElevated(), RoundedCornerShape(10.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (isScanningBle) {
                                        CircularProgressIndicator(color = ElectricCyan(), modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Scanning for BLE ELM327 adapters...", color = PrimaryText(), fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.SansSerif)
                                    } else {
                                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = SecondaryText(), modifier = Modifier.size(40.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No BLE Adapters Found Yet", color = PrimaryText(), fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.SansSerif)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Plug the adapter in, then tap Scan BLE Adapters. BLE adapters do not need pairing.", color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(bleDevices) { device ->
                                    val bleRow = connectionState.rowState(BluetoothDeviceInfo.address(context, device))
                                    Surface(
                                        color = DarkSurface(),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, ElectricCyan().copy(alpha = 0.6f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = BluetoothDeviceInfo.displayName(context, device, "BLE OBD Adapter"),
                                                    color = PrimaryText(),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                                Text(
                                                    // Raw device.address throws SecurityException on
                                                    // Android 12+ when BLUETOOTH_CONNECT is missing; the
                                                    // guarded accessor never throws.
                                                    text = "MAC: ${BluetoothDeviceInfo.address(context, device) ?: "unavailable"}",
                                                    color = SecondaryText(),
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                            Button(
                                                onClick = { if (bleRow.connected) onDisconnect() else runCatching { connectSelected(device, true) } },
                                                enabled = bleRow.enabled && pendingPairDevice == null,
                                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = if (bleRow.label == "Connect") "Connect BLE" else bleRow.label,
                                                    color = OnAccent(),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: RAW TERMINAL
                if (selectedTab == 2) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LIVE ELM327 AT / OBD COMMAND CONSOLE",
                            color = SecondaryText(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick commands row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("ATZ", "ATRV", "0100", "010C", "010D", "03").forEach { cmd ->
                                Surface(
                                    modifier = Modifier.clickable { onSendCommand(cmd) },
                                    color = DarkSurfaceElevated(),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = cmd,
                                        color = ElectricCyan(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Terminal Log Output Box
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            color = OnAccent(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DarkSurfaceElevated())
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                reverseLayout = true
                            ) {
                                items(terminalLogs.reversed()) { log ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = log.time,
                                            color = SecondaryText(),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = log.direction,
                                            color = when (log.direction) {
                                                "TX" -> ElectricCyan()
                                                "RX" -> TealAccent()
                                                "ERR" -> CriticalRed()
                                                else -> SecondaryText()
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = log.text,
                                            color = SecondaryText(),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Command input row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = commandInput,
                                onValueChange = { commandInput = it.uppercase() },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Send OBD command (e.g. 010C or ATRV)...", fontSize = 11.sp, color = SecondaryText(), fontFamily = FontFamily.Monospace) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan(),
                                    unfocusedBorderColor = DarkSurfaceElevated(),
                                    focusedTextColor = PrimaryText(),
                                    unfocusedTextColor = SecondaryText()
                                )
                            )

                            Button(
                                onClick = {
                                    if (commandInput.isNotBlank()) {
                                        onSendCommand(commandInput.trim())
                                        commandInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Send", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                // TAB 3: TROUBLESHOOTING
                if (selectedTab == 3) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(DarkSurface(), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Text("KIA SPORTAGE OBD-II CONNECTION GUIDE", color = ElectricCyan(), fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(8.dp))

                        listOf(
                            "1. OBD-II Port Location" to "On Kia Sportage 2.0 MPI, the 16-pin DLC port is located below the driver's dashboard near the hood release latch.",
                            "2. Ignition Required" to "Turn car ignition to 'ON' (or start engine). An unpowered ECU will not respond to PID queries.",
                            "3. Initial Pairing" to "In Android Settings > Bluetooth, pair with 'OBDII', 'V-LINK', or 'ELM327'. Typical PIN is 1234 or 0000.",
                            "4. Chinese Head Unit Pairing" to "On Android Head Units (TS10, UIS7862, MTK), ensure head unit Bluetooth PIN in factory settings matches the adapter (1234).",
                            "5. Supported Protocol" to "Kia Sportage MPI AWD communicates over ISO 15765-4 CAN (11-bit ID, 500 kbaud). The launcher automatically initializes this protocol."
                        ).forEach { (title, desc) ->
                            Text(title, color = PrimaryText(), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text(desc, color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}
