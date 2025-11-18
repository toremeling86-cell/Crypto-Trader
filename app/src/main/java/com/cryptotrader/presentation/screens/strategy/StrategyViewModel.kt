package com.cryptotrader.presentation.screens.strategy

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.CryptoTraderApplication
import com.cryptotrader.data.repository.StrategyRepository
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.domain.usecase.GenerateStrategyUseCase
import com.cryptotrader.domain.usecase.BacktestValidation
import com.cryptotrader.domain.usecase.BacktestStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class StrategyViewModel @Inject constructor(
    private val strategyRepository: StrategyRepository,
    private val generateStrategyUseCase: GenerateStrategyUseCase,
    private val strategyAnalytics: com.cryptotrader.domain.analytics.StrategyAnalytics,
    private val notificationManager: com.cryptotrader.notifications.NotificationManager,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(StrategyState())
    val uiState: StateFlow<StrategyState> = _uiState.asStateFlow()

    val strategies: StateFlow<List<Strategy>> = strategyRepository
        .getAllStrategies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingStrategies: StateFlow<List<Strategy>> = strategyRepository
        .getPendingStrategies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map of strategy ID to performance metrics
    private val _strategyPerformances = MutableStateFlow<Map<String, com.cryptotrader.domain.analytics.StrategyPerformance>>(emptyMap())
    val strategyPerformances: StateFlow<Map<String, com.cryptotrader.domain.analytics.StrategyPerformance>> = _strategyPerformances.asStateFlow()

    init {
        // Calculate analytics whenever strategies change
        viewModelScope.launch {
            strategies.collect { strategyList ->
                if (strategyList.isNotEmpty()) {
                    calculateAllAnalytics(strategyList.map { it.id })
                }
            }
        }
    }

    private suspend fun calculateAllAnalytics(strategyIds: List<String>) {
        try {
            val performances = strategyIds.associateWith { strategyId ->
                strategyAnalytics.calculateStrategyPerformance(strategyId)
            }
            _strategyPerformances.value = performances
        } catch (e: Exception) {
            Timber.e(e, "Error calculating strategy analytics")
        }
    }

    fun onDescriptionChanged(description: String) {
        _uiState.value = _uiState.value.copy(description = description, errorMessage = null)
    }

    fun generateStrategy(tradingPairs: List<String> = listOf("XXBTZUSD")) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isGenerating = true,
                    errorMessage = null,
                    backtestValidation = null
                )

                val description = _uiState.value.description
                if (description.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        errorMessage = "Please describe your strategy"
                    )
                    return@launch
                }

                // Generate strategy with automatic backtesting
                val result = generateStrategyUseCase(
                    description = description,
                    tradingPairs = tradingPairs,
                    runBacktest = true // Auto-backtest all AI strategies
                )

                if (result.isSuccess) {
                    val generationResult = result.getOrNull()!!
                    val strategy = generationResult.strategy
                    val validation = generationResult.backtestValidation

                    // Store strategy in database
                    strategyRepository.insertStrategy(strategy)

                    // Build success/warning message based on backtest
                    val message = buildBacktestMessage(validation)

                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generatedStrategy = strategy,
                        backtestValidation = validation,
                        description = "",
                        successMessage = message
                    )

                    Timber.i("Strategy generated: ${strategy.name}")
                    if (validation != null) {
                        Timber.i("Backtest status: ${validation.status}")
                    }
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error generating strategy")
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    errorMessage = e.message ?: "Failed to generate strategy"
                )
            }
        }
    }

    /**
     * Build user-friendly message based on backtest results
     */
    private fun buildBacktestMessage(validation: BacktestValidation?): String {
        if (validation == null) {
            return "Strategy generated successfully (no backtest available)"
        }

        val result = validation.result
        return when (validation.status) {
            BacktestStatus.EXCELLENT -> {
                "✅ Excellent backtest! Win rate: ${result.winRate.toInt()}%, " +
                        "Profit: ${result.totalPnLPercent.toInt()}%"
            }
            BacktestStatus.GOOD -> {
                "✅ Good backtest! Win rate: ${result.winRate.toInt()}%, " +
                        "Profit: ${result.totalPnLPercent.toInt()}%"
            }
            BacktestStatus.ACCEPTABLE -> {
                "⚠️ Acceptable backtest. Win rate: ${result.winRate.toInt()}%, " +
                        "Profit: ${result.totalPnLPercent.toInt()}%. Use with caution."
            }
            BacktestStatus.FAILED -> {
                "❌ Backtest FAILED! Win rate: ${result.winRate.toInt()}%, " +
                        "Profit: ${result.totalPnLPercent.toInt()}%. DO NOT activate!"
            }
        }
    }

    fun showConfirmDialog(strategyId: String) {
        _uiState.value = _uiState.value.copy(
            confirmDialogStrategyId = strategyId
        )
    }

    fun dismissConfirmDialog() {
        _uiState.value = _uiState.value.copy(
            confirmDialogStrategyId = null
        )
    }

    fun confirmToggleStrategy(strategyId: String, isActive: Boolean) {
        viewModelScope.launch {
            try {
                // Safety check: If activating and there's a failed backtest, warn user
                if (isActive) {
                    val validation = _uiState.value.backtestValidation
                    if (validation != null && validation.status == BacktestStatus.FAILED) {
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "⚠️ Cannot activate: This strategy FAILED backtesting. " +
                                    "It would likely lose money in live trading."
                        )
                        dismissConfirmDialog()
                        return@launch
                    }
                }

                strategyRepository.setStrategyActive(strategyId, isActive)
                Timber.d("Strategy toggled: $strategyId -> $isActive")

                // Send notification when activating strategy
                if (isActive) {
                    val strategy = strategies.value.find { it.id == strategyId }
                    if (strategy != null) {
                        notificationManager.notifyStrategyActivated(strategy.name)

                        // Auto-start trading worker when strategy is activated
                        startTradingWorkerIfNeeded()
                    }
                }

                dismissConfirmDialog()
            } catch (e: Exception) {
                Timber.e(e, "Error toggling strategy")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to activate strategy: ${e.message}"
                )
            }
        }
    }

    fun toggleStrategy(strategyId: String, isActive: Boolean) {
        // If activating, show confirmation dialog
        if (isActive) {
            showConfirmDialog(strategyId)
        } else {
            // Deactivating doesn't need confirmation
            confirmToggleStrategy(strategyId, false)
        }
    }

    fun showDeleteConfirmDialog(strategy: Strategy) {
        _uiState.value = _uiState.value.copy(
            deleteConfirmStrategy = strategy
        )
    }

    fun dismissDeleteConfirmDialog() {
        _uiState.value = _uiState.value.copy(
            deleteConfirmStrategy = null
        )
    }

    fun confirmDeleteStrategy() {
        viewModelScope.launch {
            try {
                val strategy = _uiState.value.deleteConfirmStrategy
                if (strategy != null) {
                    strategyRepository.deleteStrategy(strategy)
                    Timber.d("Strategy deleted: ${strategy.name}")
                    dismissDeleteConfirmDialog()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting strategy")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete strategy: ${e.message}"
                )
            }
        }
    }

    fun deleteStrategy(strategy: Strategy) {
        showDeleteConfirmDialog(strategy)
    }

    fun approveStrategy(strategyId: String) {
        viewModelScope.launch {
            try {
                strategyRepository.approveStrategy(strategyId)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Strategi godkjent! Du kan nå aktivere den."
                )
                Timber.i("Strategy approved: $strategyId")
            } catch (e: Exception) {
                Timber.e(e, "Error approving strategy")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Kunne ikke godkjenne strategi: ${e.message}"
                )
            }
        }
    }

    fun rejectStrategy(strategyId: String) {
        viewModelScope.launch {
            try {
                strategyRepository.rejectStrategy(strategyId)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Strategi avvist og fjernet fra listen."
                )
                Timber.i("Strategy rejected: $strategyId")
            } catch (e: Exception) {
                Timber.e(e, "Error rejecting strategy")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Kunne ikke avvise strategi: ${e.message}"
                )
            }
        }
    }

    /**
     * Set trading mode for a strategy (INACTIVE, PAPER, LIVE)
     */
    fun setTradingMode(strategyId: String, mode: com.cryptotrader.domain.model.TradingMode) {
        viewModelScope.launch {
            try {
                val strategy = strategyRepository.getStrategyById(strategyId)
                if (strategy == null) {
                    Timber.e("Strategy not found: $strategyId")
                    return@launch
                }

                // Update strategy with new trading mode
                val updatedStrategy = strategy.copy(
                    tradingMode = mode,
                    isActive = mode != com.cryptotrader.domain.model.TradingMode.INACTIVE
                )

                strategyRepository.updateStrategy(updatedStrategy)

                Timber.i("🎯 Strategy ${strategy.name} set to ${mode.name} mode")

                // Start or stop AutoTradingService
                updateAutoTradingService()

                // Show success message
                val modeText = when (mode) {
                    com.cryptotrader.domain.model.TradingMode.INACTIVE -> "deaktivert"
                    com.cryptotrader.domain.model.TradingMode.PAPER -> "Paper Trading"
                    com.cryptotrader.domain.model.TradingMode.LIVE -> "Live Trading"
                }
                _uiState.value = _uiState.value.copy(
                    successMessage = "Strategi satt til $modeText"
                )

            } catch (e: Exception) {
                Timber.e(e, "Error setting trading mode")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Kunne ikke endre trading mode: ${e.message}"
                )
            }
        }
    }

    /**
     * Start or stop AutoTradingService based on active strategies
     */
    private suspend fun updateAutoTradingService() {
        try {
            val activeStrategies = strategyRepository.getActiveStrategies().first()

            if (activeStrategies.isEmpty()) {
                // No active strategies -> stop service
                com.cryptotrader.services.AutoTradingService.stop(application)
                Timber.i("🛑 AutoTradingService stopped (no active strategies)")
            } else {
                // Has active strategies -> start service
                com.cryptotrader.services.AutoTradingService.start(application)
                Timber.i("🚀 AutoTradingService started (${activeStrategies.size} active strategies)")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating AutoTradingService")
        }
    }

    /**
     * Start trading worker if there are active strategies
     */
    private fun startTradingWorkerIfNeeded() {
        viewModelScope.launch {
            try {
                val app = application as CryptoTraderApplication
                app.scheduleTradingWorker()
                Timber.i("✅ Trading worker auto-started (strategy activated)")
            } catch (e: Exception) {
                Timber.e(e, "Failed to auto-start trading worker")
            }
        }
    }
}

data class StrategyState(
    val description: String = "",
    val isGenerating: Boolean = false,
    val generatedStrategy: Strategy? = null,
    val backtestValidation: BacktestValidation? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val confirmDialogStrategyId: String? = null,
    val deleteConfirmStrategy: Strategy? = null
)
