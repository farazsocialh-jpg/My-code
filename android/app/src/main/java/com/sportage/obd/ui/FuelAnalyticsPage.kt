package com.sportage.obd.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.viewmodel.ObdViewModel
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.WarningAmber
import com.sportage.obd.fuel.FuelFillEntry
import com.sportage.obd.fuel.FuelHistoryStore
import com.sportage.obd.fuel.FuelMath
import com.sportage.obd.fuel.TripHistoryStore
import com.sportage.obd.fuel.TripRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** SharedPreferences keys for fuel settings (kept beside the other launcher prefs). */
private const val FUEL_PREFS = "sportage_fuel_settings"
private const val KEY_PRICE = "fuel_price_qar_per_liter"
private const val KEY_TANK = "tank_capacity_liters"

private fun loadFuelSettings(context: android.content.Context): Pair<Double, Double> {
    val prefs = context.getSharedPreferences(FUEL_PREFS, android.content.Context.MODE_PRIVATE)
    return prefs.getFloat(KEY_PRICE, 2.05f).toDouble() to prefs.getFloat(KEY_TANK, 62f).toDouble()
}

private fun saveFuelSettings(context: android.content.Context, price: Double, tank: Double) {
    context.getSharedPreferences(FUEL_PREFS, android.content.Context.MODE_PRIVATE)
        .edit()
        .putFloat(KEY_PRICE, price.toFloat())
        .putFloat(KEY_TANK, tank.toFloat())
        .apply()
}

/** Short source tag so every fuel number says where it came from. */
private fun sourceLabel(source: String?): String = when (source) {
    "ECU_DIRECT" -> "Direct ECU (PID 5E)"
    "CALCULATED" -> "Calculated (MAF estimate)"
    else -> "No fuel data"
}

