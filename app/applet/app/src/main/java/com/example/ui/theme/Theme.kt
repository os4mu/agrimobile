package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = AgriPrimaryDarkGreen,
    onPrimary = AgriSurfaceWhite,
    secondary = AgriLightGreen,
    onSecondary = AgriPrimaryDarkGreen,
    background = AgriSurfaceWhite,
    onBackground = AgriTextPrimary,
    surface = AgriSurfaceWhite,
    onSurface = AgriTextPrimary
)

@Composable
fun AgriStockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
