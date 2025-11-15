package com.cryptotrader.presentation.screens.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cryptotrader.domain.model.*
import com.cryptotrader.presentation.theme.profit
import com.cryptotrader.presentation.theme.loss
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main Portfolio Screen with 5 tabs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }

    val holdingsState by viewModel.holdingsState.collectAsState()
    val performanceState by viewModel.performanceState.collectAsState()
    val activityState by viewModel.activityState.collectAsState()
    val analyticsState by viewModel.analyticsState.collectAsState()
    val riskState by viewModel.riskState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Portfolio") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Holdings", maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Performance", maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Activity", maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Analytics", maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("Risk", maxLines = 1) }
                )
            }

            // Tab content
            when (selectedTab) {
                0 -> HoldingsTab(holdingsState)
                1 -> PerformanceTab(performanceState, viewModel)
                2 -> ActivityTab(activityState, viewModel)
                3 -> AnalyticsTab(analyticsState)
                4 -> RiskTab(riskState)
            }
        }
    }
}

/**
 * Holdings Tab - Shows current portfolio holdings
 */
@Composable
fun HoldingsTab(state: HoldingsState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // Total value card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Total Portfolio Value",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "$${String.format("%.2f", state.totalValue)}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Holdings list
            items(state.holdings) { holding ->
                HoldingCard(holding)
            }

            // Error message
            state.error?.let { error ->
                item {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
    }
}

@Composable
fun HoldingCard(holding: PortfolioHolding) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    holding.assetName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${String.format("%.2f", holding.percentOfPortfolio)}%",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Amount", style = MaterialTheme.typography.bodySmall)
                    Text(
                        String.format("%.8f", holding.amount),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Value", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "$${String.format("%.2f", holding.currentValue)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Price: $${String.format("%.2f", holding.currentPrice)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    holding.assetType.name,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Performance Tab - Shows P&L and performance metrics
 */
@Composable
fun PerformanceTab(state: PerformanceState, viewModel: PortfolioViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Period selector
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TimePeriod.values().forEach { period ->
                    FilterChip(
                        selected = state.selectedPeriod == period,
                        onClick = { viewModel.setPerformancePeriod(period) },
                        label = { Text(period.name.replace("_", " ")) }
                    )
                }
            }
        }

        // Metrics cards
        item {
            MetricCard("Total Return", "$${String.format("%.2f", state.totalReturn)}")
        }
        item {
            MetricCard("ROI", "${String.format("%.2f", state.roi)}%")
        }
        item {
            MetricCard("Daily P&L", "$${String.format("%.2f", state.dailyPnL)}")
        }

        // Performance Chart
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Portfolio Value Over Time",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (state.chartData.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.ShowChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No performance data yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Portfolio snapshots will appear here over time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        PortfolioChart(
                            chartData = state.chartData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Activity Tab - Shows trade history
 */
@Composable
fun ActivityTab(state: ActivityState, viewModel: PortfolioViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search bar placeholder
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.searchTrades(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search trades") },
                singleLine = true
            )
        }

        // Trades list
        items(state.filteredTrades) { trade ->
            TradeCard(trade)
        }

        if (state.filteredTrades.isEmpty() && !state.isLoading) {
            item {
                Text(
                    "No trades found",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trade.pair,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = trade.type.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (trade.type == TradeType.BUY)
                        MaterialTheme.colorScheme.profit
                    else
                        MaterialTheme.colorScheme.loss,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Price",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$${String.format("%.2f", trade.price)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Amount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        String.format("%.8f", trade.volume),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(trade.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "P&L: $${String.format("%.2f", trade.profit ?: 0.0)}",
                    color = if ((trade.profit ?: 0.0) >= 0)
                        MaterialTheme.colorScheme.profit
                    else
                        MaterialTheme.colorScheme.loss,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Analytics Tab - Shows advanced metrics
 */
@Composable
fun AnalyticsTab(state: AnalyticsState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { MetricCard("Sharpe Ratio", String.format("%.2f", state.sharpeRatio)) }
        item { MetricCard("Max Drawdown", "$${String.format("%.2f", state.maxDrawdown)}") }
        item { MetricCard("Win Rate", "${String.format("%.2f", state.winRate)}%") }
        item { MetricCard("Profit Factor", String.format("%.2f", state.profitFactor)) }
        item { MetricCard("Avg Hold Time", "${state.avgHoldTime / 1000 / 60 / 60}h") }

        state.bestTrade?.let { trade ->
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Best Trade",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${trade.pair}: $${String.format("%.2f", trade.profit ?: 0.0)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.profit
                        )
                    }
                }
            }
        }

        state.worstTrade?.let { trade ->
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Worst Trade",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${trade.pair}: $${String.format("%.2f", trade.profit ?: 0.0)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.loss
                        )
                    }
                }
            }
        }
    }
}

/**
 * Risk Tab - Shows risk metrics
 */
@Composable
fun RiskTab(state: RiskState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { MetricCard("Diversification Score", "${String.format("%.1f", state.diversificationScore)}/100") }
        item { MetricCard("Value at Risk (95%)", "${String.format("%.2f", state.valueAtRisk)}%") }
        item { MetricCard("Volatility Score", "${String.format("%.2f", state.volatilityScore)}%") }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Exposure by Asset",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    state.exposureByAsset.forEach { (asset, exposure) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                asset,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "${String.format("%.2f", exposure)}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Professional Portfolio Chart Component using Vico
 */
@Composable
fun PortfolioChart(
    chartData: List<com.cryptotrader.domain.model.ChartPoint>,
    modifier: Modifier = Modifier
) {
    if (chartData.isEmpty()) return

    val chartEntryModel = remember(chartData) {
        com.patrykandpatrick.vico.core.entry.entryModelOf(
            *chartData.mapIndexed { index, point ->
                index to point.value.toFloat()
            }.toTypedArray()
        )
    }

    com.patrykandpatrick.vico.compose.chart.Chart(
        chart = com.patrykandpatrick.vico.compose.chart.line.lineChart(),
        model = chartEntryModel,
        modifier = modifier
    )
}

@Composable
fun MetricCard(label: String, value: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun formatTime(timestamp: Long?): String {
    if (timestamp == null) return "N/A"
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
