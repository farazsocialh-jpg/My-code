package com.sportage.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.*

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 5

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LinearProgressIndicator(
            progress = { currentPage.toFloat() / (totalPages - 1) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = ElectricCyan(),
            trackColor = DarkSurfaceElevated()
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (currentPage) {
            0 -> OnboardingPage(
                icon = Icons.Default.Info,
                title = "Welcome to Sportage OBD",
                body = "Your two-in-one automotive companion: real-time OBD-II diagnostics and a car launcher for head units and phones."
            )
            1 -> OnboardingPage(
                icon = Icons.Default.Bluetooth,
                title = "Connect Your Adapter",
                body = "Pair an ELM327 Bluetooth adapter with your phone, then connect in the app. Required permissions: Bluetooth, location (for device scanning), and notifications (for alerts)."
            )
            2 -> OnboardingPage(
                icon = Icons.Default.PrivacyTip,
                title = "Your Data Stays Private",
                body = "Vehicle data (OBD readings, DTCs, trips) is stored locally on your device. Crash reports are optional. No vehicle data is sent to a cloud service for advertising or profiling."
            )
            3 -> OnboardingPage(
                icon = Icons.Default.DirectionsCar,
                title = "Ready to Drive",
                body = "Explore live gauges, scan for trouble codes, track trips and fuel, control media, and navigate — all from one screen."
            )
            4 -> OnboardingPage(
                icon = Icons.Default.CloudDownload,
                title = "Optional Crash Reports",
                body = "If the app crashes unexpectedly, you may send an anonymous crash report through Firebase Crashlytics to help us fix it. No name, contacts, location history, trips, or diagnostic trouble codes are included. You can leave this off and the app still works fully."
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (currentPage > 0) {
                OutlinedButton(
                    onClick = { currentPage-- },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryText())
                ) {
                    Text("Back")
                }
            }

            Button(
                onClick = {
                    if (currentPage < totalPages - 1) {
                        currentPage++
                    } else {
                        onComplete()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan())
            ) {
                Text(if (currentPage < totalPages - 1) "Next" else "Get Started")
                Icon(
                    if (currentPage < totalPages - 1) Icons.Default.ArrowForward else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = ElectricCyan()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            color = PrimaryText(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = body,
            color = SecondaryText(),
            fontSize = 15.sp,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
    }
}
