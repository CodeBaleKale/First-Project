package com.snapcal.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF0F766E)
val TealSoft = Color(0xFFD7EEEB)
val Cream = Color(0xFFFAF7F2)
val Ink = Color(0xFF21262B)
val Amber = Color(0xFFB45309)
val AmberSoft = Color(0xFFFDEEDE)
val DangerRed = Color(0xFFB91C1C)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealSoft,
    onPrimaryContainer = Teal,
    secondary = Amber,
    secondaryContainer = AmberSoft,
    onSecondaryContainer = Amber,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    error = DangerRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EC8BD),
    primaryContainer = Color(0xFF0B4F4A),
    secondary = Color(0xFFE8A35D),
)

@Composable
fun SnapCalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
