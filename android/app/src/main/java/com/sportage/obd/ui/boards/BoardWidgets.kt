package com.sportage.obd.ui.boards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.map.HybridLocationMap
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One widget card on a board. When [editing] is true the card also exposes
 * remove and reorder controls, so a driver can arrange the board freely.
 */
@Composable
fun BoardWidget(
    type: BoardWidgetType,
    data: BoardData,
    editing: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onRemove: () -> Unit = {},
    onMoveLeft: () -> Unit = {},
    onMoveRight: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.testTag("board_widget_${type.id}").fillMaxWidth().heightIn(min = 96.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(type.title.uppercase(Locale.ENGLISH),
                    color = SecondaryText(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (editing) {
                    if (canMoveLeft) {
                        IconButton(onMoveLeft, Modifier.size(28.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move left", tint = SecondaryText(), modifier = Modifier.size(18.dp))
                        }
                    }
                    if (canMoveRight) {
                        IconButton(onMoveRight, Modifier.size(28.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move right", tint = SecondaryText(), modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onRemove, Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Remove widget", tint = CriticalRed(), modifier = Modifier.size(18.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) { WidgetBody(type, data) }
        }
    }
}

@Composable
private fun WidgetBody(type: BoardWidgetType, data: BoardData) {
    when (type) {
        BoardWidgetType.SPEED -> BigValue(
            data.speed?.toString() ?: "--", data.speedUnit,
            if ((data.speed ?: 0) > 140) CriticalRed() else PrimaryText())
        BoardWidgetType.RPM -> BigValue("${data.rpm ?: "--"}", "rpm",
            if ((data.rpm ?: 0) > 6500) CriticalRed() else PrimaryText())
        BoardWidgetType.COOLANT -> BigValue("${data.coolant ?: "--"}", "°C",
            if ((data.coolant ?: 0) > 105) CriticalRed() else PrimaryText())
        BoardWidgetType.INTAKE_TEMP -> BigValue("${data.intakeTemp ?: "--"}", "°C")
        BoardWidgetType.FUEL -> BigValue("${data.fuelLevel ?: "--"}", "% fuel",
            if ((data.fuelLevel ?: 0) < 15) WarningAmber() else PrimaryText())
        BoardWidgetType.THROTTLE -> BigValue(data.throttle?.let { "%.0f".format(it) } ?: "--", "% throttle")
        BoardWidgetType.LOAD -> BigValue(data.load?.let { "%.0f".format(it) } ?: "--", "% load")
        BoardWidgetType.VOLTAGE -> BigValue(data.voltage?.let { "%.1f".format(it) } ?: "--", "volts",
            if ((data.voltage ?: 13f) < 11.5f) WarningAmber() else PrimaryText())
        BoardWidgetType.GEAR -> BigValue(if (data.gear == 0) "P/N" else "D${data.gear}", "gear")
        BoardWidgetType.MAF -> BigValue(data.mafGs?.let { "%.1f".format(it) } ?: "--", "g/s air")
        BoardWidgetType.TIMING -> BigValue(data.timingAdvanceDeg?.let { "%.1f".format(it) } ?: "--", "°BTDC")
        BoardWidgetType.MAP -> Box(Modifier.fillMaxWidth().height(210.dp)) {
            HybridLocationMap(data.currentLat, data.currentLon, 0)
        }
        BoardWidgetType.CLOCK -> ClockWidget()
        BoardWidgetType.GPS -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BigValue("%.4f".format(data.currentLat), "latitude")
            BigValue("%.4f".format(data.currentLon), "longitude")
            data.gpsStatus?.let { Text(it, color = SecondaryText(), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        BoardWidgetType.TRIP_DISTANCE -> BigValue("%.1f".format(data.tripDistanceKm), "km trip")
        BoardWidgetType.TRIP_DURATION -> BigValue(formatDuration(data.tripDurationSeconds), "duration")
        BoardWidgetType.CONNECTION -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Bolt, null, tint = if (data.connectionOk) ElectricCyan() else CriticalRed(), modifier = Modifier.size(20.dp))
            Text(data.connectionLabel, color = PrimaryText(), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        }
        BoardWidgetType.SPEED_GRAPH -> HistoryGraph(data.speedHistory, 200, "Speed · ${data.speedUnit}")
        BoardWidgetType.RPM_GRAPH -> HistoryGraph(data.rpmHistory, 7000, "RPM")
    }
}


@Composable
private fun BigValue(value: String, unit: String, color: androidx.compose.ui.graphics.Color = PrimaryText()) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, color = color, fontSize = 34.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Text(unit, color = SecondaryText(), fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
    }
}

@Composable
private fun ClockWidget() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    Column {
        Text(SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(now)),
            color = PrimaryText(), fontSize = 34.sp, fontWeight = FontWeight.Light)
        Text(SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(Date(now)), color = SecondaryText(), fontSize = 11.sp)
    }
}

@Composable
private fun HistoryGraph(history: List<Pair<Long, Int>>, maxValue: Int, label: String) {
    val lineColor = ElectricCyan()
    val shadeColor = MaterialTheme.colorScheme.background
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = SecondaryText(), fontSize = 10.sp)
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            Canvas(Modifier.fillMaxSize().background(shadeColor, RoundedCornerShape(8.dp))) {
                if (history.size < 2) return@Canvas
                val path = Path()
                history.forEachIndexed { idx, pair ->
                    val x = size.width * (idx.toFloat() / (history.size - 1))
                    val y = size.height - (size.height * (pair.second.coerceIn(0, maxValue) / maxValue.toFloat()))
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = lineColor, style = Stroke(width = 2f, cap = StrokeCap.Round))
            }
        }
    }
}

internal fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%dm".format(m)
}
