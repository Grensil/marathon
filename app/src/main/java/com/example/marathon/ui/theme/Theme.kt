package com.example.marathon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MarathonDarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = DarkBackground,
    secondary = AccentOrange,
    onSecondary = TextWhite,
    tertiary = AccentRed,
    background = DarkBackground,
    onBackground = TextWhite,
    surface = DarkSurface,
    onSurface = TextWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextLightGray,
    outline = DividerDark
)

@Composable
fun MarathonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MarathonDarkColorScheme,
        content = content
    )
}
