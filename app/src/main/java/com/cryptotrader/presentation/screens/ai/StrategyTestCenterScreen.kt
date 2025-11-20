package com.cryptotrader.presentation.screens.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cryptotrader.domain.backtesting.BacktestResult
import com.cryptotrader.domain.model.Strategy
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StrategyTestCenterScreen(
    viewModel: StrategyTestCenterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val availableDatasets by viewModel.availableDatasets.collectAsState()
    val activeDataset by viewModel.activeDataset.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Strategy Test Center",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Backtest strategies with realistic fees and slippage",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.backtestResult != null -> {
                BacktestResultView(
                    result = uiState.backtestResult!!,
                    onRunAnother = { viewModel.resetToStrategySelection() }
                )
            }
            else -> {
                StrategySelectionView(
                    strategies = uiState.strategies,
                    selectedStrategy = uiState.selectedStrategy,
                    onStrategySelected = { viewModel.selectStrategy(it) },
                    backtestConfig = uiState.backtestConfig,
                    onStartBacktest = { viewModel.runBacktest() },
                    availableDatasets = availableDatasets,
                    activeDataset = activeDataset,
                    onDatasetSelected = { viewModel.activateDataset(it) }
                )
            }
        }
    }
}

@Composable
fun StrategySelectionView(
    strategies: List<Strategy>,
    selectedStrategy: Strategy?,
    onStrategySelected: (Strategy) -> Unit,
    backtestConfig: BacktestConfig,
    onStartBacktest: () -> Unit,
    availableDatasets: List<com.cryptotrader.domain.model.ManagedDataset>,
    activeDataset: com.cryptotrader.domain.model.ManagedDataset?,
    onDatasetSelected: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dataset Manager Section
        item {
            DatasetSelectorSection(
                availableDatasets = availableDatasets,
                activeDataset = activeDataset,
                onDatasetSelected = onDatasetSelected
            )
        }

        // Strategy Selection Header
        item {
            Text(
                text = "1. Select Strategy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (strategies.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No strategies available",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Create a strategy in the AI Assistant tab first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(strategies) { strategy ->
                StrategySelectCard(
                    strategy = strategy,
                    isSelected = selectedStrategy?.id == strategy.id,
                    onSelect = { onStrategySelected(strategy) }
                )
            }
        }

        if (selectedStrategy != null) {
            // Backtest Configuration Header
            item {
                Text(
                    text = "2. Test Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                BacktestConfigCard(config = backtestConfig)
            }

            // Run Backtest Button
            item {
                Button(
                    onClick = onStartBacktest,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run Backtest", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategySelectCard(
    strategy: Strategy,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strategy.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (strategy.source == com.cryptotrader.domain.model.StrategySource.AI_CLAUDE) {
                        Text(
                            text = "AI Generated",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoChip("SL: ${strategy.stopLossPercent}%")
                InfoChip("TP: ${strategy.takeProfitPercent}%")
                InfoChip("Size: ${strategy.positionSizePercent}%")
            }
        }
    }
}

@Composable
fun InfoChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun BacktestConfigCard(config: BacktestConfig) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Starting Balance:", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "$${NumberFormat.getNumberInstance().format(config.startingBalance)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Maker Fee:", style = MaterialTheme.typography.bodyMedium)
                Text("${config.makerFee * 100}%", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Taker Fee:", style = MaterialTheme.typography.bodyMedium)
                Text("${config.takerFee * 100}%", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Slippage:", style = MaterialTheme.typography.bodyMedium)
                Text("${config.slippagePercent}%", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Data Points:", style = MaterialTheme.typography.bodyMedium)
                Text("${config.dataPoints} candles", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun BacktestResultView(
    result: BacktestResult,
    onRunAnother: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.totalPnL >= 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = result.strategyName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total P&L", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${if (result.totalPnL >= 0) "+" else ""}$${NumberFormat.getNumberInstance().format(result.totalPnL)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${if (result.totalPnLPercent >= 0) "+" else ""}${String.format("%.2f", result.totalPnLPercent)}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Ending Balance", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "$${NumberFormat.getNumberInstance().format(result.endingBalance)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            // Performance Metrics
            Text(
                text = "Performance Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MetricRow("Total Trades", "${result.totalTrades}")
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    MetricRow("Winning Trades", "${result.winningTrades}")
                    MetricRow("Losing Trades", "${result.losingTrades}")
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    MetricRow("Win Rate", "${String.format("%.1f", result.winRate)}%")
                    MetricRow("Profit Factor", String.format("%.2f", result.profitFactor))
                    MetricRow("Sharpe Ratio", String.format("%.2f", result.sharpeRatio))
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    MetricRow("Max Drawdown", "$${NumberFormat.getNumberInstance().format(result.maxDrawdown)}")
                    MetricRow("Best Trade", "$${NumberFormat.getNumberInstance().format(result.bestTrade)}")
                    MetricRow("Worst Trade", "$${NumberFormat.getNumberInstance().format(result.worstTrade)}")
                }
            }
        }

        item {
            // Cost Analysis
            Text(
                text = "Cost Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MetricRow(
                        "Total Fees & Costs",
                        "$${NumberFormat.getNumberInstance().format(result.totalFees)}"
                    )
                    Text(
                        text = "Includes trading fees, slippage, and spread costs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricRow(
                        "Cost per Trade",
                        if (result.totalTrades > 0) {
                            "$${String.format("%.2f", result.totalFees / result.totalTrades)}"
                        } else {
                            "$0.00"
                        }
                    )
                    MetricRow(
                        "Costs as % of Starting",
                        "${String.format("%.2f", (result.totalFees / result.startingBalance) * 100)}%"
                    )
                }
            }
        }

        item {
            Button(
                onClick = onRunAnother,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Run Another Test")
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DatasetSelectorSection(
    availableDatasets: List<com.cryptotrader.domain.model.ManagedDataset>,
    activeDataset: com.cryptotrader.domain.model.ManagedDataset?,
    onDatasetSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 Dataset Manager",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select a curated dataset for backtesting",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (availableDatasets.isEmpty()) {
                Text(
                    text = "No datasets available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                )
            } else {
                availableDatasets.forEach { dataset ->
                    DatasetCard(
                        dataset = dataset,
                        isActive = dataset.id == activeDataset?.id,
                        onClick = { onDatasetSelected(dataset.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetCard(
    dataset: com.cryptotrader.domain.model.ManagedDataset,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isActive) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = dataset.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (dataset.isFavorite) {
                        Text("⭐", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = dataset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🪙 ${dataset.asset}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                    Text(
                        text = "⏱ ${dataset.timeframe}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                    Text(
                        text = "📊 ${dataset.barCount} bars",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Tier: ${dataset.dataTier.tierName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )
            }
        }
    }
}
