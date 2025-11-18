package com.cryptotrader.presentation.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cryptotrader.domain.model.ExpertReport
import com.cryptotrader.domain.model.ReportCategory
import com.cryptotrader.domain.model.ReportSentiment
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reports Library Screen
 *
 * Browse, search, and manage expert trading reports
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<ExpertReport?>(null) }

    // Show messages
    LaunchedEffect(uiState.scanMessage, uiState.errorMessage) {
        // Messages are handled by snackbar in scaffold
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Reports Library")
                        Text(
                            "${uiState.reports.size} of ${uiState.totalReports}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Scan button
                    IconButton(onClick = { viewModel.scanForNewReports() }) {
                        if (uiState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, "Scan for new reports")
                        }
                    }

                    // Filter button
                    IconButton(onClick = { showFilterSheet = true }) {
                        Badge(
                            containerColor = if (hasActiveFilters(uiState)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                        ) {
                            Icon(Icons.Default.FilterList, "Filter")
                        }
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
            // Search bar
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Active filters chips
            if (hasActiveFilters(uiState)) {
                ActiveFiltersRow(
                    uiState = uiState,
                    onClearFilters = { viewModel.clearFilters() },
                    onRemoveCategory = { viewModel.setCategory(null) },
                    onRemoveSentiment = { viewModel.setSentiment(null) },
                    onRemoveAsset = { viewModel.setAsset(null) },
                    onToggleUnanalyzed = { viewModel.toggleUnanalyzedOnly() }
                )
            }

            // Stats row
            StatsRow(
                total = uiState.totalReports,
                analyzed = uiState.analyzedCount,
                unanalyzed = uiState.unanalyzedCount
            )

            // Reports list
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.reports.isEmpty()) {
                EmptyState(
                    hasFilters = hasActiveFilters(uiState),
                    onClearFilters = { viewModel.clearFilters() },
                    onScan = { viewModel.scanForNewReports() }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.reports, key = { it.id }) { report ->
                        ReportCard(
                            report = report,
                            onClick = { selectedReport = report }
                        )
                    }
                }
            }
        }
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            uiState = uiState,
            onDismiss = { showFilterSheet = false },
            onCategorySelected = { viewModel.setCategory(it) },
            onSentimentSelected = { viewModel.setSentiment(it) },
            onAssetSelected = { viewModel.setAsset(it) },
            onToggleUnanalyzed = { viewModel.toggleUnanalyzedOnly() },
            onSortSelected = { viewModel.setSortBy(it) }
        )
    }

    // Report detail dialog
    selectedReport?.let { report ->
        ReportDetailDialog(
            report = report,
            onDismiss = { selectedReport = null }
        )
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search reports...") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun ActiveFiltersRow(
    uiState: ReportsState,
    onClearFilters: () -> Unit,
    onRemoveCategory: () -> Unit,
    onRemoveSentiment: () -> Unit,
    onRemoveAsset: () -> Unit,
    onToggleUnanalyzed: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = false,
                onClick = onClearFilters,
                label = { Text("Clear all") },
                leadingIcon = { Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp)) }
            )
        }

        uiState.selectedCategory?.let { category ->
            item {
                FilterChip(
                    selected = true,
                    onClick = onRemoveCategory,
                    label = { Text(category.displayName) },
                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                )
            }
        }

        uiState.selectedSentiment?.let { sentiment ->
            item {
                FilterChip(
                    selected = true,
                    onClick = onRemoveSentiment,
                    label = { Text(sentiment.displayName) },
                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                )
            }
        }

        uiState.selectedAsset?.let { asset ->
            item {
                FilterChip(
                    selected = true,
                    onClick = onRemoveAsset,
                    label = { Text(asset) },
                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                )
            }
        }

        if (uiState.showOnlyUnanalyzed) {
            item {
                FilterChip(
                    selected = true,
                    onClick = onToggleUnanalyzed,
                    label = { Text("Unanalyzed") },
                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                )
            }
        }
    }
}

@Composable
fun StatsRow(
    total: Int,
    analyzed: Int,
    unanalyzed: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem("Total", total, MaterialTheme.colorScheme.primary)
        StatItem("Analyzed", analyzed, Color(0xFF4CAF50))
        StatItem("Unanalyzed", unanalyzed, Color(0xFFFFA726))
    }
}

