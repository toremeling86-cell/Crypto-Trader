package com.cryptotrader.presentation.screens.strategy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun StrategyConfigScreen(
    viewModel: StrategyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val strategies by viewModel.strategies.collectAsState()
    val pendingStrategies by viewModel.pendingStrategies.collectAsState()
    val performances by viewModel.strategyPerformances.collectAsState()

    // Activation Confirmation Dialog
    state.confirmDialogStrategyId?.let { strategyId ->
        val strategy = strategies.find { it.id == strategyId }
        if (strategy != null) {
            ConfirmActivationDialog(
                strategy = strategy,
                onConfirm = { viewModel.confirmToggleStrategy(strategyId, true) },
                onDismiss = { viewModel.dismissConfirmDialog() }
            )
        }
    }

    // Delete Confirmation Dialog
    state.deleteConfirmStrategy?.let { strategy ->
        ConfirmDeleteDialog(
            strategy = strategy,
            onConfirm = { viewModel.confirmDeleteStrategy() },
            onDismiss = { viewModel.dismissDeleteConfirmDialog() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Trading Strategies",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pending Strategies Section (AI-generated strategies awaiting approval)
        if (pendingStrategies.isNotEmpty()) {
            Text(
                text = "Pending AI Strategies (${pendingStrategies.size})",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.height(300.dp), // Fixed height for pending section
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pendingStrategies) { strategy ->
                    PendingStrategyCard(
                        strategy = strategy,
                        onApprove = { viewModel.approveStrategy(strategy.id) },
                        onReject = { viewModel.rejectStrategy(strategy.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Info Card - Direct users to AI Chat for creating strategies
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Lag nye strategier med AI",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Gå til AI Chat-fanen for å la Claude generere trading strategier basert på live markedsdata.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Strategies List
        Text(
            text = "Your Strategies (${strategies.size})",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (strategies.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No strategies yet. Create one above!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(strategies) { strategy ->
                    StrategyItem(
                        strategy = strategy,
                        performance = performances[strategy.id],
                        onToggle = { viewModel.toggleStrategy(strategy.id, !strategy.isActive) },
                        onDelete = { viewModel.deleteStrategy(strategy) },
                        onSetTradingMode = { mode -> viewModel.setTradingMode(strategy.id, mode) }
                    )
                }
            }
        }
    }
}

@Composable
fun StrategyItem(
    strategy: com.cryptotrader.domain.model.Strategy,
    performance: com.cryptotrader.domain.analytics.StrategyPerformance?,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onSetTradingMode: (com.cryptotrader.domain.model.TradingMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strategy.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = strategy.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Trading Mode Selector
            Text(
                text = "Trading Mode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TradingModeButton(
                    label = "Inactive",
                    icon = "⏸️",
                    isSelected = strategy.tradingMode == com.cryptotrader.domain.model.TradingMode.INACTIVE,
                    onClick = { onSetTradingMode(com.cryptotrader.domain.model.TradingMode.INACTIVE) },
                    modifier = Modifier.weight(1f)
                )
                TradingModeButton(
                    label = "Paper",
                    icon = "📄",
                    isSelected = strategy.tradingMode == com.cryptotrader.domain.model.TradingMode.PAPER,
                    onClick = { onSetTradingMode(com.cryptotrader.domain.model.TradingMode.PAPER) },
                    modifier = Modifier.weight(1f)
                )
                TradingModeButton(
                    label = "Live",
                    icon = "💰",
                    isSelected = strategy.tradingMode == com.cryptotrader.domain.model.TradingMode.LIVE,
                    onClick = { onSetTradingMode(com.cryptotrader.domain.model.TradingMode.LIVE) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Basic Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem("Trades", "${performance?.totalTrades ?: 0}")
                StatItem("Win Rate", "${String.format("%.1f%%", performance?.winRate ?: 0.0)}")
                StatItem(
                    "P&L",
                    if (performance != null) {
                        String.format("$%.2f", performance.totalPnL)
                    } else "$0.00",
                    color = if ((performance?.totalPnL ?: 0.0) >= 0) Color.Green else Color.Red
                )
            }

            // Expandable detailed analytics
            if (performance != null && performance.totalTrades > 0) {
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (expanded) "Hide Details" else "Show Analytics")
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Performance Grid
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            DetailStatItem("Winning Trades", "${performance.winningTrades}")
                            DetailStatItem("Losing Trades", "${performance.losingTrades}")
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            DetailStatItem("Avg Profit", String.format("$%.2f", performance.averageProfit))
                            DetailStatItem("Avg Loss", String.format("$%.2f", performance.averageLoss))
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            DetailStatItem("Profit Factor", String.format("%.2f", performance.profitFactor))
                            DetailStatItem("Sharpe Ratio", String.format("%.2f", performance.sharpeRatio))
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            DetailStatItem("Best Trade", String.format("$%.2f", performance.bestTrade))
                            DetailStatItem("Worst Trade", String.format("$%.2f", performance.worstTrade))
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            DetailStatItem("Max Drawdown", String.format("$%.2f", performance.maxDrawdown))
                            DetailStatItem("Total P&L %", String.format("%.2f%%", performance.totalPnLPercent))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DetailStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ConfirmActivationDialog(
    strategy: com.cryptotrader.domain.model.Strategy,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Activate Trading Strategy?")
        },
        text = {
            Column {
                Text(
                    text = "You are about to activate automatic trading with real money:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Strategy: ${strategy.name}",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Pairs: ${strategy.tradingPairs.joinToString()}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Position Size: ${String.format("%.1f%%", strategy.positionSizePercent)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Stop Loss: ${String.format("%.1f%%", strategy.stopLossPercent)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Take Profit: ${String.format("%.1f%%", strategy.takeProfitPercent)}",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "WARNING: Cryptocurrency trading carries significant risk. Only trade with funds you can afford to lose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Activate with Real Money")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConfirmDeleteDialog(
    strategy: com.cryptotrader.domain.model.Strategy,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete Strategy?")
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to delete this strategy?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = strategy.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (strategy.isActive) {
                    Text(
                        text = "WARNING: This strategy is currently ACTIVE. Deleting it will stop all automatic trading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (strategy.totalTrades > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Trade History: ${strategy.totalTrades} trades will remain in your history.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PendingStrategyCard(
    strategy: com.cryptotrader.domain.model.Strategy,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strategy.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "AI-Generated • ${strategy.riskLevel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Strategy Info
            Text(
                text = strategy.description,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Trading Pairs
            Text(
                text = "Pairs: ${strategy.tradingPairs.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            // Risk Parameters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text(
                    text = "SL: ${String.format("%.1f%%", strategy.stopLossPercent)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "TP: ${String.format("%.1f%%", strategy.takeProfitPercent)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Size: ${String.format("%.1f%%", strategy.positionSizePercent)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Analysis Report (expandable)
            if (strategy.analysisReport != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (expanded) "Skjul Claude's Analyse" else "Vis Claude's Analyse")
                }

                if (expanded) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Claude's Market Analysis:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strategy.analysisReport,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50) // Green
                    )
                ) {
                    Text("Godkjenn")
                }

                Button(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Avvis")
                }
            }
        }
    }
}

/**
 * Button for selecting trading mode (Inactive, Paper, Live)
 */
@Composable
fun TradingModeButton(
    label: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isSelected && label == "Live" -> Color(0xFF4CAF50) // Green for Live
                isSelected && label == "Paper" -> Color(0xFF2196F3) // Blue for Paper
                isSelected -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
