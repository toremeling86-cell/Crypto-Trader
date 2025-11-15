package com.cryptotrader.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Dark Theme Colors - Professional Crypto/Finance Theme
val CryptoBlue80 = Color(0xFF64B5F6)        // Light blue for dark theme
val CryptoCyan80 = Color(0xFF4DD0E1)        // Light cyan for dark theme
val CryptoGreen80 = Color(0xFF81C784)       // Light green for dark theme
val CryptoOrange80 = Color(0xFFFFB74D)      // Light orange for dark theme
val CryptoRed80 = Color(0xFFE57373)         // Light red for dark theme

val DarkBackground = Color(0xFF121212)       // Material 3 dark background
val DarkSurface = Color(0xFF1E1E1E)          // Slightly lighter surface
val DarkSurfaceVariant = Color(0xFF2C2C2C)   // Even lighter for cards

// Light Theme Colors - Professional Crypto/Finance Theme
val CryptoBlue40 = Color(0xFF1976D2)        // Dark blue for light theme
val CryptoCyan40 = Color(0xFF0097A7)        // Dark cyan for light theme
val CryptoGreen40 = Color(0xFF388E3C)       // Dark green for light theme
val CryptoOrange40 = Color(0xFFF57C00)      // Dark orange for light theme
val CryptoRed40 = Color(0xFFD32F2F)         // Dark red for light theme

val LightBackground = Color(0xFFFAFAFA)      // Material 3 light background
val LightSurface = Color(0xFFFFFFFF)         // Pure white surface
val LightSurfaceVariant = Color(0xFFF5F5F5)  // Slightly darker for cards

// Semantic Color Extensions
val ColorScheme.success: Color
    get() = if (background.luminance() > 0.5) {
        CryptoGreen40  // Dark green for light theme
    } else {
        CryptoGreen80  // Light green for dark theme
    }

val ColorScheme.profit: Color
    get() = success

val ColorScheme.loss: Color
    get() = error

val ColorScheme.warning: Color
    get() = if (background.luminance() > 0.5) {
        CryptoOrange40  // Dark orange for light theme
    } else {
        CryptoOrange80  // Light orange for dark theme
    }
