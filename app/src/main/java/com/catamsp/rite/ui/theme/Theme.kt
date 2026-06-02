package com.catamsp.rite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF1C1C1E),
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color(0xFFFFFFFF),
    error = Color(0xFFFF453A),
    tertiary = Color(0xFF30D158),
    tertiaryContainer = Color(0xFFFFD60A),
    outlineVariant = Color(0xFF3A3A3C),
    errorContainer = Color(0xFF2C2C2E),
    onErrorContainer = Color(0xFFEBEBF5),
)

val SurfaceTertiary = Color(0xFF6E6E73)
val OutlineDim = Color(0xFF636366)

@Composable
fun RiteTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
