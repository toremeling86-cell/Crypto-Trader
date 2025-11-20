package com.cryptotrader.presentation.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.PortfolioRepository
import com.cryptotrader.data.repository.StrategyRepository
import com.cryptotrader.domain.analytics.PerformanceCalculator
import com.cryptotrader.domain.analytics.StrategyAnalytics
import com.cryptotrader.domain.analytics.StrategyPerformance
import com.cryptotrader.domain.model.PerformanceMetrics
import com.cryptotrader.domain.model.PortfolioSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val performanceCalculator: PerformanceCalculator,
    private val strategyAnalytics: StrategyAnalytics,
    private val strategyRepository: StrategyRepository,
    private val portfolioRepository: PortfolioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PerformanceState())
    val uiState: StateFlow<PerformanceState> = _uiState.asStateFlow()

    init {
        loadPerformanceData()
    }

    private fun loadPerformanceData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 1. Load Portfolio Performance Metrics
                val snapshots = portfolioRepository.getPortfolioHistory().first() // Assuming this returns List<PortfolioSnapshot>
                val metrics = performanceCalculator.calculatePerformanceDecimal(snapshots)
                
                // 2. Load Strategy Performance
                val strategies = strategyRepository.getAllStrategies().first()
                val strategyPerformances = strategies.map { strategy ->
                    val perf = strategyAnalytics.calculateStrategyPerformance(strategy.id)
                    strategy.name to perf
                }.toMap()

                // 3. Prepare Chart Data (P&L over time)
                val pnlChartData = snapshots.map { snapshot ->
                    snapshot.timestamp to snapshot.totalPnLDecimal.toDouble()
                }

                // 4. Prepare Win/Loss Distribution
                // We need to aggregate trades from all strategies or get global trade stats
                // For simplicity, let's aggregate from strategy performances
                var totalWins = 0
                var totalLosses = 0
                strategyPerformances.values.forEach { 
                    totalWins += it.winningTrades
                    totalLosses += it.losingTrades
                }

                // 5. Trades per Pair
                // This would require querying all trades. Let's assume we can get this from a repository or aggregate.
                // For now, we'll leave it empty or implement if we have a TradeRepository.
                // Let's skip detailed trades-per-pair for this MVP step to avoid over-fetching.

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    performanceMetrics = metrics,
                    strategyPerformances = strategyPerformances,
                    pnlChartData = pnlChartData,
                    winLossDistribution = totalWins to totalLosses,
                    bestTrade = strategyPerformances.values.maxOfOrNull { it.bestTrade } ?: 0.0,
                    worstTrade = strategyPerformances.values.minOfOrNull { it.worstTrade } ?: 0.0,
                    totalTrades = totalWins + totalLosses,
                    activePositions = 0 // TODO: Get from PositionRepository if needed
                )

            } catch (e: Exception) {
                Timber.e(e, "Error loading performance data")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load performance data: ${e.message}"
                )
            }
        }
    }
    
    fun refreshData() {
        loadPerformanceData()
    }
}

data class PerformanceState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val performanceMetrics: PerformanceMetrics? = null,
    val strategyPerformances: Map<String, StrategyPerformance> = emptyMap(),
    val pnlChartData: List<Pair<Long, Double>> = emptyList(),
    val winLossDistribution: Pair<Int, Int> = 0 to 0,
    val bestTrade: Double = 0.0,
    val worstTrade: Double = 0.0,
    val totalTrades: Int = 0,
    val activePositions: Int = 0
)
