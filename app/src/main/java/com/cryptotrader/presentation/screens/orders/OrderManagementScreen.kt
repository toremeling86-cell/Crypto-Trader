package com.cryptotrader.presentation.screens.orders

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
import com.cryptotrader.domain.model.Order
import com.cryptotrader.domain.model.OrderStatus
import com.cryptotrader.domain.model.OrderType
import com.cryptotrader.domain.model.TradeType
import com.cryptotrader.utils.formatCurrency
import com.cryptotrader.utils.formatTimeAgo
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderManagementScreen(
    viewModel: OrderManagementViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val orders by viewModel.orders.collectAsState()
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
                title = { Text("Order Management", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showFilterSheet = !showFilterSheet }) {
                        Icon(
                            imageVector = if (showFilterSheet) Icons.Default.FilterListOff else Icons.Default.FilterList,
                            contentDescription = "Filter"
                        )
                    }
                    // Refresh is automatic via Flow, but we can keep a manual trigger if needed
                    // IconButton(onClick = viewModel::refreshOrders) {
                    //     Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    // }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    viewModel.setPairFilter(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search by pair (e.g. BTC/USD)") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter Chips
            if (showFilterSheet) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = true, // Logic to be connected to VM state if needed visually
                        onClick = { viewModel.setStatusFilter(null) },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.setStatusFilter(OrderStatus.OPEN) },
                        label = { Text("Open") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.setStatusFilter(OrderStatus.FILLED) },
                        label = { Text("Filled") }
                    )
                }
            }

            // Messages
            state.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
            state.successMessage?.let {
                Text(
                    text = it,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Order List
            if (orders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No orders found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(orders) { order ->
                        OrderCard(
                            order = order,
                            onCancel = { viewModel.cancelOrder(order.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Pair, Side, Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = order.pair,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = if (order.type == TradeType.BUY) Color(0xFF4CAF50) else Color(0xFFE57373)
                    ) {
                        Text(
                            text = order.type.name,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                OrderStatusBadge(status = order.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Amount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = order.quantity.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column {
                    Text(
                        text = "Price",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = order.price?.formatCurrency() ?: "Market",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = ((order.price ?: 0.0) * order.quantity).formatCurrency(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            // Footer: Date and Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(order.placedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (order.status == OrderStatus.OPEN || order.status == OrderStatus.PENDING) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun OrderStatusBadge(status: OrderStatus) {
    val (color, icon) = when (status) {
        OrderStatus.PENDING -> MaterialTheme.colorScheme.primary to "⏳"
        OrderStatus.OPEN -> Color(0xFFFFA726) to "🟡" // Orange
        OrderStatus.FILLED -> Color(0xFF4CAF50) to "✅" // Green
        OrderStatus.PARTIALLY_FILLED -> Color(0xFF29B6F6) to "🔵" // Light Blue
        OrderStatus.CANCELLED -> MaterialTheme.colorScheme.error to "🛑"
        OrderStatus.EXPIRED -> MaterialTheme.colorScheme.onSurfaceVariant to "❌"
        OrderStatus.REJECTED -> MaterialTheme.colorScheme.error to "⛔"
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = icon, style = MaterialTheme.typography.bodySmall)
            Text(
                text = status.name,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
