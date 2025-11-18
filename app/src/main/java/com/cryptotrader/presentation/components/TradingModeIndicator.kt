package com.cryptotrader.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Trading mode indicator component
 *
 * Displays a clear visual indicator for Live vs Paper trading mode
 * - Live Mode: Red/orange background with warning indicator
 * - Paper Mode: Blue/green background with info indicator
 */
@Composable
fun TradingModeIndicator(
    isLiveMode: Boolean,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    isCompact: Boolean = false
) {
    val backgroundColor = if (isLiveMode) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val textColor = if (isLiveMode) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    val text = if (isLiveMode) {
        if (isCompact) "LIVE" else "LIVE MODE"
    } else {
        if (isCompact) "PAPER" else "PAPER MODE"
    }

    val icon = if (showIcon) {
        if (isLiveMode) "⚠️ " else "📄 "
    } else {
        ""
    }

    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(
                horizontal = if (isCompact) 12.dp else 16.dp,
                vertical = if (isCompact) 6.dp else 8.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$icon$text",
            style = MaterialTheme.typography.labelMedium,
            fontSize = if (isCompact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/**
 * Large trading mode banner
 * For use in settings or prominent locations
 */
@Composable
fun TradingModeBanner(
    isLiveMode: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isLiveMode) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val textColor = if (isLiveMode) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    val title = if (isLiveMode) {
        "⚠️ LIVE TRADING MODE"
    } else {
        "📄 PAPER TRADING MODE"
    }

    val description = if (isLiveMode) {
        "Real money trades are active. All orders will be executed on Kraken."
    } else {
        "Simulated trading for practice. No real money involved."
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isLiveMode) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }
    }
}
