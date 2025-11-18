package com.cryptotrader.presentation.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cryptotrader.data.local.entities.AIMarketAnalysisEntity
import com.cryptotrader.presentation.screens.chat.ChatScreen
import com.cryptotrader.presentation.screens.strategy.StrategyConfigScreen
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AIScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        CenterAlignedTopAppBar(
            title = { Text("AI Mission Control") },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Tab Row
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 0.dp
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Chat") },
                icon = { Icon(Icons.Default.Chat, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Analysis") },
                icon = { Icon(Icons.Default.Insights, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Strategies") },
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Reports Library") },
                icon = { Icon(Icons.Default.Article, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 4,
                onClick = { selectedTab = 4 },
                text = { Text("Test Center") },
                icon = { Icon(Icons.Default.Science, contentDescription = null) }
            )
        }

        // Tab Content
        when (selectedTab) {
            0 -> ChatTab()
            1 -> AnalysisTab()
            2 -> StrategiesTab()
            3 -> ReportsTab()
            4 -> TestCenterTab()
        }
    }
}

@Composable
fun ChatTab() {
    // Use existing ChatScreen but without top bar (since AIScreen has it)
    ChatScreen(onNavigateBack = {})
}

@Composable
fun AnalysisTab(
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val latestAnalysis by viewModel.latestAnalysis.collectAsState()
    val history by viewModel.analysisHistory.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Analyze Now button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Market Analysis",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Claude's AI-powered insights",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { viewModel.analyzeMarket() },
                    enabled = !uiState.isAnalyzing
                ) {
                    if (uiState.isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (uiState.isAnalyzing) "Analyzing..." else "Analyze Now")
                }
            }
        }

        // Error message
        uiState.error?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Analysis Failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Latest Analysis
        if (latestAnalysis != null) {
            item {
                AnalysisCard(
                    analysis = latestAnalysis!!,
                    isLatest = true
                )
            }

            // History header
            if (history.size > 1) {
                item {
                    Text(
                        text = "Recent Analysis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // History items (skip first one as it's already shown as latest)
            items(history.drop(1)) { analysis ->
                AnalysisCard(
                    analysis = analysis,
                    isLatest = false
                )
            }
        } else {
            // No analysis yet
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Analysis Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap 'Analyze Now' to get Claude's market insights",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StrategiesTab() {
    // Use existing StrategyConfigScreen
    StrategyConfigScreen()
}

@Composable
fun ReportsTab() {
    // Use existing ReportsScreen from reports package
    com.cryptotrader.presentation.screens.reports.ReportsScreen()
}

@Composable
fun TestCenterTab() {
    // Use Strategy Test Center screen
    StrategyTestCenterScreen()
}

/**
 * Analysis Card - Displays a single market analysis
 */
@Composable
fun AnalysisCard(
    analysis: AIMarketAnalysisEntity,
    isLatest: Boolean
) {
    var expanded by remember { mutableStateOf(isLatest) }

    val sentimentColor = when (analysis.sentiment) {
        "BULLISH" -> Color(0xFF4CAF50)  // Green
        "BEARISH" -> Color(0xFFF44336)  // Red
        else -> Color(0xFFFF9800)       // Orange for NEUTRAL
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isLatest) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Sentiment + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sentiment indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(sentimentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = analysis.sentiment,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = sentimentColor
                    )

                    if (isLatest) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Latest",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Timestamp
                Text(
                    text = formatTimestamp(analysis.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Market Condition & Confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Condition: ${analysis.marketCondition}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Confidence: ${(analysis.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Trigger type
            Text(
                text = "Trigger: ${analysis.triggerType}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Expandable content
            if (expanded) {
                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Key Insights
                if (analysis.keyInsights.isNotEmpty()) {
                    AnalysisSection(
                        title = "Key Insights",
                        icon = Icons.Default.Lightbulb,
                        content = analysis.keyInsights.split(","),
                        tintColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Risk Factors
                if (analysis.riskFactors.isNotEmpty()) {
                    AnalysisSection(
                        title = "Risk Factors",
                        icon = Icons.Default.Warning,
                        content = analysis.riskFactors.split(","),
                        tintColor = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Opportunities
                if (analysis.opportunities.isNotEmpty()) {
                    AnalysisSection(
                        title = "Opportunities",
                        icon = Icons.Default.TrendingUp,
                        content = analysis.opportunities.split(","),
                        tintColor = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Analyzed Symbols
                if (analysis.symbolsAnalyzed.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Symbols: ${analysis.symbolsAnalyzed}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Expand/Collapse button
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(if (expanded) "Show Less" else "Show More")
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Section component for displaying lists (insights, risks, opportunities)
 */
@Composable
fun AnalysisSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: List<String>,
    tintColor: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tintColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        content.forEach { item ->
            if (item.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "•",
                        modifier = Modifier.padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = item.trim(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Format timestamp to readable format
 */
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
