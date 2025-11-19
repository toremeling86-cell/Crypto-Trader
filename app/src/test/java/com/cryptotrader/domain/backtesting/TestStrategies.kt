package com.cryptotrader.domain.backtesting

import com.cryptotrader.domain.model.*

/**
 * Test strategies for backtest validation
 *
 * These strategies cover different complexities and trading styles:
 * 1. Buy-and-Hold (benchmark)
 * 2. RSI Mean Reversion
 * 3. MACD + RSI Combo
 * 4. Bollinger Bands Breakout
 */
object TestStrategies {

    /**
     * Strategy 1: Simple Buy-and-Hold (Benchmark)
     *
     * - Buys immediately and holds
     * - Never sells
     * - Used as baseline to compare active strategies
     */
    fun buyAndHold(): Strategy {
        return Strategy(
            id = "test-buy-hold",
            name = "Buy and Hold BTC",
            description = "Simple buy and hold strategy for benchmarking",
            entryConditions = listOf("ALWAYS_TRUE"),
            exitConditions = listOf("NEVER"),
            positionSizePercent = 95.0,  // Deploy 95% of capital
            stopLossPercent = 0.0,  // No stop-loss
            takeProfitPercent = 0.0,  // No take-profit
            tradingPairs = listOf("XXBTZUSD"),
            riskLevel = RiskLevel.LOW,
            isActive = false,
            tradingMode = TradingMode.PAPER
        )
    }

    /**
     * Strategy 2: RSI Mean Reversion
     *
     * Entry: RSI < 30 (oversold)
     * Exit: RSI > 70 (overbought)
     *
     * Expected performance (Jan 2024):
     * - Trades: 6-10
     * - Win rate: 60-70%
     * - Return: +2-4%
     */
    fun rsiMeanReversion(): Strategy {
        return Strategy(
            id = "test-rsi-mean-reversion",
            name = "RSI Mean Reversion",
            description = "Buy when oversold (RSI < 30), sell when overbought (RSI > 70)",
            entryConditions = listOf("RSI_14 < 30"),  // Oversold
            exitConditions = listOf("RSI_14 > 70"),    // Overbought
            positionSizePercent = 50.0,  // Risk 50% per trade
            stopLossPercent = 5.0,  // 5% stop-loss
            takeProfitPercent = 10.0,  // 10% take-profit
            tradingPairs = listOf("XXBTZUSD"),
            riskLevel = RiskLevel.MEDIUM,
            isActive = false,
            tradingMode = TradingMode.PAPER
        )
    }

    /**
     * Strategy 3: MACD + RSI Combo
     *
     * Entry:
     * - MACD crosses above signal line (bullish)
     * - RSI < 50 (not overbought)
     *
     * Exit:
     * - MACD crosses below signal line (bearish)
     * - OR RSI > 70 (overbought)
     *
     * Expected performance (Jan 2024):
     * - Trades: 4-8
     * - Win rate: 50-60%
     * - Return: +1-3%
     */
    fun macdRsiCombo(): Strategy {
        return Strategy(
            id = "test-macd-rsi-combo",
            name = "MACD + RSI Combo",
            description = "Combines MACD crossover with RSI confirmation",
            entryConditions = listOf(
                "MACD_CROSSOVER_UP",  // MACD crosses above signal
                "RSI_14 < 50"          // Not overbought
            ),
            exitConditions = listOf(
                "MACD_CROSSOVER_DOWN",  // MACD crosses below signal
                "RSI_14 > 70"            // OR overbought
            ),
            positionSizePercent = 30.0,  // Conservative position size
            stopLossPercent = 3.0,  // Tight stop-loss
            takeProfitPercent = 6.0,  // 2:1 reward/risk ratio
            tradingPairs = listOf("XXBTZUSD"),
            riskLevel = RiskLevel.MEDIUM,
            isActive = false,
            tradingMode = TradingMode.PAPER
        )
    }

