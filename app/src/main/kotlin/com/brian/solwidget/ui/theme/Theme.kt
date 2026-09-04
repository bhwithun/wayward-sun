package com.brian.solwidget.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object SolColors {
    val Navy = Color(0xFF0B1220)
    val Panel = Color(0xFF152036)
    val PanelAlt = Color(0xFF1C2B45)
    val TableStripe = Color(0xFF2A4163)
    val Live = Color(0xFF2EE6A6)
    val Forecast = Color(0xFFF5C542)
    val Now = Color(0xFFFF8A4C)
    val Capacity = Color(0xFFD7DEEA)
    val Ink = Color(0xFFE8EEF7)
    val Muted = Color(0xFF9AA8BF)
    val Grid = Color(0x334A6288)
    val TouOff = Color(0x142EE6A6)
    val TouMid = Color(0x33C9A227)
    val TouPeak = Color(0x4DE07070)
    val Temp = Color(0xFFFFFFFF)
    val TempFreeze = Color(0xFF64B5F6)
    val TempLow = Color(0xFF4EE6E6)
    val TempHot = Color(0xFFFF8A4C)
    val Precip = Color(0x665CA8FF)
}

private val DarkColorScheme = darkColorScheme(
    primary = SolColors.Forecast,
    onPrimary = Color(0xFF1A1403),
    secondary = SolColors.Live,
    onSecondary = Color(0xFF042117),
    background = SolColors.Navy,
    surface = SolColors.Panel,
    onBackground = SolColors.Ink,
    onSurface = SolColors.Ink,
    surfaceVariant = SolColors.PanelAlt,
    onSurfaceVariant = SolColors.Muted,
    error = Color(0xFFFF8A80)
)

@Composable
fun SolWidgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
