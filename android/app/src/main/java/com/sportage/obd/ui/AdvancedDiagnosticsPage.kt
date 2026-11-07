package com.sportage.obd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.Mode06MonitorResult
import com.sportage.obd.viewmodel.ObdViewModel
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.TealAccent
import com.sportage.obd.ui.theme.WarningAmber

/** Verification status so no untested parameter is ever presented as confirmed. */
enum class ParamStatus(val label: String, val color: @Composable () -> Color) {
    CONFIRMED("CONFIRMED", { TealAccent() }),
    AVAILABLE_UNTESTED("AVAILABLE BUT UNTESTED", { WarningAmber() }),
    UNSUPPORTED("UNSUPPORTED", { CriticalRed() })
}

@Composable
private fun StatusBadge(status: ParamStatus) {
    Surface(color = status.color().copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
        Text(
            status.label, color = status.color(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun fmt(v: Double?, digits: Int = 2): String =
    v?.let { String.format(java.util.Locale.US, "%.${digits}f", it) } ?: "--"

@Composable
fun AdvancedDiagnosticsPage(
    dtcCodes: List<DtcItem>,
    viewModel: ObdViewModel? = null
) {
    val telemetry = viewModel?.telemetry?.collectAsState()?.value
    val supported = viewModel?.supportedPids?.collectAsState()?.value ?: emptySet()
    val readiness = viewModel?.readiness?.collectAsState()?.value
    val mode06MonitorResult = viewModel?.mode06MonitorResult?.collectAsState()?.value
    val sensors = telemetry?.sensors ?: emptyMap()
    val vehicleInfo = telemetry?.vehicleInfo ?: emptyMap()

    // Live O2 history for the graph (upstream B1S1 = PID 14, downstream B1S2 = PID 15).
    val upstreamHistory = remember { mutableStateListOf<Float>() }
    val downstreamHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(telemetry?.timestamp) {
        val up = sensors["14"]?.values?.firstOrNull()?.value?.toFloat()
        val down = sensors["15"]?.values?.firstOrNull()?.value?.toFloat()
        if (up != null) {
            upstreamHistory.add(up)
            if (upstreamHistory.size > 120) upstreamHistory.removeAt(0)
        }
        if (down != null) {
            downstreamHistory.add(down)
            if (downstreamHistory.size > 120) downstreamHistory.removeAt(0)
        }
    }

    fun sensorValue(pid: String): Double? = sensors[pid]?.values?.firstOrNull()?.value
    val upstream = sensorValue("14")
    val downstream = sensorValue("15")
    val upstreamWide = sensors["24"]?.values
    val shortTrim = telemetry?.shortTermFuelTrimPct?.toDouble()
    val longTrim = telemetry?.longTermFuelTrimPct?.toDouble()
    val p0420 = dtcCodes.firstOrNull { it.code.startsWith("P0420") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Insights, null, Modifier.size(22.dp), tint = ElectricCyan())
            Spacer(Modifier.width(10.dp))
            Text("Advanced diagnostics", Modifier.weight(1f),
                color = PrimaryText(), style = MaterialTheme.typography.titleLarge)
        }

        // ---------------- OXYGEN SENSORS ----------------
        AdvSection("Oxygen sensors — bank 1") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Upstream (B1 S1)", Modifier.weight(1f), color = SecondaryText())
                StatusBadge(if ("14" in supported) ParamStatus.CONFIRMED else ParamStatus.UNSUPPORTED)
            }
            Row {
                AdvStat("Voltage", fmt(upstream) + " V")
                AdvStat("Short trim", fmt(sensors["14"]?.values?.getOrNull(1)?.value, 1) + " %")
            }
            if (upstreamWide != null) {
                AdvStat("Wideband lambda (PID 24)", fmt(upstreamWide.firstOrNull()?.value) + " λ")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Downstream (B1 S2)", Modifier.weight(1f), color = SecondaryText())
                StatusBadge(if ("15" in supported) ParamStatus.CONFIRMED else ParamStatus.UNSUPPORTED)
            }
            AdvStat("Voltage", fmt(downstream) + " V")
            Text(
                if (upstream != null && downstream != null)
                    "Upstream should switch rapidly around 0.1–0.9 V while the downstream stays " +
                        "relatively steady if the catalyst stores oxygen. Both switching identically " +
                        "can indicate reduced catalyst efficiency — never judge on this alone."
                else "Connect to the ECU to read live oxygen sensor data.",
                color = SecondaryText(), style = MaterialTheme.typography.bodySmall
            )
            if (upstreamHistory.size > 4) {
                O2Graph(upstreamHistory, downstreamHistory)
                Text("Upstream (cyan) vs downstream (amber) voltage, last ~2 minutes",
                    color = SecondaryText(), style = MaterialTheme.typography.labelSmall)
            }
        }

        // ---------------- FUEL TRIM CORRELATION ----------------
        AdvSection("Fuel trim correlation") {
            Row {
                AdvStat("Short-term B1", fmt(shortTrim, 1) + " %")
                AdvStat("Long-term B1", fmt(longTrim, 1) + " %")
            }
            val totalTrim = if (shortTrim != null && longTrim != null) shortTrim + longTrim else null
            Text(
                buildString {
                    append("Combined trim: ${fmt(totalTrim, 1)} %. ")
                    append(
                        when {
                            totalTrim == null -> "Connect to see trims."
                            totalTrim > 10 -> "Noticeably lean — the ECU keeps adding fuel. Check for vacuum leaks, a dirty MAF or a weak fuel pump before suspecting O2 sensors."
                            totalTrim < -10 -> "Noticeably rich — the ECU keeps pulling fuel. Check for a leaking injector, high fuel pressure or a stuck-rich O2 sensor."
                            else -> "Within the normal ±10 % band; the fuel system is operating close to target."
                        }
                    )
                },
                color = SecondaryText(), style = MaterialTheme.typography.bodySmall
            )
        }

        // ---------------- CATALYST MONITORING ----------------
        AdvSection("Catalyst monitoring") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("P0420 (catalyst efficiency below threshold)", Modifier.weight(1f), color = SecondaryText())
                if (p0420 != null) StatusBadge(ParamStatus.CONFIRMED)
                else Text("not stored", color = SecondaryText(), style = MaterialTheme.typography.labelSmall)
            }
            Text(
                if (p0420 != null)
                    "P0420 is currently stored: ${p0420.code} — ${p0420.description}. This code alone does NOT prove the catalytic converter is faulty. Exhaust leaks, faulty O2 sensors, engine misfires or oil/coolant consumption can all trigger it. Have the exhaust and sensors checked before replacing the converter."
                else "No P0420 stored in the ECU at the last scan.",
                color = if (p0420 != null) WarningAmber() else SecondaryText(),
                style = MaterialTheme.typography.bodySmall
            )
            Row {
                AdvStat("Catalyst temp B1 S1", fmt(sensorValue("3C"), 0) + " °C")
                AdvStat("Catalyst temp B1 S2", fmt(sensorValue("3E"), 0) + " °C")
            }
            val catMonitor = readiness?.sparkIgnitionMonitors?.firstOrNull { it.name.contains("Catalyst", ignoreCase = true) }
            Text(
                "Catalyst readiness monitor: " + when {
                    catMonitor == null -> "not reported"
                    catMonitor.complete -> "complete"
                    else -> "incomplete (drive cycle not finished since codes cleared)"
                },
                color = SecondaryText(), style = MaterialTheme.typography.bodySmall
            )
            // Mode 06 monitor results
            if (mode06MonitorResult != null && (mode06MonitorResult.sparkIgnitionComponents.isNotEmpty() || mode06MonitorResult.compressionIgnitionComponents.isNotEmpty())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode 06 monitor results", Modifier.weight(1f), color = SecondaryText())
                    StatusBadge(ParamStatus.CONFIRMED)
                }
                if (mode06MonitorResult.sparkIgnitionComponents.isNotEmpty()) {
                    Text(
                        "SAE J1979 in-use performance tracking (spark ignition): " +
                            mode06MonitorResult.sparkIgnitionComponents
                                .map { "${it.componentId}: ${it.rawCounts}" }
                                .joinToString(", "),
                        color = SecondaryText(), style = MaterialTheme.typography.bodySmall
                    )
                }
                if (mode06MonitorResult.compressionIgnitionComponents.isNotEmpty()) {
                    Text(
                        "SAE J1979 in-use performance tracking (compression ignition): " +
                            mode06MonitorResult.compressionIgnitionComponents
                                .map { "${it.componentId}: ${it.rawCounts}" }
                                .joinToString(", "),
                        color = SecondaryText(), style = MaterialTheme.typography.bodySmall
                    )
                }
                if (mode06MonitorResult.rawResponse.isNotBlank() && mode06MonitorResult.rawResponse != "No response") {
                    Text(
                        "Raw response preserved: ${mode06MonitorResult.rawResponse}",
                        color = SecondaryText(), style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "Mode 06 results are shown as completion/condition counts, not pass/fail. " +
                        "Manufacturer-specific limits are not assumed.",
                    color = SecondaryText(), style = MaterialTheme.typography.labelSmall
                )
            } else {
                Text(
                    "Mode 06 monitor results: " +
                        (mode06MonitorResult?.let {
                            buildString {
                                if (it.sparkIgnitionComponents.isNotEmpty()) append("SI ${it.sparkIgnitionComponents.size} components")
                                if (it.compressionIgnitionComponents.isNotEmpty()) {
                                    if (isNotEmpty()) append(", ")
                                    append("CI ${it.compressionIgnitionComponents.size} components")
                                }
                                if (it.rawResponse.isNotBlank() && it.rawResponse != "No response") append(" · raw preserved")
                            }
                        } ?: "not requested yet") +
                        (if (mode06MonitorResult?.standardShape != true) "; raw Mode 06 preserved where no SAE J1979 shape matches" else "") +
                        ". Not a diagnosis — limits are not assumed.",
                    color = SecondaryText(), style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // ---------------- TRANSMISSION ----------------
        AdvSection("Transmission") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gear position (PID 01 A4)", Modifier.weight(1f), color = SecondaryText())
                StatusBadge(if ("A4" in supported) ParamStatus.CONFIRMED else ParamStatus.AVAILABLE_UNTESTED)
            }
            val gear = telemetry?.transmissionGear
            Text(
                "Current gear: " + when (gear) {
                    null -> "not reporting (ECU may not expose PID A4)"
                    -2 -> "P"
                    -1 -> "R"
                    0 -> "N"
                    else -> gear.toString()
                } + if (gear == null) "" else " (direct ECU reading)",
                color = PrimaryText(), style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Fluid temperature and clutch/solenoid data require Kia-specific manufacturer codes " +
                    " (AVAILABLE BUT UNTESTED with this ELM327-style adapter). Not requested automatically — " +
                    "the app never sends commands that could alter transmission behaviour.",
                color = SecondaryText(), style = MaterialTheme.typography.bodySmall
            )
        }

        // ---------------- AWD ----------------
        AdvSection("All-wheel drive") {
            StatusBadge(ParamStatus.UNSUPPORTED)
            Spacer(Modifier.height(4.dp))
            Text(
                "The AWD coupling status, torque split and differential temperature live in Kia's proprietary " +
                    "modules and are NOT accessible through standard OBD-II. This app will not invent or assume " +
                    "AWD readings. A Kia-specific diagnostic tool would be required.",
                color = SecondaryText(), style = MaterialTheme.typography.bodySmall
            )
        }

        // ---------------- MODULE DISCOVERY ----------------
        AdvSection("Module discovery") {
            Text(
                "Standard OBD-II talks to the engine control unit (ECU). Other modules — ABS/ESC, SRS airbag, " +
                    "BCM, TCM, EPS, TPMS, HVAC and the AWD controller — need Kia-specific addressing and are " +
                    "not polled by this app, so no complete module coverage is claimed.",
                color = SecondaryText(), style = MaterialTheme.typography.bodySmall
            )
            if (vehicleInfo.isNotEmpty()) {
                vehicleInfo.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(k, Modifier.weight(1f), color = SecondaryText(), style = MaterialTheme.typography.bodySmall)
                        Text(v, color = PrimaryText(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvSection(title: String, content: @Composable () -> Unit) {
    Surface(color = DarkSurface(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = PrimaryText(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            content()
        }
    }
}

@Composable
private fun AdvStat(label: String, value: String) {
    Column(Modifier.padding(end = 24.dp)) {
        Text(label, color = SecondaryText(), style = MaterialTheme.typography.labelMedium)
        Text(value, color = PrimaryText(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun O2Graph(upstream: List<Float>, downstream: List<Float>) {
    val upColor = ElectricCyan()
    val downColor = WarningAmber()
    val gridColor = SecondaryText().copy(alpha = 0.3f)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        fun draw(values: List<Float>, color: Color) {
            if (values.size < 2) return
            val maxV = 1.0f
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = i.toFloat() / (values.size - 1) * size.width
                val y = size.height - (v / maxV).coerceIn(0f, 1f) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }
        drawRect(Color(0xFF232830))
        draw(upstream, upColor)
        draw(downstream, downColor)
        // 0.45 V reference line
        val y = size.height * 0.55f
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}
