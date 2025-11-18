package com.cryptotrader.domain.backtesting

import com.cryptotrader.data.local.dao.DataCoverageDao
import com.cryptotrader.data.local.dao.OHLCBarDao
import com.cryptotrader.data.local.entities.OHLCBarEntity
import com.cryptotrader.domain.model.DataTier
import com.cryptotrader.domain.model.Strategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backtest Data Provider - Intelligent Data Selection for Backtesting
 *
 * Automatically selects the appropriate data tier and timeframe based on:
 * - Strategy complexity
 * - Trading pair
 * - Available data coverage
 * - User preferences
 *
 * SKELETON IMPLEMENTATION - AI integration will enhance this later
 */
@Singleton
class BacktestDataProvider @Inject constructor(
    private val ohlcBarDao: OHLCBarDao,
    private val dataCoverageDao: DataCoverageDao
) {

    /**
     * Get backtest data for a strategy
     *
     * Automatically selects:
     * - Appropriate data tier based on strategy complexity
     * - Optimal timeframe
     * - Sufficient date range with quality data
     *
     * @param strategy Strategy to backtest
     * @param preferredTier User-preferred data tier (optional)
     * @param startDate Backtest start date (optional, auto-selects if null)
     * @param endDate Backtest end date (optional, defaults to latest available)
     * @return BacktestDataSet with OHLC entities and converted PriceBars
     */
    suspend fun getBacktestData(
        strategy: Strategy,
        preferredTier: DataTier? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ): BacktestDataSet = withContext(Dispatchers.IO) {
        try {
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("📊 BACKTEST DATA SELECTION STARTING")
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("Strategy: ${strategy.name}")
            Timber.i("Trading Pairs: ${strategy.tradingPairs.joinToString(", ")}")

            // Step 1: Select data tier
            val dataTier = preferredTier ?: selectDataTierForStrategy(strategy)
            Timber.i("Selected Data Tier: ${dataTier.tierName}")

            // Step 2: Select asset (first trading pair for now)
            val asset = strategy.tradingPairs.firstOrNull() ?: "XXBTZUSD"
            Timber.i("Selected Asset: $asset")

            // Step 3: Select timeframe
            val timeframe = selectTimeframeForStrategy(strategy)
            Timber.i("Selected Timeframe: $timeframe")

            // Step 4: Check data coverage
            val coverage = dataCoverageDao.getCoverage(asset, timeframe)
            if (coverage == null) {
                Timber.w("❌ No data coverage found for $asset $timeframe")
                return@withContext BacktestDataSet.empty(
                    error = "No historical data available for $asset $timeframe"
                )
            }

            Timber.i("Data Coverage: ${formatTimestamp(coverage.earliestTimestamp)} → ${formatTimestamp(coverage.latestTimestamp)}")
            Timber.i("Total Bars: ${coverage.totalBars}, Quality: ${String.format("%.1f%%", coverage.dataQualityScore * 100)}")

            // Step 5: Determine date range
            val actualStartDate = startDate ?: coverage.earliestTimestamp
            val actualEndDate = endDate ?: coverage.latestTimestamp

            Timber.i("Backtest Period: ${formatTimestamp(actualStartDate)} → ${formatTimestamp(actualEndDate)}")

            // Step 6: Load OHLC bars from database
            val ohlcBars = ohlcBarDao.getBarsInRange(asset, timeframe, actualStartDate, actualEndDate)
                .filter { it.dataTier == dataTier.name } // Filter by tier

            if (ohlcBars.isEmpty()) {
                Timber.w("❌ No bars found in date range for tier ${dataTier.tierName}")
                return@withContext BacktestDataSet.empty(
                    error = "No data available for $asset $timeframe in selected date range (tier: ${dataTier.tierName})"
                )
            }

            Timber.i("✅ Loaded ${ohlcBars.size} bars from database")

            // Step 7: Convert to PriceBar format
            val priceBars = ohlcBars.map { it.toPriceBar() }

            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("✅ DATA SELECTION COMPLETE")
            Timber.i("   Bars: ${priceBars.size}")
            Timber.i("   Tier: ${dataTier.tierName}")
            Timber.i("   Quality: ${String.format("%.2f", coverage.dataQualityScore)}")
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            BacktestDataSet(
                asset = asset,
                timeframe = timeframe,
                dataTier = dataTier,
                startTimestamp = actualStartDate,
                endTimestamp = actualEndDate,
                ohlcBars = ohlcBars,
                priceBars = priceBars,
                dataQualityScore = coverage.dataQualityScore
            )

        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to get backtest data")
            BacktestDataSet.empty(error = "Failed to load data: ${e.message}")
        }
    }

    /**
     * Select appropriate data tier based on strategy complexity
     *
     * SKELETON LOGIC - Will be enhanced with AI later
     * Current logic:
     * - High-frequency indicators → TIER_1_PREMIUM or TIER_2_PROFESSIONAL
     * - Standard indicators → TIER_3_STANDARD
     * - Simple strategies → TIER_4_BASIC
     */
    private fun selectDataTierForStrategy(strategy: Strategy): DataTier {
        // Check for high-frequency indicators
        val allConditions = (strategy.entryConditions + strategy.exitConditions).joinToString(" ")

        val isHighFrequency = allConditions.contains("volume", ignoreCase = true) ||
                             allConditions.contains("tick", ignoreCase = true) ||
                             allConditions.contains("order book", ignoreCase = true)

        val isAdvanced = allConditions.contains("MACD", ignoreCase = true) ||
                        allConditions.contains("Bollinger", ignoreCase = true) ||
                        allConditions.contains("ATR", ignoreCase = true)

        return when {
            isHighFrequency -> {
                Timber.i("📈 High-frequency strategy detected → Using TIER_2_PROFESSIONAL")
                DataTier.TIER_2_PROFESSIONAL
            }
            isAdvanced -> {
                Timber.i("📊 Advanced strategy detected → Using TIER_3_STANDARD")
                DataTier.TIER_3_STANDARD
            }
            else -> {
                Timber.i("📉 Simple strategy → Using TIER_4_BASIC")
                DataTier.TIER_4_BASIC
            }
        }
    }

    /**
     * Select optimal timeframe for strategy
     *
     * SKELETON LOGIC - Currently returns first available
     * TODO: Analyze strategy conditions to determine best timeframe
     */
    private fun selectTimeframeForStrategy(strategy: Strategy): String {
        // Default to 1h for now
        // TODO: Parse strategy conditions to detect timeframe requirements
        return "1h"
    }

    /**
     * Get available data tiers for an asset/timeframe combination
     */
    suspend fun getAvailableTiers(asset: String, timeframe: String): List<DataTier> = withContext(Dispatchers.IO) {
        val tiers = ohlcBarDao.getDistinctDataTiers(asset, timeframe)
        tiers.mapNotNull { tierString ->
            try {
                DataTier.fromString(tierString)
            } catch (e: Exception) {
                Timber.w("Unknown data tier: $tierString")
                null
            }
        }
    }

    /**
     * Format timestamp for logging
     */
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(timestamp))
    }
}

/**
 * Extension: Convert OHLCBarEntity to PriceBar
 */
fun OHLCBarEntity.toPriceBar(): PriceBar {
    return PriceBar(
        timestamp = this.timestamp,
        open = this.open,
        high = this.high,
        low = this.low,
        close = this.close,
        volume = this.volume
    )
}

/**
 * Backtest Data Set - Complete dataset for backtesting
 */
data class BacktestDataSet(
    val asset: String,
    val timeframe: String,
    val dataTier: DataTier,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val ohlcBars: List<OHLCBarEntity>,
    val priceBars: List<PriceBar>,
    val dataQualityScore: Double,
    val error: String? = null
) {
    val isValid: Boolean
        get() = error == null && priceBars.isNotEmpty()

    companion object {
        fun empty(error: String): BacktestDataSet {
            return BacktestDataSet(
                asset = "",
                timeframe = "",
                dataTier = DataTier.TIER_4_BASIC,
                startTimestamp = 0L,
                endTimestamp = 0L,
                ohlcBars = emptyList(),
                priceBars = emptyList(),
                dataQualityScore = 0.0,
                error = error
            )
        }
    }
}
