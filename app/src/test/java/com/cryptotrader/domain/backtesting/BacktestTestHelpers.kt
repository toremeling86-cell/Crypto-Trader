package com.cryptotrader.domain.backtesting

import com.cryptotrader.data.local.dao.OHLCBarDao
import com.cryptotrader.data.local.dao.TimestampRange
import com.cryptotrader.data.local.entities.OHLCBarEntity
import com.cryptotrader.data.repository.HistoricalDataRepository
import com.cryptotrader.data.repository.TradeRepository
import com.cryptotrader.domain.indicators.atr.AtrCalculator
import com.cryptotrader.domain.indicators.bollingerbands.BollingerBandsCalculator
import com.cryptotrader.domain.indicators.cache.IndicatorCache
import com.cryptotrader.domain.indicators.macd.MacdCalculator
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculator
import com.cryptotrader.domain.indicators.rsi.RsiCalculator
import com.cryptotrader.domain.indicators.stochastic.StochasticCalculator
import com.cryptotrader.domain.indicators.volume.VolumeIndicatorCalculator
import com.cryptotrader.domain.model.*
import com.cryptotrader.domain.trading.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.mockito.kotlin.*

/**
 * Test helpers for backtest integration tests
 *
 * Provides Mockito-based mock implementations of dependencies needed by BacktestEngine
 * These are designed for testing only and don't provide full functionality
 */

/**
 * Mock OHLCBarDao for testing (doesn't interact with database)
 */
class MockOHLCBarDao : OHLCBarDao {
    override suspend fun insert(bar: OHLCBarEntity) {}
    override suspend fun insertAll(bars: List<OHLCBarEntity>) {}
    override suspend fun getBarsInRange(asset: String, timeframe: String, startTimestamp: Long, endTimestamp: Long): List<OHLCBarEntity> = emptyList()
    override fun getBarsInRangeFlow(asset: String, timeframe: String, startTimestamp: Long, endTimestamp: Long): Flow<List<OHLCBarEntity>> = flowOf(emptyList())
    override suspend fun getLatestBar(asset: String, timeframe: String): OHLCBarEntity? = null
    override suspend fun getBarCount(asset: String, timeframe: String): Long = 0L
    override suspend fun getTimestampRange(asset: String, timeframe: String): TimestampRange? = null
    override suspend fun deleteAllBars(asset: String, timeframe: String) {}
    override suspend fun deleteOlderThan(timestamp: Long) {}
    override suspend fun getAllAssets(): List<String> = emptyList()
    override suspend fun getTimeframesForAsset(asset: String): List<String> = emptyList()
    override suspend fun getDistinctDataTiers(asset: String, timeframe: String): List<String> = emptyList()
}

/**
 * Factory for creating test BacktestEngine with mock dependencies
 */
object BacktestTestFactory {

    /**
     * Create a BacktestEngine with all mock dependencies for testing
     */
    fun createBacktestEngine(): BacktestEngine {
        // Create mock repositories using Mockito
        val mockOHLCBarDao = MockOHLCBarDao()
        val mockTradeRepository: TradeRepository = mock {
            onBlocking { calculateActualAvgWinPercent(any()) } doReturn null
            onBlocking { calculateActualAvgLossPercent(any()) } doReturn null
        }
        val mockHistoricalDataRepository: HistoricalDataRepository = mock {
            onBlocking { getRecentBars(any(), any(), any()) } doReturn Result.success(emptyList())
        }

        // Create real instances with mock repositories
        val kellyCriterion = KellyCriterionCalculator(mockTradeRepository)
        val volatilityStopLoss = VolatilityStopLossCalculator(mockHistoricalDataRepository)
        val riskManager = RiskManager(kellyCriterion, volatilityStopLoss)

        // Create indicator cache
        val indicatorCache = IndicatorCache()

        // Create price history manager
        val priceHistoryManager = PriceHistoryManager(indicatorCache)

        // Create market data adapter
        val marketDataAdapter = MarketDataAdapter()

        // Create all required calculators for StrategyEvaluatorV2 using Mockito mocks
        val rsiCalculator: RsiCalculator = com.cryptotrader.domain.indicators.rsi.RsiCalculatorImpl()
        val maCalculator: MovingAverageCalculator = mock()
        val macdCalculator: MacdCalculator = mock()
        val bollingerCalculator: BollingerBandsCalculator = mock()
        val atrCalculator: AtrCalculator = mock()
        val stochasticCalculator: StochasticCalculator = mock()
        val volumeCalculator: VolumeIndicatorCalculator = mock()

        // Create strategy evaluators
        val strategyEvaluator = StrategyEvaluator()
        val strategyEvaluatorV2 = StrategyEvaluatorV2(
            rsiCalculator = rsiCalculator,
            maCalculator = maCalculator,
            macdCalculator = macdCalculator,
            bollingerCalculator = bollingerCalculator,
            atrCalculator = atrCalculator,
            stochasticCalculator = stochasticCalculator,
            volumeCalculator = volumeCalculator,
            indicatorCache = indicatorCache,
            priceHistoryManager = priceHistoryManager,
            marketDataAdapter = marketDataAdapter
        )

        // Create multi-timeframe analyzer
        val multiTimeframeAnalyzer = MultiTimeframeAnalyzer(
            historicalDataRepository = mockHistoricalDataRepository,
            strategyEvaluator = strategyEvaluator,
            strategyEvaluatorV2 = strategyEvaluatorV2,
            marketDataAdapter = marketDataAdapter,
            priceHistoryManager = priceHistoryManager,
            movingAverageCalculator = maCalculator
        )

        // Create market regime detector
        val marketRegimeDetector = MarketRegimeDetector(
            historicalDataRepository = mockHistoricalDataRepository
        )

        // Create trading engine
        val tradingEngine = TradingEngine(
            riskManager = riskManager,
            strategyEvaluator = strategyEvaluator,
            strategyEvaluatorV2 = strategyEvaluatorV2,
            multiTimeframeAnalyzer = multiTimeframeAnalyzer,
            marketRegimeDetector = marketRegimeDetector
        )

        // Create backtest engine
        return BacktestEngine(
            tradingEngine = tradingEngine,
            riskManager = riskManager,
            ohlcBarDao = mockOHLCBarDao
        )
    }
}
