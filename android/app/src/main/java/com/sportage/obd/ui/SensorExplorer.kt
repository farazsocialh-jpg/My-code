package com.sportage.obd.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.obd.SensorCatalog
import com.sportage.obd.service.ObdTelemetry
import com.sportage.obd.ui.theme.*
import com.sportage.obd.viewmodel.ObdViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SensorExplorer(viewModel: ObdViewModel?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sensor_preferences", Context.MODE_PRIVATE) }
    val telemetry = viewModel?.telemetry?.collectAsState()?.value ?: ObdTelemetry()
    val supported = viewModel?.supportedPids?.collectAsState()?.value ?: emptySet()
    val connection = viewModel?.connectionState?.collectAsState()?.value
    val demo = viewModel?.isDemoMode?.collectAsState()?.value ?: false
    val connected = connection is ConnectionState.Connected && !demo
    val vin = viewModel?.vin?.collectAsState()?.value
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())!!.toSet()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Supported") }
    var vehicle by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val history = remember { mutableStateMapOf<String, List<Pair<Long, Double>>>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(connection) { history.clear() }
    LaunchedEffect(telemetry.sensors) {
        telemetry.sensors.forEach { (pid, sample) ->
            val old = history[pid].orEmpty()
            val number = sample.values.firstOrNull()?.value
            if (number != null && old.lastOrNull()?.first != sample.timestamp)
                history[pid] = (old + (sample.timestamp to number)).takeLast(120)
        }
    }
    fun refresh() {
        if (busy || viewModel == null) return
        busy = true; message = null
        scope.launch {
            try {
                if (vehicle) viewModel.readVehicleInformation() else viewModel.refreshSensorCapabilities()
                message = if (vehicle) "Read complete. NO DATA means this ECU did not provide that response."
                    else "Discovery complete. Supported readings update automatically."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = "Read failed. Check the adapter connection and retry." }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("OBD workspace", style = MaterialTheme.typography.titleLarge, color = PrimaryText())
            IconButton(onClick = { refresh() }, enabled = connected && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "Refresh OBD capabilities", tint = if (connected) ElectricCyan() else SecondaryText())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!vehicle, { vehicle = false }, { Text("Live sensors") })
            FilterChip(vehicle, { vehicle = true }, { Text("Vehicle / tests") })
        }
        if (!connected) Text(if (demo) "Demo mode. Connect a real adapter for extended ECU readings."
            else "Connect an OBD adapter to discover available readings.", color = SecondaryText(), style = MaterialTheme.typography.bodyMedium)
        message?.let { Text(it, color = SecondaryText(), style = MaterialTheme.typography.bodySmall) }
        if (vehicle) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                item { InfoReading("VIN", vin ?: "Not reported") }
                item { InfoReading("Standard sensor coverage", "${SensorCatalog.entries.count { it.pid in supported }} supported catalogue PIDs / ${SensorCatalog.entries.size} available definitions") }
                item { InfoReading("Limits", "Generic ELM327 support does not guarantee ABS, airbags, TPMS, service resets or Kia coding. No actuator tests or undocumented write commands are issued automatically.") }
                item { InfoReading("On-board monitor results", "Mode 06: SAE J1979 in-use performance tracking where the ECU provides it (completion / conditions encountered). Raw Mode 06 is preserved where no standard shape matches. Not a diagnosis.") }
                items(telemetry.vehicleInfo.toList()) { (label, value) -> InfoReading(label, value) }
                if (telemetry.vehicleInfo.isEmpty()) item { Text("Use refresh to read adapter information, calibration data, ECU name and Mode 06 monitor responses.", color = SecondaryText()) }
            }
        } else {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Search sensors, category or PID") },
                leadingIcon = { Icon(Icons.Default.Search, null) })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Supported", "Favorites", "All").forEach { name ->
                    FilterChip(filter == name, { filter = name }, { Text(name) })
                }
            }
            val entries = SensorCatalog.entries.filter {
                (filter == "All" || (filter == "Favorites" && it.pid in favorites) || (filter == "Supported" && it.pid in supported)) &&
                    (query.isBlank() || "${it.title} ${it.category} ${it.pid}".contains(query, true))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${entries.size} ${if (entries.size == 1) "sensor" else "sensors"} · first valid ECU", color = SecondaryText(), style = MaterialTheme.typography.labelMedium)
                TextButton(enabled = telemetry.sensors.isNotEmpty(), onClick = {
                    scope.launch {
                        try {
                            val snapshot = telemetry.sensors.values.toList()
                            val file = withContext(Dispatchers.IO) {
                                val dir = File(context.getExternalFilesDir(null), "obd_logs").apply { mkdirs() }
                                File(dir, "sensors_${System.currentTimeMillis()}.csv").apply {
                                    writeText("sample_timestamp,pid,sensor,value,unit\n" + snapshot.joinToString("") { sample ->
                                        sample.values.joinToString("") { value ->
                                            "${sample.timestamp},${sample.pid},\"${value.label.replace("\"", "\"\"")}\",${value.value},\"${value.unit}\"\n"
                                        }
                                    })
                                }
                            }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Share sensor snapshot"))
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { message = "Could not share the snapshot. Check storage and available sharing apps." }
                    }
                }) { Text("Export") }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (entries.isEmpty()) item {
                    Text(if (filter == "Favorites") "No matching favorites. Use the star beside a sensor to pin it."
                        else "No matching supported sensors. Connect, refresh, or choose All to browse the read-only catalogue.",
                        color = SecondaryText(), modifier = Modifier.padding(vertical = 24.dp))
                }
                items(entries, key = { it.pid }) { definition ->
                    val sample = telemetry.sensors[definition.pid]
                    Column(Modifier.fillMaxWidth().clickable {
                        expanded = if (expanded == definition.pid) null else definition.pid
                    }.padding(vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(definition.title, color = PrimaryText(), style = MaterialTheme.typography.titleSmall)
                                Text("${definition.category} · 01${definition.pid}", color = SecondaryText(), style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = {
                                favorites = if (definition.pid in favorites) favorites - definition.pid else favorites + definition.pid
                                prefs.edit().putStringSet("favorites", favorites).apply()
                            }) { Icon(if (definition.pid in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                                "Favorite ${definition.title}", tint = if (definition.pid in favorites) WarningAmber() else SecondaryText()) }
                        }
                        Text(sample?.values?.joinToString("  /  ") { it.formatted() } ?: when {
                            supported.isEmpty() -> "Support not discovered"
                            definition.pid !in supported -> "Not supported by this ECU"
                            else -> "Supported · waiting for response"
                        }, color = if (sample != null && connected && !sample.stale(now)) ElectricCyan() else SecondaryText(),
                            style = MaterialTheme.typography.bodyMedium)
                        if (sample != null) Text(
                            if (!connected) "Last received · disconnected" else
                                "${((now - sample.timestamp).coerceAtLeast(0) / 1000)} s ago${if (sample.stale(now)) " · stale" else ""}",
                            color = SecondaryText(), style = MaterialTheme.typography.labelSmall)
                        if (expanded == definition.pid) {
                            val values = history[definition.pid].orEmpty().map { it.second }
                            Text(if (values.isEmpty()) "No samples yet. Unsupported PIDs are not polled."
                                else "First value since opening: min ${values.minOrNull()} · max ${values.maxOrNull()} · ${values.size} samples",
                                color = SecondaryText(), style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    HorizontalDivider(color = DarkSurfaceElevated())
                }
            }
        }
    }
}

@Composable
private fun InfoReading(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = SecondaryText(), style = MaterialTheme.typography.labelMedium)
        Text(value, color = PrimaryText(), style = MaterialTheme.typography.bodyMedium)
    }
}
