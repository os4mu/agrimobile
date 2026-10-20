package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AgriPrimaryDarkGreen,
    onPrimary = Color.White,
    secondary = AgriSecondaryGreen,
    onSecondary = Color.White,
    secondaryContainer = AgriLightGreen,
    onSecondaryContainer = AgriPrimaryDarkGreen,
    background = Color.White,
    onBackground = AgriTextPrimary,
    surface = Color.White,
    onSurface = AgriTextPrimary,
    surfaceVariant = Color.White,
    onSurfaceVariant = AgriTextPrimary,
    surfaceTint = Color.Transparent,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White,
    outline = AgriBorder,
    outlineVariant = AgriBorder
)

@Composable
fun AgriStockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