@Composable
fun StatItem(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
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
fun ReportCard(
    report: ExpertReport,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title and sentiment
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                report.sentiment?.let { sentiment ->
                    SentimentBadge(sentiment)
                }
            }

            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Author
                report.author?.let { author ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Date
                Text(
                    text = formatDate(report.publishedDate ?: report.uploadDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Category
                Text(
                    text = report.category.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Assets
            if (report.assets.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(report.assets.take(5)) { asset ->
                        AssistChip(
                            onClick = {},
                            label = { Text(asset, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    if (report.assets.size > 5) {
                        item {
                            Text(
                                "+${report.assets.size - 5}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            // Status indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (report.analyzed) {
                    StatusChip("✓ Analyzed", Color(0xFF4CAF50))
                }

                if (report.usedInStrategies > 0) {
                    StatusChip("${report.usedInStrategies} Strategies", MaterialTheme.colorScheme.primary)
                }

                report.impactScore?.let { score ->
                    if (score > 0.7) {
                        StatusChip("High Impact", Color(0xFFFF9800))
                    }
                }
            }
        }
    }
}

@Composable
fun SentimentBadge(sentiment: ReportSentiment) {
    val color = when (sentiment) {
        ReportSentiment.BULLISH -> Color(0xFF4CAF50)
        ReportSentiment.BEARISH -> Color(0xFFF44336)
        ReportSentiment.NEUTRAL -> Color(0xFF9E9E9E)
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = sentiment.displayName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun EmptyState(
    hasFilters: Boolean,
    onClearFilters: () -> Unit,
    onScan: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Description,
                null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = if (hasFilters) "No reports match filters" else "No reports yet",
                style = MaterialTheme.typography.titleMedium
            )

            if (hasFilters) {
                Button(onClick = onClearFilters) {
                    Text("Clear Filters")
                }
            } else {
                Text(
                    text = "Add markdown reports to\n/sdcard/Android/data/com.cryptotrader/files/ExpertReports/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onScan) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan for Reports")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    uiState: ReportsState,
    onDismiss: () -> Unit,
    onCategorySelected: (ReportCategory?) -> Unit,
    onSentimentSelected: (ReportSentiment?) -> Unit,
    onAssetSelected: (String?) -> Unit,
    onToggleUnanalyzed: () -> Unit,
    onSortSelected: (SortOption) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Filters & Sorting", style = MaterialTheme.typography.titleLarge)

            // Category filter
            Text("Category", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = uiState.selectedCategory == null,
                        onClick = { onCategorySelected(null) },
                        label = { Text("All") }
                    )
                }
                items(ReportCategory.values()) { category ->
                    FilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category.displayName) }
                    )
                }
            }

            // Sentiment filter
            Text("Sentiment", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.selectedSentiment == null,
                    onClick = { onSentimentSelected(null) },
                    label = { Text("All") }
                )
                ReportSentiment.values().forEach { sentiment ->
                    FilterChip(
                        selected = uiState.selectedSentiment == sentiment,
                        onClick = { onSentimentSelected(sentiment) },
                        label = { Text(sentiment.displayName) }
                    )
                }
            }

            // Asset filter
            if (uiState.availableAssets.isNotEmpty()) {
                Text("Asset", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = uiState.selectedAsset == null,
                            onClick = { onAssetSelected(null) },
                            label = { Text("All") }
                        )
                    }
                    items(uiState.availableAssets) { asset ->
                        FilterChip(
                            selected = uiState.selectedAsset == asset,
                            onClick = { onAssetSelected(asset) },
                            label = { Text(asset) }
                        )
                    }
                }
            }

            // Analysis status
            FilterChip(
                selected = uiState.showOnlyUnanalyzed,
                onClick = onToggleUnanalyzed,
                label = { Text("Show only unanalyzed") }
            )

            Divider()

            // Sort options
            Text("Sort by", style = MaterialTheme.typography.labelMedium)
            SortOption.values().forEach { option ->
                FilterChip(
                    selected = uiState.sortBy == option,
                    onClick = { onSortSelected(option) },
                    label = { Text(option.displayName) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailDialog(
    report: ExpertReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f),
        title = {
            Text(
                report.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    // Metadata
                    report.author?.let {
                        MetadataRow("Author", it)
                    }
                    report.source?.let {
                        MetadataRow("Source", it)
                    }
                    MetadataRow("Category", report.category.displayName)
                    MetadataRow("Date", formatDate(report.publishedDate ?: report.uploadDate))

                    report.sentiment?.let {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sentiment", style = MaterialTheme.typography.labelMedium)
                            SentimentBadge(it)
                        }
                    }

                    if (report.assets.isNotEmpty()) {
                        MetadataRow("Assets", report.assets.joinToString(", "))
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                item {
                    // Full content (not truncated)
                    Text(
                        text = "Content:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    Text(
                        text = report.content,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun hasActiveFilters(state: ReportsState): Boolean {
    return state.selectedCategory != null ||
            state.selectedSentiment != null ||
            state.selectedAsset != null ||
            state.showOnlyUnanalyzed
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
