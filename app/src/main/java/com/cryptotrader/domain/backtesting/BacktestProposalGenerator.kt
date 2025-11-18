package com.cryptotrader.domain.backtesting

import com.cryptotrader.data.local.dao.DataCoverageDao
import com.cryptotrader.data.local.dao.OHLCBarDao
import com.cryptotrader.domain.model.BacktestProposal
import com.cryptotrader.domain.model.DataTier
import com.cryptotrader.domain.model.Strategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backtest Proposal Generator - AI-Powered Backtest Recommendations
 *
 * Generates intelligent backtest proposals with educational explanations.
 * This is what Claude in the chat uses to suggest backtests to the user.
 *
 * EDUCATIONAL APPROACH:
 * - Explains WHY each parameter was chosen
 * - Highlights risks and considerations
 * - Teaches user about data quality importance
 * - Allows user to learn and catch potential errors
 */
@Singleton
class BacktestProposalGenerator @Inject constructor(
    private val ohlcBarDao: OHLCBarDao,
    private val dataCoverageDao: DataCoverageDao,
    private val backtestDataProvider: BacktestDataProvider
) {

    /**
     * Generate backtest proposal for a strategy
     *
     * This is called by AI chat when user wants to backtest a strategy
     */
    suspend fun generateProposal(strategy: Strategy): BacktestProposal = withContext(Dispatchers.IO) {
        try {
            Timber.i("Generating backtest proposal for strategy: ${strategy.name}")

            // Step 1: Analyze strategy complexity
            val strategyAnalysis = analyzeStrategy(strategy)

            // Step 2: Recommend data tier
            val recommendedTier = recommendDataTier(strategyAnalysis)

            // Step 3: Select asset
            val asset = strategy.tradingPairs.firstOrNull() ?: "XXBTZUSD"

            // Step 4: Recommend timeframe
            val recommendedTimeframe = recommendTimeframe(strategyAnalysis)

            // Step 5: Get available tiers
            val availableTiers = backtestDataProvider.getAvailableTiers(asset, recommendedTimeframe)

            // Step 6: Check data coverage
            val coverage = dataCoverageDao.getCoverage(asset, recommendedTimeframe)

            // Step 7: Generate educational explanations
            val tierRationale = explainTierChoice(recommendedTier, strategyAnalysis)
            val timeframeRationale = explainTimeframeChoice(recommendedTimeframe, strategyAnalysis)
            val dateRangeRationale = explainDateRangeChoice(coverage)

            // Step 8: Generate warnings and educational notes
            val warnings = generateWarnings(recommendedTier, strategyAnalysis, coverage)
            val educationalNotes = generateEducationalNotes(recommendedTier, strategyAnalysis)

            // Step 9: Create proposal
            BacktestProposal(
                strategyId = strategy.id,
                strategyName = strategy.name,
                proposedDataTier = recommendedTier,
                proposedAsset = asset,
                proposedTimeframe = recommendedTimeframe,
                proposedStartDate = coverage?.earliestTimestamp,
                proposedEndDate = coverage?.latestTimestamp,
                tierRationale = tierRationale,
                timeframeRationale = timeframeRationale,
                dateRangeRationale = dateRangeRationale,
                overallRecommendation = generateOverallRecommendation(recommendedTier, strategyAnalysis),
                availableDataTiers = availableTiers.ifEmpty { listOf(DataTier.TIER_4_BASIC) },
                estimatedBarsCount = coverage?.totalBars ?: 0,
                dataQualityScore = coverage?.dataQualityScore ?: 0.7,
                warnings = warnings,
                educationalNotes = educationalNotes
            )

        } catch (e: Exception) {
            Timber.e(e, "Failed to generate backtest proposal")

            // Return fallback proposal
            BacktestProposal(
                strategyId = strategy.id,
                strategyName = strategy.name,
                proposedDataTier = DataTier.TIER_4_BASIC,
                proposedAsset = strategy.tradingPairs.firstOrNull() ?: "XXBTZUSD",
                proposedTimeframe = "1h",
                proposedStartDate = null,
                proposedEndDate = null,
                tierRationale = "Using basic tier as fallback due to error: ${e.message}",
                timeframeRationale = "Default 1-hour timeframe",
                dateRangeRationale = "Full available range",
                overallRecommendation = "This is a fallback proposal. Please review carefully.",
                availableDataTiers = listOf(DataTier.TIER_4_BASIC),
                estimatedBarsCount = 0,
                dataQualityScore = 0.5,
                warnings = listOf("Proposal generation encountered an error. Settings may not be optimal.")
            )
        }
    }

    /**
     * Analyze strategy to determine complexity and requirements
     */
    private fun analyzeStrategy(strategy: Strategy): StrategyAnalysis {
        val allConditions = (strategy.entryConditions + strategy.exitConditions).joinToString(" ")

        val isHighFrequency = allConditions.contains("volume", ignoreCase = true) ||
                             allConditions.contains("tick", ignoreCase = true) ||
                             allConditions.contains("order book", ignoreCase = true) ||
                             allConditions.contains("second", ignoreCase = true)

        val usesAdvancedIndicators = allConditions.contains("MACD", ignoreCase = true) ||
                                     allConditions.contains("Bollinger", ignoreCase = true) ||
                                     allConditions.contains("ATR", ignoreCase = true) ||
                                     allConditions.contains("Stochastic", ignoreCase = true)

        val usesBasicIndicators = allConditions.contains("RSI", ignoreCase = true) ||
                                  allConditions.contains("SMA", ignoreCase = true) ||
                                  allConditions.contains("EMA", ignoreCase = true)

        val requiresPrecision = strategy.stopLossPercent < 2.0 || // Tight stop loss
                               strategy.takeProfitPercent < 5.0    // Tight take profit

        return StrategyAnalysis(
            isHighFrequency = isHighFrequency,
            usesAdvancedIndicators = usesAdvancedIndicators,
            usesBasicIndicators = usesBasicIndicators,
            requiresPrecision = requiresPrecision,
            tradingPairs = strategy.tradingPairs
        )
    }

    /**
     * Recommend data tier based on strategy analysis
     */
    private fun recommendDataTier(analysis: StrategyAnalysis): DataTier {
        return when {
            analysis.isHighFrequency -> DataTier.TIER_2_PROFESSIONAL
            analysis.usesAdvancedIndicators -> DataTier.TIER_3_STANDARD
            analysis.requiresPrecision -> DataTier.TIER_3_STANDARD
            else -> DataTier.TIER_4_BASIC
        }
    }

    /**
     * Recommend timeframe based on strategy
     */
    private fun recommendTimeframe(analysis: StrategyAnalysis): String {
        return when {
            analysis.isHighFrequency -> "1m"
            analysis.usesAdvancedIndicators -> "15m"
            else -> "1h"
        }
    }

    /**
     * Explain why this data tier was chosen (educational)
     */
    private fun explainTierChoice(tier: DataTier, analysis: StrategyAnalysis): String {
        return when (tier) {
            DataTier.TIER_1_PREMIUM -> """
                I recommend **PREMIUM tier** (order book data) because:
                ${if (analysis.isHighFrequency) "- Your strategy uses high-frequency indicators requiring nanosecond precision\n" else ""}
                - Order book depth provides crucial market microstructure information
                - This is hedge fund quality data with 20 levels of bid/ask depth
                - Best for strategies requiring slippage modeling and liquidity analysis

                ⚠️ Note: This tier has the most data and will take longer to process
            """.trimIndent()

            DataTier.TIER_2_PROFESSIONAL -> """
                I recommend **PROFESSIONAL tier** (tick-by-tick trades) because:
                ${if (analysis.isHighFrequency) "- Your strategy contains high-frequency elements (volume/tick analysis)\n" else ""}
                ${if (analysis.requiresPrecision) "- Your tight stop-loss (${analysis.requiresPrecision}) requires precise entry/exit modeling\n" else ""}
                - Tick data captures every individual trade with aggressor side
                - This provides ~970 trades per minute for BTC with nanosecond precision
                - Professional-grade data suitable for institutional-level backtesting

                💡 This is ideal for order flow and VWAP strategies
            """.trimIndent()

            DataTier.TIER_3_STANDARD -> """
                I recommend **STANDARD tier** (aggregated trades) because:
                ${if (analysis.usesAdvancedIndicators) "- Your strategy uses standard indicators (MACD, Bollinger, ATR)\n" else ""}
                - Aggregated Binance trades with millisecond precision
                - Good balance between quality and processing speed
                - Sufficient for most technical analysis strategies

                💡 This tier is recommended for most retail/semi-professional strategies
            """.trimIndent()

            DataTier.TIER_4_BASIC -> """
                I recommend **BASIC tier** (OHLCV candles) because:
                - Your strategy uses simple indicators (RSI, SMA, EMA)
                - Pre-processed candle data is sufficient for technical analysis
                - Fastest backtesting with good-enough quality
                - Perfect for strategy prototyping and educational purposes

                💡 Good for learning and quick validation. Consider upgrading tier for live trading
            """.trimIndent()
        }
    }

    /**
     * Explain timeframe choice
     */
    private fun explainTimeframeChoice(timeframe: String, analysis: StrategyAnalysis): String {
        return when (timeframe) {
            "1m" -> "1-minute candles chosen for high-frequency strategy elements. Provides granular price action."
            "5m" -> "5-minute candles balance detail with processing speed. Good for intraday strategies."
            "15m" -> "15-minute candles chosen for swing trading with advanced indicators. Reduces noise while maintaining signal quality."
            "1h" -> "1-hour candles chosen as they work well with your indicator timeframes and provide reliable signals with less noise."
            "4h" -> "4-hour candles for longer-term position trading. Better signal-to-noise ratio."
            "1d" -> "Daily candles for swing/position trading strategies."
            else -> "Selected timeframe: $timeframe"
        }
    }

    /**
     * Explain date range choice
     */
    private fun explainDateRangeChoice(coverage: com.cryptotrader.data.local.entities.DataCoverageEntity?): String {
        if (coverage == null) {
            return "No historical data available yet. You'll need to import data first."
        }

        val daysOfData = (coverage.latestTimestamp - coverage.earliestTimestamp) / (24 * 60 * 60 * 1000)
        val qualityPercent = coverage.dataQualityScore * 100

        return """
            Using full available date range (${daysOfData} days of data).

            **Data Quality**: ${String.format("%.1f%%", qualityPercent)} complete
            - Total bars: ${coverage.totalBars}
            - Expected bars: ${coverage.expectedBars}
            - Missing bars: ${coverage.missingBarsCount}
            ${if (coverage.gapsCount > 0) "\n⚠️ Found ${coverage.gapsCount} gaps in data" else ""}

            💡 More data = more reliable backtest results, but also longer processing time
        """.trimIndent()
    }

    /**
     * Generate warnings based on analysis
     */
    private fun generateWarnings(
        tier: DataTier,
        analysis: StrategyAnalysis,
        coverage: com.cryptotrader.data.local.entities.DataCoverageEntity?
    ): List<String> {
        val warnings = mutableListOf<String>()

        // Data quality warnings
        if (coverage != null && coverage.dataQualityScore < 0.8) {
            warnings.add("Data quality is below 80%. Results may be less reliable.")
        }

        if (coverage != null && coverage.gapsCount > 10) {
            warnings.add("${coverage.gapsCount} gaps detected in data. This may affect backtest accuracy.")
        }

        // Tier mismatch warnings
        if (analysis.isHighFrequency && tier != DataTier.TIER_1_PREMIUM && tier != DataTier.TIER_2_PROFESSIONAL) {
            warnings.add("Your strategy uses high-frequency elements but is being tested on lower-tier data. Consider upgrading to PROFESSIONAL or PREMIUM tier.")
        }

        if (analysis.requiresPrecision && tier == DataTier.TIER_4_BASIC) {
            warnings.add("Your strategy has tight stop-loss/take-profit levels. BASIC tier may not capture precise entry/exit points.")
        }

        return warnings
    }

    /**
     * Generate educational notes
     */
    private fun generateEducationalNotes(tier: DataTier, analysis: StrategyAnalysis): List<String> {
        val notes = mutableListOf<String>()

        notes.add("Backtesting uses historical data to validate strategy performance before risking real money")
        notes.add("Higher data tiers provide more accurate results but take longer to process")
        notes.add("Always test across multiple market conditions (bull, bear, sideways)")

        if (tier == DataTier.TIER_4_BASIC) {
            notes.add("BASIC tier is great for learning, but consider testing with STANDARD tier before live trading")
        }

        return notes
    }

    /**
     * Generate overall recommendation
     */
    private fun generateOverallRecommendation(tier: DataTier, analysis: StrategyAnalysis): String {
        return """
            This configuration balances data quality with processing speed for your strategy type.

            ${if (tier.isProductionGrade()) "✅ This is **production-grade** data suitable for live trading decisions." else "⚠️ This is **testing-grade** data. Consider upgrading tier before live trading."}

            You can modify these settings if you prefer different parameters.
        """.trimIndent()
    }
}

/**
 * Strategy analysis result
 */
private data class StrategyAnalysis(
    val isHighFrequency: Boolean,
    val usesAdvancedIndicators: Boolean,
    val usesBasicIndicators: Boolean,
    val requiresPrecision: Boolean,
    val tradingPairs: List<String>
)
