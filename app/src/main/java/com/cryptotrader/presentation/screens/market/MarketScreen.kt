package com.cryptotrader.presentation.screens.market

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cryptotrader.domain.model.MarketSnapshot
import com.cryptotrader.presentation.theme.LocalSpacing
import com.cryptotrader.presentation.theme.profit
import com.cryptotrader.presentation.theme.loss
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    viewModel: MarketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val spacing = LocalSpacing.current

    LaunchedEffect(Unit) {
        viewModel.startAutoRefresh()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Market") },
                actions = {
                    IconButton(onClick = { viewModel.refreshMarketData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.snapshots.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null && uiState.snapshots.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error loading market data",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refreshMarketData() }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(spacing.medium)
                    ) {
                        item {
                            val lastRefresh = uiState.lastRefreshTime
                            if (lastRefresh != null) {
                                Text(
                                    text = "Last updated: ${formatTime(lastRefresh)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = spacing.medium)
                                )
                            }
                        }

                        items(uiState.snapshots) { snapshot ->
                            CryptoPriceCard(
                                snapshot = snapshot,
                                modifier = Modifier.padding(bottom = spacing.small)
                            )
                        }

                        if (uiState.snapshots.isEmpty() && !uiState.isLoading) {
                            item {
                                Text(
                                    text = "No market data available",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(spacing.large)
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isLoading && uiState.snapshots.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
fun CryptoPriceCard(
    snapshot: MarketSnapshot,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val isPositive = snapshot.changePercent24h >= 0
    val changeColor = if (isPositive) MaterialTheme.colorScheme.profit else MaterialTheme.colorScheme.loss
    val trendIcon = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Symbol and name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snapshot.getBaseCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = snapshot.getDisplayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Price and change
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatPrice(snapshot.price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = trendIcon,
                        contentDescription = null,
                        tint = changeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = formatPercent(snapshot.changePercent24h),
                        style = MaterialTheme.typography.bodyMedium,
                        color = changeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Divider(modifier = Modifier.padding(horizontal = spacing.medium))

        // 24h stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                label = "High",
                value = formatPrice(snapshot.high24h),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "Low",
                value = formatPrice(snapshot.low24h),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "Volume",
                value = formatVolume(snapshot.volume24h),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatPrice(price: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    return formatter.format(price)
}

private fun formatPercent(percent: Double): String {
    val sign = if (percent >= 0) "+" else ""
    return "$sign%.2f%%".format(percent)
}

private fun formatVolume(volume: Double): String {
    return when {
        volume >= 1_000_000 -> "%.1fM".format(volume / 1_000_000)
        volume >= 1_000 -> "%.1fK".format(volume / 1_000)
        else -> "%.0f".format(volume)
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        else -> "${diff / 3600_000}h ago"
    }
}
