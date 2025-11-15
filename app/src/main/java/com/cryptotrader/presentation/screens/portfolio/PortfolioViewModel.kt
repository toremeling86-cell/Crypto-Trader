package com.cryptotrader.presentation.screens.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.local.dao.TradeDao
import com.cryptotrader.data.mapper.toDomain
import com.cryptotrader.data.repository.PortfolioRepository
import com.cryptotrader.domain.analytics.PerformanceCalculator
import com.cryptotrader.domain.analytics.PortfolioAnalyticsEngine
import com.cryptotrader.domain.analytics.RiskAnalyticsEngine
import com.cryptotrader.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Portfolio screen
 * Manages state for all 5 tabs: Holdings, Performance, Activity, Analytics, Risk
 */
@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val tradeDao: TradeDao,
    private val analyticsEngine: PortfolioAnalyticsEngine,
    private val riskEngine: RiskAnalyticsEngine,
    private val performanceCalculator: PerformanceCalculator
) : ViewModel() {

    // Holdings State
    private val _holdingsState = MutableStateFlow(HoldingsState())
    val holdingsState: StateFlow<HoldingsState> = _holdingsState.asStateFlow()

    // Performance State
    private val _performanceState = MutableStateFlow(PerformanceState())
    val performanceState: StateFlow<PerformanceState> = _performanceState.asStateFlow()

    // Activity State
    private val _activityState = MutableStateFlow(ActivityState())
    val activityState: StateFlow<ActivityState> = _activityState.asStateFlow()

    // Analytics State
    private val _analyticsState = MutableStateFlow(AnalyticsState())
    val analyticsState: StateFlow<AnalyticsState> = _analyticsState.asStateFlow()

    // Risk State
    private val _riskState = MutableStateFlow(RiskState())
    val riskState: StateFlow<RiskState> = _riskState.asStateFlow()

    init {
        loadAllData()
    }

    /**
     * Load all data for all tabs
     */
    fun loadAllData() {
        viewModelScope.launch {
            launch { loadHoldings() }
            launch { loadPerformance() }
            launch { loadActivity() }
            launch { loadAnalytics() }
            launch { loadRisk() }
        }
    }

    /**
     * Refresh all data
     */
    fun refresh() {
        loadAllData()
    }

    /**
     * Load holdings data
     */
    private suspend fun loadHoldings() {
        try {
            _holdingsState.value = _holdingsState.value.copy(isLoading = true, error = null)

            val holdingsResult = portfolioRepository.getCurrentHoldings()
            if (holdingsResult.isSuccess) {
                val holdings = holdingsResult.getOrNull() ?: emptyList()
                val totalValue = holdings.sumOf { it.currentValue }

                _holdingsState.value = HoldingsState(
                    holdings = holdings,
                    totalValue = totalValue,
                    isLoading = false,
                    error = null
                )
            } else {
                _holdingsState.value = _holdingsState.value.copy(
                    isLoading = false,
                    error = "Failed to load holdings: ${holdingsResult.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading holdings")
            _holdingsState.value = _holdingsState.value.copy(
                isLoading = false,
                error = "Error: ${e.message}"
            )
        }
    }

    /**
     * Load performance data
     */
    private suspend fun loadPerformance() {
        try {
            _performanceState.value = _performanceState.value.copy(isLoading = true, error = null)

            val period = _performanceState.value.selectedPeriod
            val snapshots = portfolioRepository.getHistoricalSnapshots(period)

            val metrics = performanceCalculator.calculatePerformance(snapshots)
            val chartData = performanceCalculator.snapshotsToChartPoints(snapshots)

            _performanceState.value = PerformanceState(
                chartData = chartData,
                selectedPeriod = period,
                totalReturn = metrics.totalReturn,
                roi = metrics.roi,
                dailyPnL = metrics.dailyPnL,
                isLoading = false,
                error = null
            )
        } catch (e: Exception) {
            Timber.e(e, "Error loading performance")
            _performanceState.value = _performanceState.value.copy(
                isLoading = false,
                error = "Error: ${e.message}"
            )
        }
    }

    /**
     * Load activity (trades) data
     */
    private suspend fun loadActivity() {
        try {
            _activityState.value = _activityState.value.copy(isLoading = true, error = null)

            tradeDao.getAllTradesFlow().collect { tradeEntities ->
                val trades = tradeEntities.map { it.toDomain() }

                _activityState.value = ActivityState(
                    trades = trades,
                    filteredTrades = trades,
                    isLoading = false,
                    error = null
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading activity")
            _activityState.value = _activityState.value.copy(
                isLoading = false,
                error = "Error: ${e.message}"
            )
        }
    }

    /**
     * Load analytics data
     */
    private suspend fun loadAnalytics() {
        try {
            _analyticsState.value = _analyticsState.value.copy(isLoading = true, error = null)

            val trades = tradeDao.getAllTrades().map { it.toDomain() }
            val snapshots = portfolioRepository.getHistoricalSnapshots(TimePeriod.ALL_TIME)
            val values = snapshots.map { it.totalValue }

            val analytics = analyticsEngine.calculateAnalytics(trades, values)

            _analyticsState.value = AnalyticsState(
                sharpeRatio = analytics.sharpeRatio,
                maxDrawdown = analytics.maxDrawdown,
                winRate = analytics.winRate,
                profitFactor = analytics.profitFactor,
                bestTrade = analytics.bestTrade,
                worstTrade = analytics.worstTrade,
                avgHoldTime = analytics.avgHoldTime,
                monthlyReturns = analytics.monthlyReturns,
                isLoading = false,
                error = null
            )
        } catch (e: Exception) {
            Timber.e(e, "Error loading analytics")
            _analyticsState.value = _analyticsState.value.copy(
                isLoading = false,
                error = "Error: ${e.message}"
            )
        }
    }

    /**
     * Load risk metrics
     */
    private suspend fun loadRisk() {
        try {
            _riskState.value = _riskState.value.copy(isLoading = true, error = null)

            val holdingsResult = portfolioRepository.getCurrentHoldings()
            if (holdingsResult.isSuccess) {
                val holdings = holdingsResult.getOrNull() ?: emptyList()
                val snapshots = portfolioRepository.getHistoricalSnapshots(TimePeriod.ONE_MONTH)

                val returns = snapshots.zipWithNext { a, b ->
                    if (a.totalValue > 0) (b.totalValue - a.totalValue) / a.totalValue else 0.0
                }

                val riskMetrics = riskEngine.calculateRiskMetrics(holdings, returns)

                _riskState.value = RiskState(
                    diversificationScore = riskMetrics.diversificationScore,
                    exposureByAsset = riskMetrics.exposureByAsset,
                    correlationMatrix = riskMetrics.correlationMatrix,
                    valueAtRisk = riskMetrics.valueAtRisk95,
                    positionSizes = riskMetrics.positionSizes,
                    volatilityScore = riskMetrics.volatilityScore,
                    isLoading = false,
                    error = null
                )
            } else {
                _riskState.value = _riskState.value.copy(
                    isLoading = false,
                    error = "Failed to load risk metrics"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading risk")
            _riskState.value = _riskState.value.copy(
                isLoading = false,
                error = "Error: ${e.message}"
            )
        }
    }

    /**
     * Change performance period
     */
    fun setPerformancePeriod(period: TimePeriod) {
        _performanceState.value = _performanceState.value.copy(selectedPeriod = period)
        viewModelScope.launch {
            loadPerformance()
        }
    }

    /**
     * Search trades
     */
    fun searchTrades(query: String) {
        val allTrades = _activityState.value.trades
        val filtered = if (query.isBlank()) {
            allTrades
        } else {
            allTrades.filter { trade ->
                trade.pair.contains(query, ignoreCase = true) ||
                trade.type.toString().contains(query, ignoreCase = true)
            }
        }

        _activityState.value = _activityState.value.copy(
            searchQuery = query,
            filteredTrades = filtered
        )
    }
}

/**
 * State classes for each tab
 */
data class HoldingsState(
    val holdings: List<PortfolioHolding> = emptyList(),
    val totalValue: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class PerformanceState(
    val chartData: List<ChartPoint> = emptyList(),
    val selectedPeriod: TimePeriod = TimePeriod.ONE_MONTH,
    val totalReturn: Double = 0.0,
    val roi: Double = 0.0,
    val dailyPnL: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ActivityState(
    val trades: List<Trade> = emptyList(),
    val filteredTrades: List<Trade> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

data class AnalyticsState(
    val sharpeRatio: Double = 0.0,
    val maxDrawdown: Double = 0.0,
    val winRate: Double = 0.0,
    val profitFactor: Double = 0.0,
    val bestTrade: Trade? = null,
    val worstTrade: Trade? = null,
    val avgHoldTime: Long = 0,
    val monthlyReturns: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class RiskState(
    val diversificationScore: Double = 0.0,
    val exposureByAsset: Map<String, Double> = emptyMap(),
    val correlationMatrix: Map<Pair<String, String>, Double> = emptyMap(),
    val valueAtRisk: Double = 0.0,
    val positionSizes: Map<String, Double> = emptyMap(),
    val volatilityScore: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null
)
