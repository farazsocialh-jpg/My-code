package com.sportage.obd.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sportage.obd.MainActivity
import com.sportage.obd.bluetooth.BleTransport
import com.sportage.obd.bluetooth.BluetoothDeviceInfo
import com.sportage.obd.bluetooth.BluetoothTransport
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.bluetooth.ElmTransport
import com.sportage.obd.bluetooth.TransportStateRelay
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.FreezeFrame
import com.sportage.obd.obd.Mode06Decoders
import com.sportage.obd.obd.Mode06MonitorResult
import com.sportage.obd.obd.PidDecoders
import com.sportage.obd.obd.ReadinessState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/**
 * Data model for live vehicle telemetry collected by ObdService.
 */
data class ObdTelemetry(
    val speedKmh: Int? = null,
    val rpm: Int? = null,
    val coolantTempC: Int? = null,
    val intakeTempC: Int? = null,
    val fuelLevelPct: Int? = null,
    val throttlePct: Float? = null,
    val engineLoadPct: Float? = null,
    val batteryVoltage: Float? = null,
    val mafGs: Float? = null,
    val shortTermFuelTrimPct: Float? = null,
    val longTermFuelTrimPct: Float? = null,
    val timingAdvanceDeg: Float? = null,
    val fuelEconomyL100: Float? = null,
    val fuelUsedLiters: Float = 0f,
    /** Direct ECU fuel rate from PID 5E when supported, L/h. */
    val fuelRateEcuLh: Float? = null,
    /** Tank-to-tank calibration factor applied to calculated usage (1.0 = none). */
    val fuelCalibrationFactor: Float = 1f,
    /** ECU_DIRECT or CALCULATED, so the UI can label every fuel figure. */
    val fuelSource: String? = null,
    val idleFuelLiters: Float = 0f,
    val cityFuelLiters: Float = 0f,
    val highwayFuelLiters: Float = 0f,
    val cityDistanceKm: Float = 0f,
    val highwayDistanceKm: Float = 0f,
    val estimatedGear: Int = 0,
    /** Actual transmission gear from PID 01A4 when the ECU supports it: -2 P, -1 R, 0 N, 1..10 forward. */
    val transmissionGear: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val sampleRateHz: Float = 0f,
    val sensors: Map<String, com.sportage.obd.obd.SensorSample> = emptyMap(),
    val vehicleInfo: Map<String, String> = emptyMap()
)

/**
 * ObdService: Android Foreground Service with type 'connectedDevice'.
 * Runs continuously in the background, decoupled from Activity lifecycle,
 * maintaining an active link (Bluetooth Classic SPP or BLE) with ELM327
 * adapters and streaming vehicle telemetry.
 */
class ObdService : Service() {