@Composable
fun FuelAnalyticsPage(
    distanceKm: Double,
    tripDurationSeconds: Long,
    maxSpeedKmh: Int,
    avgSpeedKmh: Int,
    onResetTrip: () -> Unit,
    viewModel: ObdViewModel? = null
) {
    val context = LocalContext.current
    val telemetry = viewModel?.telemetry?.collectAsState()?.value
    val fuelStore = remember { FuelHistoryStore(context) }
    val tripStore = remember { TripHistoryStore(context) }
    var fills by remember { mutableStateOf(fuelStore.fills()) }
    var trips by remember { mutableStateOf(tripStore.trips()) }
    var (priceQar, tankLiters) = remember { loadFuelSettings(context) }
    var priceText by remember { mutableStateOf("%.2f".format(Locale.US, priceQar)) }
    var tankText by remember { mutableStateOf("%.0f".format(Locale.US, tankLiters)) }
    var litersText by remember { mutableStateOf("") }
    var kmText by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    val calibration = FuelMath.calibrationFactor(fills)
    val rateLh = telemetry?.fuelRateEcuLh
    val fuelUsed = telemetry?.fuelUsedLiters ?: 0f
    // Calibrated trip usage: apply the tank-to-tank factor to the accumulated figure.
    val calibratedUsed = fuelUsed * (telemetry?.fuelCalibrationFactor ?: 1f)
    val instantL100 = telemetry?.fuelEconomyL100
    val avgL100 = if (distanceKm > 0.5 && calibratedUsed > 0.05) calibratedUsed / distanceKm * 100.0 else null
    val kmPerL = if (avgL100 != null && avgL100 > 0.1) 100.0 / avgL100 else null
    val fuelPct = telemetry?.fuelLevelPct
    val remainingLiters = fuelPct?.let { tankLiters * it / 100.0 }
    val rangeKm = if (avgL100 != null && avgL100 > 0.5 && remainingLiters != null)
        remainingLiters / avgL100 * 100.0 else null
    val tripCostQar = priceQar * calibratedUsed
    val costPerKm = if (distanceKm > 1) tripCostQar / distanceKm else null
    val lastFill = fills.lastOrNull()
    val actualL100 = lastFill?.actualL100()

    val metrics = listOf(
        FuelReading("Instant consumption", instantL100?.let { "%.1f".format(Locale.US, it) } ?: "--",
            "L/100 km", ElectricCyan()),
        FuelReading("Trip average", avgL100?.let { "%.1f".format(Locale.US, it) } ?: "--", "L/100 km"),
        FuelReading("Efficiency", kmPerL?.let { "%.1f".format(Locale.US, it) } ?: "--", "km/L"),
        FuelReading("Fuel rate", rateLh?.let { "%.1f".format(Locale.US, it) } ?: "--", "L/h"),
        FuelReading("Trip fuel used", "%.2f".format(Locale.US, calibratedUsed), "liters"),
        FuelReading("Remaining range", rangeKm?.let { "%.0f".format(Locale.US, it) } ?: "--", "km"),
        FuelReading("Trip fuel cost", "%.2f".format(Locale.US, tripCostQar), "QAR"),
        FuelReading("Cost per km", costPerKm?.let { "%.3f".format(Locale.US, it) } ?: "--", "QAR/km")
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalGasStation, null, Modifier.size(22.dp), tint = ElectricCyan())
            Spacer(Modifier.width(10.dp))
            Text("Fuel", Modifier.weight(1f), color = PrimaryText(), style = MaterialTheme.typography.titleLarge)
            Text(
                sourceLabel(telemetry?.fuelSource),
                color = SecondaryText(), style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = onResetTrip, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save & reset trip")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxWidth().height(260.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(metrics) { FuelTile(it) }
        }

        // Drive-condition split for the current trip.
        FuelSection("Driving conditions this trip") {
            val cityL = telemetry?.cityFuelLiters ?: 0f
            val hwyL = telemetry?.highwayFuelLiters ?: 0f
            val idleL = telemetry?.idleFuelLiters ?: 0f
            val cityKm = telemetry?.cityDistanceKm ?: 0f
            val hwyKm = telemetry?.highwayDistanceKm ?: 0f
            FuelStat("City", cityKm, cityL)
            FuelStat("Highway", hwyKm, hwyL)
            FuelStat("Idling", 0f, idleL)
        }

        // Tank-to-tank calibration: user enters what was filled and km since last fill.
        FuelSection("Tank-to-tank calibration") {
            Text(
                "After filling up, enter the liters added and the distance since the previous fill. " +
                    "The app compares this with its own estimate and derives a correction factor.",
                color = SecondaryText(), style = MaterialTheme.typography.bodySmall
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = litersText, onValueChange = { litersText = it },
                    label = { Text("Liters filled") }, modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = kmText, onValueChange = { kmText = it },
                    label = { Text("km since last fill") }, modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = {
                        val liters = litersText.toDoubleOrNull()
                        val km = kmText.toDoubleOrNull()
                        if (liters == null || km == null || liters <= 0.5 || km <= 1) {
                            feedback = "Enter liters and km since the last fill."
                        } else {
                            val entry = FuelFillEntry(
                                dateMs = System.currentTimeMillis(),
                                litersFilled = liters,
                                kmSinceLastFill = km,
                                pricePerLiterQar = priceQar,
                                obdLitersSinceLastFill = fuelUsed.toDouble(),
                                tripKmAtFill = distanceKm
                            )
                            fuelStore.addFill(entry)
                            fills = fuelStore.fills()
                            feedback = "Fill recorded. Correction factor: %.2fx".format(
                                Locale.US, FuelMath.calibrationFactor(fills) ?: 1.0
                            )
                            litersText = ""
                            kmText = ""
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.LocalGasStation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Record fill-up")
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    feedback ?: "Current factor: %s".format(
                        Locale.US,
                        calibration?.let { "%.2fx (from %d fills)".format(Locale.US, it, fills.size) }
                            ?: "not calibrated yet"
                    ),
                    color = if (feedback != null) WarningAmber() else SecondaryText(),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                )
            }
            if (actualL100 != null) {
                Text(
                    "Last fill: %.1f L/100 km actual vs %.1f L/100 km estimated".format(
                        Locale.US, actualL100, lastFill?.obdL100() ?: 0.0
                    ),
                    color = SecondaryText(), style = MaterialTheme.typography.bodySmall
                )
            }
            if (fills.isNotEmpty()) {
                val fmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
                Text(
                    "History: " + fills.takeLast(6).reversed().joinToString("  •  ") {
                        "%s: %.0f L / %.0f km".format(Locale.US, fmt.format(Date(it.dateMs)), it.litersFilled, it.kmSinceLastFill)
                    },
                    color = SecondaryText(), style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Price and tank settings.
        FuelSection("Settings") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = priceText, onValueChange = { priceText = it },
                    label = { Text("Fuel price (QAR/L)") }, modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = tankText, onValueChange = { tankText = it },
                    label = { Text("Tank capacity (L)") }, modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            FilledTonalButton(
                onClick = {
                    val p = priceText.toDoubleOrNull()
                    val t = tankText.toDoubleOrNull()
                    if (p != null && t != null && p > 0 && t >= 20) {
                        saveFuelSettings(context, p, t)
                        feedback = "Settings saved."
                    } else feedback = "Check price and tank values."
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Save settings") }
        }

        // Persisted trip history (newest first).
        if (trips.isNotEmpty()) {
            FuelSection("Trip history") {
                val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)
                trips.takeLast(8).reversed().forEach { t: TripRecord ->
                    Text(
                        "%s — %.1f km, %.2f L, %s".format(
                            Locale.US, fmt.format(Date(t.dateMs)), t.distanceKm, t.fuelLiters,
                            t.avgL100?.let { "%.1f L/100 km".format(Locale.US, it) } ?: "no fuel data"
                        ),
                        color = SecondaryText(), style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private data class FuelReading(val title: String, val value: String, val unit: String, val color: Color? = null)

@Composable
private fun FuelTile(reading: FuelReading) {
    Surface(color = DarkSurface(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(reading.title, color = SecondaryText(), style = MaterialTheme.typography.labelMedium,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(reading.value, color = reading.color ?: PrimaryText(),
                fontSize = if (reading.value.length > 6) 20.sp else 28.sp,
                fontWeight = FontWeight.Medium, maxLines = 1)
            Text(reading.unit, color = SecondaryText(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun FuelSection(title: String, content: @Composable () -> Unit) {
    Surface(color = DarkSurface(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = PrimaryText(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            content()
        }
    }
}

@Composable
private fun FuelStat(label: String, km: Float, liters: Float) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = SecondaryText(), style = MaterialTheme.typography.bodyMedium)
        Text(
            "%.1f km  •  %.2f L".format(Locale.US, km, liters),
            color = PrimaryText(), style = MaterialTheme.typography.bodyMedium
        )
    }
}
