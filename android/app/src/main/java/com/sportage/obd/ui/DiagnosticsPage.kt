package com.sportage.obd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.Mode06MonitorResult
import com.sportage.obd.service.AlertSeverity
import com.sportage.obd.ui.theme.*
import com.sportage.obd.viewmodel.ObdViewModel
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsPage(
    dtcCodes: List<DtcItem>,
    isScanning: Boolean,
    onScan: () -> Unit,
    onClear: () -> Unit,
    connectionState: ConnectionState,
    viewModel: ObdViewModel? = null
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Stored (03)", "Pending (07)", "Permanent (0A)", "Freeze Frame", "Readiness", "Mode 06 Monitors")

    val pendingCodes = viewModel?.pendingDtcList?.collectAsState()?.value ?: emptyList()
    val permanentCodes = viewModel?.permanentDtcList?.collectAsState()?.value ?: emptyList()
    val freezeFrame = viewModel?.freezeFrame?.collectAsState()?.value
    val readiness = viewModel?.readiness?.collectAsState()?.value
    val mode06MonitorResult = viewModel?.mode06MonitorResult?.collectAsState()?.value
    val vin = viewModel?.vin?.collectAsState()?.value
    val supportedPids = viewModel?.supportedPids?.collectAsState()?.value ?: emptySet()
    val scope = rememberCoroutineScope()

    var auxScanning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Action Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkBackground(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, DarkSurfaceElevated())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            tint = ElectricCyan(),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Diagnostics",
                            color = PrimaryText(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Text(
                        text = "MODES 03 / 07 / 0A · FREEZE FRAME · READINESS MONITORS · MODE 06",
                        color = SecondaryText(),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    if (vin != null) {
                        Text(
                            text = "VIN: $vin",
                            color = ElectricCyan(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        onClick = onScan,
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = OnAccent(), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanning...", color = OnAccent(), fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = OnAccent(), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan ECU (03)", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                        }
                    }

                    Button(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        onClick = { showClearConfirm = true },
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = CriticalRed().copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = OnAccent(), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clearing...", color = OnAccent(), fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = OnAccent(), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Codes (04)", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                        }
                    }
                }

                // Mode 06 refresh button
                Button(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    onClick = {
                        auxScanning = true
                        scope.launch {
                            viewModel?.refreshMode06Monitors()
                            auxScanning = false
                        }
                    },
                    enabled = !auxScanning && viewModel != null,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    if (auxScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = OnAccent(), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refreshing...", color = OnAccent(), fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = OnAccent(), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh Mode 06", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                    }
                }
            }
        }

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = DarkBackground(),
            contentColor = ElectricCyan()
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            title,
                            maxLines = 1,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            color = if (selectedTab == idx) ElectricCyan() else SecondaryText()
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> DtcListContent(dtcCodes, emptyHint = "No Stored Diagnostic Trouble Codes Found")
            1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            auxScanning = true
                            scope.launch {
                                viewModel?.scanPendingDtcsNow()
                                auxScanning = false
                            }
                        },
                        enabled = !auxScanning && viewModel != null,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Scan Pending (07)", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                    }
                }
                DtcListContent(pendingCodes, emptyHint = "No Pending Codes · ECU monitors have not confirmed a fault yet")
            }
            2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            auxScanning = true
                            scope.launch {
                                viewModel?.scanPermanentDtcsNow()
                                auxScanning = false
                            }
                        },
                        enabled = !auxScanning && viewModel != null,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "Scan Permanent (0A)",
                            color = OnAccent(),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp
                        )
                    }
                }
                DtcListContent(permanentCodes, emptyHint = "No Permanent Codes · Stored codes that survived a clear attempt")
            }
            3 -> FreezeFrameContent(freezeFrame, supportedPids, connectionState, viewModel, scope, auxScanning) { auxScanning = it }
            4 -> ReadinessContent(readiness, supportedPids, connectionState, viewModel, scope, auxScanning) { auxScanning = it }
            5 -> Mode06MonitorsContent(mode06MonitorResult, supportedPids, connectionState, viewModel, scope, auxScanning) { auxScanning = it }
        }
    }
}

