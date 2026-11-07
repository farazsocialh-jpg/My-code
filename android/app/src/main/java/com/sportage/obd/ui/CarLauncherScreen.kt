package com.sportage.obd.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import com.sportage.obd.map.HybridLocationMap
import androidx.compose.ui.platform.testTag
import com.sportage.obd.map.NavigationLauncher
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.media.MediaAppInfo
import com.sportage.obd.media.MediaApps
import com.sportage.obd.media.NowPlayingController
import com.sportage.obd.media.chosenMusicApp
import com.sportage.obd.media.saveChosenMusicApp
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.DtcSeverity
import com.sportage.obd.service.ActiveAlert
import com.sportage.obd.service.AlertSeverity
import com.sportage.obd.service.AlertThresholds
import com.sportage.obd.ui.theme.*
import com.sportage.obd.ui.boards.BoardData
import com.sportage.obd.ui.boards.BoardScreen
import com.sportage.obd.ui.boards.PrefsBoardStore
import com.sportage.obd.viewmodel.ObdViewModel
import kotlinx.coroutines.launch
import java.util.Locale

/** Shows a dialog with all installed music apps for the user to choose from. */
private fun showMusicAppPicker(context: Context) {
    val apps = MediaApps.musicApps(context)
    if (apps.isEmpty()) {
        Toast.makeText(context, "No music apps found on this device.", Toast.LENGTH_LONG).show()
        return
    }
    val chosenApp = chosenMusicApp(context)
    val defaultIdx = if (chosenApp != null) apps.indexOfFirst { it.packageName == chosenApp } else 0
    var selectedIdx = defaultIdx
    android.app.AlertDialog.Builder(context)
        .setTitle("Select Music App")
        .setSingleChoiceItems(
            apps.map { it.label }.toTypedArray(),
            defaultIdx,
            { _, which -> selectedIdx = which }
        )
        .setPositiveButton("Open") { _, _ ->
            val selected = apps[selectedIdx]
            runCatching { context.startActivity(selected.launchIntent) }
            // Save the user's choice for future launches
            saveChosenMusicApp(context, selected.packageName)
        }
        .setNegativeButton("Cancel") { _, _ -> }
        .show()
}

data class RadioStation(val freq: Float, val name: String, val genre: String)

val SPORTAGE_RADIO_PRESETS = listOf(
    RadioStation(91.7f, "QBS Radio", "English Hits"),
    RadioStation(94.0f, "Al Rayyan FM", "Arabic Culture"),
    RadioStation(97.5f, "Doha Hit FM", "Top 40"),
    RadioStation(101.4f, "Sportage Drive", "Automotive Stereo"),
    RadioStation(104.8f, "BBC World Service", "Global News"),
    RadioStation(107.6f, "Oryx FM", "French & Jazz")
)