    companion object {
        const val CHANNEL_ID = "sportage_obd_service_channel"
        const val ALERT_CHANNEL_ID = "sportage_obd_alerts_channel"
        const val NOTIFICATION_ID = 2026
        const val ALERT_NOTIFICATION_ID = 2027
        const val ACTION_START = "com.sportage.obd.action.START_SERVICE"
        const val ACTION_STOP = "com.sportage.obd.action.STOP_SERVICE"
        const val ACTION_PAUSE_POLLING = "com.sportage.obd.action.PAUSE_POLLING"
        const val ACTION_RESUME_POLLING = "com.sportage.obd.action.RESUME_POLLING"

        private const val PREFS_NAME = "sportage_obd_service"
        private const val KEY_LAST_DEVICE = "last_adapter_address"
        private const val KEY_LAST_DEVICE_IS_BLE = "last_adapter_is_ble"
        private const val KEY_CSV_AUTOSTART = "csv_autostart"

        /** True while the foreground service is alive, so a restarted UI can re-attach to it. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val binder = ObdServiceBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var transport: ElmTransport = BluetoothTransport()
    private var useBle = false
    private var pollingJob: Job? = null
    private var reconnectJob: Job? = null
    private var connectJob: Job? = null
    private val stateRelay = TransportStateRelay(serviceScope)
    @Volatile private var connectionGeneration = 0L

    // Created lazily in onCreate: constructing these as field initializers runs during
    // service instantiation, BEFORE Android attaches the service Context. That crashed the
    // app with "Unable to create service" on some devices (getSharedPreferences /
    // ToneGenerator with a dead context).
    private var alertEngine: AlertEngine? = null
    private var csvLogger: CsvLogger? = null

    // Target Bluetooth device to maintain connection with
    private var activeDevice: BluetoothDevice? = null

    // Telemetry and connection state
    private val _telemetry = MutableStateFlow(ObdTelemetry())
    val telemetry: StateFlow<ObdTelemetry> = _telemetry.asStateFlow()

    private val _isPollingPaused = MutableStateFlow(false)
    val isPollingPaused: StateFlow<Boolean> = _isPollingPaused.asStateFlow()

    private val _dtcList = MutableStateFlow<List<DtcItem>>(emptyList())
    val dtcList: StateFlow<List<DtcItem>> = _dtcList.asStateFlow()

    private val _pendingDtcList = MutableStateFlow<List<DtcItem>>(emptyList())
    val pendingDtcList: StateFlow<List<DtcItem>> = _pendingDtcList.asStateFlow()

    private val _permanentDtcList = MutableStateFlow<List<DtcItem>>(emptyList())
    val permanentDtcList: StateFlow<List<DtcItem>> = _permanentDtcList.asStateFlow()

    private val _freezeFrame = MutableStateFlow<FreezeFrame?>(null)
    val freezeFrame: StateFlow<FreezeFrame?> = _freezeFrame.asStateFlow()

    private val _readiness = MutableStateFlow<ReadinessState?>(null)
    val readiness: StateFlow<ReadinessState?> = _readiness.asStateFlow()

    private val _mode06MonitorResult = MutableStateFlow<Mode06MonitorResult?>(null)
    val mode06MonitorResult: StateFlow<Mode06MonitorResult?> = _mode06MonitorResult.asStateFlow()

    private val _vin = MutableStateFlow<String?>(null)
    val vin: StateFlow<String?> = _vin.asStateFlow()

    private val _supportedPids = MutableStateFlow<Set<String>>(emptySet())
    val supportedPids: StateFlow<Set<String>> = _supportedPids.asStateFlow()

    private val _isCsvLogging = MutableStateFlow(false)
    val isCsvLogging: StateFlow<Boolean> = _isCsvLogging.asStateFlow()

    val activeAlerts: StateFlow<List<ActiveAlert>>
        get() = alertEngine?.activeAlerts ?: MutableStateFlow<List<ActiveAlert>>(emptyList())

    val csvLogFile: java.io.File? get() = csvLogger?.activeFile

    val connectionState: StateFlow<ConnectionState> = stateRelay.state

    // Diagnostic / frequency tracking
    private var packetCount = 0
    private var lastHzCalculationTime = System.currentTimeMillis()
    private var currentHz = 0f

    // Trip fuel accounting
    private var fuelUsedLiters = 0f
    private var lastFuelSampleAtMs = 0L
    private var fuelCalibrationFactor = 1f
    private var idleFuelLiters = 0f
    private var cityFuelLiters = 0f
    private var highwayFuelLiters = 0f
    private var cityDistanceKm = 0f
    private var highwayDistanceKm = 0f

    // Critical-alert notifications are throttled: the polling loop runs at ~30 Hz and used to
    // re-post the same warning on every iteration.
    private var lastAlertNotificationAtMs = 0L

    inner class ObdServiceBinder : Binder() {
        fun getService(): ObdService = this@ObdService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        // Nothing in here may throw: an exception during service creation kills the whole
        // app with "Unable to create service". Every step is guarded; failures degrade
        // gracefully instead of crashing.
        try {
            alertEngine = AlertEngine(this)
        } catch (e: Exception) {
            android.util.Log.e("ObdService", "AlertEngine init failed - alerts disabled", e)
            alertEngine = null
        }
        try {
            csvLogger = CsvLogger(this)
        } catch (e: Exception) {
            android.util.Log.e("ObdService", "CsvLogger init failed - logging disabled", e)
            csvLogger = null
        }
        try {
            createNotificationChannels()
        } catch (e: Exception) {
            android.util.Log.e("ObdService", "notification channels failed", e)
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification("OBD Service Initializing..."))
        } catch (e: Exception) {
            // Some OEM ROMs throw from inside startForeground (their notification pipeline
            // touches android.net.Uri). The service still works without being foregrounded.
            android.util.Log.e("ObdService", "startForeground failed - continuing anyway", e)
        }
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent == null) {
                // START_STICKY restart after the process was killed: nothing to resume, so restore the
                // adapter the driver was last connected to.
                restoreLastDevice()
                return START_STICKY
            }
            when (intent.action) {
                ACTION_STOP -> {
                    disconnect()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                ACTION_PAUSE_POLLING -> {
                    pausePolling()
                }
                ACTION_RESUME_POLLING -> {
                    resumePolling()
                }
                ACTION_START -> {
                    updateNotification("Ready for Bluetooth link")
                }
            }
        } catch (e: Exception) {
            // Same rule as onCreate: never let a command crash the process.
            android.util.Log.e("ObdService", "onStartCommand error", e)
        }
        return START_STICKY
    }

    /**
     * Connect to target Bluetooth OBD adapter with automatic reconnection.
     * [ble] selects the transport: BLE GATT for vgate-style adapters,
     * classic SPP otherwise.
     */
    @Synchronized
    fun connectToDevice(device: BluetoothDevice, ble: Boolean = false): Boolean {
        // The service, not a delayed UI flag, is the single-flight authority.
        if (connectJob?.isActive == true || connectionState.value is ConnectionState.Connecting) return false
        val address = BluetoothDeviceInfo.address(this, device)
        if (address == null) {
            stateRelay.publish(ConnectionState.Error("Nearby devices permission is required."))
            return false
        }
        val generation = ++connectionGeneration
        activeDevice = device
        useBle = ble
        reconnectJob?.cancel()
        stopPolling()
        stateRelay.detach()

        // Release the previous link before swapping transports. An SPP adapter accepts a single
        // client, so a socket left open here made every later connect fail with "socket busy".
        try {
            transport.disconnect()
        } catch (_: Exception) {}

        transport = if (ble) BleTransport(this) else BluetoothTransport()
        val link = transport
        stateRelay.attach(link.connectionState, address)
        _telemetry.value = ObdTelemetry()
        _supportedPids.value = emptySet()
        _vin.value = null
        _readiness.value = null
        _dtcList.value = emptyList()
        _pendingDtcList.value = emptyList()
        _permanentDtcList.value = emptyList()
        _freezeFrame.value = null
        _mode06MonitorResult.value = null
        _isPollingPaused.value = false
        alertEngine?.clear()
        connectJob = serviceScope.launch {
            // transport.connect() can throw (permission revoked mid-dialog, adapter unplugged);
            // an uncaught exception in this launch would terminate the process. Everything
            // after the connect is also guarded: a failed init/polling start must surface as
            // a connection error, never as a crash.
            val success = try {
                link.connect(device)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                updateNotification("Bluetooth permission is required to connect")
                false
            } catch (e: Exception) {
                updateNotification("Connection failed: ${e.localizedMessage ?: "Bluetooth error"}")
                false
            }
            currentCoroutineContext().ensureActive()
            if (generation != connectionGeneration || link !== transport) {
                link.disconnect()
                return@launch
            }
            if (success) {
                try {
                    rememberDevice(device)
                } catch (_: Exception) {}
                updateNotification("Connected to ${BluetoothDeviceInfo.displayName(this@ObdService, device)} · Polling active")
                try {
                    initializeElm327()
                    discoverVehicleCapabilities()
                    currentCoroutineContext().ensureActive()
                    if (generation != connectionGeneration) return@launch
                    if (prefs().getBoolean(KEY_CSV_AUTOSTART, false)) startCsvLogging()
                    startPollingLoop()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    link.disconnect()
                    stateRelay.publish(ConnectionState.Error("Adapter initialization failed: ${e.message}"))
                }
            } else {
                // A laptop/car stereo is often paired but is not an ELM adapter.
                // Failed manual attempts must stop, not reconnect forever.
                if (connectionState.value !is ConnectionState.Error) {
                    stateRelay.publish(ConnectionState.Error("Could not connect to the selected OBD adapter."))
                }
                updateNotification("Connection failed. Select the OBD adapter to retry.")
            }
        }
        return true
    }

    @Synchronized
    fun disconnect() {
        connectionGeneration++
        activeDevice = null
        forgetDevice()
        reconnectJob?.cancel()
        connectJob?.cancel()
        connectJob = null
        stopPolling()
        stateRelay.detach()
        transport.disconnect()
        _telemetry.value = ObdTelemetry()
        stopCsvLogging()
        alertEngine?.clear()
        updateNotification("Disconnected")
    }

    fun pausePolling() {
        _isPollingPaused.value = true
        updateNotification("Polling paused (Ignition off mode)")
    }

    fun resumePolling() {
        if (connectionState.value !is ConnectionState.Connected || connectJob?.isActive == true) return
        _isPollingPaused.value = false
        if (pollingJob == null || pollingJob?.isActive == false) {
            startPollingLoop()
        }
        updateNotification("Streaming vehicle telemetry")
    }

    suspend fun sendRawCommand(command: String): String {
        return transport.sendCommand(command)
    }

    private suspend fun sampleCommand(command: String): String {
        val generation = connectionGeneration
        val response = transport.sendCommand(command)
        if (generation == connectionGeneration && command.startsWith("01") && command.length == 4) {
            com.sportage.obd.obd.SensorCatalog.decode(command.takeLast(2), response)?.let { sample ->
                _telemetry.update { it.copy(sensors = it.sensors + (sample.pid to sample)) }
            }
        }
        return response
    }

    suspend fun refreshSensorCapabilities() {
        check(connectJob?.isActive != true) { "Adapter is still initializing" }
        if (connectionState.value is ConnectionState.Connected) discoverVehicleCapabilities()
    }

    /** Raw diagnostic responses are labelled as such; manufacturer limits are not guessed. */
    suspend fun readVehicleInformation() {
        check(connectJob?.isActive != true) { "Adapter is still initializing" }
        if (connectionState.value !is ConnectionState.Connected) return
        val generation = connectionGeneration
        val link = transport
        val info = linkedMapOf<String, String>()
        for ((label, command) in listOf("Adapter" to "ATI", "Protocol" to "ATDP",
            "Calibration ID · raw response" to "0904", "Calibration verification · raw response" to "0906",
            "ECU name · raw response" to "090A", "On-board monitor tests · raw response" to "06")) {
            if (generation != connectionGeneration) return
            val response = link.sendCommand(command)
            info[label] = response.take(4000).ifBlank { "No response" }
        }
        if (generation == connectionGeneration) _telemetry.update { it.copy(vehicleInfo = info) }
    }

    suspend fun scanDtcCodes(): List<DtcItem> {
        return try {
            val response = transport.sendCommand("03")
            val parsed = PidDecoders.decodeDtcCodes(response)
            _dtcList.value = parsed
            parsed
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Mode 07: pending (not-yet-confirmed) trouble codes. */
    suspend fun scanPendingDtcs(): List<DtcItem> {
        return try {
            val response = transport.sendCommand("07")
            val parsed = PidDecoders.decodeDtcCodes(response)
            _pendingDtcList.value = parsed
            parsed
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Mode 0A: permanent codes that survive a Mode 04 clear. */
    suspend fun scanPermanentDtcs(): List<DtcItem> {
        return try {
            val response = transport.sendCommand("0A")
            val parsed = PidDecoders.decodeDtcCodes(response)
            _permanentDtcList.value = parsed
            parsed
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Mode 02: freeze frame stored with the last confirmed fault. */
    suspend fun readFreezeFrame(): FreezeFrame? {
        return try {
            val items = mutableListOf<com.sportage.obd.obd.FreezeFrameItem>()
            for (pid in listOf("04", "05", "0C", "0D", "0F", "10", "11", "2F")) {
                val response = transport.sendCommand("02${pid}00")
                val normalized = response.replace(Regex("42\\s*${pid[0]}\\s*${pid[1]}\\s*00", RegexOption.IGNORE_CASE), "42$pid")
                items += PidDecoders.decodeFreezeFrame(normalized).items
            }
            val cause = transport.sendCommand("020200")
            val code = Regex("420200([0-9A-F]{4})", RegexOption.IGNORE_CASE)
                .find(cause.replace(Regex("\\s"), ""))?.groupValues?.get(1)
            val parsed = FreezeFrame(code?.let { PidDecoders.decodeDtcCodes("43$it").firstOrNull() }, items)
            if (parsed.items.isNotEmpty()) {
                _freezeFrame.value = parsed
                parsed
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /** Mode 01 PID 01: MIL status and readiness monitors. */
    suspend fun readReadiness(): ReadinessState? {
        return try {
            val response = transport.sendCommand("0101")
            val parsed = PidDecoders.decodeReadiness(response)
            if (parsed != null) _readiness.value = parsed
            parsed
        } catch (e: Exception) {
            null
        }
    }

    /** Mode 06 on-board monitor results. */
    suspend fun refreshMode06Monitors() {
        if (connectionState.value !is ConnectionState.Connected) return
        val generation = connectionGeneration
        val link = transport
        try {
            val response = link.sendCommand("06")
            if (generation == connectionGeneration) {
                _mode06MonitorResult.value = Mode06Decoders.decodeMode06(response)
            }
        } catch (_: Exception) {
            if (generation == connectionGeneration) _mode06MonitorResult.value = null
        }
    }

    /** Mode 09 PID 02: vehicle identification number. */
    suspend fun readVin(): String? {
        return try {
            val response = transport.sendCommand("0902")
            val vin = PidDecoders.decodeVin(response)
            if (vin != null) _vin.value = vin
            vin
        } catch (e: Exception) {
            null
        }
    }

    suspend fun clearDtcCodes(): Boolean {
        if ((_telemetry.value.speedKmh ?: 0) > 0) return false
        return try {
            val response = transport.sendCommand("04")
            // Accept the real Mode 04 positive response ("44", optionally behind a CAN
            // header like "7E8 44") or a plain OK. A bare contains("44") matched random
            // bytes from interleaved poll replies (e.g. speed 0x44 = 68 km/h).
            val confirmed = response.lineSequence().any { line ->
                val tokens = line.trim().trimStart('>').split(' ', '\t').filter { it.isNotBlank() }
                val payload = tokens.dropWhile { Regex("^([0-9A-F]{3}|[0-9A-F]{6,8})$").matches(it.uppercase()) }
                payload.isNotEmpty() && (payload[0].uppercase() == "44" || payload[0].uppercase() == "OK")
            }
            if (confirmed) {
                _dtcList.value = emptyList()
                _pendingDtcList.value = emptyList()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // --- Warning thresholds ---

    /**
     * Sets the tank-to-tank calibration factor applied to calculated fuel usage.
     * Direct ECU fuel-rate readings are not scaled.
     */
    fun setFuelCalibration(factor: Float) {
        fuelCalibrationFactor = factor.coerceIn(0.5f, 2.5f)
    }

    /** Resets the trip fuel accumulators (called from the trip reset workflow). */
    fun resetTripFuel() {
        fuelUsedLiters = 0f
        idleFuelLiters = 0f
        cityFuelLiters = 0f
        highwayFuelLiters = 0f
        cityDistanceKm = 0f
        highwayDistanceKm = 0f
        lastFuelSampleAtMs = 0L
    }

    fun updateAlertThresholds(thresholds: AlertThresholds) {
        alertEngine?.updateThresholds(thresholds)
    }

    fun alertThresholds(): AlertThresholds = alertEngine?.currentThresholds() ?: AlertThresholds()

    // --- CSV logging ---

    fun startCsvLogging(): Boolean {
        return try {
            val file = csvLogger?.start() ?: return false
            _isCsvLogging.value = file.exists()
            file.exists()
        } catch (_: Exception) {
            csvLogger?.stop()
            _isCsvLogging.value = false
            false
        }
    }

    fun stopCsvLogging() {
        csvLogger?.stop()
        _isCsvLogging.value = false
    }

    fun setCsvAutostart(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_CSV_AUTOSTART, enabled).apply()
    }

    fun csvAutostart(): Boolean = prefs().getBoolean(KEY_CSV_AUTOSTART, false)

    private suspend fun initializeElm327() {
        try {
            transport.sendCommand("ATZ")      // Reset ELM327
            delay(150)
            transport.sendCommand("ATE0")     // Echo off
            delay(50)
            transport.sendCommand("ATL0")     // Linefeeds off
            delay(50)
            transport.sendCommand("ATS0")     // Spaces off
            delay(50)
            transport.sendCommand("ATH0")     // Headers off
            delay(50)
            transport.sendCommand("ATSP0")    // Auto protocol detect (matches CAN 11/500 for Sportage)
            delay(100)
        } catch (e: CancellationException) { throw e }
    }

    /**
     * Queries the ECU for its supported PID bitmaps (0100/0120/0140),
     * the VIN and the current readiness state right after connecting, so
     * the UI can adapt instead of polling unsupported PIDs forever.
     */
    private suspend fun discoverVehicleCapabilities() {
        val supported = mutableSetOf<String>()
        try {
            for (base in 0x00..0xE0 step 0x20) {
                val cmd = "01" + String.format("%02X", base)
                val resp = withTimeoutOrNull(2000) { transport.sendCommand(cmd) }
                supported += PidDecoders.decodeSupportedPids(resp ?: "", base)
                if ("%02X".format(base + 0x20) !in supported) break
            }
            _supportedPids.value = supported
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}

        try {
            readVin()
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        try {
            readReadiness()
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            // Independent cycle counter for the auxiliary PID rotation. Deliberately
            // separate from packetCount: packetCount is reset every second by the
            // Hz calculation below, which previously caused the aux PID rotation
            // (packetCount / 2) % 6 to restart from coolant on every reset and, on
            // slower adapters, never reach the later PIDs (fuel, throttle, load,
            // voltage).
            var auxPidCycle = 0
            var extraCycle = 0
            var nextExtraAt = 0L
            val unavailable = mutableMapOf<String, Int>()
            lastFuelSampleAtMs = System.currentTimeMillis()

            while (isActive && activeDevice != null) {
                if (_isPollingPaused.value) {
                    delay(500)
                    continue
                }

                try {
                    // 1. Engine RPM (PID 010C) - high priority
                    val rpmResp = sampleCommand("010C")
                    val rpm = PidDecoders.decodeRpm(rpmResp)

                    // 2. Vehicle Speed (PID 010D) - high priority
                    val spdResp = sampleCommand("010D")
                    val speed = PidDecoders.decodeSpeed(spdResp)

                    // Measure sampling frequency
                    packetCount += 2
                    val now = System.currentTimeMillis()
                    if (now - lastHzCalculationTime >= 1000) {
                        currentHz = (packetCount * 1000f) / (now - lastHzCalculationTime)
                        packetCount = 0
                        lastHzCalculationTime = now
                    }

                    // Estimate gear based on speed and RPM for Kia Sportage 6-speed automatic
                    val gear = estimateGear(speed ?: 0, rpm ?: 0)

                    // Lower-frequency secondary PIDs (rotation independent of
                    // the Hz sample counter; only polls PIDs the ECU supports)
                    var coolant = _telemetry.value.coolantTempC
                    var intake = _telemetry.value.intakeTempC
                    var fuel = _telemetry.value.fuelLevelPct
                    var throttle = _telemetry.value.throttlePct
                    var load = _telemetry.value.engineLoadPct
                    var voltage = _telemetry.value.batteryVoltage
                    var maf = _telemetry.value.mafGs
                    var stft = _telemetry.value.shortTermFuelTrimPct
                    var ltft = _telemetry.value.longTermFuelTrimPct
                    var timing = _telemetry.value.timingAdvanceDeg
                    var actualGear = _telemetry.value.transmissionGear

                    val supported = _supportedPids.value

                    fun pidSupported(pid: String) = supported.isEmpty() || supported.contains(pid)

                    when (auxPidCycle % 10) {
                        0 -> if (pidSupported("05")) {
                            val resp = sampleCommand("0105")
                            coolant = PidDecoders.decodeCoolantTemp(resp) ?: coolant
                        }
                        1 -> if (pidSupported("0F")) {
                            val resp = sampleCommand("010F")
                            intake = PidDecoders.decodeIntakeTemp(resp) ?: intake
                        }
                        2 -> if (pidSupported("2F")) {
                            val resp = sampleCommand("012F")
                            fuel = PidDecoders.decodeFuelLevel(resp) ?: fuel
                        }
                        3 -> if (pidSupported("11")) {
                            val resp = sampleCommand("0111")
                            throttle = PidDecoders.decodeThrottle(resp) ?: throttle
                        }
                        4 -> if (pidSupported("04")) {
                            val resp = sampleCommand("0104")
                            load = PidDecoders.decodeLoad(resp) ?: load
                        }
                        5 -> {
                            val resp = sampleCommand("ATRV")
                            voltage = PidDecoders.decodeBatteryVoltage(resp) ?: voltage
                        }
                        6 -> if (pidSupported("10")) {
                            val resp = sampleCommand("0110")
                            maf = PidDecoders.decodeMaf(resp) ?: maf
                        }
                        7 -> if (pidSupported("06")) {
                            val resp = sampleCommand("0106")
                            stft = PidDecoders.decodeShortTermFuelTrim(resp) ?: stft
                        }
                        8 -> if (pidSupported("07")) {
                            val resp = sampleCommand("0107")
                            ltft = PidDecoders.decodeLongTermFuelTrim(resp) ?: ltft
                        }
                        9 -> if (pidSupported("0E")) {
                            val resp = sampleCommand("010E")
                            timing = PidDecoders.decodeTimingAdvance(resp) ?: timing
                        }
                    }
                    // Transmission gear (PID A4) whenever the ECU advertises it. This is
                    // the only way to tell P / N / R / D apart at standstill.
                    if (pidSupported("A4")) {
                        val resp = sampleCommand("01A4")
                        actualGear = PidDecoders.decodeTransmissionGear(resp) ?: actualGear
                    }
                    auxPidCycle = (auxPidCycle + 1) % 10

                    // One extra request per 750 ms at most. Core readings retain priority.
                    if (android.os.SystemClock.elapsedRealtime() >= nextExtraAt) {
                        val extras = com.sportage.obd.obd.SensorCatalog.extraPids(supported)
                            .filter { (unavailable[it] ?: 0) < 3 }
                        if (extras.isNotEmpty()) {
                            val pid = extras[extraCycle.mod(extras.size)]
                            extraCycle = (extraCycle + 1).mod(extras.size)
                            val response = sampleCommand("01$pid")
                            if (com.sportage.obd.obd.SensorCatalog.decode(pid, response) == null)
                                unavailable[pid] = (unavailable[pid] ?: 0) + 1
                            else unavailable.remove(pid)
                        }
                        nextExtraAt = android.os.SystemClock.elapsedRealtime() + 750L
                    }

                    // Fuel rate: prefer the ECU's direct PID 5E reading; fall back to the
                    // MAF estimate (stoich AFR 14.7, gasoline ~0.745 g/ml). Calculated
                    // figures are scaled by the tank-to-tank calibration factor and are
                    // always labeled as estimates - never as ECU measurements.
                    val sensorsNow = _telemetry.value.sensors
                    val ecuRateLh = sensorsNow["5E"]?.values
                        ?.firstOrNull { it.unit == "L/h" }?.value?.toFloat()
                    var rateLh: Float? = null
                    var fuelIsDirect = false
                    if (ecuRateLh != null && ecuRateLh >= 0f) {
                        rateLh = ecuRateLh
                        fuelIsDirect = true
                    } else if (maf != null) {
                        rateLh = (com.sportage.obd.fuel.FuelMath.litersPerHourFromMaf(maf.toDouble()) * fuelCalibrationFactor).toFloat()
                    }
                    var economy: Float? = null
                    if (rateLh != null) {
                        if (speed != null && speed > 3) economy = rateLh / speed * 100f
                        val dtSec = (now - lastFuelSampleAtMs) / 1000f
                        if (dtSec in 0f..30f) {
                            val used = (rateLh ?: 0f) * dtSec / 3600f
                            fuelUsedLiters += used
                            when (com.sportage.obd.fuel.FuelMath.classify((speed ?: 0).toDouble())) {
                                com.sportage.obd.fuel.DriveClass.IDLE -> idleFuelLiters += used
                                com.sportage.obd.fuel.DriveClass.CITY -> {
                                    cityFuelLiters += used
                                    cityDistanceKm += (speed ?: 0) * dtSec / 3600f
                                }
                                com.sportage.obd.fuel.DriveClass.HIGHWAY -> {
                                    highwayFuelLiters += used
                                    highwayDistanceKm += (speed ?: 0) * dtSec / 3600f
                                }
                            }
                        }
                    }
                    lastFuelSampleAtMs = now

                    val newTelemetry = ObdTelemetry(
                        speedKmh = speed,
                        rpm = rpm,
                        coolantTempC = coolant,
                        intakeTempC = intake,
                        fuelLevelPct = fuel,
                        throttlePct = throttle,
                        engineLoadPct = load,
                        batteryVoltage = voltage,
                        mafGs = maf,
                        shortTermFuelTrimPct = stft,
                        longTermFuelTrimPct = ltft,
                        timingAdvanceDeg = timing,
                        fuelEconomyL100 = economy,
                        fuelUsedLiters = fuelUsedLiters,
                        fuelRateEcuLh = if (fuelIsDirect) rateLh else null,
                        fuelCalibrationFactor = fuelCalibrationFactor,
                        fuelSource = when {
                            rateLh == null -> null
                            fuelIsDirect -> "ECU_DIRECT"
                            fuelCalibrationFactor != 1f -> "CALIBRATED"
                            else -> "CALCULATED"
                        },
                        idleFuelLiters = idleFuelLiters,
                        cityFuelLiters = cityFuelLiters,
                        highwayFuelLiters = highwayFuelLiters,
                        cityDistanceKm = cityDistanceKm,
                        highwayDistanceKm = highwayDistanceKm,
                        estimatedGear = gear,
                        transmissionGear = actualGear,
                        timestamp = now,
                        sampleRateHz = currentHz,
                        sensors = _telemetry.value.sensors,
                        vehicleInfo = _telemetry.value.vehicleInfo
                    )

                    _telemetry.update { newTelemetry.copy(sensors = it.sensors, vehicleInfo = it.vehicleInfo) }
                    alertEngine?.evaluate(newTelemetry)
                    val criticalAlert = alertEngine?.activeAlerts?.value?.firstOrNull { it.severity == AlertSeverity.CRITICAL }
                    if (criticalAlert != null && now - lastAlertNotificationAtMs >= 60_000L) {
                        lastAlertNotificationAtMs = now
                        postAlertNotification(criticalAlert)
                    } else if (criticalAlert == null) {
                        lastAlertNotificationAtMs = 0L
                    }
                    if (_isCsvLogging.value && csvLogger?.log(newTelemetry) != true) {
                        stopCsvLogging()
                        updateNotification("OBD recording stopped: storage unavailable")
                    }

                    delay(30) // Fast cycle rate
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (isActive && activeDevice != null) {
                        scheduleReconnect()
                        break
                    }
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun scheduleReconnect() {
        stopPolling()
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            var attempt = 0
            val target = activeDevice ?: return@launch
            val generation = connectionGeneration
            val link = transport
            link.disconnect()
            while (isActive && activeDevice == target && generation == connectionGeneration && attempt < 5) {
                attempt++
                val backoffSec = min(16, 1 shl min(4, attempt - 1))
                updateNotification("Reconnecting in ${backoffSec}s (Attempt $attempt)...")
                delay(backoffSec * 1000L)

                if (!isActive || activeDevice == null) break

                updateNotification("Reconnecting to ${BluetoothDeviceInfo.displayName(this@ObdService, target)}...")
                val ok = try {
                    link.connect(target)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SecurityException) {
                    false
                } catch (_: Exception) {
                    false
                }
                currentCoroutineContext().ensureActive()
                if (generation != connectionGeneration) return@launch
                if (ok) {
                    try {
                        updateNotification("Connected to ${BluetoothDeviceInfo.displayName(this@ObdService, target)}")
                        initializeElm327()
                        discoverVehicleCapabilities()
                        currentCoroutineContext().ensureActive()
                        if (generation != connectionGeneration) return@launch
                        startPollingLoop()
                        break
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        link.disconnect()
                        stateRelay.publish(ConnectionState.Error("Adapter setup failed: ${e.message}"))
                    }
                }
            }
            if (attempt >= 5 && connectionState.value !is ConnectionState.Connected) {
                stateRelay.publish(ConnectionState.Error("Adapter unavailable after 5 retries. Tap Connect to retry."))
            }
        }
    }

    private fun estimateGear(speedKmh: Int, rpm: Int): Int {
        if (speedKmh <= 2 || rpm < 600) return 0 // P / N
        val ratio = rpm.toFloat() / speedKmh.toFloat()
        return when {
            ratio > 80f -> 1
            ratio in 55f..80f -> 2
            ratio in 38f..55f -> 3
            ratio in 28f..38f -> 4
            ratio in 22f..28f -> 5
            else -> 6
        }
    }

    private fun rememberDevice(device: BluetoothDevice) {
        val address = BluetoothDeviceInfo.address(this, device) ?: return
        prefs().edit()
            .putString(KEY_LAST_DEVICE, address)
            .putBoolean(KEY_LAST_DEVICE_IS_BLE, useBle)
            .apply()
    }

    private fun forgetDevice() {
        prefs().edit()
            .remove(KEY_LAST_DEVICE)
            .remove(KEY_LAST_DEVICE_IS_BLE)
            .apply()
    }

    private fun prefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Rebuilds the link after a sticky restart using the adapter MAC persisted on the last
     * successful connect. Silent no-op when Bluetooth is off, permission is missing, or the
     * address is unknown.
     */
    private fun restoreLastDevice() {
        if (activeDevice != null) return
        val address = prefs().getString(KEY_LAST_DEVICE, null) ?: return
        val ble = prefs().getBoolean(KEY_LAST_DEVICE_IS_BLE, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            updateNotification("Bluetooth permission required to restore link")
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) {
            updateNotification("Bluetooth is off · waiting for adapter")
            return
        }
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (_: Exception) {
            forgetDevice()
            return
        }
        updateNotification("Restoring link to ${BluetoothDeviceInfo.displayName(this, device)}...")
        connectToDevice(device, ble)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Sportage OBD Live Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps ELM327 OBD-II streaming active in background"
                setShowBadge(false)
            }
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Vehicle Warning Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Threshold warnings such as engine overheating"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(alertChannel)
        }
    }

    private fun postAlertNotification(alert: ActiveAlert) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Vehicle Warning")
            .setContentText(alert.message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sportage OBD-II Intelligence")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        // Some head-unit ROMs throw from notify() (missing POST_NOTIFICATIONS handling,
        // broken NotificationManager); a status update must never kill the service.
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopPolling()
        reconnectJob?.cancel()
        connectJob?.cancel()
        stateRelay.detach()
        serviceScope.cancel()
        transport.disconnect()
        csvLogger?.stop()
        alertEngine?.close()
    }
}
