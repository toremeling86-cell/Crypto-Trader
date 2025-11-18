package com.cryptotrader.ui.backtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.dataimport.BatchDataImporter
import com.cryptotrader.data.dataimport.ParsedDataFile
import com.cryptotrader.domain.backtesting.BacktestOrchestrator
import com.cryptotrader.domain.backtesting.BacktestProposalGenerator
import com.cryptotrader.domain.backtesting.BacktestResult
import com.cryptotrader.domain.model.BacktestDecision
import com.cryptotrader.domain.model.BacktestProposal
import com.cryptotrader.domain.model.DataTier
import com.cryptotrader.domain.model.Strategy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Backtest Management ViewModel - Complete Backtest System Access
 *
 * Provides UI access to:
 * 1. Data Import (scan and import historical data)
 * 2. Backtest Proposals (AI-generated recommendations)
 * 3. Backtest Execution (run backtests with validation)
 * 4. Results Viewing (historical backtest results)
 *
 * USE THIS FOR TESTING:
 * - Call scanAvailableData() to find data files
 * - Call importDataFiles() to load data to database
 * - Call generateBacktestProposal() to get AI recommendation
 * - Call executeBacktest() to run backtest
 */
@HiltViewModel
class BacktestManagementViewModel @Inject constructor(
    private val batchDataImporter: BatchDataImporter,
    private val backtestProposalGenerator: BacktestProposalGenerator,
    private val backtestOrchestrator: BacktestOrchestrator
) : ViewModel() {

    // ==============================
    // STATE
    // ==============================

    private val _uiState = MutableStateFlow<BacktestUiState>(BacktestUiState.Idle)
    val uiState: StateFlow<BacktestUiState> = _uiState.asStateFlow()

    private val _availableDataFiles = MutableStateFlow<List<ParsedDataFile>>(emptyList())
    val availableDataFiles: StateFlow<List<ParsedDataFile>> = _availableDataFiles.asStateFlow()

    private val _currentProposal = MutableStateFlow<BacktestProposal?>(null)
    val currentProposal: StateFlow<BacktestProposal?> = _currentProposal.asStateFlow()

    private val _latestResult = MutableStateFlow<BacktestResult?>(null)
    val latestResult: StateFlow<BacktestResult?> = _latestResult.asStateFlow()

    // ==============================
    // DATA IMPORT FUNCTIONS
    // ==============================

    /**
     * Step 1: Scan for available data files
     * Call this to find all importable CSV/Parquet files
     */
    fun scanAvailableData() {
        viewModelScope.launch {
            try {
                _uiState.value = BacktestUiState.ScanningData
                Timber.i("🔍 Scanning for available data files...")

                val files = batchDataImporter.scanAvailableData()
                _availableDataFiles.value = files

                Timber.i("✅ Found ${files.size} data files")
                files.groupBy { it.dataTier }.forEach { (tier, tierFiles) ->
                    Timber.i("   ${tier.tierName}: ${tierFiles.size} files")
                }

                _uiState.value = BacktestUiState.DataScanComplete(files.size)

            } catch (e: Exception) {
                Timber.e(e, "Failed to scan data files")
                _uiState.value = BacktestUiState.Error("Failed to scan data: ${e.message}")
            }
        }
    }

    /**
     * Step 2: Import data files to database
     * Call this after scanning to load data
     */
    fun importDataFiles(files: List<ParsedDataFile>) {
        viewModelScope.launch {
            try {
                _uiState.value = BacktestUiState.ImportingData(0, files.size)
                Timber.i("📥 Importing ${files.size} data files...")

                batchDataImporter.importBatch(files).collect { progress ->
                    _uiState.value = BacktestUiState.ImportingData(
                        progress.currentFileIndex,
                        progress.totalFiles
                    )

                    if (progress.isComplete) {
                        Timber.i("✅ Import complete: ${progress.totalBarsImported} bars imported")
                        Timber.i("   Success: ${progress.successCount}, Failed: ${progress.failureCount}")
                        _uiState.value = BacktestUiState.ImportComplete(
                            progress.totalBarsImported,
                            progress.successCount,
                            progress.failureCount
                        )
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Import failed")
                _uiState.value = BacktestUiState.Error("Import failed: ${e.message}")
            }
        }
    }

    // ==============================
    // BACKTEST PROPOSAL FUNCTIONS
    // ==============================

    /**
     * Step 3: Generate AI backtest proposal
     * This is what AI chat would call to propose a backtest
     */
    fun generateBacktestProposal(strategy: Strategy) {
        viewModelScope.launch {
            try {
                _uiState.value = BacktestUiState.GeneratingProposal
                Timber.i("🤖 Generating backtest proposal for: ${strategy.name}")

                val proposal = backtestProposalGenerator.generateProposal(strategy)
                _currentProposal.value = proposal

                Timber.i("✅ Proposal generated:")
                Timber.i("   Tier: ${proposal.proposedDataTier.tierName}")
                Timber.i("   Asset: ${proposal.proposedAsset}")
                Timber.i("   Timeframe: ${proposal.proposedTimeframe}")
                Timber.i("   Quality: ${String.format("%.1f%%", proposal.dataQualityScore * 100)}")

                _uiState.value = BacktestUiState.ProposalReady(proposal)

            } catch (e: Exception) {
                Timber.e(e, "Failed to generate proposal")
                _uiState.value = BacktestUiState.Error("Proposal generation failed: ${e.message}")
            }
        }
    }

    /**
     * Step 4: Execute backtest with user decision
     * User can approve proposal or modify parameters
     */
    fun executeBacktest(strategy: Strategy, decision: BacktestDecision) {
        viewModelScope.launch {
            try {
                _uiState.value = BacktestUiState.RunningBacktest
                Timber.i("🚀 Executing backtest for: ${strategy.name}")
                Timber.i("   Approved: ${decision.approved}")
                Timber.i("   Has modifications: ${decision.hasModifications}")

                val result = if (decision.hasModifications) {
                    // User modified parameters - use manual mode
                    Timber.i("   Using MANUAL mode with user modifications")
                    backtestOrchestrator.runBacktestManual(
                        strategy = strategy,
                        dataTier = decision.modifiedDataTier ?: DataTier.TIER_4_BASIC,
                        asset = decision.modifiedAsset,
                        timeframe = decision.modifiedTimeframe,
                        startDate = decision.modifiedStartDate,
                        endDate = decision.modifiedEndDate,
                        startingBalance = decision.modifiedStartingBalance ?: 10000.0
                    )
                } else {
                    // User approved AI proposal - use auto mode
                    Timber.i("   Using AUTO mode (AI proposal approved)")
                    backtestOrchestrator.runBacktest(strategy)
                }

                _latestResult.value = result

                if (result.validationError != null) {
                    Timber.e("❌ Backtest failed validation: ${result.validationError}")
                    _uiState.value = BacktestUiState.Error(result.validationError)
                } else {
                    Timber.i("✅ Backtest completed successfully!")
                    Timber.i("   Trades: ${result.totalTrades}")
                    Timber.i("   Win Rate: ${String.format("%.1f%%", result.winRate)}")
                    Timber.i("   P&L: ${String.format("%.2f%%", result.totalPnLPercent)}")
                    _uiState.value = BacktestUiState.BacktestComplete(result)
                }

            } catch (e: Exception) {
                Timber.e(e, "Backtest execution failed")
                _uiState.value = BacktestUiState.Error("Backtest failed: ${e.message}")
            }
        }
    }

    /**
     * Quick test function - runs complete flow automatically
     * Use this for quick testing!
     */
    fun runQuickTest(strategy: Strategy) {
        viewModelScope.launch {
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("🧪 QUICK TEST MODE - COMPLETE FLOW")
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            try {
                // Step 1: Generate proposal
                generateBacktestProposal(strategy)

                // Wait for proposal to be ready
                kotlinx.coroutines.delay(500)

                val proposal = _currentProposal.value
                if (proposal != null) {
                    // Step 2: Auto-approve proposal
                    val decision = BacktestDecision(
                        proposalId = proposal.proposalId,
                        approved = true,
                        userNote = "Quick test - auto approved"
                    )

                    // Step 3: Execute backtest
                    executeBacktest(strategy, decision)
                } else {
                    Timber.e("No proposal generated")
                }

            } catch (e: Exception) {
                Timber.e(e, "Quick test failed")
            }
        }
    }

    // ==============================
    // UTILITY FUNCTIONS
    // ==============================

    fun clearCurrentProposal() {
        _currentProposal.value = null
    }

    fun clearLatestResult() {
        _latestResult.value = null
    }

    fun resetState() {
        _uiState.value = BacktestUiState.Idle
    }
}

/**
 * UI State for backtest management
 */
sealed class BacktestUiState {
    object Idle : BacktestUiState()
    object ScanningData : BacktestUiState()
    data class DataScanComplete(val filesFound: Int) : BacktestUiState()
    data class ImportingData(val current: Int, val total: Int) : BacktestUiState()
    data class ImportComplete(
        val totalBars: Long,
        val successCount: Int,
        val failureCount: Int
    ) : BacktestUiState()
    object GeneratingProposal : BacktestUiState()
    data class ProposalReady(val proposal: BacktestProposal) : BacktestUiState()
    object RunningBacktest : BacktestUiState()
    data class BacktestComplete(val result: BacktestResult) : BacktestUiState()
    data class Error(val message: String) : BacktestUiState()
}
