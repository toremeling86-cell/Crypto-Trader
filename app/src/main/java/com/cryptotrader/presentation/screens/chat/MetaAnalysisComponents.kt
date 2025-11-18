package com.cryptotrader.presentation.screens.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cryptotrader.domain.model.MetaAnalysis

/**
 * Pulsing green badge showing count of unanalyzed reports
 */
@Composable
fun PulsingGreenBadge(count: Int) {
    if (count == 0) return

    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(24.dp)
            .background(
                color = Color(0xFF4CAF50), // Green
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Button to trigger meta-analysis with timeframe selection
 */
@Composable
fun MetaAnalysisButton(
    unanalyzedCount: Int,
    isAnalyzing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTimeframe: com.cryptotrader.domain.model.AnalysisTimeframe = com.cryptotrader.domain.model.AnalysisTimeframe.WEEKLY,
    onTimeframeChange: (com.cryptotrader.domain.model.AnalysisTimeframe) -> Unit = {}
) {
    var showTimeframeMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Timeframe Selector
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Tidsramme",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selectedTimeframe.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = selectedTimeframe.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { showTimeframeMenu = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Velg tidsramme")
                    }

                    DropdownMenu(
                        expanded = showTimeframeMenu,
                        onDismissRequest = { showTimeframeMenu = false }
                    ) {
                        com.cryptotrader.domain.model.AnalysisTimeframe.values().forEach { timeframe ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = timeframe.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (timeframe == selectedTimeframe) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = timeframe.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    onTimeframeChange(timeframe)
                                    showTimeframeMenu = false
                                },
                                leadingIcon = {
                                    if (timeframe == selectedTimeframe) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Analysis Button
        Button(
            onClick = onClick,
            enabled = !isAnalyzing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White
            )
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyserer...")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kjør Meta-Analyse (${selectedTimeframe.displayName})")
            }
        }
    }
}

/**
 * Dialog showing analysis progress
 */
@Composable
fun AnalysisProgressDialog(
    progress: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "🤖 Opus 4.1 Analyserer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = progress,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Dette kan ta opptil 60 sekunder...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Card showing strategy preview from meta-analysis
 */
@Composable
fun StrategyPreviewCard(
    analysis: MetaAnalysis,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "✨ Analyse Fullført",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Basert på ${analysis.reportCount} ekspertrapporter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Strategy name
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = analysis.strategyName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = analysis.recommendedStrategy.description,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Confidence & Risk
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Confidence
                        Card(modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Tillit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(analysis.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        analysis.confidence >= 0.8 -> Color(0xFF4CAF50)
                                        analysis.confidence >= 0.6 -> Color(0xFFFFA726)
                                        else -> Color(0xFFEF5350)
                                    }
                                )
                            }
                        }

                        // Risk Level
                        Card(modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Risiko",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = analysis.riskLevel.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Market Outlook
                analysis.marketOutlook?.let { outlook ->
                    item {
                        Card {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Markedsutsikter",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = when (outlook) {
                                        com.cryptotrader.domain.model.MarketOutlook.BULLISH -> "🐂 Bullish"
                                        com.cryptotrader.domain.model.MarketOutlook.BEARISH -> "🐻 Bearish"
                                        com.cryptotrader.domain.model.MarketOutlook.NEUTRAL -> "➡️ Nøytral"
                                        com.cryptotrader.domain.model.MarketOutlook.VOLATILE -> "⚡ Volatil"
                                        com.cryptotrader.domain.model.MarketOutlook.UNCERTAIN -> "❓ Usikker"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Consensus
                analysis.consensus?.let { consensus ->
                    item {
                        Card {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "✅ Konsensus",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = consensus,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Contradictions
                analysis.contradictions?.let { contradictions ->
                    item {
                        Card {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚠️ Motsetninger",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9800)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = contradictions,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Trading Pairs
                item {
                    Card {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Handelspar",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = analysis.tradingPairs.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Key Insights
                if (analysis.recommendedStrategy.keyInsights.isNotEmpty()) {
                    item {
                        Card {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "💡 Nøkkelinnsikt",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                analysis.recommendedStrategy.keyInsights.forEach { insight ->
                                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text("• ", style = MaterialTheme.typography.bodySmall)
                                        Text(insight, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Risk Factors
                if (analysis.recommendedStrategy.riskFactors.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚠️ Risikofaktorer",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                analysis.recommendedStrategy.riskFactors.forEach { risk ->
                                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text("• ", style = MaterialTheme.typography.bodySmall)
                                        Text(risk, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onReject()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Avvis")
                        }

                        Button(
                            onClick = {
                                onApprove()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Opprett Strategi")
                        }
                    }
                }
            }
        }
    }
}
