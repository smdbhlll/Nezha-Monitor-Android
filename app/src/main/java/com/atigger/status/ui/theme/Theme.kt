package com.atigger.status.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = StatusBlueDark,
    secondary = StatusMintDark,
    tertiary = StatusBlueDark,
    background = StatusSurfaceDark,
    surface = Color(0xFF132033),
    surfaceContainerHigh = Color(0xFF18263A),
    onPrimary = Color(0xFF0E1830),
    onBackground = Color(0xFFF2F6FF),
    onSurface = Color(0xFFF2F6FF),
    onSurfaceVariant = Color(0xFFAFC1DD)
)

private val LightColorScheme = lightColorScheme(
    primary = StatusBlue,
    secondary = StatusMint,
    tertiary = StatusBlue,
    background = Color(0xFFF4F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = StatusInk,
    onSurface = StatusInk,
    onSurfaceVariant = Color(0xFF596579)
)

@Composable
fun StatusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
