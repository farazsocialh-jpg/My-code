package com.sportage.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.viewmodel.ObdViewModel
import com.sportage.obd.fuel.TripHistoryStore
import com.sportage.obd.fuel.TripRecord
import com.sportage.obd.ui.AppDrawerDialog
import com.sportage.obd.ui.BluetoothDialog
import com.sportage.obd.ui.CarLauncherScreen
import com.sportage.obd.ui.OnboardingScreen
import com.sportage.obd.ui.PrivacyPolicyScreen
import com.sportage.obd.ui.TerminalLog
import com.sportage.obd.ui.theme.SportageObdTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.os.bundleOf
import com.sportage.obd.bluetooth.BluetoothDeviceInfo
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity(), LocationListener {

    companion object {
        const val APP_BUILD_TAG = "2026-11-06.r"
    }

    private val viewModel: ObdViewModel by viewModels()
    private var locationManager: LocationManager? = null

    // Crash report from the previous run, shown as a dialog until dismissed.
    private var previousCrashReport by mutableStateOf<String?>(null)

    // Active Navigation Page (0: Home, 1: Gauges & Graphs, 2: DTC Diagnostics, 3: Trip, 4: Media, 5: Settings)
    private var activePage by mutableStateOf(0)

    // Bluetooth Dialog State
    private var showBluetoothDialog by mutableStateOf(false)
    private var showAppDrawer by mutableStateOf(false)
    private var pairedDevicesList = mutableStateListOf<BluetoothDevice>()
    private val terminalLogs = mutableStateListOf<TerminalLog>()

    // Live Telemetry state (authoritative values)
    private var liveSpeed by mutableStateOf<Int?>(null)
    private var liveRpm by mutableStateOf<Int?>(null)
    private var liveCoolant by mutableStateOf<Int?>(null)
    private var liveIntakeTemp by mutableStateOf<Int?>(null)
    private var liveFuelLevel by mutableStateOf<Int?>(null)
    private var liveThrottle by mutableStateOf<Float?>(null)
    private var liveLoad by mutableStateOf<Float?>(null)
    private var liveVoltage by mutableStateOf<Float?>(null)
    private var liveGear by mutableStateOf<Int>(0)
    private var liveTransmissionGear by mutableStateOf<Int?>(null)
    private var measuredHz by mutableStateOf<Float?>(null)

    // Trip Computer State
    private var tripDistanceKm by mutableStateOf(0.0)
    private var tripStartTime by mutableStateOf(System.currentTimeMillis())
    private var tripDurationSeconds by mutableStateOf(0L)
    private var tripMaxSpeed by mutableStateOf(0)
    private var tripAvgSpeed by mutableStateOf(0)
    private var isTripActive by mutableStateOf(true)

    // Live GPS Location
    private var currentLat by mutableStateOf(25.2854)
    private var currentLon by mutableStateOf(51.5310)
    private var currentBearing by mutableStateOf(0f)
    private var gpsCourseBearing: Float? = null
    private var gpsSpeedMs = 0f
    private var compassAzimuth: Float? = null
    private val rotationMatrix = FloatArray(9)
    private var sensorManager: SensorManager? = null
    private var weatherTempC by mutableStateOf<String?>(null)
    private var weatherText by mutableStateOf<String?>(null)
    private var lastWeatherFetchAtMs = 0L
    private var lastWeatherLat = 0.0
    private var lastWeatherLon = 0.0
    private val weatherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentGpsSpeed by mutableStateOf<Int?>(null)
    private var gpsStatus by mutableStateOf("Waiting for GPS")

    // Auto night dimming (persisted)
    private val uiPrefs by lazy { getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE) }
    private var autoNightDim by mutableStateOf(true)
    private var nightDimLevel by mutableStateOf(0.55f)

    // Theme customization (persisted): full-app color preset + optional accent override.
    private var themeId by mutableStateOf("cockpit-dark")
    private var accentOverride by mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Track permission request outcome (anonymous, no personal data).
        val anyBluetoothGranted = permissions.entries.any { it.key.contains("BLUETOOTH") && it.value }
        analyticsLogEvent(
            "permissions_obtained",
            "bluetooth_granted" to anyBluetoothGranted
        )
        val btEntries = permissions.entries.filter { it.key.contains("BLUETOOTH") }
        val btGranted = btEntries.all { it.value }
        if (btGranted) {
            refreshPairedDevices()
        } else if (btEntries.isNotEmpty()) {
            // Silence here looked like "no adapters found"; tell the driver what actually happened.
            Toast.makeText(
                this,
                "Bluetooth permission denied - OBD adapters cannot be scanned or connected",
                Toast.LENGTH_LONG
            ).show()
        }
        initLocationTracking()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Track app launch (first-run detection for onboarding analytics).
        val prefs = getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE)
        val launchCount = prefs.getInt("launch_count", 0) + 1
        prefs.edit().putInt("launch_count", launchCount).apply()
        val hasCompletedOnboarding = prefs.getBoolean("onboarding_complete", false)
        showOnboarding = !hasCompletedOnboarding
        val isFirstLaunch = !hasCompletedOnboarding

        showPreviousCrashReport()

        // Track onboarding / first-launch flow.
        analyticsLogEvent(
            "app_launch",
            "is_first_launch" to isFirstLaunch,
            "launch_count" to launchCount
        )

        // targetSdk 35 forces edge-to-edge on Android 15, which drew the cockpit strip underneath
        // the status bar. Opt in explicitly with transparent bars, then pad the root layout with the
        // system bar insets below.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        autoNightDim = uiPrefs.getBoolean("autoNightDim", true)
        nightDimLevel = uiPrefs.getFloat("nightDimLevel", 0.55f)
        themeId = uiPrefs.getString("themeId", "cockpit-dark") ?: "cockpit-dark"
        accentOverride = uiPrefs.getString("accentOverride", null)

        checkAndRequestPermissions()
        initLocationTracking()
        refreshPairedDevices()
        startTripTicker()

        // Handle Back button: Return to Home if on secondary page; do not exit launcher on Home
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showAppDrawer) {
                    showAppDrawer = false
                } else if (showBluetoothDialog) {
                    showBluetoothDialog = false
                } else if (activePage != 0) {
                    activePage = 0
                }
                // When on home (activePage == 0), intentionally absorb Back press
            }
        })

        setContent {
            val connState by viewModel.connectionState.collectAsState()
            val demoMode by viewModel.isDemoMode.collectAsState()
            val pollingPaused by viewModel.isPollingPaused.collectAsState()
            val dtcs by viewModel.dtcList.collectAsState()
            val isScanning by viewModel.isScanningDtc.collectAsState()
            val speedHist by viewModel.speedHistory.collectAsState()
            val rpmHist by viewModel.rpmHistory.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.telemetry.collect { t ->
                    liveSpeed = t.speedKmh
                    liveRpm = t.rpm
                    liveCoolant = t.coolantTempC
                    liveIntakeTemp = t.intakeTempC
                    liveFuelLevel = t.fuelLevelPct
                    liveThrottle = t.throttlePct
                    liveLoad = t.engineLoadPct
                    liveVoltage = t.batteryVoltage
                    liveGear = t.estimatedGear
                    liveTransmissionGear = t.transmissionGear
                    measuredHz = t.sampleRateHz.takeIf { hz -> hz > 0f }
                }
            }

            LaunchedEffect(Unit) {
                viewModel.connectionState.collect { state ->
                    when (state) {
                        is ConnectionState.Connected ->
                            logTerminal("INFO", "LINK ACTIVE: ${state.deviceName} \u00b7 ${state.protocol}")
                        is ConnectionState.Error -> logTerminal("ERR", "Adapter error: ${state.message}")
                        is ConnectionState.Connecting -> logTerminal("INFO", "Opening RFCOMM socket to ${state.address ?: "adapter"}...")
                        ConnectionState.Disconnected -> logTerminal("INFO", "Adapter link closed.")
                    }
                }
            }

            SportageObdTheme(themeId = themeId, accentOverride = accentOverride) {
                val isNight = isSystemInDarkTheme()
                Box(modifier = Modifier.fillMaxSize()) {
                    // Inset the dashboard so the status bar / navigation bar never cover controls.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {                CarLauncherScreen(
                    connectionState = connState,
                    measuredHz = measuredHz, speed = liveSpeed, rpm = liveRpm, coolant = liveCoolant, intakeTemp = liveIntakeTemp,
                    fuelLevel = liveFuelLevel, throttle = liveThrottle, load = liveLoad, voltage = liveVoltage, gear = liveGear,
                    transmissionGear = liveTransmissionGear,
                    weatherTempC = weatherTempC, weatherText = weatherText,
                    speedHistory = speedHist, rpmHistory = rpmHist,
                    currentLat = currentLat, currentLon = currentLon, currentBearing = currentBearing,
                    gpsStatus = gpsStatus,
                    activePage = activePage,
                    onNavigatePage = { page ->
                        activePage = page
                        analyticsLogLauncherPage(page)
                    },
                    onOpenBluetoothClick = {
                        refreshPairedDevices()
                        showBluetoothDialog = true
                    },
                        dtcCodes = dtcs,
                        isScanningDtc = isScanning,
                        onScanDtc = { scanDtcCodes() },
                        onClearDtc = { clearDtcCodes() },
                        tripDistanceKm = tripDistanceKm,
                        tripDurationSeconds = tripDurationSeconds,
                        tripMaxSpeed = tripMaxSpeed,
                        tripAvgSpeed = tripAvgSpeed,
                        onResetTrip = { resetTrip() },
                        onSetDefaultLauncher = { promptSetDefaultLauncher() },
                        isDemoMode = demoMode,
                        onToggleDemoMode = { toggleDemoMode(it) },
                        isPollingPaused = pollingPaused,
                        onTogglePausePolling = { togglePausePolling() },
                        onOpenAppDrawer = { showAppDrawer = true },
                        viewModel = viewModel,
                        autoNightDim = autoNightDim,
                        nightDimLevel = nightDimLevel,
                        onSetAutoNightDim = { enabled ->
                            autoNightDim = enabled
                            uiPrefs.edit().putBoolean("autoNightDim", enabled).apply()
                        },
                        onSetNightDimLevel = { level ->
                            nightDimLevel = level.coerceIn(0.1f, 0.85f)
                            uiPrefs.edit().putFloat("nightDimLevel", nightDimLevel).apply()
                        },
                        themeId = themeId,
                        accentOverride = accentOverride,
                        onSetThemeId = { id ->
                            themeId = id
                            uiPrefs.edit().putString("themeId", id).apply()
                        },
                        onSetAccentOverride = { hex ->
                            accentOverride = hex
                            uiPrefs.edit().putString("accentOverride", hex).apply()
                        },
                        onOpenPrivacyPolicy = ::openPrivacyPolicy
                    )
                    }

                    // Night dimming overlay: darkens the whole dashboard after dark.
                    // Non-interactive, so all controls stay touchable underneath.
                    if (autoNightDim && isNight) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = nightDimLevel))
                        )
                    }
                }

                // Bluetooth Connection & Terminal Modal
                BluetoothDialog(
                    isOpen = showBluetoothDialog,
                    onClose = { showBluetoothDialog = false },
                    connectionState = connState,
                    pairedDevices = pairedDevicesList,
                    onConnectDevice = { device, ble -> connectToDevice(device, ble) },
                    onDisconnect = { disconnectDevice() },
                    onRefreshDevices = { refreshPairedDevices() },
                    onSendCommand = { sendTerminalCommand(it) },
                    terminalLogs = terminalLogs,
                    isDemoMode = demoMode,
                    onToggleDemoMode = { toggleDemoMode(it) }
                )

                // Previous-run crash report: shown immediately so the crash reason can never
                // be missed. Screenshot this dialog and send it back to get the real fix.
                previousCrashReport?.let { report ->
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { previousCrashReport = null }
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = Color(0xFF0E1722)
                        ) {
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "THE APP CRASHED LAST TIME",
                                    color = Color(0xFFFF5A5A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Screenshot this box and send it - this is the reason.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = report,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .heightIn(max = 420.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = { previousCrashReport = null },
                                    modifier = Modifier.align(Alignment.End),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF33CCFF)
                                    )
                                ) {
                                    Text("Close", color = Color(0xFF05070B), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // All Installed Applications Drawer Modal
                AppDrawerDialog(
                    isOpen = showAppDrawer,
                    onClose = { showAppDrawer = false }
                )

                // Privacy Policy screen
                if (showPrivacyPolicy) {
                    PrivacyPolicyScreen(
                        onNavigateBack = { showPrivacyPolicy = false }
                    )
                }

                // First-run onboarding wizard
                if (showOnboarding) {
                    OnboardingScreen(
                        onComplete = { completeOnboarding() },
                        onDismiss = { completeOnboarding() }
                    )
                }
            }
        }
    }

    private var showPrivacyPolicy by mutableStateOf(false)
    private var showOnboarding by mutableStateOf(false)

    private fun openPrivacyPolicy() {
        showPrivacyPolicy = true
    }

    private fun completeOnboarding() {
        showOnboarding = false
        uiPrefs.edit().putBoolean("onboarding_complete", true).apply()
        analyticsLogEvent("onboarding_complete", "method" to "welcome_button")
    }

    private fun logTerminal(direction: String, text: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        terminalLogs.add(TerminalLog(direction, text, timeStr))
        if (terminalLogs.size > 200) {
            terminalLogs.removeAt(0)
        }
    }

    private fun showPreviousCrashReport() {
        try {
            val file = File(filesDir, "last_crash.txt")
            if (!file.exists()) return
            val report = file.readText().trim().take(3000)
            file.renameTo(File(filesDir, "last_crash_read.txt"))
            android.util.Log.e("SportageOBD", "Previous crash report:\n$report")
            logTerminal("ERR", "=== PREVIOUS APP CRASH (build $APP_BUILD_TAG) ===")
            report.lineSequence().take(24).forEach { line ->
                logTerminal("ERR", line.take(160))
            }
            logTerminal("ERR", "=== END OF CRASH REPORT ===")
            // Show the crash reason immediately as a dialog so it cannot be missed -
            // no need to dig through the terminal tab. Root cause first: Android wraps the
            // real reason in "Caused by:" sections below the generic headline, so put the
            // deepest cause at the top of the dialog.
            val lastCause = report.lastIndexOf("Caused by:")
            previousCrashReport = if (lastCause >= 0) {
                report.substring(lastCause).trim() + "\n\n---- full trace ----\n\n" + report
            } else {
                report
            }
        } catch (e: Exception) {
            android.util.Log.w("SportageOBD", "crash report read failed", e)
        }
    }

    private fun promptSetDefaultLauncher() {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS)
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(fallback)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open Home Settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshPairedDevices() {
        pairedDevicesList.clear()
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                pairedDevicesList.addAll(adapter.bondedDevices)
            }
        } catch (_: Exception) {}
    }

    private fun connectToDevice(device: BluetoothDevice, ble: Boolean = false) {
        // Single-flight guard: rapid double taps (or taps on several rows while the state
        // flow has not caught up yet) used to fire several concurrent connects and pair
        // attempts at once. Only one connect may be in flight.
        if (viewModel.connectionState.value is ConnectionState.Connecting) {
            logTerminal("INFO", "A connection attempt is already in progress - please wait.")
            return
        }
        try {
            if (viewModel.isDemoMode.value) {
                viewModel.setDemoMode(false)
            }
            logTerminal("INFO", "Connecting to ${BluetoothDeviceInfo.label(this, device)}${if (ble) " via BLE GATT" else ""} through the background OBD service...")
            // Starts (if needed) and binds ObdService, which owns the transport link and polling loop,
            // so telemetry keeps streaming after this activity is backgrounded or destroyed.
            viewModel.connectDevice(device, ble)
            analyticsLogObdConnection(
                success = true,
                method = if (ble) "ble" else "classic"
            )
        } catch (e: Exception) {
            // Nothing in the connect path may ever kill the process; surface it instead.
            logTerminal("ERR", "Connection request failed: ${e.localizedMessage ?: "unexpected error"}")
            Toast.makeText(this, "Connection request failed: ${e.localizedMessage ?: "unexpected error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun disconnectDevice() {
        try {
            viewModel.disconnect()
        } catch (e: Exception) {
            logTerminal("ERR", "Disconnect failed: ${e.localizedMessage ?: "unexpected error"}")
        }
        logTerminal("INFO", "Disconnected from adapter.")
        liveSpeed = null
        liveRpm = null
        liveCoolant = null
        liveVoltage = null
        measuredHz = null
    }

    private fun sendTerminalCommand(command: String) {
        lifecycleScope.launch {
            logTerminal("TX", command)
            try {
                if (viewModel.isConnected()) {
                    logTerminal("RX", viewModel.sendRawCommand(command))
                } else {
                    logTerminal("ERR", "Socket is not connected. Connect an adapter first.")
                }
            } catch (e: Exception) {
                logTerminal("ERR", e.message ?: "Command timeout or communication error")
            }
        }
    }

    private fun scanDtcCodes() {
        if (!viewModel.isConnected() && !viewModel.isDemoMode.value) {
            Toast.makeText(this, "Connect to OBD adapter before scanning DTCs", Toast.LENGTH_SHORT).show()
            analyticsLogEvent("dtc_scan", "aborted" to "not_connected")
            return
        }
        lifecycleScope.launch {
            logTerminal("TX", "03 (Read Stored Diagnostic Trouble Codes)")
            val found = viewModel.scanDtcNow()
            val hasCritical = found.any { it.severity == com.sportage.obd.obd.DtcSeverity.CRITICAL }
            analyticsLogDtcScan(found.size, hasCritical)
            if (found.isEmpty()) {
                Toast.makeText(this@MainActivity, "No Diagnostic Trouble Codes detected. ECU clean.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Found ${found.size} Diagnostic Trouble Code(s)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun clearDtcCodes() {
        if (!viewModel.isConnected() && !viewModel.isDemoMode.value) {
            Toast.makeText(this, "Connect to OBD adapter before clearing DTCs", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            logTerminal("TX", "04 (Clear Diagnostic Trouble Codes)")
            if (viewModel.clearDtcNow()) {
                Toast.makeText(this@MainActivity, "ECU Codes Cleared. Readiness monitors reset.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Clear codes failed: adapter did not acknowledge", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleDemoMode(enabled: Boolean) {
        viewModel.setDemoMode(enabled)
        if (enabled) {
            logTerminal("INFO", "Bench demo mode enabled: virtual Sportage ECU streaming.")
        } else {
            logTerminal("INFO", "Bench demo mode disabled.")
            liveSpeed = null
            liveRpm = null
            liveCoolant = null
            liveVoltage = null
            measuredHz = null
        }
    }

    private fun togglePausePolling() {
        val paused = viewModel.togglePausePolling()
        val message = if (paused) "OBD Polling Paused (Ignition Off Mode)" else "OBD Polling Resumed"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun startTripTicker() {
        lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                if (isTripActive) {
                    tripDurationSeconds = (System.currentTimeMillis() - tripStartTime) / 1000
                    val currentSpd = liveSpeed ?: currentGpsSpeed ?: 0
                    if (currentSpd > tripMaxSpeed) tripMaxSpeed = currentSpd
                    if (currentSpd > 0) {
                        // distance = speed * time
                        tripDistanceKm += (currentSpd.toDouble() / 3600.0)
                        if (tripDurationSeconds > 0) {
                            tripAvgSpeed = ((tripDistanceKm / (tripDurationSeconds / 3600.0))).toInt()
                        }
                    }
                }
            }
        }
    }

    private fun resetTrip() {
        // Persist the completed trip for the fuel-history page before clearing it.
        if (tripDistanceKm > 0.5) {
            val telemetry = viewModel.telemetry.value
            val fuelUsed = (telemetry?.fuelUsedLiters ?: 0f) * (telemetry?.fuelCalibrationFactor ?: 1f)
            val avgL100 = if (fuelUsed > 0.05) fuelUsed / tripDistanceKm * 100.0 else null
            TripHistoryStore(this).addTrip(
                TripRecord(
                    dateMs = System.currentTimeMillis(),
                    distanceKm = tripDistanceKm,
                    durationSeconds = tripDurationSeconds,
                    fuelLiters = fuelUsed.toDouble(),
                    avgL100 = avgL100,
                    maxSpeedKmh = tripMaxSpeed,
                    avgSpeedKmh = tripAvgSpeed
                )
            )
            viewModel.resetTripFuel()
        }
        tripDistanceKm = 0.0
        tripStartTime = System.currentTimeMillis()
        tripDurationSeconds = 0L
        tripMaxSpeed = liveSpeed ?: 0
        tripAvgSpeed = 0
        Toast.makeText(this, "Trip computer reset", Toast.LENGTH_SHORT).show()
    }

    private fun initLocationTracking() {
        try {
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                val lm = locationManager ?: return
                // Some head units have no NETWORK provider; approximate-only permission
                // can also reject GPS. Neither should prevent the other provider working.
                runCatching { lm.removeUpdates(this) }
                val providers = lm.getProviders(true).filter {
                    it == LocationManager.NETWORK_PROVIDER ||
                        (hasFine && it == LocationManager.GPS_PROVIDER)
                }
                gpsStatus = if (providers.isEmpty()) "Enable device location" else "Waiting for location"
                val best = providers.mapNotNull {
                    runCatching { lm.getLastKnownLocation(it) }.getOrNull()
                }.maxByOrNull { it.time }
                if (best != null) {
                    currentLat = best.latitude
                    currentLon = best.longitude
                    gpsStatus = "Last known location"
                }

                for (provider in providers) {
                    runCatching { lm.requestLocationUpdates(provider, 2000L, 5f, this) }
                }
            } else gpsStatus = "Location permission needed"
        } catch (_: SecurityException) { gpsStatus = "Location permission needed" }
        catch (_: Exception) { gpsStatus = "Waiting for location" }
    }

    override fun onLocationChanged(location: Location) {
        if (!com.sportage.obd.map.TrackerMapPolicy.valid(location.latitude, location.longitude)) return
        currentLat = location.latitude
        currentLon = location.longitude
        gpsCourseBearing = if (location.hasBearing()) location.bearing else gpsCourseBearing
        gpsSpeedMs = if (location.hasSpeed()) location.speed else 0f
        currentGpsSpeed = if (location.hasSpeed()) (location.speed * 3.6f).toInt() else null
        updateEffectiveBearing()
        gpsStatus = (if (location.provider == LocationManager.GPS_PROVIDER) "GPS" else "Network location") +
            (if (location.hasAccuracy()) " · ±${location.accuracy.toInt()} m" else "")
        maybeRefreshWeather()
    }

    /**
     * Compass-driven heading. Google-Maps behavior: the location arrow follows the
     * device's magnetic orientation while stationary (so turning the phone rotates
     * the arrow) and switches to the more accurate GPS course once the vehicle is
     * actually moving.
     */
    private val headingSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            // TYPE_ROTATION_VECTOR delivers a quaternion, not gravity/magnetic arrays.
            // getRotationMatrix() would read the null magnetometer array and crash
            // (NullPointerException "Attempt to read from null array" on rotation).
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val r = rotationMatrix
            // The matrix rows are the world-axis components of each device axis:
            // row 0 = east, row 1 = north (row 2 = up). The horizontal heading of any
            // device axis is atan2(east, north) of its world vector.
            fun headingOf(ax: Float, ay: Float, az: Float): Pair<Float, Float>? {
                val east = r[0] * ax + r[1] * ay + r[2] * az
                val north = r[3] * ax + r[4] * ay + r[5] * az
                val horiz = kotlin.math.hypot(east.toDouble(), north.toDouble())
                if (horiz < 0.35) return null // axis points too vertically to give a heading
                val deg = ((Math.toDegrees(Math.atan2(east.toDouble(), north.toDouble())).toFloat() % 360f) + 360f) % 360f
                return deg to horiz.toFloat()
            }

            // Candidate 1: the UI "up" axis (top edge of the screen content). Correct
            // when the phone lies flat or is tilted back, like a dashboard mount.
            val uiUp = when (runCatching { windowManager.defaultDisplay.rotation }.getOrDefault(Surface.ROTATION_0)) {
                Surface.ROTATION_90 -> floatArrayOf(-1f, 0f, 0f)
                Surface.ROTATION_180 -> floatArrayOf(0f, -1f, 0f)
                Surface.ROTATION_270 -> floatArrayOf(1f, 0f, 0f)
                else -> floatArrayOf(0f, 1f, 0f)
            }
            val upHeading = headingOf(uiUp[0], uiUp[1], uiUp[2])
            // Candidate 2: the back of the phone (the direction the user looks "through"
            // the screen). Correct when the phone is upright, like a windscreen mount.
            // Without this, an upright landscape phone has a near-vertical UI-up axis
            // and the arrow freezes - the map arrow stopped rotating in landscape.
            val backHeading = headingOf(0f, 0f, -1f)

            val raw = when {
                upHeading != null && (backHeading == null || upHeading.second >= backHeading.second) -> upHeading.first
                backHeading != null -> backHeading.first
                else -> return // no usable axis; keep the last heading
            }
            // Low-pass across the 0/360 wraparound so the arrow does not jitter.
            compassAzimuth = compassAzimuth?.let { smoothAngle(it, raw, 0.25f) } ?: raw
            updateEffectiveBearing()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun smoothAngle(previous: Float, next: Float, alpha: Float): Float {
        val delta = ((next - previous + 540f) % 360f) - 180f
        return (previous + alpha * delta + 360f) % 360f
    }

    /** GPS course while driving, compass orientation while stationary. */
    private fun updateEffectiveBearing() {
        currentBearing = when {
            gpsSpeedMs > 1.5f && gpsCourseBearing != null -> gpsCourseBearing!!
            compassAzimuth != null -> compassAzimuth!!
            gpsCourseBearing != null -> gpsCourseBearing!!
            else -> currentBearing
        }
    }

    /**
     * Fetches current conditions from Open-Meteo (free, no API key) when the position
     * has moved > 5 km or the last sample is older than 30 minutes. Weather codes per
     * https://open-meteo.com/en/docs.
     */
    private fun maybeRefreshWeather() {
        val now = System.currentTimeMillis()
        val moved = Math.hypot(currentLat - lastWeatherLat, currentLon - lastWeatherLon) > 0.05
        if (!moved && now - lastWeatherFetchAtMs < 30 * 60 * 1000L) return
        lastWeatherFetchAtMs = now
        lastWeatherLat = currentLat
        lastWeatherLon = currentLon
        weatherScope?.launch {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=" +
                    "%.4f&longitude=%.4f&current=temperature_2m,weather_code".format(currentLat, currentLon)
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build()
                val request = okhttp3.Request.Builder().url(url)
                    .header("User-Agent", "SportageOBD/" + APP_BUILD_TAG).build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@launch
                    val body = org.json.JSONObject(resp.body?.string() ?: return@launch)
                    val current = body.optJSONObject("current") ?: return@launch
                    val temp = current.optDouble("temperature_2m", Double.NaN)
                    val code = current.optInt("weather_code", -1)
                    if (!temp.isNaN()) weatherTempC = "%.0f°C".format(temp)
                    weatherText = when (code) {
                        0 -> "Clear sky"; 1 -> "Mainly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
                        45, 48 -> "Fog"; 51, 53, 55 -> "Drizzle"; 56, 57 -> "Freezing drizzle"
                        61, 63, 65 -> "Rain"; 66, 67 -> "Freezing rain"; 71, 73, 75 -> "Snow"
                        77 -> "Snow grains"; 80, 81, 82 -> "Rain showers"; 85, 86 -> "Snow showers"
                        95 -> "Thunderstorm"; 96, 99 -> "Thunderstorm w/ hail"
                        else -> null
                    }
                }
            } catch (_: Exception) {
                // Offline or blocked: leave the last sample in place.
            }
        }
    }

    override fun onProviderEnabled(provider: String) { initLocationTracking() }
    override fun onProviderDisabled(provider: String) { initLocationTracking() }
    @Deprecated("Required by LocationListener on Android 9/10")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onResume() {
        super.onResume()
        refreshPairedDevices()
        initLocationTracking()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensor ->
            sensorManager?.registerListener(headingSensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onStop() {
        runCatching { locationManager?.removeUpdates(this) }
        runCatching { sensorManager?.unregisterListener(headingSensorListener) }
        sensorManager = null
        currentGpsSpeed = null
        gpsStatus = "Location paused"
        super.onStop()
    }

    // --- Analytics helper ---

    /**
     * Logs an anonymous analytics event to Firebase Analytics.
     * No personal data, no device identifiers beyond what Firebase automatically
     * collects. Analytics is always-on in this app but can be gated by user
     * consent in a future update.
     *
     * All context values are primitives or short strings; nothing that identifies
     * the user, their vehicle VIN, or their location is sent.
     */
    private fun analyticsLogEvent(eventName: String, vararg parameters: Pair<String, Any?>) {
        try {
            val bundle = bundleOf(*parameters.map { (k, v) -> k to (v?.toString() ?: "null") }.toTypedArray())
            (application as? SportageObdApp)?.firebaseAnalytics?.logEvent(eventName, bundle)
        } catch (_: Exception) {
            // Analytics must never break the app.
        }
    }

    /** Convenience: track an OBD connection attempt result. */
    private fun analyticsLogObdConnection(success: Boolean, method: String = "unknown") {
        analyticsLogEvent(
            "obd_connection",
            "success" to success,
            "method" to method
        )
    }

    /** Convenience: track a DTC scan completion. */
    private fun analyticsLogDtcScan(dtcCount: Int, hasCritical: Boolean) {
        analyticsLogEvent(
            "dtc_scan",
            "dtc_count" to dtcCount,
            "has_critical" to hasCritical
        )
    }

    /** Convenience: track launcher page navigation. */
    private fun analyticsLogLauncherPage(page: Int) {
        analyticsLogEvent("launcher_page", "page" to page)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            refreshPairedDevices()
            initLocationTracking()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // ObdService owns the RFCOMM socket and the polling loop, so the link and telemetry
        // survive this activity being backgrounded or destroyed.
        runCatching { locationManager?.removeUpdates(this) }
        weatherScope.cancel()
    }
}