@Composable
private fun DtcListContent(codes: List<DtcItem>, emptyHint: String) {
    if (codes.isEmpty()) {
        Surface(color = DarkSurface(), shape = RoundedCornerShape(12.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    tint = SecondaryText(),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(emptyHint, color = SecondaryText(), fontSize = 13.sp, fontFamily = FontFamily.SansSerif)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(codes.size) { index ->
                DtcCodeCard(codes[index])
            }
        }
    }
}

@Composable
private fun DtcCodeCard(item: DtcItem) {
    val severityColor = when (item.severity) {
        com.sportage.obd.obd.DtcSeverity.CRITICAL -> CriticalRed()
        com.sportage.obd.obd.DtcSeverity.WARNING -> WarningAmber()
        com.sportage.obd.obd.DtcSeverity.ADVISORY -> ElectricCyan()
        else -> ElectricCyan()
    }
    Surface(
        color = DarkSurface(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, severityColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = severityColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(
                    when (item.severity) {
                        com.sportage.obd.obd.DtcSeverity.CRITICAL -> Icons.Default.Warning
                        com.sportage.obd.obd.DtcSeverity.WARNING -> Icons.Default.Warning
                        com.sportage.obd.obd.DtcSeverity.ADVISORY -> Icons.Default.Info
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.code,
                    color = PrimaryText(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = item.description,
                    color = SecondaryText(),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = item.severity.name,
                    color = severityColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Composable
private fun FreezeFrameContent(
    freezeFrame: com.sportage.obd.obd.FreezeFrame?,
    supportedPids: Set<String>,
    connectionState: ConnectionState,
    viewModel: ObdViewModel?,
    scope: kotlinx.coroutines.CoroutineScope,
    auxScanning: Boolean,
    onAuxScanning: (Boolean) -> Unit
) {
    val frame = freezeFrame
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (frame != null && frame.items.isNotEmpty()) {
            Text(
                "Freeze Frame · Last Fault Snapshot",
                color = PrimaryText(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                "Recorded at the moment of the last confirmed fault",
                color = SecondaryText(),
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
            frame.items.forEach { item ->
                Surface(color = DarkSurface(), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            item.label,
                            color = ElectricCyan(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            item.value,
                            color = SecondaryText(),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onAuxScanning(true)
                        scope.launch {
                            viewModel?.readFreezeFrameNow()
                            onAuxScanning(false)
                        }
                    },
                    enabled = !auxScanning && viewModel != null,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Read Freeze Frame", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                }
            }
            if (frame == null) {
                Text(
                    "No freeze frame data available",
                    color = SecondaryText(),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Composable
private fun ReadinessContent(
    readiness: com.sportage.obd.obd.ReadinessState?,
    supportedPids: Set<String>,
    connectionState: ConnectionState,
    viewModel: ObdViewModel?,
    scope: kotlinx.coroutines.CoroutineScope,
    auxScanning: Boolean,
    onAuxScanning: (Boolean) -> Unit
) {
    val state = readiness
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state != null) {
            Text(
                "Readiness Monitors",
                color = PrimaryText(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                "Shows which emissions monitors have completed their self-checks",
                color = SecondaryText(),
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
            val allMonitors = if (state.sparkIgnitionMonitors.isNotEmpty()) state.sparkIgnitionMonitors else state.compressionIgnitionMonitors
            allMonitors.forEach { monitor ->
                val name = monitor.name
                val ready = monitor.complete
                Surface(color = DarkSurface(), shape = RoundedCornerShape(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            name,
                            color = SecondaryText(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (ready) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (ready) TealAccent() else CriticalRed(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onAuxScanning(true)
                        scope.launch {
                            viewModel?.readReadinessNow()
                            onAuxScanning(false)
                        }
                    },
                    enabled = !auxScanning && viewModel != null,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Read Readiness", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                }
            }
            if (state == null) {
                Text(
                    "No readiness data available",
                    color = SecondaryText(),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Composable
private fun Mode06MonitorsContent(
    mode06MonitorResult: Mode06MonitorResult?,
    supportedPids: Set<String>,
    connectionState: ConnectionState,
    viewModel: ObdViewModel?,
    scope: kotlinx.coroutines.CoroutineScope,
    auxScanning: Boolean,
    onAuxScanning: (Boolean) -> Unit
) {
    val result = mode06MonitorResult
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Mode 06 · On-Board Monitor Results",
            color = PrimaryText(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
        Text(
            "SAE J1979 in-use performance tracking (completion/conditions encountered). Not a diagnosis.",
            color = SecondaryText(),
            fontSize = 12.sp,
            fontFamily = FontFamily.SansSerif
        )
        val hasContent = result != null && (result.sparkIgnitionComponents.isNotEmpty() || result.compressionIgnitionComponents.isNotEmpty())
        if (hasContent) {
            // Spark ignition monitors
            if (result.sparkIgnitionComponents.isNotEmpty()) {
                Text(
                    "Spark Ignition Monitors",
                    color = ElectricCyan(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif
                )
                result.sparkIgnitionComponents.forEach { component ->
                    Surface(color = DarkSurface(), shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                component.componentId,
                                color = ElectricCyan(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                component.name,
                                color = SecondaryText(),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Completed: ${component.completionCount}",
                                    color = PrimaryText(),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    "Conditions: ${component.conditionsEncountered}",
                                    color = SecondaryText(),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            if (component.completionFraction != null) {
                                Text(
                                    "Ratio: ${(component.completionFraction * 100).toInt()}%",
                                    color = if (component.completionFraction > 0.5f) TealAccent() else WarningAmber(),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }

            // Compression ignition monitors
            if (result.compressionIgnitionComponents.isNotEmpty()) {
                Text(
                    "Compression Ignition Monitors",
                    color = ElectricCyan(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif
                )
                result.compressionIgnitionComponents.forEach { component ->
                    Surface(color = DarkSurface(), shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                component.componentId,
                                color = ElectricCyan(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                component.name,
                                color = SecondaryText(),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Completed: ${component.completionCount}",
                                    color = PrimaryText(),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    "Conditions: ${component.conditionsEncountered}",
                                    color = SecondaryText(),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            if (component.completionFraction != null) {
                                Text(
                                    "Ratio: ${(component.completionFraction * 100).toInt()}%",
                                    color = if (component.completionFraction > 0.5f) TealAccent() else WarningAmber(),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }

            // Raw response
            if (result.rawResponse.isNotBlank() && result.rawResponse != "No response") {
                Surface(color = DarkSurface(), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "Raw Response",
                            color = SecondaryText(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            result.rawResponse,
                            color = PrimaryText(),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.height(IntrinsicSize.Min)
                        )
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onAuxScanning(true)
                        scope.launch {
                            viewModel?.refreshMode06Monitors()
                            onAuxScanning(false)
                        }
                    },
                    enabled = !auxScanning && viewModel != null,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Refresh Mode 06", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                }
            }
            Text(
                "No Mode 06 data available. Tap refresh to request on-board monitor results from the ECU.",
                color = SecondaryText(),
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
private fun MetricTile(reading: com.sportage.obd.ui.PanelReading) {
    Surface(color = DarkSurface(), shape = RoundedCornerShape(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                reading.label,
                color = SecondaryText(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                reading.value,
                color = reading.color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif
            )
            if (reading.unit.isNotBlank()) {
                Text(
                    reading.unit,
                    color = SecondaryText(),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}
