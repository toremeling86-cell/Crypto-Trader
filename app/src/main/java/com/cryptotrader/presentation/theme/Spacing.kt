package com.cryptotrader.presentation.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Professional Spacing System for CryptoTrader
 * Provides consistent spacing tokens throughout the app
 */
data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 48.dp
)

/**
 * CompositionLocal for accessing spacing tokens
 * Usage: MaterialTheme.spacing.medium
 */
val LocalSpacing = compositionLocalOf { Spacing() }
