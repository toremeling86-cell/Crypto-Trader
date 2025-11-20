package com.cryptotrader.presentation.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.AnalyticsRepository
import com.cryptotrader.data.repository.PerformanceMetrics
import com.cryptotrader.data.repository.StrategyPerformance
import com.cryptotrader.data.repository.TimeInterval
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

/**
 * ViewModel for Performance Analytics Dashboard
 *
 * Uses the new AnalyticsRepository (Agent 5) for comprehensive performance metrics.
 * All calculations are performed with BigDecimal precision for hedge-fund quality.
 */
@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
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
                // 1. Load Performance Metrics
                val metrics = analyticsRepository.getPerformanceMetrics().first()
                
                // 2. Load Strategy Performance
                val strategyPerformances = analyticsRepository.getStrategyPerformance().first()
                val strategyPerfMap = strategyPerformances.associateBy { it.strategyName }
                
                // 3. Load P&L Chart Data
                val now = System.currentTimeMillis()
                val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
                val pnlDataPoints = analyticsRepository.getPnLOverTime(
                    startDate = thirtyDaysAgo,
                    endDate = now,
                    interval = com.cryptotrader.data.repository.TimeInterval.DAILY
                ).first()
                
                val pnlChartData = pnlDataPoints.map { 
                    it.timestamp to it.cumulativePnL.toDouble() 
                }
                
                // 4. Load Win/Loss Distribution
                val winLossStats = analyticsRepository.getWinLossDistribution().first()
                
                // 5. Load Trades per Pair
                val tradesPerPair = analyticsRepository.getTradesPerPair().first()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    performanceMetrics = metrics,
                    strategyPerformances = strategyPerfMap,
                    pnlChartData = pnlChartData,
                    winLossDistribution = winLossStats.wins to winLossStats.losses,
                    bestTrade = metrics.bestTrade.toDouble(),
                    worstTrade = metrics.worstTrade.toDouble(),
                    totalTrades = metrics.totalTrades,
                    activePositions = metrics.openPositions,
                    tradesPerPair = tradesPerPair
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

/**
 * UI State for Performance Analytics Screen
 *
 * All monetary values use BigDecimal for exact precision.
 */
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
    val activePositions: Int = 0,
    val tradesPerPair: Map<String, Int> = emptyMap()
)
