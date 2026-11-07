package com.sportage.obd.ui

data class PanelReading(
    val label: String,
    val value: String,
    val unit: String = "",
    val color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White
)
