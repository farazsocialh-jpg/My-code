package com.sportage.obd.ui.boards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText

private data class PickerEntry(val type: BoardWidgetType, val icon: ImageVector, val group: String)

private val pickerEntries = listOf(
    PickerEntry(BoardWidgetType.SPEED, Icons.Default.Speed, "Driving"),
    PickerEntry(BoardWidgetType.RPM, Icons.Default.Speed, "Driving"),
    PickerEntry(BoardWidgetType.GEAR, Icons.Default.Speed, "Driving"),
    PickerEntry(BoardWidgetType.SPEED_GRAPH, Icons.Default.Speed, "Driving"),
    PickerEntry(BoardWidgetType.RPM_GRAPH, Icons.Default.Speed, "Driving"),
    PickerEntry(BoardWidgetType.COOLANT, Icons.Default.Check, "Engine"),
    PickerEntry(BoardWidgetType.INTAKE_TEMP, Icons.Default.Check, "Engine"),
    PickerEntry(BoardWidgetType.LOAD, Icons.Default.Check, "Engine"),
    PickerEntry(BoardWidgetType.THROTTLE, Icons.Default.Check, "Engine"),
    PickerEntry(BoardWidgetType.MAF, Icons.Default.Check, "Engine"),
    PickerEntry(BoardWidgetType.TIMING, Icons.Default.Check, "Engine"),
    PickerEntry(BoardWidgetType.VOLTAGE, Icons.Default.Check, "Engine"),
    PickerEntry(BoardWidgetType.FUEL, Icons.Default.Check, "Engine"),
    PickerEntry(BoardWidgetType.MAP, Icons.Default.Explore, "Location"),
    PickerEntry(BoardWidgetType.GPS, Icons.Default.Explore, "Location"),
    PickerEntry(BoardWidgetType.CLOCK, Icons.Default.Schedule, "Location"),
    PickerEntry(BoardWidgetType.TRIP_DISTANCE, Icons.Default.Schedule, "Trip"),
    PickerEntry(BoardWidgetType.TRIP_DURATION, Icons.Default.Schedule, "Trip"),
    PickerEntry(BoardWidgetType.CONNECTION, Icons.Default.Check, "Status")
)

/**
 * Widget catalog for the current board. Types already placed on the board are
 * marked with a check and tapping them does nothing, so a board never gets
 * duplicate widgets.
 */
@Composable
fun WidgetPicker(
    isOpen: Boolean,
    boardIndex: Int,
    layout: BoardLayout,
    onAdd: (BoardWidgetType) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return
    val placed = layout.boards.getOrNull(boardIndex)?.toSet() ?: emptySet()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text("Add widget to board ${boardIndex + 1}", color = PrimaryText(), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                Modifier.testTag("widget_picker_list").fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pickerEntries.groupBy { it.group }.forEach { (group, entries) ->
                    Text(group.uppercase(), color = SecondaryText(), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                    entries.forEach { entry ->
                        val added = entry.type in placed
                        val enabled = !added
                        Row(
                            modifier = Modifier
                                .testTag("picker_${entry.type.id}")
                                .fillMaxWidth()
                                .clickable(enabled = enabled) { onAdd(entry.type) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(28.dp).background(
                                    if (added) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (added) Icon(Icons.Default.Check, "Already added", tint = SecondaryText(), modifier = Modifier.size(16.dp))
                                else Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(entry.type.title, color = if (enabled) PrimaryText() else SecondaryText(), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    )
}
