package com.sportage.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.ui.theme.*

@Composable
fun QuickActionDock(
    onMusic: () -> Unit,
    onMediaKey: (Int) -> Unit,
    onPhone: () -> Unit,
    onApps: () -> Unit,
    onBluetooth: () -> Unit,
    logging: Boolean,
    canLog: Boolean,
    onLogging: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground(), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickActionChip(
            icon = Icons.Default.MusicNote,
            label = "Media",
            onClick = onMusic,
            active = false
        )
        QuickActionChip(
            icon = Icons.Default.PlayArrow,
            label = "Play/Pause",
            onClick = { onMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
            active = false
        )
        QuickActionChip(
            icon = Icons.Default.Phone,
            label = "Phone",
            onClick = onPhone,
            active = false
        )
        QuickActionChip(
            icon = Icons.Default.Apps,
            label = "Apps",
            onClick = onApps,
            active = false
        )
        QuickActionChip(
            icon = Icons.Default.Bluetooth,
            label = "OBD",
            onClick = onBluetooth,
            active = false
        )
        QuickActionChip(
            icon = if (logging) Icons.Default.Stop else Icons.Default.Sensors,
            label = if (logging) "Stop Log" else "Log",
            onClick = onLogging,
            active = logging && canLog,
            enabled = canLog
        )
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .background(
                if (active) ElectricCyan().copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Surface(
            color = if (active) ElectricCyan() else DarkSurfaceElevated(),
            shape = CircleShape,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (active) Icons.Default.Brightness1 else icon,
                    contentDescription = label,
                    tint = if (active) OnAccent() else PrimaryText(),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = label,
            color = if (active) ElectricCyan() else SecondaryText(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
