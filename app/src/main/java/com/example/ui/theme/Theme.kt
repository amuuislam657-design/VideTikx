package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MyTubeDarkColorScheme = darkColorScheme(
    primary = TubeCrimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3F121B),
    onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = TubeCrimsonSoft,
    onSecondary = Color(0xFF28080E),
    secondaryContainer = DarkSurfaceHigh,
    onSecondaryContainer = DarkOnBackground,
    tertiary = WarningAmber,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceElevated,
    surfaceContainerHigh = DarkSurfaceHigh,
    outline = DarkOutline,
    outlineVariant = Color(0xFF1F1F29),
    error = Color(0xFFFF5C5C),
    onError = Color.White
)

private val MyTubeLightColorScheme = lightColorScheme(
    primary = TubeCrimsonDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5E9),
    onPrimaryContainer = Color(0xFF6B091A),
    secondary = TubeCrimson,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceElevated,
    onSecondaryContainer = LightOnBackground,
    tertiary = WarningAmber,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightBackground,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceElevated,
    surfaceContainerHigh = LightSurfaceHigh,
    outline = LightOutline,
    outlineVariant = Color(0xFFEBEBF2),
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun MyTubeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MyTubeDarkColorScheme else MyTubeLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MyTubeTypography,
        content = content
    )
}
