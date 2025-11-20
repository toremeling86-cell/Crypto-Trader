package com.cryptotrader.presentation.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.StrategyRepository
import com.cryptotrader.domain.backtesting.BacktestEngine
import com.cryptotrader.domain.backtesting.BacktestResult
import com.cryptotrader.domain.backtesting.PriceBar
import com.cryptotrader.domain.backtesting.TradingCostModel
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.utils.toBigDecimalMoney
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

/**
 * Configuration for backtesting
 */
data class BacktestConfig(
    val startingBalance: Double = 10000.0,
    val makerFee: Double = 0.0016,  // 0.16%
    val takerFee: Double = 0.0026,  // 0.26%
    val slippagePercent: Double = 0.05,  // 0.05%
    val spreadPercent: Double = 0.02,  // 0.02%
    val dataPoints: Int = 100,  // Number of price bars to generate
    val useRealisticSlippage: Boolean = true,
    val useTieredFees: Boolean = false
)

/**
 * UI State for Strategy Test Center
 */
data class StrategyTestCenterUiState(
    val strategies: List<Strategy> = emptyList(),
    val selectedStrategy: Strategy? = null,
    val backtestConfig: BacktestConfig = BacktestConfig(),
    val backtestResult: BacktestResult? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StrategyTestCenterViewModel @Inject constructor(
    private val strategyRepository: StrategyRepository,
    private val backtestEngine: BacktestEngine,
    private val marketDataRepository: com.cryptotrader.data.repository.MarketDataRepository,
    private val datasetManager: com.cryptotrader.domain.data.DatasetManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StrategyTestCenterUiState())
    val uiState: StateFlow<StrategyTestCenterUiState> = _uiState.asStateFlow()

    // Expose dataset manager state
    val availableDatasets = datasetManager.availableDatasets
    val activeDataset = datasetManager.activeDataset

    init {
        loadStrategies()
    }

    private fun loadStrategies() {
        viewModelScope.launch {
            try {
                strategyRepository.getAllStrategies().collect { strategies ->
                    _uiState.value = _uiState.value.copy(
                        strategies = strategies,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading strategies")
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load strategies: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun selectStrategy(strategy: Strategy) {
        _uiState.value = _uiState.value.copy(selectedStrategy = strategy)
    }

    fun resetToStrategySelection() {
        _uiState.value = _uiState.value.copy(
            selectedStrategy = null,
            backtestResult = null,
            error = null
        )
    }

    fun runBacktest() {
        val strategy = _uiState.value.selectedStrategy ?: return
        val config = _uiState.value.backtestConfig

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Fetch REAL historical data from Kraken
                val pair = strategy.tradingPairs.firstOrNull() ?: "XXBTZUSD"
                val ohlcResult = marketDataRepository.getHistoricalOHLC(
                    pair = pair,
                    intervalMinutes = 60, // 1 hour candles
                    numCandles = config.dataPoints
                )

                if (ohlcResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Kunne ikke hente historisk data: ${ohlcResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }

                val ohlcCandles = ohlcResult.getOrNull()!!
                val historicalData = marketDataRepository.convertToPriceBars(ohlcCandles)
                Timber.i("Fetched ${historicalData.size} REAL candles from Kraken for backtest")

                // Create cost model from config
                val costModel = TradingCostModel(
                    makerFee = config.makerFee,
                    takerFee = config.takerFee,
                    slippagePercent = config.slippagePercent,
                    spreadPercent = config.spreadPercent,
                    useRealisticSlippage = config.useRealisticSlippage,
                    useTieredFees = config.useTieredFees
                )

                // Run backtest (temporarily set strategy as active for testing)
                val testStrategy = strategy.copy(isActive = true)
                val resultDecimal = backtestEngine.runBacktestDecimal(
                    strategy = testStrategy,
                    historicalData = historicalData,
                    startingBalance = config.startingBalance.toBigDecimalMoney(),
                    costModel = costModel,
                    ohlcBars = null // Skip tier validation for manual test
                )
                
                val result = resultDecimal.toBacktestResult()

                _uiState.value = _uiState.value.copy(
                    backtestResult = result,
                    isLoading = false
                )

                Timber.i("Backtest completed: P&L ${result.totalPnL}, Win Rate: ${result.winRate}%")

            } catch (e: Exception) {
                Timber.e(e, "Error running backtest")
                _uiState.value = _uiState.value.copy(
                    error = "Failed to run backtest: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun updateConfig(config: BacktestConfig) {
        _uiState.value = _uiState.value.copy(backtestConfig = config)
    }

    fun activateDataset(datasetId: String) {
        viewModelScope.launch {
            try {
                datasetManager.activateDataset(datasetId)
                Timber.i("Activated dataset: $datasetId")
            } catch (e: Exception) {
                Timber.e(e, "Error activating dataset")
                _uiState.value = _uiState.value.copy(
                    error = "Failed to activate dataset: ${e.message}"
                )
            }
        }
    }
}
