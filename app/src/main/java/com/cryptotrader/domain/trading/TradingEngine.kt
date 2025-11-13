package com.cryptotrader.domain.trading

import com.cryptotrader.domain.model.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core trading engine that evaluates strategies and generates trade signals
 */
@Singleton
class TradingEngine @Inject constructor(
    private val riskManager: RiskManager,
    private val strategyEvaluator: StrategyEvaluator,
    private val multiTimeframeAnalyzer: MultiTimeframeAnalyzer,
    private val marketRegimeDetector: MarketRegimeDetector
) {

    /**
     * Evaluate a strategy against current market data and generate trade signal
     */
    suspend fun evaluateStrategy(
        strategy: Strategy,
        marketData: MarketTicker,
        portfolio: Portfolio
    ): TradeSignal? {
        try {
            // Check if strategy is active
            if (!strategy.isActive) {
                Timber.d("Strategy ${strategy.name} is not active")
                return null
            }

            // Check if pair matches
            if (!strategy.tradingPairs.contains(marketData.pair)) {
                return null
            }

            // Check market regime filter
            if (strategy.useRegimeFilter) {
                val regime = marketRegimeDetector.detectRegime(marketData.pair)
                if (!strategy.allowedRegimes.contains(regime.name)) {
                    Timber.d("Trade filtered: Market regime $regime not allowed for ${strategy.name}")
                    return null
                }
            }

            // Evaluate entry conditions with multi-timeframe analysis
            val shouldEnter = evaluateEntryConditionsWithTimeframes(strategy, marketData)
            if (shouldEnter.shouldEnter) {
                return generateBuySignal(strategy, marketData, portfolio, shouldEnter.confidence)
            }

            // Evaluate exit conditions
            val shouldExit = evaluateExitConditions(strategy, marketData)
            if (shouldExit) {
                return generateSellSignal(strategy, marketData, portfolio)
            }

            return null
        } catch (e: Exception) {
            Timber.e(e, "Error evaluating strategy: ${strategy.name}")
            return null
        }
    }

    /**
     * Evaluate entry conditions with multi-timeframe confirmation
     */
    private suspend fun evaluateEntryConditionsWithTimeframes(
        strategy: Strategy,
        marketData: MarketTicker
    ): MultiTimeframeResult {
        return if (strategy.useMultiTimeframe) {
            // Use multi-timeframe analysis for higher accuracy
            multiTimeframeAnalyzer.evaluateMultiTimeframe(strategy, marketData.pair, marketData)
        } else {
            // Fall back to single timeframe evaluation
            val result = evaluateEntryConditions(strategy, marketData)
            MultiTimeframeResult(
                shouldEnter = result,
                confirmedTimeframes = if (result) listOf(60) else emptyList(),
                allTimeframes = listOf(60),
                confidence = if (result) 0.75 else 0.0
            )
        }
    }

    private fun evaluateEntryConditions(
        strategy: Strategy,
        marketData: MarketTicker
    ): Boolean {
        // Use real strategy evaluator with technical indicators
        return strategyEvaluator.evaluateEntryConditions(strategy, marketData)
    }

    private fun evaluateExitConditions(
        strategy: Strategy,
        marketData: MarketTicker
    ): Boolean {
        // Use real strategy evaluator with technical indicators
        return strategyEvaluator.evaluateExitConditions(strategy, marketData)
    }

    private fun generateBuySignal(
        strategy: Strategy,
        marketData: MarketTicker,
        portfolio: Portfolio,
        confidence: Double = 0.75
    ): TradeSignal {
        // Calculate position size
        val positionValue = portfolio.availableBalance * (strategy.positionSizePercent / 100.0)
        val volume = positionValue / marketData.ask

        // Apply risk management
        val adjustedVolume = riskManager.adjustPositionSize(
            requestedVolume = volume,
            price = marketData.ask,
            availableBalance = portfolio.availableBalance,
            strategy = strategy
        )

        val reason = if (strategy.useMultiTimeframe) {
            "Multi-timeframe signal confirmed (confidence: ${String.format("%.2f", confidence)})"
        } else {
            "Entry conditions met: ${strategy.entryConditions.joinToString()}"
        }

        return TradeSignal(
            strategyId = strategy.id,
            pair = marketData.pair,
            action = TradeAction.BUY,
            confidence = confidence,
            targetPrice = marketData.ask,
            suggestedVolume = adjustedVolume,
            reason = reason
        )
    }

    private fun generateSellSignal(
        strategy: Strategy,
        marketData: MarketTicker,
        portfolio: Portfolio
    ): TradeSignal? {
        // Check if we have positions to sell
        val assetName = marketData.pair.substring(0, 3) // Simplified
        val assetBalance = portfolio.balances[assetName]

        if (assetBalance == null || assetBalance.balance <= 0) {
            return null
        }

        return TradeSignal(
            strategyId = strategy.id,
            pair = marketData.pair,
            action = TradeAction.SELL,
            confidence = 0.8,
            targetPrice = marketData.bid,
            suggestedVolume = assetBalance.balance,
            reason = "Exit conditions met: ${strategy.exitConditions.joinToString()}"
        )
    }

    /**
     * Batch evaluate multiple strategies
     */
    suspend fun evaluateStrategies(
        strategies: List<Strategy>,
        marketData: Map<String, MarketTicker>,
        portfolio: Portfolio
    ): List<TradeSignal> {
        val signals = mutableListOf<TradeSignal>()

        strategies.forEach { strategy ->
            strategy.tradingPairs.forEach { pair ->
                marketData[pair]?.let { ticker ->
                    evaluateStrategy(strategy, ticker, portfolio)?.let { signal ->
                        signals.add(signal)
                    }
                }
            }
        }

        return signals
    }
}
