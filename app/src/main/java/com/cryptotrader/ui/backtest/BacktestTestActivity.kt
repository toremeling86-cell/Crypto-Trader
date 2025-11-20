package com.cryptotrader.ui.backtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.domain.model.TradingMode
import com.cryptotrader.presentation.theme.CryptoTraderTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Backtest Test Activity - Test hele backtest-systemet
 *
 * For å kjøre:
 * adb shell am start -n com.cryptotrader/.ui.backtest.BacktestTestActivity
 */
@AndroidEntryPoint
class BacktestTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CryptoTraderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BacktestTestScreen()
                }
            }
        }
    }
}

@Composable
fun BacktestTestScreen(
    viewModel: BacktestManagementViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val availableDataFiles by viewModel.availableDataFiles.collectAsState()
    val currentProposal by viewModel.currentProposal.collectAsState()
    val latestResult by viewModel.latestResult.collectAsState()
    val availableDatasets by viewModel.availableDatasets.collectAsState()
    val activeDataset by viewModel.activeDataset.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🧪 Backtest System Test",
            style = MaterialTheme.typography.headlineMedium
        )

        // Dataset Selector Section
        DatasetSelectorSection(
            availableDatasets = availableDatasets,
            activeDataset = activeDataset,
            onDatasetSelected = { datasetId ->
                viewModel.activateDataset(datasetId)
            }
        )

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (uiState) {
                    is BacktestUiState.Error -> MaterialTheme.colorScheme.errorContainer
                    is BacktestUiState.BacktestComplete -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (uiState) {
                        is BacktestUiState.Idle -> "⚪ Klar"
                        is BacktestUiState.ScanningData -> "🔍 Skanner data..."
                        is BacktestUiState.DataScanComplete -> "✅ Fant ${(uiState as BacktestUiState.DataScanComplete).filesFound} filer"
                        is BacktestUiState.ImportingData -> {
                            val state = uiState as BacktestUiState.ImportingData
                            "📥 Importerer ${state.current}/${state.total}"
                        }
                        is BacktestUiState.ImportComplete -> {
                            val state = uiState as BacktestUiState.ImportComplete
                            "✅ Import ferdig: ${state.totalBars} bars (${state.successCount} ✓, ${state.failureCount} ✗)"
                        }
                        is BacktestUiState.GeneratingProposal -> "🤖 Genererer AI-forslag..."
                        is BacktestUiState.ProposalReady -> "📋 Forslag klart"
                        is BacktestUiState.RunningBacktest -> "🚀 Kjører backtest..."
                        is BacktestUiState.BacktestComplete -> "✅ Backtest fullført!"
                        is BacktestUiState.Error -> "❌ Feil: ${(uiState as BacktestUiState.Error).message}"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Step 1: Scan Data
        Button(
            onClick = { viewModel.scanAvailableData() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("1️⃣ Skann tilgjengelige datafiler")
        }

        if (availableDataFiles.isNotEmpty()) {
            Text("Fant ${availableDataFiles.size} datafiler:")
            availableDataFiles.take(5).forEach { file ->
                Text(
                    text = "  • ${file.fileName}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (availableDataFiles.size > 5) {
                Text(
                    text = "  ... og ${availableDataFiles.size - 5} til",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Step 2: Import Data
        Button(
            onClick = { viewModel.importDataFiles(availableDataFiles) },
            modifier = Modifier.fillMaxWidth(),
            enabled = availableDataFiles.isNotEmpty()
        ) {
            Text("2️⃣ Importer data til database")
        }

        // Step 3: Create Test Strategy
        val testStrategy = remember {
            Strategy(
                id = "test-strategy-${System.currentTimeMillis()}",
                name = "RSI Test Strategy",
                description = "Simple RSI strategy for testing",
                tradingPairs = listOf("XXBTZUSD"),
                entryConditions = listOf(
                    "RSI < 30"
                ),
                exitConditions = listOf(
                    "RSI > 70"
                ),
                stopLossPercent = 2.0,
                takeProfitPercent = 5.0,
                positionSizePercent = 5.0,
                isActive = true,
                tradingMode = TradingMode.PAPER
            )
        }

        // Step 3: Generate Proposal
        Button(
            onClick = { viewModel.generateBacktestProposal(testStrategy) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("3️⃣ Generer AI backtest-forslag")
        }

        // Show Proposal
        currentProposal?.let { proposal ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📋 AI Forslag",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tier: ${proposal.proposedDataTier.tierName}")
                    Text("Asset: ${proposal.proposedAsset}")
                    Text("Timeframe: ${proposal.proposedTimeframe}")
                    Text("Kvalitet: ${String.format("%.1f%%", proposal.dataQualityScore * 100)}")

                    if (proposal.warnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("⚠️ Advarsler:", style = MaterialTheme.typography.titleSmall)
                        proposal.warnings.forEach { warning ->
                            Text("  • $warning", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Step 4: Execute Backtest (using proposal or quick test)
        Button(
            onClick = { viewModel.runQuickTest(testStrategy) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("4️⃣ Kjør Quick Test (komplett)")
        }

        // Show Results
        latestResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.validationError != null) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (result.validationError != null) "❌ Backtest Feilet" else "✅ Backtest Resultat",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (result.validationError != null) {
                        Text("Feil: ${result.validationError}")
                    } else {
                        Text("Trades: ${result.totalTrades}")
                        Text("Win Rate: ${String.format("%.1f%%", result.winRate)}")
                        Text("Total P&L: ${String.format("%.2f%%", result.totalPnLPercent)}")
                        Text("Sharpe Ratio: ${String.format("%.2f", result.sharpeRatio)}")
                        Text("Max Drawdown: ${String.format("%.2f%%", result.maxDrawdown)}")

                        result.dataTier?.let { tier ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Data Tier: ${tier.tierName}", style = MaterialTheme.typography.titleSmall)
                            result.dataQualityScore?.let { quality ->
                                Text("Data Quality: ${String.format("%.1f%%", quality * 100)}")
                            }
                        }
                    }
                }
            }
        }

        // Reset Button
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = { viewModel.resetState() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🔄 Reset")
        }
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
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
