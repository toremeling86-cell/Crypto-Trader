package com.cryptotrader.presentation.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cryptotrader.domain.model.Trade
import com.cryptotrader.utils.formatCurrency
import com.cryptotrader.utils.formatTimeAgo

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val recentTrades by viewModel.recentTrades.collectAsState()
    val activeStrategies by viewModel.activeStrategies.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Don't cache paper trading mode - read it fresh each time so it updates when changed in Settings
    val isPaperTrading = com.cryptotrader.utils.CryptoUtils.isPaperTradingMode(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Paper Trading Banner
        if (isPaperTrading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFA500) // Orange
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📄 PAPER TRADING MODE",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Trading with simulated money - No real funds at risk",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Trading Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isTradingActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (state.isTradingActive) "🟢 LIVE TRADING ACTIVE" else "⚪ TRADING STOPPED",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = if (state.isTradingActive)
                            "Checking ${activeStrategies.size} strategies every minute"
                        else
                            "Click START to begin automated trading",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                Button(
                    onClick = {
                        if (state.isTradingActive) {
                            viewModel.stopTrading()
                        } else {
                            viewModel.startTrading()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isTradingActive) Color.Red else Color.Green
                    )
                ) {
                    Text(
                        text = if (state.isTradingActive) "STOP" else "START",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Emergency Stop Button
        // Don't cache emergency stop status - read it fresh each time
        val isEmergencyStopped = com.cryptotrader.utils.CryptoUtils.isEmergencyStopActive(context)
        var showStopConfirmDialog by remember { mutableStateOf(false) }

        if (showStopConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showStopConfirmDialog = false },
                title = { Text("🚨 EMERGENCY STOP") },
                text = {
                    Text("This will IMMEDIATELY:\n\n• Disable all active strategies\n• Stop all automated trading\n\nAre you sure?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.activateEmergencyStop()
                            showStopConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("STOP ALL TRADING")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStopConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (isEmergencyStopped) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Red)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🚨 EMERGENCY STOP ACTIVE",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All automated trading is HALTED",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.deactivateEmergencyStop() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("Resume Trading", color = Color.Red)
                    }
                }
            }
        } else {
            Button(
                onClick = { showStopConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text(
                    text = "🚨 EMERGENCY STOP ALL TRADING",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.errorMessage != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = state.errorMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            else -> {
                // Portfolio Card
                state.portfolio?.let { portfolio ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Total Portfolio Value",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = portfolio.totalValue.formatCurrency(),
                                style = MaterialTheme.typography.headlineLarge
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Total P&L
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Total P&L",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = portfolio.totalProfit.formatCurrency(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (portfolio.totalProfit >= 0) Color.Green else Color.Red
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format("%.2f%%", portfolio.totalProfitPercent),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (portfolio.totalProfit >= 0) Color.Green else Color.Red
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
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = portfolio.dayProfit.formatCurrency(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (portfolio.dayProfit >= 0) Color.Green else Color.Red
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format("%.2f%%", portfolio.dayProfitPercent),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (portfolio.dayProfit >= 0) Color.Green else Color.Red
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Active Strategies
                Text(
                    text = "Active Strategies (${activeStrategies.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (activeStrategies.isEmpty()) {
                    Text(
                        text = "No active strategies",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(0.4f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activeStrategies) { strategy ->
                            StrategyCard(strategy = strategy)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Recent Trades
                Text(
                    text = "Recent Trades",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (recentTrades.isEmpty()) {
                    Text(
                        text = "No trades yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(0.6f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentTrades.take(10)) { trade ->
                            TradeCard(trade = trade)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StrategyCard(strategy: com.cryptotrader.domain.model.Strategy) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = strategy.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${strategy.totalTrades} trades | ${String.format("%.1f%%", strategy.winRate)} win rate",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun TradeCard(trade: Trade) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${trade.type} ${trade.pair}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (trade.type.name == "BUY") Color.Green else Color.Red
                )
                Text(
                    text = trade.timestamp.formatTimeAgo(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = trade.price.formatCurrency(), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Vol: ${String.format("%.4f", trade.volume)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
