package com.ecoloop.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EcoColorScheme = lightColorScheme(
    primary = EcoPrimary,
    onPrimary = Color.White,
    primaryContainer = EcoMint,
    onPrimaryContainer = EcoInk,
    secondary = EcoAccent,
    onSecondary = Color.White,
    secondaryContainer = EcoMint,
    onSecondaryContainer = EcoInk,
    background = EcoBackground,
    onBackground = EcoInk,
    surface = Color.White,
    onSurface = EcoInk,
    surfaceVariant = EcoBackground,
    onSurfaceVariant = EcoMuted,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = EcoMint,
    outline = EcoOutline,
    outlineVariant = EcoOutline,
    error = EcoError,
)

private val EcoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun EcoLoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EcoColorScheme,
        typography = EcoTypography,
        shapes = EcoShapes,
        content = content,
    )
}