    /**
     * Strategy 4: Bollinger Bands Breakout
     *
     * Entry: Price touches or breaks below lower Bollinger Band
     * Exit: Price touches or breaks above upper Bollinger Band
     *
     * Expected performance (Jan 2024):
     * - Trades: 3-6
     * - Win rate: 70-80%
     * - Return: +2-5%
     */
    fun bollingerBreakout(): Strategy {
        return Strategy(
            id = "test-bollinger-breakout",
            name = "Bollinger Bands Breakout",
            description = "Buy at lower band, sell at upper band",
            entryConditions = listOf("PRICE_BELOW_BB_LOWER"),  // Price at/below lower band
            exitConditions = listOf("PRICE_ABOVE_BB_UPPER"),    // Price at/above upper band
            positionSizePercent = 40.0,  // Moderate position size
            stopLossPercent = 4.0,  // 4% stop-loss
            takeProfitPercent = 8.0,  // 8% take-profit
            tradingPairs = listOf("XXBTZUSD"),
            riskLevel = RiskLevel.MEDIUM,
            isActive = false,
            tradingMode = TradingMode.PAPER
        )
    }

    /**
     * Strategy 5: EMA Crossover (Classic trend-following)
     *
     * Entry: EMA(50) crosses above EMA(200) (golden cross)
     * Exit: EMA(50) crosses below EMA(200) (death cross)
     *
     * Expected performance (Jan 2024):
     * - Trades: 1-3 (slow strategy)
     * - Win rate: 40-60%
     * - Return: -1% to +3%
     */
    fun emaCrossover(): Strategy {
        return Strategy(
            id = "test-ema-crossover",
            name = "EMA Crossover (50/200)",
            description = "Classic golden cross/death cross strategy",
            entryConditions = listOf("EMA_50 > EMA_200"),  // Golden cross
            exitConditions = listOf("EMA_50 < EMA_200"),    // Death cross
            positionSizePercent = 80.0,  // Large position for trend-following
            stopLossPercent = 10.0,  // Wide stop-loss for trend
            takeProfitPercent = 20.0,  // Large target for trend
            tradingPairs = listOf("XXBTZUSD"),
            riskLevel = RiskLevel.LOW,  // Low frequency = low risk
            isActive = false,
            tradingMode = TradingMode.PAPER
        )
    }

    /**
     * Strategy 6: High-Frequency Scalping (for TIER_1_PREMIUM data)
     *
     * Entry: Order book imbalance > 70% AND tight spread
     * Exit: Quick profit (0.2%) OR stop-loss (0.1%)
     *
     * Note: Requires TIER_1_PREMIUM data (order book Level 20)
     */
    fun highFrequencyScalping(): Strategy {
        return Strategy(
            id = "test-hf-scalping",
            name = "High-Frequency Scalping",
            description = "Order flow-based scalping strategy",
            entryConditions = listOf(
                "ORDER_IMBALANCE > 0.7",  // Strong buy/sell pressure
                "BID_ASK_SPREAD < 0.02%"   // Tight spread
            ),
            exitConditions = listOf("PRICE_TICK_UP"),  // Quick exit
            positionSizePercent = 10.0,  // Small position per trade
            stopLossPercent = 0.1,  // Very tight stop (0.1%)
            takeProfitPercent = 0.2,  // Small target (0.2%)
            tradingPairs = listOf("XXBTZUSD"),
            riskLevel = RiskLevel.HIGH,  // High frequency = high risk
            isActive = false,
            tradingMode = TradingMode.PAPER
        )
    }

    /**
     * Get all test strategies
     */
    fun all(): List<Strategy> {
        return listOf(
            buyAndHold(),
            rsiMeanReversion(),
            macdRsiCombo(),
            bollingerBreakout(),
            emaCrossover(),
            highFrequencyScalping()
        )
    }

    /**
     * Get basic test strategies (for TIER_4_BASIC data)
     */
    fun basicStrategies(): List<Strategy> {
        return listOf(
            buyAndHold(),
            rsiMeanReversion(),
            macdRsiCombo(),
            bollingerBreakout()
        )
    }

    /**
     * Get advanced test strategies (require higher tier data)
     */
    fun advancedStrategies(): List<Strategy> {
        return listOf(
            emaCrossover(),
            highFrequencyScalping()
        )
    }
}
