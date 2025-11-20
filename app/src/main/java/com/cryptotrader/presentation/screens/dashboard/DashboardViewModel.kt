package com.cryptotrader.presentation.screens.dashboard

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cryptotrader.CryptoTraderApplication
import com.cryptotrader.data.repository.KrakenRepository
import com.cryptotrader.data.repository.StrategyRepository
import com.cryptotrader.domain.model.Portfolio
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.domain.model.Trade
import com.cryptotrader.domain.trading.StrategyEvaluator
import com.cryptotrader.utils.formatCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val krakenRepository: KrakenRepository,
    private val strategyRepository: StrategyRepository,
    private val strategyEvaluator: StrategyEvaluator,
    private val profitCalculator: com.cryptotrader.domain.trading.ProfitCalculator,
    private val notificationManager: com.cryptotrader.notifications.NotificationManager,
    private val context: android.content.Context,
    private val application: Application
) : ViewModel() {

    private val currencyPreferences = com.cryptotrader.data.preferences.CurrencyPreferences(context)

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    val recentTrades: StateFlow<List<Trade>> = krakenRepository
        .getRecentTrades(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeStrategies: StateFlow<List<Strategy>> = strategyRepository
        .getActiveStrategies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadDashboardData()
        observeTradingWorkerStatus()
        startLivePortfolioMonitoring()
        startLivePriceUpdates()
    }

    /**
     * Observe trading worker status
     */
    private fun observeTradingWorkerStatus() {
        viewModelScope.launch {
            try {
                WorkManager.getInstance(application)
                    .getWorkInfosForUniqueWorkFlow("trading_worker")
                    .collect { workInfos ->
                        val isRunning = workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                        _uiState.value = _uiState.value.copy(isTradingActive = isRunning)
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error observing trading worker status")
            }
        }
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                // Check if API credentials are configured (only in non-paper-trading mode)
                val isPaperTrading = com.cryptotrader.utils.CryptoUtils.isPaperTradingMode(context)
                _uiState.value = _uiState.value.copy(isPaperTrading = isPaperTrading)

                if (!isPaperTrading) {
                    val hasApiKeys = com.cryptotrader.utils.CryptoUtils.hasApiCredentials(context)
                    if (!hasApiKeys) {
                        Timber.w("⚠️ Kraken API keys not configured - showing empty portfolio")

                        // Create empty portfolio to show user needs to configure API
                        val emptyPortfolio = Portfolio(
                            totalValue = 0.0,
                            availableBalance = 0.0,
                            balances = emptyMap(),
                            totalProfit = 0.0,
                            totalProfitPercent = 0.0,
                            dayProfit = 0.0,
                            dayProfitPercent = 0.0,
                            openPositions = 0
                        )

                        _uiState.value = _uiState.value.copy(
                            portfolio = emptyPortfolio,
                            isLoading = false,
                            errorMessage = "⚙️ Configure Kraken API keys in Settings to see your portfolio"
                        )
                        return@launch
                    }
                }

                // Load balance
                val balanceResult = krakenRepository.getBalance()
                if (balanceResult.isSuccess) {
                    val krakenBalances = balanceResult.getOrNull() ?: emptyMap()
                    Timber.d("📊 Kraken balances received: ${krakenBalances.size} assets")

                    // Parse Kraken balance response and convert to USD
                    // Format: {"ZUSD": "1000.0000", "XXBT": "0.5000", ...}
                    val assetBalances = mutableMapOf<String, com.cryptotrader.domain.model.AssetBalance>()
                    var totalValueUSD = 0.0
                    var availableFunds = 0.0

                    // Get EUR/USD exchange rate for conversion using correct Kraken pair name
                    var eurUsdRate = currencyPreferences.getCachedEurUsdRate()
                    val eurUsdPair = com.cryptotrader.utils.KrakenAssetMapper.getKrakenPair("EUR", "USD")
                    val eurTickerResult = krakenRepository.getTicker(eurUsdPair)
                    if (eurTickerResult.isSuccess) {
                        eurUsdRate = eurTickerResult.getOrNull()?.last ?: eurUsdRate
                        currencyPreferences.updateEurUsdRate(eurUsdRate)
                    }

                    // Get USD/NOK exchange rate for conversion
                    var usdNokRate = 10.50 // Default fallback rate
                    val usdNokPair = "USDNOK" // Kraken uses USDNOK format
                    val nokTickerResult = krakenRepository.getTicker(usdNokPair)
                    if (nokTickerResult.isSuccess) {
                        usdNokRate = nokTickerResult.getOrNull()?.last ?: usdNokRate
                    }

                    krakenBalances.forEach { (asset, balanceStr) ->
                        val balance = balanceStr.toDoubleOrNull() ?: 0.0
                        if (balance > 0) {
                            // Normalize Kraken asset names using comprehensive mapper
                            // ZUSD -> USD, XXBT -> BTC, XETH -> ETH, etc.
                            val normalizedAsset = com.cryptotrader.utils.KrakenAssetMapper.normalizeAsset(asset)

                            // Calculate value in USD
                            val valueInUSD = when (normalizedAsset) {
                                "USD" -> balance // Already in USD
                                "EUR" -> {
                                    // EUR already converted above using eurUsdRate
                                    balance * eurUsdRate
                                }
                                "BTC" -> {
                                    // Get BTC price to calculate USD value
                                    val btcPair = com.cryptotrader.utils.KrakenAssetMapper.getKrakenPair("BTC", "USD")
                                    val btcTickerResult = krakenRepository.getTicker(btcPair)
                                    if (btcTickerResult.isSuccess) {
                                        balance * (btcTickerResult.getOrNull()?.last ?: 0.0)
                                    } else {
                                        Timber.w("Failed to fetch BTC price for portfolio calculation")
                                        0.0 // Cannot calculate value without price
                                    }
                                }
                                "ETH" -> {
                                    // Get ETH price to calculate USD value
                                    val ethPair = com.cryptotrader.utils.KrakenAssetMapper.getKrakenPair("ETH", "USD")
                                    val ethTickerResult = krakenRepository.getTicker(ethPair)
                                    if (ethTickerResult.isSuccess) {
                                        balance * (ethTickerResult.getOrNull()?.last ?: 0.0)
                                    } else {
                                        Timber.w("Failed to fetch ETH price for portfolio calculation")
                                        0.0 // Cannot calculate value without price
                                    }
                                }
                                "SOL" -> {
                                    // Get SOL price to calculate USD value
                                    val solPair = com.cryptotrader.utils.KrakenAssetMapper.getKrakenPair("SOL", "USD")
                                    val solTickerResult = krakenRepository.getTicker(solPair)
                                    if (solTickerResult.isSuccess) {
                                        balance * (solTickerResult.getOrNull()?.last ?: 0.0)
                                    } else {
                                        Timber.w("Failed to fetch SOL price for portfolio calculation")
                                        0.0 // Cannot calculate value without price
                                    }
                                }
                                else -> {
                                    // Try to get price for unknown asset
                                    try {
                                        val unknownPair = com.cryptotrader.utils.KrakenAssetMapper.getKrakenPair(normalizedAsset, "USD")
                                        val tickerResult = krakenRepository.getTicker(unknownPair)
                                        if (tickerResult.isSuccess) {
                                            balance * (tickerResult.getOrNull()?.last ?: 0.0)
                                        } else {
                                            Timber.w("Cannot get price for unknown asset: $normalizedAsset")
                                            0.0
                                        }
                                    } catch (e: IllegalArgumentException) {
                                        Timber.w("Unknown asset without USD pair mapping: $normalizedAsset")
                                        0.0
                                    }
                                }
                            }

                            assetBalances[normalizedAsset] = com.cryptotrader.domain.model.AssetBalance(
                                asset = normalizedAsset,
                                balance = balance,
                                valueInUSD = valueInUSD,
                                percentOfPortfolio = 0.0, // Will calculate after total
                                valueInEUR = valueInUSD / eurUsdRate // Convert USD to EUR
                            )

                            totalValueUSD += valueInUSD

                            // Available funds = USD + EUR balances (not locked in positions)
                            if (normalizedAsset == "USD" || normalizedAsset == "EUR") {
                                availableFunds += valueInUSD
                            }
                        }
                    }

                    // Update percentages (no need to recalculate EUR values, already set)
                    val assetBalancesWithPercent = assetBalances.mapValues { (_, assetBalance) ->
                        assetBalance.copy(
                            percentOfPortfolio = if (totalValueUSD > 0) {
                                (assetBalance.valueInUSD / totalValueUSD) * 100.0
                            } else 0.0
                        )
                    }

                    // Calculate P&L
                    val (totalPnL, totalPnLPercent, _) = profitCalculator.calculateTotalPnL()
                    val (dayPnL, dayPnLPercent, _) = profitCalculator.calculateDailyPnL()

                    val portfolio = Portfolio(
                        totalValue = totalValueUSD,
                        availableBalance = availableFunds,
                        balances = assetBalancesWithPercent,
                        totalProfit = totalPnL,
                        totalProfitPercent = totalPnLPercent,
                        dayProfit = dayPnL,
                        dayProfitPercent = dayPnLPercent,
                        openPositions = assetBalances.size - 1, // Exclude USD
                        // EUR values
                        totalValueEUR = totalValueUSD / eurUsdRate,
                        availableBalanceEUR = availableFunds / eurUsdRate,
                        totalProfitEUR = totalPnL / eurUsdRate,
                        dayProfitEUR = dayPnL / eurUsdRate,
                        eurUsdRate = eurUsdRate,
                        // NOK values
                        totalValueNOK = totalValueUSD * usdNokRate,
                        availableBalanceNOK = availableFunds * usdNokRate,
                        totalProfitNOK = totalPnL * usdNokRate,
                        dayProfitNOK = dayPnL * usdNokRate,
                        usdNokRate = usdNokRate
                    )

                    // Get price history status
                    val priceHistoryStatus = strategyEvaluator.getAllPriceHistoryStatus()

                    _uiState.value = _uiState.value.copy(
                        portfolio = portfolio,
                        isLoading = false,
                        priceHistoryStatus = priceHistoryStatus
                    )

                    Timber.i("✅ Portfolio loaded: Total value = ${portfolio.totalValue.formatCurrency()}")
                } else {
                    throw balanceResult.exceptionOrNull() ?: Exception("Unknown error")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading dashboard data")

                // Create empty portfolio on error
                val emptyPortfolio = Portfolio(
                    totalValue = 0.0,
                    availableBalance = 0.0,
                    balances = emptyMap(),
                    totalProfit = 0.0,
                    totalProfitPercent = 0.0,
                    dayProfit = 0.0,
                    dayProfitPercent = 0.0,
                    openPositions = 0
                )

                _uiState.value = _uiState.value.copy(
                    portfolio = emptyPortfolio,
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load portfolio data"
                )
            }
        }
    }

    fun refresh() {
        loadDashboardData()
    }

    /**
     * Get selected currency preference
     */
    fun getSelectedCurrency(): com.cryptotrader.data.preferences.Currency {
        return currencyPreferences.getSelectedCurrency()
    }

    /**
     * Set currency preference
     */
    fun setSelectedCurrency(currency: com.cryptotrader.data.preferences.Currency) {
        currencyPreferences.setSelectedCurrency(currency)
        // Refresh to recalculate display (though values are pre-calculated, UI needs to update)
        _uiState.value = _uiState.value.copy()
    }

    /**
     * Get friendly message about price history readiness
     */
    fun getPriceHistoryMessage(): String? {
        val status = _uiState.value.priceHistoryStatus
        if (status.isEmpty()) {
            return "Connecting to market data..."
        }

        val notReady = status.filter { (_, counts) -> counts.first < counts.second }
        if (notReady.isEmpty()) {
            return null // All ready
        }

        // Show status for first not-ready pair
        val (pair, counts) = notReady.entries.first()
        return "Building price history for $pair: ${counts.first}/${counts.second} data points"
    }

    /**
     * Activate emergency stop - disable all strategies and stop trading
     */
    fun activateEmergencyStop() {
        viewModelScope.launch {
            try {
                Timber.w("🚨 EMERGENCY STOP ACTIVATED")

                // Mark emergency stop as active
                com.cryptotrader.utils.CryptoUtils.activateEmergencyStop(context)

                // Send emergency notification
                notificationManager.notifyEmergencyStop()

                // Disable all active strategies
                val activeStrategies = strategyRepository.getActiveStrategies().first()
                activeStrategies.forEach { strategy ->
                    strategyRepository.setStrategyActive(strategy.id, false)
                    Timber.d("Disabled strategy: ${strategy.name}")
                }

                _uiState.value = _uiState.value.copy(
                    errorMessage = "🚨 EMERGENCY STOP ACTIVATED - All trading halted"
                )

                Timber.i("Emergency stop completed. ${activeStrategies.size} strategies disabled.")
            } catch (e: Exception) {
                Timber.e(e, "Error during emergency stop")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Emergency stop error: ${e.message}"
                )
            }
        }
    }

    /**
     * Deactivate emergency stop - resume trading
     */
    fun deactivateEmergencyStop() {
        com.cryptotrader.utils.CryptoUtils.deactivateEmergencyStop(context)
        notificationManager.clearEmergencyStopNotification()
        Timber.i("Emergency stop deactivated - Trading can resume")
        _uiState.value = _uiState.value.copy(
            errorMessage = "Emergency stop deactivated. You can now enable strategies."
        )
    }

    /**
     * Start trading worker
     */
    fun startTrading() {
        viewModelScope.launch {
            try {
                val app = application as CryptoTraderApplication
                app.scheduleTradingWorker()

                _uiState.value = _uiState.value.copy(
                    isTradingActive = true,
                    errorMessage = "✅ Live trading started - Checking strategies every minute"
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to start trading")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to start trading: ${e.message}"
                )
            }
        }
    }

    /**
     * Stop trading worker
     */
    fun stopTrading() {
        viewModelScope.launch {
            try {
                val app = application as CryptoTraderApplication
                app.stopTradingWorker()

                _uiState.value = _uiState.value.copy(
                    isTradingActive = false,
                    errorMessage = "⏹️ Live trading stopped - No automatic trades will be placed"
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop trading")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to stop trading: ${e.message}"
                )
            }
        }
    }

    /**
     * Start periodic portfolio refresh (every 30 seconds)
     */
    private fun startLivePortfolioMonitoring() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000) // 30 seconds
                try {
                    loadDashboardData()
                    Timber.d("📊 Portfolio refreshed automatically")
                } catch (e: Exception) {
                    Timber.e(e, "Error during automatic portfolio refresh")
                }
            }
        }
    }

    /**
     * Start WebSocket connection for real-time price updates
     */
    private fun startLivePriceUpdates() {
        viewModelScope.launch {
            try {
                // Get all active trading pairs from strategies
                val strategies = strategyRepository.getActiveStrategies().first()
                val tradingPairs = strategies.flatMap { it.tradingPairs }.distinct()

                if (tradingPairs.isNotEmpty()) {
                    Timber.d("📡 Starting live price updates for: ${tradingPairs.joinToString()}")

                    krakenRepository.subscribeToTickerUpdates(tradingPairs)
                        .collect { ticker ->
                            Timber.d("💹 Price update: ${ticker.pair} = $${ticker.last}")
                            // Note: latestPriceUpdate is removed as TickerUpdate doesn't match MarketTicker type
                        }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting live price updates")
            }
        }
    }

    /**
     * Toggle Paper Trading Mode
     */
    fun setPaperTradingMode(enabled: Boolean) {
        viewModelScope.launch {
            try {
                com.cryptotrader.utils.CryptoUtils.setPaperTradingMode(context, enabled)
                
                // Update state
                _uiState.value = _uiState.value.copy(
                    isPaperTrading = enabled,
                    errorMessage = if (enabled) 
                        "Switched to Paper Trading Mode (Simulated)" 
                    else 
                        "⚠️ Switched to LIVE TRADING MODE (Real Money)"
                )
                
                // Reload data to reflect mode change (different portfolios)
                loadDashboardData()
                
                Timber.i("Trading mode switched: ${if (enabled) "PAPER" else "LIVE"}")
            } catch (e: Exception) {
                Timber.e(e, "Error switching trading mode")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to switch trading mode: ${e.message}"
                )
            }
        }
    }
}

data class DashboardState(
    val portfolio: Portfolio? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val priceHistoryStatus: Map<String, Pair<Int, Int>> = emptyMap(), // pair -> (current, required)
    val isTradingActive: Boolean = false,
    val isPaperTrading: Boolean = true
)
