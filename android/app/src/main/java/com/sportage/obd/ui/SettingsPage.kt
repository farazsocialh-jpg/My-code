package com.sportage.obd.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    speedUnit: String,
    onToggleSpeedUnit: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    isDemoMode: Boolean,
    onToggleDemoMode: (Boolean) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    isPollingPaused: Boolean,
    onTogglePausePolling: () -> Unit,
    onOpenAppDrawer: () -> Unit,
    viewModel: com.sportage.obd.viewmodel.ObdViewModel?,
    autoNightDim: Boolean,
    nightDimLevel: Float,
    onSetAutoNightDim: (Boolean) -> Unit,
    onSetNightDimLevel: (Float) -> Unit,
    themeId: String,
    accentOverride: String?,
    onSetThemeId: (String) -> Unit,
    onSetAccentOverride: (String?) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenCrashReportSettings: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showClearDataConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkSurface()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Settings", color = PrimaryText(), fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Sportage OBD", color = SecondaryText(), fontSize = 12.sp)
                }
                IconButton(onClick = onOpenAppDrawer) {
                    Icon(Icons.Default.ArrowBack, "Back to launcher", tint = SecondaryText(), modifier = Modifier.size(24.dp))
                }
            }

            Surface(
                color = DarkBackground(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Info, "App info", tint = SecondaryText(), modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sportage OBD-II Intelligence", color = PrimaryText(), fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("OBD-II diagnostics + car launcher for Kia Sportage", color = SecondaryText(), fontSize = 12.sp)
                    }
                }
            }

            Text("Privacy & Safety", color = ElectricCyan(), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))

            SectionItem(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy Policy",
                subtitle = "In-app privacy policy and behavior overview",
                onClick = onOpenPrivacyPolicy
            )

            SectionItem(
                icon = Icons.Default.Security,
                title = "Data Safety",
                subtitle = "What this app collects and how it is used",
                onClick = {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open app info", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            SectionItem(
                icon = Icons.Default.CloudDownload,
                title = "Crash Reports",
                subtitle = "Share anonymous crash data to help us fix bugs",
                onClick = onOpenCrashReportSettings
            )

            Text("Launcher & Defaults", color = ElectricCyan(), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))

            SectionItem(
                icon = Icons.Default.ManageSearch,
                title = "Set as Default Launcher",
                subtitle = "Makes Sportage OBD the home screen on this device",
                onClick = onSetDefaultLauncher
            )

            Text("Diagnostics & Telemetry", color = ElectricCyan(), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))

            SectionItem(
                icon = Icons.Default.BugReport,
                title = "Diagnostic Codes (DTCs)",
                subtitle = "Scan and clear trouble codes from your vehicle\'s ECU",
                onClick = { /* handled by parent screen */ }
            )

            SectionItem(
                icon = Icons.Default.PieChart,
                title = "Trip & Fuel Analytics",
                subtitle = "Distance, duration, max speed, average speed, and fuel used",
                onClick = { /* handled by parent screen */ }
            )

            SectionItem(
                icon = Icons.Default.Sensors,
                title = "OBD Sensor Data",
                subtitle = "Live engine speed, coolant, fuel, throttle, load, and more",
                onClick = { /* handled by parent screen */ }
            )

            Text("Appearance", color = ElectricCyan(), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))

            SectionItem(
                icon = Icons.Default.DarkMode,
                title = "Night Dim",
                subtitle = "Auto-dim the cockpit at night",
                onClick = { onSetAutoNightDim(!autoNightDim) }
            )

            if (autoNightDim) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Dim level", color = PrimaryText(), fontSize = 14.sp)
                    Slider(
                        value = nightDimLevel,
                        onValueChange = onSetNightDimLevel,
                        valueRange = 0.3f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan(),
                            activeTrackColor = ElectricCyan(),
                            inactiveTrackColor = DarkSurfaceElevated()
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("%.0f%% dimmed".format(nightDimLevel * 100), color = SecondaryText(), fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .clickable { onToggleSpeedUnit() }
                    .background(DarkBackground())
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DirectionsCar, "Speed unit", tint = SecondaryText(), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Speed unit", color = PrimaryText(), fontSize = 14.sp)
                        Text(speedUnit, color = SecondaryText(), fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onToggleSpeedUnit, colors = ButtonDefaults.textButtonColors(contentColor = ElectricCyan())) {
                    Text(if (speedUnit == "km/h") "Switch to MPH" else "Switch to KM/H", fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .clickable { onToggleDemoMode(!isDemoMode) }
                    .background(DarkBackground())
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BugReport, "Demo mode", tint = SecondaryText(), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Demo mode", color = PrimaryText(), fontSize = 14.sp)
                        Text(if (isDemoMode) "Using simulated data" else "Using live Bluetooth data", color = SecondaryText(), fontSize = 12.sp)
                    }
                }
                Switch(checked = isDemoMode, onCheckedChange = onToggleDemoMode, colors = SwitchDefaults.colors(checkedTrackColor = ElectricCyan()))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .clickable { onTogglePausePolling() }
                    .background(DarkBackground())
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isPollingPaused) Icons.Default.Pause else Icons.Default.PlayArrow, "Polling", tint = SecondaryText(), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(if (isPollingPaused) "Polling paused" else "Streaming OBD data", color = PrimaryText(), fontSize = 14.sp)
                        Text(if (isPollingPaused) "Tap to resume" else "Tap to pause", color = SecondaryText(), fontSize = 12.sp)
                    }
                }
                Switch(checked = isPollingPaused, onCheckedChange = { onTogglePausePolling() }, colors = SwitchDefaults.colors(checkedTrackColor = WarningAmber()))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(DarkBackground())
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Theme", color = PrimaryText(), fontSize = 14.sp)
                Text(themeId.replaceFirstChar { it.uppercase() }, color = OnAccent(), fontSize = 14.sp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(DarkBackground())
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Accent", color = PrimaryText(), fontSize = 14.sp)
                Text(accentOverride ?: "none", color = OnAccent(), fontSize = 14.sp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(DarkBackground())
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Version", color = SecondaryText(), fontSize = 12.sp)
                    Text("0.2.0", color = PrimaryText(), fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Build", color = SecondaryText(), fontSize = 12.sp)
                    Text("debug", color = PrimaryText(), fontSize = 14.sp)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .clickable { showClearDataConfirm = true }
                    .background(CriticalRed().copy(alpha = 0.08f))
                    .border(BorderStroke(1.dp, CriticalRed().copy(alpha = 0.4f)))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteOutline, "Clear data", tint = CriticalRed(), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Clear Data / Reset Onboarding", color = CriticalRed(), fontSize = 14.sp)
                        Text("Clears onboarding and UI preferences. Your trip and DTC data stays in the app unless you also clear app storage.", color = CriticalRed().copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 2)
                    }
                }
                Icon(Icons.Default.ChevronRight, "Open", tint = CriticalRed(), modifier = Modifier.size(20.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenBluetoothSettings)
                    .background(DarkBackground())
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bluetooth, "Bluetooth settings", tint = SecondaryText(), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Bluetooth Settings", color = PrimaryText(), fontSize = 14.sp)
                }
                Icon(Icons.Default.ChevronRight, "Open", tint = SecondaryText(), modifier = Modifier.size(20.dp))
            }
        }
    }

    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            icon = { Icon(Icons.Default.Warning, "Warning", tint = CriticalRed(), modifier = Modifier.size(32.dp)) },
            title = { Text("Clear Data / Reset Onboarding?", color = PrimaryText(), fontSize = 16.sp) },
            text = {
                Text(
                    "This resets onboarding_complete and the UI preferences (Demo mode, speed unit, night dim, theme, accent).\n\nYour trip logs, DTC scans, and CSV telemetry files are stored separately and are NOT removed by this action.",
                    color = SecondaryText(), fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDataConfirm = false
                        val prefs = context.getSharedPreferences("sportage_obd_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()
                        Toast.makeText(context, "Onboarding and UI settings cleared.\n\nTrip/DTC/CSV data kept. Re-open app to see onboarding again.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = CriticalRed())
                ) {
                    Text("Clear & Reset", fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }, colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText())) {
                    Text("Cancel", fontSize = 14.sp)
                }
            },
            containerColor = DarkSurface(),
            titleContentColor = PrimaryText(),
            textContentColor = SecondaryText()
        )
    }
}

@Composable
private fun SectionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = DarkBackground(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, "Section", tint = SecondaryText(), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, color = PrimaryText(), fontSize = 14.sp)
                    if (subtitle != null) Text(subtitle, color = SecondaryText(), fontSize = 11.sp, maxLines = 1)
                }
            }
            Icon(Icons.Default.ChevronRight, "Open", tint = SecondaryText(), modifier = Modifier.size(20.dp))
        }
    }
}
