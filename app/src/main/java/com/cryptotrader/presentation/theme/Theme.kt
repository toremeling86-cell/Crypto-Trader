package com.cryptotrader.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CryptoBlue80,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFCCE5FF),

    secondary = CryptoCyan80,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFFA6EEFF),

    tertiary = CryptoGreen80,
    onTertiary = Color(0xFF00390A),
    tertiaryContainer = Color(0xFF005314),
    onTertiaryContainer = Color(0xFF9BF7A8),

    error = CryptoRed80,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = DarkBackground,
    onBackground = Color(0xFFE3E2E6),

    surface = DarkSurface,
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC4C6D0),

    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44464F)
)

private val LightColorScheme = lightColorScheme(
    primary = CryptoBlue40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCE5FF),
    onPrimaryContainer = Color(0xFF001E30),

    secondary = CryptoCyan40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA6EEFF),
    onSecondaryContainer = Color(0xFF001F24),

    tertiary = CryptoGreen40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9BF7A8),
    onTertiaryContainer = Color(0xFF002106),

    error = CryptoRed40,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = LightBackground,
    onBackground = Color(0xFF1A1C1E),

    surface = LightSurface,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF44464F),

    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0)
)

@Composable
fun CryptoTraderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Extension property to access spacing tokens
 * Usage: MaterialTheme.spacing.medium
 */
val MaterialTheme.spacing: Spacing
    @Composable
    get() = LocalSpacing.current
