package com.aicardgrader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Navy,
    secondary = NavyLight,
    background = Surface,
    surface = Navy,
    onBackground = Cream,
    onSurface = Cream
)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Cream,
    secondary = Gold,
    background = Cream,
    surface = Color(0xFFFFFFFF),
    onBackground = Navy,
    onSurface = Navy
)

@Composable
fun AiCardGraderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
