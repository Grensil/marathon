package com.example.marathon.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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

private val MarathonLightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = TextWhite,
    secondary = AccentOrange,
    onSecondary = TextWhite,
    tertiary = AccentRed,
    background = LightBackground,
    onBackground = TextBlack,
    surface = LightSurface,
    onSurface = TextBlack,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextDarkGray,
    outline = DividerLight
)

@Composable
fun MarathonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MarathonDarkColorScheme else MarathonLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
