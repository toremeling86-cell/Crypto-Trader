package com.cryptotrader.presentation.screens.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.cryptotrader.utils.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    viewModel: PerformanceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Analytics", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { viewModel.refreshData() }) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state.isLoading && state.performanceMetrics == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Key Metrics Cards
                item {
                    Text(
                        "Overview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Total P&L",
                            value = state.performanceMetrics?.totalReturn?.formatCurrency() ?: "$0.00",
                            subtitle = "${String.format("%.2f", state.performanceMetrics?.totalReturnPercent ?: 0.0)}%",
                            isPositive = (state.performanceMetrics?.totalReturn ?: 0.0) >= 0,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Win Rate",
                            value = "${String.format("%.1f", calculateOverallWinRate(state))}%",
                            subtitle = "${state.totalTrades} trades",
                            isPositive = calculateOverallWinRate(state) >= 50.0,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Best Trade",
                            value = state.bestTrade.formatCurrency(),
                            subtitle = "Single win",
                            isPositive = true,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Worst Trade",
                            value = state.worstTrade.formatCurrency(),
                            subtitle = "Single loss",
                            isPositive = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "ROI",
                            value = "${String.format("%.2f", state.performanceMetrics?.roi ?: 0.0)}%",
                            subtitle = "Return on investment",
                            isPositive = (state.performanceMetrics?.roi ?: 0.0) >= 0,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Daily P&L",
                            value = state.performanceMetrics?.dailyPnL?.formatCurrency() ?: "$0.00",
                            subtitle = "${String.format("%.2f", state.performanceMetrics?.dailyPnLPercent ?: 0.0)}%",
                            isPositive = (state.performanceMetrics?.dailyPnL ?: 0.0) >= 0,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Charts Section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Charts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // P&L Chart Placeholder
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "P&L Over Time Chart",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "TODO: Implement with Vico library",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Win/Loss Distribution
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text(
                                "Win/Loss Distribution",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${state.winLossDistribution.first}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Wins",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${state.winLossDistribution.second}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color(0xFFE57373),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Losses",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "TODO: Pie chart with Vico library",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                // Strategy Performance Table
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Strategy Performance",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.strategyPerformances.entries.toList()) { (strategyName, performance) ->
                    StrategyPerformanceCard(
                        strategyName = strategyName,
                        performance = performance
                    )
                }

                if (state.strategyPerformances.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No strategy data available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFE57373)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StrategyPerformanceCard(
    strategyName: String,
    performance: com.cryptotrader.domain.analytics.StrategyPerformance
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = strategyName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Win Rate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${String.format("%.1f", performance.winRate)}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Column {
                    Text("P&L", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        performance.totalPnL.formatCurrency(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (performance.totalPnL >= 0) Color(0xFF4CAF50) else Color(0xFFE57373)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Trades", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${performance.totalTrades}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun calculateOverallWinRate(state: PerformanceState): Double {
    val totalWins = state.winLossDistribution.first
    val totalTrades = state.totalTrades
    return if (totalTrades > 0) {
        (totalWins.toDouble() / totalTrades.toDouble()) * 100.0
    } else 0.0
}
