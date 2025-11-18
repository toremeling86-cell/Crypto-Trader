package com.cryptotrader.presentation.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cryptotrader.domain.model.Trade
import com.cryptotrader.presentation.theme.profit
import com.cryptotrader.presentation.theme.loss
import com.cryptotrader.presentation.theme.warning
import com.cryptotrader.utils.formatCurrency
import com.cryptotrader.utils.formatTimeAgo
import com.cryptotrader.presentation.components.TradingModeIndicator

/**
 * Professional Wall Street-Level Dashboard
 * Clean, sophisticated design without emojis or amateur aesthetics
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val recentTrades by viewModel.recentTrades.collectAsState()
    val activeStrategies by viewModel.activeStrategies.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isPaperTrading = com.cryptotrader.utils.CryptoUtils.isPaperTradingMode(context)
    val isEmergencyStopped = com.cryptotrader.utils.CryptoUtils.isEmergencyStopActive(context)
    var showStopConfirmDialog by remember { mutableStateOf(false) }
    var selectedCurrency by remember { mutableStateOf(viewModel.getSelectedCurrency()) }

    // Emergency Stop Confirmation Dialog
    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "EMERGENCY STOP",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This will immediately:\n\n" +
                    "• Disable all active strategies\n" +
                    "• Stop all automated trading\n" +
                    "• Require manual resume\n\n" +
                    "Are you sure?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        viewModel.activateEmergencyStop()
                        showStopConfirmDialog = false
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text("Stop All Trading", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", fontWeight = FontWeight.SemiBold) },
                actions = {
                    // Trading Mode Indicator
                    TradingModeIndicator(
                        isLiveMode = !isPaperTrading,
                        isCompact = true,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    // Currency toggle - cycles through USD -> EUR -> NOK
                    FilledTonalButton(
                        onClick = {
                            selectedCurrency = when (selectedCurrency) {
                                com.cryptotrader.data.preferences.Currency.USD -> com.cryptotrader.data.preferences.Currency.EUR
                                com.cryptotrader.data.preferences.Currency.EUR -> com.cryptotrader.data.preferences.Currency.NOK
                                com.cryptotrader.data.preferences.Currency.NOK -> com.cryptotrader.data.preferences.Currency.USD
                            }
                            viewModel.setSelectedCurrency(selectedCurrency)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = selectedCurrency.code,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Paper Trading Mode Indicator - Professional Design
            if (isPaperTrading) {
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.warning),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.warning.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.warning,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PAPER TRADING MODE",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.warning
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Simulated trading • No real funds at risk",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Trading Status - Professional Design with LED Indicator
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Professional LED Status Indicator
                            Canvas(modifier = Modifier.size(12.dp)) {
                                drawCircle(
                                    color = if (state.isTradingActive)
                                        androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                    else
                                        androidx.compose.ui.graphics.Color(0xFF9E9E9E)
                                )
                            }
                            Column {
                                Text(
                                    text = if (state.isTradingActive) "LIVE TRADING" else "TRADING PAUSED",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (state.isTradingActive)
                                        "${activeStrategies.size} active strategies"
                                    else
                                        "Start to enable automation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                if (state.isTradingActive) {
                                    viewModel.stopTrading()
                                } else {
                                    viewModel.startTrading()
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (state.isTradingActive)
                                    MaterialTheme.colorScheme.errorContainer
                                else
                                    MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = if (state.isTradingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.isTradingActive) "Stop" else "Start",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Emergency Stop - Professional Design
            item {
                if (isEmergencyStopped) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "EMERGENCY STOP ACTIVE",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "All automated trading halted",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = { viewModel.deactivateEmergencyStop() },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Resume Trading", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showStopConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "EMERGENCY STOP",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Loading State
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Error State
            state.errorMessage?.let { error ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Portfolio Summary Card - Professional Design
            state.portfolio?.let { portfolio ->
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Portfolio Value",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // Show exchange rate info
                                when (selectedCurrency) {
                                    com.cryptotrader.data.preferences.Currency.EUR -> {
                                        Text(
                                            text = "1 EUR = ${String.format("$%.4f", portfolio.eurUsdRate)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    com.cryptotrader.data.preferences.Currency.NOK -> {
                                        Text(
                                            text = "$1 = ${String.format("%.2f kr", portfolio.usdNokRate)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    else -> {} // USD selected - no exchange rate needed
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when (selectedCurrency) {
                                    com.cryptotrader.data.preferences.Currency.EUR -> portfolio.totalValueEUR.formatCurrency(selectedCurrency)
                                    com.cryptotrader.data.preferences.Currency.USD -> portfolio.totalValue.formatCurrency(selectedCurrency)
                                    com.cryptotrader.data.preferences.Currency.NOK -> portfolio.totalValueNOK.formatCurrency(selectedCurrency)
                                },
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(20.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(16.dp))

                            // Total P&L
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Total P&L",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when (selectedCurrency) {
                                            com.cryptotrader.data.preferences.Currency.EUR -> portfolio.totalProfitEUR.formatCurrency(selectedCurrency)
                                            com.cryptotrader.data.preferences.Currency.USD -> portfolio.totalProfit.formatCurrency(selectedCurrency)
                                            com.cryptotrader.data.preferences.Currency.NOK -> portfolio.totalProfitNOK.formatCurrency(selectedCurrency)
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (portfolio.totalProfit >= 0)
                                            MaterialTheme.colorScheme.profit
                                        else
                                            MaterialTheme.colorScheme.loss
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format("%.2f%%", portfolio.totalProfitPercent),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (portfolio.totalProfit >= 0)
                                            MaterialTheme.colorScheme.profit
                                        else
                                            MaterialTheme.colorScheme.loss
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Daily P&L
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Today's P&L",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when (selectedCurrency) {
                                            com.cryptotrader.data.preferences.Currency.EUR -> portfolio.dayProfitEUR.formatCurrency(selectedCurrency)
                                            com.cryptotrader.data.preferences.Currency.USD -> portfolio.dayProfit.formatCurrency(selectedCurrency)
                                            com.cryptotrader.data.preferences.Currency.NOK -> portfolio.dayProfitNOK.formatCurrency(selectedCurrency)
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (portfolio.dayProfit >= 0)
                                            MaterialTheme.colorScheme.profit
                                        else
                                            MaterialTheme.colorScheme.loss
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format("%.2f%%", portfolio.dayProfitPercent),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (portfolio.dayProfit >= 0)
                                            MaterialTheme.colorScheme.profit
                                        else
                                            MaterialTheme.colorScheme.loss
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Active Strategies Section
            item {
                Text(
                    text = "Active Strategies",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (activeStrategies.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "No active strategies",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(activeStrategies) { strategy ->
                    StrategyCard(strategy = strategy)
                }
            }

            // Recent Trades Section
            item {
                Text(
                    text = "Recent Trades",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (recentTrades.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "No trades yet",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(recentTrades.take(10)) { trade ->
                    TradeCard(trade = trade)
                }
            }
        }
    }
}

@Composable
fun StrategyCard(strategy: com.cryptotrader.domain.model.Strategy) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Name and Top Performer Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Professional status indicator
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(
                            color = if (strategy.isActive)
                                androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            else
                                androidx.compose.ui.graphics.Color(0xFF9E9E9E)
                        )
                    }
                    Text(
                        text = strategy.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Top Performer Badge (Phase 3C)
                if (strategy.isTopPerformer) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF4CAF50))
                    ) {
                        Text(
                            text = "TOP 10%",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // AI-Generated Strategy Info (Phase 3C)
            if (strategy.source == com.cryptotrader.domain.model.StrategySource.AI_CLAUDE && strategy.sourceReportCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "AI-Generated from ${ strategy.sourceReportCount} expert reports",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Performance Score (Phase 3C)
            if (strategy.performanceScore > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Performance Score",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.1f/100", strategy.performanceScore),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            strategy.performanceScore >= 80.0 -> Color(0xFF4CAF50)
                            strategy.performanceScore >= 60.0 -> Color(0xFFFFA726)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Core Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trades",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = strategy.totalTrades.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Win Rate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.1f%%", strategy.winRate),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (strategy.winRate >= 50.0)
                            MaterialTheme.colorScheme.profit
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
                if (strategy.totalProfit != 0.0) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "P&L",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = strategy.totalProfit.formatCurrency(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (strategy.totalProfit >= 0)
                                MaterialTheme.colorScheme.profit
                            else
                                MaterialTheme.colorScheme.loss
                        )
                    }
                }
            }

            // Advanced Metrics (Phase 3C) - Show if strategy has trades
            if (strategy.totalTrades > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // Profit Factor and Sharpe Ratio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (strategy.profitFactor > 0.0) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Profit Factor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%.2f", strategy.profitFactor),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    strategy.profitFactor >= 2.0 -> Color(0xFF4CAF50)
                                    strategy.profitFactor >= 1.0 -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.loss
                                }
                            )
                        }
                    }

                    strategy.sharpeRatio?.let { sharpe ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sharpe Ratio",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%.2f", sharpe),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    sharpe >= 1.5 -> Color(0xFF4CAF50)
                                    sharpe >= 1.0 -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.loss
                                }
                            )
                        }
                    }

                    if (strategy.maxDrawdown != 0.0) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Max Drawdown",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%.1f%%", strategy.maxDrawdown),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.loss
                            )
                        }
                    }
                }

                // Current Streak
                if (strategy.currentStreak != 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Streak",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${if (strategy.currentStreak > 0) "+" else ""}${strategy.currentStreak}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (strategy.currentStreak > 0)
                                MaterialTheme.colorScheme.profit
                            else
                                MaterialTheme.colorScheme.loss
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TradeCard(trade: Trade) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Professional side indicator
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(
                        color = if (trade.type.name == "BUY")
                            androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        else
                            androidx.compose.ui.graphics.Color(0xFFE57373)
                    )
                }
                Column {
                    Text(
                        text = "${trade.type.name} ${trade.pair}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = trade.timestamp.formatTimeAgo(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = trade.price.formatCurrency(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format("%.4f", trade.volume),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
