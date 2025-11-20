package com.cryptotrader.presentation.screens.positions

import androidx.compose.foundation.background
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
import com.cryptotrader.domain.model.Position
import com.cryptotrader.domain.model.PositionSide
import com.cryptotrader.domain.model.PositionStatus
import com.cryptotrader.utils.formatCurrency
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionManagementScreen(
    viewModel: PositionManagementViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val positions by viewModel.positions.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState() // Collect current filter
    var showFilterSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Handle messages
    LaunchedEffect(state.errorMessage, state.successMessage) {
        if (state.errorMessage != null || state.successMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Positions", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { viewModel.refreshPrices() }) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Prices")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search and Filter
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        viewModel.setSearchQuery(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search pair (e.g. XBTUSD)") },
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentFilter == PositionFilter.OPEN,
                        onClick = { viewModel.setFilter(PositionFilter.OPEN) },
                        label = { Text("Open") },
                        leadingIcon = if (currentFilter == PositionFilter.OPEN) {
                            { Icon(Icons.Default.Check, "Selected", modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = currentFilter == PositionFilter.CLOSED,
                        onClick = { viewModel.setFilter(PositionFilter.CLOSED) },
                        label = { Text("Closed") },
                        leadingIcon = if (currentFilter == PositionFilter.CLOSED) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = currentFilter == PositionFilter.ALL,
                        onClick = { viewModel.setFilter(PositionFilter.ALL) },
                        label = { Text("All") },
                        leadingIcon = if (currentFilter == PositionFilter.ALL) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            // Messages
            state.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            state.successMessage?.let {
                Text(
                    text = it,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Positions List
            when {
                state.isLoading && positions.isEmpty() -> {
                    // Show loading skeletons on initial load
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(5) { // Show 5 skeleton cards
                            com.cryptotrader.presentation.components.PositionCardSkeleton()
                        }
                    }
                }
                positions.isEmpty() -> {
                    // Show professional empty state
                    com.cryptotrader.presentation.components.EmptyPositions()
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(positions) { position ->
                            PositionCard(
                                position = position,
                                onClose = { 
                                    // Calculate approximate current price for closing
                                    // In a real scenario, the backend/repo handles the market order price
                                    val currentPrice = if (position.side == PositionSide.LONG) {
                                        position.entryPrice + (position.unrealizedPnL / position.quantity)
                                    } else {
                                        position.entryPrice - (position.unrealizedPnL / position.quantity)
                                    }
                                    viewModel.closePosition(position.id, currentPrice) 
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PositionCard(
    position: Position,
    onClose: () -> Unit
) {
    val isClosed = position.status == PositionStatus.CLOSED || position.status == PositionStatus.LIQUIDATED
    val pnl = if (isClosed) position.realizedPnL ?: 0.0 else position.unrealizedPnL
    val pnlPercent = if (isClosed) position.realizedPnLPercent ?: 0.0 else position.unrealizedPnLPercent
    val isProfit = pnl >= 0

    // Derive current price for display (since it's not in the model directly)
    val currentPrice = if (isClosed) {
        position.exitPrice ?: 0.0
    } else {
        if (position.side == PositionSide.LONG) {
            position.entryPrice + (position.unrealizedPnL / position.quantity)
        } else {
            position.entryPrice - (position.unrealizedPnL / position.quantity)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = position.pair,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = if (position.side == PositionSide.LONG) Color(0xFF4CAF50) else Color(0xFFE57373)
                    ) {
                        Text(
                            text = position.side.name,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Surface(
                    color = if (isProfit) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color(0xFFE57373).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${if (isProfit) "+" else ""}${pnl.formatCurrency()} (${String.format("%.2f", pnlPercent)}%)",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (isProfit) Color(0xFF4CAF50) else Color(0xFFE57373),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(position.quantity.toString(), style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text("Entry", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(position.entryPrice.formatCurrency(), style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (isClosed) "Exit" else "Current", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(currentPrice.formatCurrency(), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Footer
            if (!isClosed) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Opened: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(position.openedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Close Position")
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Closed: ${position.closedAt?.let { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(it)) } ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