@Composable
fun CarLauncherScreen(
    connectionState: ConnectionState,
    measuredHz: Float? = null,
    speed: Int?,
    rpm: Int?,
    coolant: Int?,
    intakeTemp: Int?,
    fuelLevel: Int?,
    throttle: Float?,
    load: Float?,
    voltage: Float?,
    gear: Int,
    transmissionGear: Int? = null,
    weatherTempC: String? = null,
    weatherText: String? = null,
    speedHistory: List<Pair<Long, Int>>,
    rpmHistory: List<Pair<Long, Int>>,
    currentLat: Double = 25.2854,
    currentLon: Double = 51.5310,
    currentBearing: Float = 0f,
    activePage: Int,
    onNavigatePage: (Int) -> Unit,
    onOpenBluetoothClick: () -> Unit,
    dtcCodes: List<DtcItem>,
    isScanningDtc: Boolean,
    onScanDtc: () -> Unit,
    onClearDtc: () -> Unit,
    tripDistanceKm: Double,
    tripDurationSeconds: Long,
    tripMaxSpeed: Int,
    tripAvgSpeed: Int,
    onResetTrip: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    isDemoMode: Boolean,
    onToggleDemoMode: (Boolean) -> Unit,
    isPollingPaused: Boolean = false,
    onTogglePausePolling: () -> Unit = {},
    onOpenAppDrawer: () -> Unit = {},
    viewModel: ObdViewModel? = null,
    autoNightDim: Boolean = true,
    nightDimLevel: Float = 0.55f,
    onSetAutoNightDim: (Boolean) -> Unit = {},
    onSetNightDimLevel: (Float) -> Unit = {},
    gpsStatus: String? = null,
    themeId: String = "cockpit-dark",
    accentOverride: String? = null,
    onSetThemeId: (String) -> Unit = {},
    onSetAccentOverride: (String?) -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp
    val activeAlerts = viewModel?.activeAlerts?.collectAsState()?.value ?: emptyList()

    var speedUnit by remember { mutableStateOf("km/h") }
    val displaySpeed = if (speed != null) {
        if (speedUnit == "mph") (speed * 0.621371).toInt() else speed
    } else null

    // Customizable swipeable boards: live values bundled once for every widget.
    val boardStore = remember { PrefsBoardStore(context) }
    val boardTelemetry = viewModel?.telemetry?.collectAsState()?.value
    val boardData = BoardData(
        speed = displaySpeed,
        speedUnit = speedUnit,
        rpm = rpm,
        coolant = coolant,
        intakeTemp = intakeTemp,
        fuelLevel = fuelLevel,
        throttle = throttle,
        load = load,
        voltage = voltage,
        gear = gear,
        mafGs = boardTelemetry?.mafGs,
        timingAdvanceDeg = boardTelemetry?.timingAdvanceDeg,
        currentLat = currentLat,
        currentLon = currentLon,
        gpsStatus = gpsStatus,
        tripDistanceKm = tripDistanceKm,
        tripDurationSeconds = tripDurationSeconds,
        connectionLabel = when (connectionState) {
            is ConnectionState.Connected -> "Connected · ${connectionState.deviceName}"
            is ConnectionState.Connecting -> "Connecting"
            is ConnectionState.Error -> "Error · reconnect"
            ConnectionState.Disconnected -> "Disconnected"
        },
        connectionOk = connectionState is ConnectionState.Connected,
        speedHistory = speedHistory,
        rpmHistory = rpmHistory
    )

    // Radio State
    var radioFreq by remember { mutableStateOf(101.4f) }
    var radioStationName by remember { mutableStateOf("Sportage Drive FM") }
    var isMediaPlaying by remember { mutableStateOf(true) }

    fun sendMediaKey(keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {}
    }

    fun launchLocalMusicApp() {
        val candidates = listOf(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
            Intent(Intent.ACTION_VIEW).apply { type = "audio/*" },
            Intent("android.intent.action.MUSIC_PLAYER")
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        // Show app picker dialog for user to choose music app
        showMusicAppPicker(context)
    }

    fun launchHeadUnitRadioApp() {
        val radioPackages = listOf(
            "com.ts.main.radio",
            "com.syu.radio",
            "com.microntek.radio",
            "com.android.fmradio"
        )
        for (pkg in radioPackages) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        try {
            val generic = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(generic)
        } catch (_: Exception) {}
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground())
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isLandscape = screenWidth >= screenHeight
        val aspectRatio = if (screenHeight.value > 0) screenWidth.value / screenHeight.value else 1.7f
        if (isLandscape) {
            // LANDSCAPE: Adaptive Left Slim Dock + Cockpit Viewport (Head Units 1024x600, 1280x720, 1920x720 & Phone Landscape)
            Row(modifier = Modifier.fillMaxSize()) {
                CockpitNavigation(activePage, onNavigatePage, onOpenAppDrawer, rail = true)

                // Cockpit Viewport
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    CockpitStatusStrip(
                    connectionState = connectionState,
                    isDemoMode = isDemoMode,
                    onOpenBluetoothClick = onOpenBluetoothClick
                )

                    ActiveAlertBanner(activeAlerts)

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (activePage) {
                            0 -> PermanentHomeSplitView(
                                isLandscape = true,
                                aspectRatio = aspectRatio,
                                currentLat = currentLat,
                                currentLon = currentLon,
                                gpsStatus = gpsStatus,
                                speed = displaySpeed,
                                speedUnit = speedUnit,
                                onToggleSpeedUnit = { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" },
                                rpm = rpm,
                                coolant = coolant,
                                voltage = voltage,
                                load = load,
                                throttle = throttle,
                                gear = gear,
                                connectionState = connectionState,
                                measuredHz = measuredHz,
                                isDemoMode = isDemoMode,
                                onOpenNavigation = {
                                    if (!NavigationLauncher.open(context, currentLat, currentLon)) {
                                        Toast.makeText(context, "No compatible navigation app could open. Install or enable a maps app.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                            1 -> GaugesAndGraphsPage(
                                speed = displaySpeed,
                                speedUnit = speedUnit,
                                rpm = rpm,
                                coolant = coolant,
                                intakeTemp = intakeTemp,
                                fuelLevel = fuelLevel,
                                throttle = throttle,
                                load = load,
                                voltage = voltage,
                                gear = gear,
                                speedHistory = speedHistory,
                                rpmHistory = rpmHistory,
                                viewModel = viewModel
                            )
                            6 -> SensorExplorer(viewModel)
                            7 -> BoardScreen(data = boardData, store = boardStore)
                            2 -> DiagnosticsPage(
                                dtcCodes = dtcCodes,
                                isScanning = isScanningDtc,
                                onScan = onScanDtc,
                                onClear = onClearDtc,
                                connectionState = connectionState,
                                viewModel = viewModel
                            )
                            3 -> TripComputerPage(
                                distanceKm = tripDistanceKm,
                                durationSeconds = tripDurationSeconds,
                                maxSpeed = tripMaxSpeed,
                                avgSpeed = tripAvgSpeed,
                                currentSpeed = displaySpeed ?: 0,
                                speedUnit = speedUnit,
                                currentLat = currentLat,
                                currentLon = currentLon,
                                onReset = onResetTrip,
                                viewModel = viewModel
                            )
                            8 -> FuelAnalyticsPage(
                                distanceKm = tripDistanceKm,
                                tripDurationSeconds = tripDurationSeconds,
                                maxSpeedKmh = tripMaxSpeed,
                                avgSpeedKmh = tripAvgSpeed,
                                onResetTrip = onResetTrip,
                                viewModel = viewModel
                            )
                            9 -> AdvancedDiagnosticsPage(
                                dtcCodes = dtcCodes,
                                viewModel = viewModel
                            )
                            4 -> MediaRadioPage(
                                radioFreq = radioFreq,
                                onFreqChange = { radioFreq = it },
                                radioStationName = radioStationName,
                                onStationChange = { name, freq ->
                                    radioStationName = name
                                    radioFreq = freq
                                },
                                isMediaPlaying = isMediaPlaying,
                                onTogglePlayPause = {
                                    isMediaPlaying = !isMediaPlaying
                                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                },
                                onPrev = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                                onNext = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) },
                                onLaunchNativeRadio = { launchHeadUnitRadioApp() },
                                onLaunchLocalMusic = { launchLocalMusicApp() }
                            )
                            5 -> SettingsPage(
                                speedUnit = speedUnit,
                                onToggleSpeedUnit = { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" },
                                onSetDefaultLauncher = onSetDefaultLauncher,
                                isDemoMode = isDemoMode,
                                onToggleDemoMode = onToggleDemoMode,
                                onOpenBluetoothSettings = {
                                    try {
                                        context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    } catch (_: Exception) {}
                                },
                                isPollingPaused = isPollingPaused,
                                onTogglePausePolling = onTogglePausePolling,
                                onOpenAppDrawer = onOpenAppDrawer,
                                viewModel = viewModel,
                                autoNightDim = autoNightDim,
                                nightDimLevel = nightDimLevel,
                                onSetAutoNightDim = onSetAutoNightDim,
                                onSetNightDimLevel = onSetNightDimLevel,
                                themeId = themeId,
                                accentOverride = accentOverride,
                                onSetThemeId = onSetThemeId,
                                onSetAccentOverride = onSetAccentOverride,
                                onOpenPrivacyPolicy = onOpenPrivacyPolicy
                            )
                        }
                    }
                    QuickActionDock(
                        onMusic = { launchLocalMusicApp() }, onMediaKey = { sendMediaKey(it) },
                        onPhone = { openPhoneDialer(context) }, onApps = onOpenAppDrawer,
                        onBluetooth = onOpenBluetoothClick,
                        logging = viewModel?.isCsvLogging?.collectAsState()?.value ?: false,
                        canLog = viewModel != null && connectionState is ConnectionState.Connected && !isDemoMode,
                        onLogging = {
                            if (viewModel?.isCsvLogging?.value == true) viewModel.stopCsvLogging()
                            else if (viewModel?.startCsvLogging() != true) Toast.makeText(context, "Connect an OBD adapter before recording.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        } else {
            // PORTRAIT: Full-width Top Status Strip + Viewport + Bottom Navigation Bar (e.g. Samsung Galaxy S24 Ultra Phone Mount)
            Column(modifier = Modifier.fillMaxSize()) {
                CockpitStatusStrip(
                    connectionState = connectionState,
                    isDemoMode = isDemoMode,
                    onOpenBluetoothClick = onOpenBluetoothClick
                )

                ActiveAlertBanner(activeAlerts)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activePage) {
                        0 -> PermanentHomeSplitView(
                            isLandscape = false,
                            aspectRatio = aspectRatio,
                            currentLat = currentLat,
                            currentLon = currentLon,
                            gpsStatus = gpsStatus,
                            speed = displaySpeed,
                            speedUnit = speedUnit,
                            onToggleSpeedUnit = { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" },
                            rpm = rpm,
                            coolant = coolant,
                            voltage = voltage,
                            load = load,
                            throttle = throttle,
                            intake = intakeTemp,
                            fuel = fuelLevel,
                            transmissionGear = transmissionGear,
                            gear = gear,
                            weatherTempC = weatherTempC,
                            weatherText = weatherText,
                            currentBearing = currentBearing,
                            connectionState = connectionState,
                            measuredHz = measuredHz,
                            isDemoMode = isDemoMode,
                            onOpenNavigation = {
                                if (!NavigationLauncher.open(context, currentLat, currentLon)) {
                                    Toast.makeText(context, "No compatible navigation app could open. Install or enable a maps app.", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                        1 -> GaugesAndGraphsPage(
                            speed = displaySpeed,
                            speedUnit = speedUnit,
                            rpm = rpm,
                            coolant = coolant,
                            intakeTemp = intakeTemp,
                            fuelLevel = fuelLevel,
                            throttle = throttle,
                            load = load,
                            voltage = voltage,
                            gear = gear,
                            speedHistory = speedHistory,
                            rpmHistory = rpmHistory,
                            viewModel = viewModel
                        )
                        6 -> SensorExplorer(viewModel)
                        7 -> BoardScreen(data = boardData, store = boardStore)
                            2 -> DiagnosticsPage(
                            dtcCodes = dtcCodes,
                            isScanning = isScanningDtc,
                            onScan = onScanDtc,
                            onClear = onClearDtc,
                            connectionState = connectionState,
                            viewModel = viewModel
                        )
                        3 -> TripComputerPage(
                            distanceKm = tripDistanceKm,
                            durationSeconds = tripDurationSeconds,
                            maxSpeed = tripMaxSpeed,
                            avgSpeed = tripAvgSpeed,
                            currentSpeed = displaySpeed ?: 0,
                            speedUnit = speedUnit,
                            currentLat = currentLat,
                            currentLon = currentLon,
                            onReset = onResetTrip,
                            viewModel = viewModel
                        )
                        8 -> FuelAnalyticsPage(
                            distanceKm = tripDistanceKm,
                            tripDurationSeconds = tripDurationSeconds,
                            maxSpeedKmh = tripMaxSpeed,
                            avgSpeedKmh = tripAvgSpeed,
                            onResetTrip = onResetTrip,
                            viewModel = viewModel
                        )
                        9 -> AdvancedDiagnosticsPage(
                            dtcCodes = dtcCodes,
                            viewModel = viewModel
                        )
                        4 -> MediaRadioPage(
                            radioFreq = radioFreq,
                            onFreqChange = { radioFreq = it },
                            radioStationName = radioStationName,
                            onStationChange = { name, freq ->
                                radioStationName = name
                                radioFreq = freq
                            },
                            isMediaPlaying = isMediaPlaying,
                            onTogglePlayPause = {
                                isMediaPlaying = !isMediaPlaying
                                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                            },
                            onPrev = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                            onNext = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) },
                            onLaunchNativeRadio = { launchHeadUnitRadioApp() },
                            onLaunchLocalMusic = { launchLocalMusicApp() }
                        )
                        5 -> SettingsPage(
                            speedUnit = speedUnit,
                            onToggleSpeedUnit = { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" },
                            onSetDefaultLauncher = onSetDefaultLauncher,
                            isDemoMode = isDemoMode,
                            onToggleDemoMode = onToggleDemoMode,
                            onOpenBluetoothSettings = {
                                try {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) {}
                            },
                            isPollingPaused = isPollingPaused,
                            onTogglePausePolling = onTogglePausePolling,
                            onOpenAppDrawer = onOpenAppDrawer,
                            viewModel = viewModel,
                            autoNightDim = autoNightDim,
                            nightDimLevel = nightDimLevel,
                            onSetAutoNightDim = onSetAutoNightDim,
                            onSetNightDimLevel = onSetNightDimLevel,
                            themeId = themeId,
                            accentOverride = accentOverride,
                            onSetThemeId = onSetThemeId,
                            onSetAccentOverride = onSetAccentOverride,
                            onOpenPrivacyPolicy = onOpenPrivacyPolicy
                        )
                    }
                }

                QuickActionDock(
                    onMusic = { launchLocalMusicApp() }, onMediaKey = { sendMediaKey(it) },
                    onPhone = { openPhoneDialer(context) }, onApps = onOpenAppDrawer,
                    onBluetooth = onOpenBluetoothClick,
                    logging = viewModel?.isCsvLogging?.collectAsState()?.value ?: false,
                    canLog = viewModel != null && connectionState is ConnectionState.Connected && !isDemoMode,
                    onLogging = {
                        if (viewModel?.isCsvLogging?.value == true) viewModel.stopCsvLogging()
                        else if (viewModel?.startCsvLogging() != true) Toast.makeText(context, "Connect an OBD adapter before recording.", Toast.LENGTH_SHORT).show()
                    }
                )
                CockpitNavigation(activePage, onNavigatePage, onOpenAppDrawer, rail = false)
            }
        }
    }
}

// =========================================================================
// HELPER: ACTIVE WARNING BANNER (threshold alerts)
// =========================================================================
@Composable
fun ActiveAlertBanner(activeAlerts: List<ActiveAlert>) {
    if (activeAlerts.isEmpty()) return
    val critical = activeAlerts.any { it.severity == AlertSeverity.CRITICAL }
    val color = if (critical) CriticalRed() else AmberWarning()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = activeAlerts.joinToString("  ·  ") { it.message },
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// =========================================================================
// HELPER: TOP COCKPIT STATUS STRIP
// =========================================================================
@Composable
fun PermanentHomeSplitView(
    isLandscape: Boolean,
    aspectRatio: Float = 1.7f,
    currentLat: Double,
    currentLon: Double,
    speed: Int?,
    speedUnit: String,
    onToggleSpeedUnit: () -> Unit,
    rpm: Int?,
    coolant: Int?,
    voltage: Float?,
    load: Float?,
    throttle: Float?,
    intake: Int? = null,
    fuel: Int? = null,
    transmissionGear: Int? = null,
    gear: Int,
    weatherTempC: String? = null,
    weatherText: String? = null,
    currentBearing: Float = 0f,
    connectionState: ConnectionState,
    measuredHz: Float?,
    isDemoMode: Boolean,
    onOpenNavigation: () -> Unit,
    gpsStatus: String? = null
) {
    if (isLandscape) {
        // Landscape: Adaptive Dominant Live Map Pane (approx 58-64%) + Persistent OBD Pane (approx 36-42%)
        val mapWeight = if (aspectRatio >= 2.0f) 0.64f else 0.58f
        val obdWeight = 1.0f - mapWeight

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(mapWeight).fillMaxHeight()) {
                LiveMapSurface(
                    currentLat = currentLat,
                    currentLon = currentLon,
                    currentBearing = currentBearing,
                    speed = speed,
                    speedUnit = speedUnit,
                    onOpenNavigation = onOpenNavigation,
                    gpsStatus = gpsStatus
                )
            }

            Box(modifier = Modifier.weight(obdWeight).fillMaxHeight()) {
                CockpitTelemetryPane(
                    speed = speed,
                    speedUnit = speedUnit,
                    onToggleSpeedUnit = onToggleSpeedUnit,
                    rpm = rpm,
                    coolant = coolant,
                    voltage = voltage,
                    load = load,
                    throttle = throttle,
                    intake = intake,
                    fuel = fuel,
                    transmissionGear = transmissionGear,
                    gear = gear,
                    weatherTempC = weatherTempC,
                    weatherText = weatherText,
                    connectionState = connectionState,
                    measuredHz = measuredHz,
                    isDemoMode = isDemoMode
                )
            }
        }
    } else {
        // Portrait: prioritize map height, retaining extra telemetry room at enlarged font scales.
        val portraitMapWeight = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f) 0.52f else 0.56f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(portraitMapWeight).fillMaxWidth()) {
                LiveMapSurface(
                    currentLat = currentLat,
                    currentLon = currentLon,
                    currentBearing = currentBearing,
                    speed = speed,
                    speedUnit = speedUnit,
                    onOpenNavigation = onOpenNavigation,
                    gpsStatus = gpsStatus
                )
            }

            Box(modifier = Modifier.weight(1f - portraitMapWeight).fillMaxWidth()) {
                CockpitTelemetryPane(
                    speed = speed,
                    speedUnit = speedUnit,
                    onToggleSpeedUnit = onToggleSpeedUnit,
                    rpm = rpm,
                    coolant = coolant,
                    voltage = voltage,
                    load = load,
                    throttle = throttle,
                    intake = intake,
                    fuel = fuel,
                    transmissionGear = transmissionGear,
                    gear = gear,
                    weatherTempC = weatherTempC,
                    weatherText = weatherText,
                    connectionState = connectionState,
                    measuredHz = measuredHz,
                    isDemoMode = isDemoMode
                )
            }
        }
    }
}

// =========================================================================
// SUB-VIEW: NATIVE LOCATION TRACKER
// =========================================================================
@Composable
fun LiveMapSurface(
    currentLat: Double, currentLon: Double, speed: Int?, speedUnit: String,
    onOpenNavigation: () -> Unit,
    gpsStatus: String? = null,
    currentBearing: Float = 0f
) {
    var speedAlertThreshold by remember { mutableIntStateOf(100) }
    val overspeed = speed != null && speedAlertThreshold > 0 && speed > speedAlertThreshold
    Surface(color = DarkSurface(), shape = RoundedCornerShape(16.dp), modifier = Modifier.testTag("home_map_card").fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.testTag("home_map_header").fillMaxWidth().padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Map", color = PrimaryText(), style = MaterialTheme.typography.titleMedium)
                    gpsStatus?.let { Text(it, color = SecondaryText(), style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                }
                FilledIconButton(
                    onClick = onOpenNavigation, modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = DarkSurfaceElevated(), contentColor = ElectricCyan()
                    )
                ) { Icon(Icons.Default.Navigation, "Open navigation app", Modifier.size(24.dp)) }
            }
            Box(Modifier.testTag("home_map_viewport").fillMaxWidth().weight(1f)) { HybridLocationMap(currentLat, currentLon, 0, currentBearing) }
            Row(
                Modifier.testTag("home_map_footer").fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        speedAlertThreshold = when (speedAlertThreshold) {
                            80 -> 100; 100 -> 120; 120 -> 140; 140 -> 0; else -> 80
                        }
                    }, modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = if (overspeed) CriticalRed() else SecondaryText())
                ) {
                    Icon(if (overspeed) Icons.Default.Warning else Icons.Default.NotificationsNone, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (speedAlertThreshold == 0) "Alert off" else "Alert · $speedAlertThreshold $speedUnit",
                        style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "${"%.4f".format(currentLat)}, ${"%.4f".format(currentLon)}",
                    modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = SecondaryText(), style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// =========================================================================
// SUB-VIEW: AUTHORITATIVE PRIMARY OBD TELEMETRY PANE
// =========================================================================
@Composable
fun GaugesAndGraphsPage(
    speed: Int?,
    speedUnit: String,
    rpm: Int?,
    coolant: Int?,
    intakeTemp: Int?,
    fuelLevel: Int?,
    throttle: Float?,
    load: Float?,
    voltage: Float?,
    gear: Int,
    speedHistory: List<Pair<Long, Int>>,
    rpmHistory: List<Pair<Long, Int>>,
    viewModel: ObdViewModel? = null
) {
    val liveTelemetry = viewModel?.telemetry?.collectAsState()?.value
    val metrics = listOf(
        PanelReading("Engine speed", rpm?.toString() ?: "--", "rpm", if ((rpm ?: 0) > 6500) CriticalRed() else PrimaryText()),
        PanelReading("Coolant", coolant?.toString() ?: "--", "°C", if ((coolant ?: 0) > 105) CriticalRed() else PrimaryText()),
        PanelReading("Intake air", intakeTemp?.toString() ?: "--", "°C"),
        PanelReading("Engine load", load?.let { "%.1f".format(it) } ?: "--", "%"),
        PanelReading("Throttle", throttle?.let { "%.1f".format(it) } ?: "--", "%"),
        PanelReading("Fuel level", fuelLevel?.toString() ?: "--", "%"),
        PanelReading("Battery", voltage?.let { "%.1f".format(it) } ?: "--", "V"),
        PanelReading("Gear estimate", if (gear == 0) "P/N" else "D$gear", "automatic"),
        PanelReading("Air flow", liveTelemetry?.mafGs?.let { "%.1f".format(it) } ?: "--", "g/s"),
        PanelReading("Timing advance", liveTelemetry?.timingAdvanceDeg?.let { "%.1f".format(it) } ?: "--", "°BTDC"),
        PanelReading("Short fuel trim", liveTelemetry?.shortTermFuelTrimPct?.let { "%+.1f".format(it) } ?: "--", "%",
            if (kotlin.math.abs(liveTelemetry?.shortTermFuelTrimPct ?: 0f) > 10f) CriticalRed() else PrimaryText()),
        PanelReading("Long fuel trim", liveTelemetry?.longTermFuelTrimPct?.let { "%+.1f".format(it) } ?: "--", "%",
            if (kotlin.math.abs(liveTelemetry?.longTermFuelTrimPct ?: 0f) > 10f) CriticalRed() else PrimaryText()),
        PanelReading("Instant economy", liveTelemetry?.fuelEconomyL100?.let { "%.1f".format(it) } ?: "--", "L/100 km"),
        PanelReading("Fuel used", liveTelemetry?.fuelUsedLiters?.let { "%.2f".format(it) } ?: "--", "liters")
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 720.dp) 4 else 2
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text("Gauges", color = PrimaryText(), style = MaterialTheme.typography.titleLarge)
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Surface(color = DarkSurface(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Engine history · 60 seconds", color = PrimaryText(), style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Speed · ${speed ?: "--"} $speedUnit", color = ElectricCyan(), style = MaterialTheme.typography.labelMedium)
                            Text("RPM · ${rpm ?: "--"}", color = WarningAmber(), style = MaterialTheme.typography.labelMedium)
                        }
                        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            // Draw-scope lambdas cannot call composables, so resolve theme colors first.
                            val plotShade = DeepShade()
                            val plotAccent = ElectricCyan()
                            val plotWarning = WarningAmber()
                            Canvas(Modifier.fillMaxSize().background(DarkBackground(), RoundedCornerShape(8.dp)).padding(8.dp)) {

                    val w = size.width
                    val h = size.height

                    // Grid lines
                    for (i in 1..4) {
                        val y = h * (i / 5f)
                        drawLine(
                            color = plotShade,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                    }

                    // Draw Speed Line (max 200 km/h)
                    if (speedHistory.size > 1) {
                        val path = Path()
                        speedHistory.forEachIndexed { idx, pair ->
                            val x = w * (idx.toFloat() / (speedHistory.size - 1))
                            val y = h - (h * (pair.second.coerceIn(0, 200) / 200f))
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = plotAccent, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                    }

                    // Draw RPM Line (max 7000 RPM)
                    if (rpmHistory.size > 1) {
                        val rpmPath = Path()
                        rpmHistory.forEachIndexed { idx, pair ->
                            val x = w * (idx.toFloat() / (rpmHistory.size - 1))
                            val y = h - (h * (pair.second.coerceIn(0, 7000) / 7000f))
                            if (idx == 0) rpmPath.moveTo(x, y) else rpmPath.lineTo(x, y)
                        }
                        drawPath(rpmPath, color = plotWarning, style = Stroke(width = 2f, cap = StrokeCap.Round))
                    }

                            }
                            if (speedHistory.size < 2 && rpmHistory.size < 2) {
                                Text("Waiting for readings", color = SecondaryText(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            items(metrics.size) { index -> MetricTile(metrics[index]) }
        }
    }
}
@Composable
private fun MetricTile(reading: PanelReading) {
    Surface(color = DarkSurface(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(120.dp)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(reading.label, color = SecondaryText(), style = MaterialTheme.typography.labelMedium,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(reading.value, color = reading.color, fontSize = if (reading.value.length > 6) 20.sp else 28.sp,
                fontWeight = FontWeight.Medium, maxLines = 1)
            Text(reading.unit, color = SecondaryText(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

private fun openPhoneDialer(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No phone dialer available", Toast.LENGTH_SHORT).show()
    }
}
