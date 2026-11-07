package com.sportage.obd.ui

import android.content.Intent
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.media.MediaAppInfo
import com.sportage.obd.media.MediaApps
import com.sportage.obd.media.chosenMusicApp
import com.sportage.obd.media.saveChosenMusicApp
import com.sportage.obd.ui.theme.*

@Composable
fun MediaRadioPage(
    radioFreq: Float,
    onFreqChange: (Float) -> Unit,
    radioStationName: String,
    onStationChange: (String, Float) -> Unit,
    isMediaPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLaunchNativeRadio: () -> Unit,
    onLaunchLocalMusic: () -> Unit
) {
    val context = LocalContext.current
    val stations = listOf(
        Triple(91.7f, "QBS Radio", "English Hits"),
        Triple(94.0f, "Al Rayyan FM", "Arabic Culture"),
        Triple(97.5f, "Doha Hit FM", "Top 40"),
        Triple(101.4f, "Sportage Drive", "Automotive Stereo"),
        Triple(104.8f, "BBC World Service", "Global News"),
        Triple(107.6f, "Oryx FM", "French & Jazz")
    )

    val selectedStation = remember(radioFreq) {
        stations.firstOrNull { Math.abs(it.first - radioFreq) < 0.05f } ?: stations.first()
    }

    Surface(
        color = DarkSurface(),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Radio, "Radio", tint = ElectricCyan(), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                "Media & Radio",
                color = PrimaryText(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                "Control your audio",
                color = SecondaryText(),
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(Modifier.height(24.dp))

            // Now playing / generic media card
            Surface(
                color = DarkBackground(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        if (isMediaPlaying) Icons.Default.MusicNote else Icons.Default.MusicOff,
                        "Now playing",
                        tint = if (isMediaPlaying) ElectricCyan() else SecondaryText(),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Now Playing",
                        color = SecondaryText(),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        "Local music or selected app",
                        color = PrimaryText(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = SecondaryText(), modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(64.dp)) {
                    Surface(
                        color = ElectricCyan(),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            if (isMediaPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play/Pause",
                            tint = OnAccent(),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", tint = SecondaryText(), modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            // Radio stations
            Text(
                "Radio Stations",
                color = PrimaryText(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                "Kia Sportage 2.0 MPI preset favorites",
                color = SecondaryText(),
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selectedStation.second,
                    color = ElectricCyan(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    " FM",
                    color = SecondaryText(),
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Text(
                selectedStation.third,
                color = SecondaryText(),
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                stations.forEach { (freq, name, genre) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (Math.abs(freq - radioFreq) < 0.05f) ElectricCyan().copy(alpha = 0.15f)
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .padding(top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(
                            "${freq.toInt()}.${if (freq % 1 >= 0.5f) 9 else 0}",
                            color = if (Math.abs(freq - radioFreq) < 0.05f) ElectricCyan() else SecondaryText(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            name,
                            color = if (Math.abs(freq - radioFreq) < 0.05f) PrimaryText() else SecondaryText(),
                            fontSize = 9.sp,
                            maxLines = 1,
                            modifier = Modifier.width(60.dp),
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onLaunchLocalMusic,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan())
                ) {
                    Icon(Icons.Default.Album, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Music App", fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                }
                OutlinedButton(
                    onClick = onLaunchNativeRadio,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryText())
                ) {
                    Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Head Unit Radio", fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                }
            }

            Text(
                "Media keys (play/pause, next, previous) are sent to the active media session when available.",
                color = SecondaryText(),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
