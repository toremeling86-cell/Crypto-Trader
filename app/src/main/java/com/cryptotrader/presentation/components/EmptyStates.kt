package com.cryptotrader.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Professional empty state components with helpful guidance
 * Used when lists/data views have no content to display
 */

@Composable
fun EmptyPositions() {
    EmptyStateBase(
        icon = Icons.Default.TrendingUp,
        title = "No Positions",
        message = "You don't have any trading positions yet.\nCreate and activate a strategy to start trading.",
        iconTint = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun EmptyOrders() {
    EmptyStateBase(
        icon = Icons.Default.Receipt,
        title = "No Orders",
        message = "No orders found.\nOrders will appear here when you place them through your strategies.",
        iconTint = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun EmptyTrades() {
    EmptyStateBase(
        icon = Icons.Default.History,
        title = "No Trade History",
        message = "Your trade history will appear here once strategies execute trades.",
        iconTint = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun EmptyStrategies() {
    EmptyStateBase(
        icon = Icons.Default.AutoAwesome,
        title = "No Strategies",
        message = "Generate a strategy with AI or create one manually to get started.",
        iconTint = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
fun EmptyAnalytics() {
    EmptyStateBase(
        icon = Icons.Default.BarChart,
        title = "No Analytics Data",
        message = "Start trading to see performance analytics and metrics.",
        iconTint = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun EmptySearchResults(searchQuery: String = "") {
    EmptyStateBase(
        icon = Icons.Default.SearchOff,
        title = "No Results",
        message = if (searchQuery.isEmpty()) 
            "No items found." 
        else 
            "No results found for \"$searchQuery\".\nTry a different search term.",
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Base composable for consistent empty state design
 */
@Composable
private fun EmptyStateBase(
    icon: ImageVector,
    title: String,
    message: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showAction: Boolean = false,
    actionText: String = "",
    onActionClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = iconTint.copy(alpha = 0.6f)
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            if (showAction) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onActionClick) {
                    Text(actionText)
                }
            }
        }
    }
}

/**
 * Empty state specifically for filtered results
 */
@Composable
fun EmptyFilteredResults(
    filterDescription: String,
    onClearFilters: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FilterAltOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            
            Text(
                text = "No Results",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "No items match the current filter:\n$filterDescription",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            TextButton(onClick = onClearFilters) {
                Icon(Icons.Default.FilterListOff, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Filters")
            }
        }
    }
}
