package com.cryptotrader.domain.trading

import com.cryptotrader.domain.indicators.Candle
import com.cryptotrader.domain.indicators.atr.AtrCalculator
import com.cryptotrader.domain.indicators.bollingerbands.BollingerBandsCalculator
import com.cryptotrader.domain.indicators.cache.IndicatorCache
import com.cryptotrader.domain.indicators.macd.MacdCalculator
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculator
import com.cryptotrader.domain.indicators.rsi.RsiCalculator
import com.cryptotrader.domain.indicators.stochastic.StochasticCalculator
import com.cryptotrader.domain.indicators.volume.VolumeIndicatorCalculator
import com.cryptotrader.domain.model.MarketTicker
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.utils.FeatureFlags
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced strategy evaluator using calculator-based indicators
 *
 * This is the V2 implementation that replaces static TechnicalIndicators calls
 * with injected calculator instances for better testability, caching, and modularity.
 *
 * Key improvements over V1:
 * - Uses injected calculators instead of static methods
 * - Leverages PriceHistoryManager for centralized candle storage
 * - Integrates with IndicatorCache for performance optimization
 * - Converts MarketTicker to Candle format for consistent OHLCV data
 * - Maintains backward compatibility with existing strategy conditions
 *
 * @property rsiCalculator Calculator for RSI indicator
 * @property maCalculator Calculator for SMA and EMA
 * @property macdCalculator Calculator for MACD indicator
 * @property bollingerCalculator Calculator for Bollinger Bands
 * @property atrCalculator Calculator for ATR (volatility)
 * @property stochasticCalculator Calculator for Stochastic Oscillator
 * @property volumeCalculator Calculator for volume-based indicators
 * @property indicatorCache Cache for indicator results
 * @property priceHistoryManager Manager for candle history storage
 * @property marketDataAdapter Adapter for converting MarketTicker to Candle
 */
@Singleton
class StrategyEvaluatorV2 @Inject constructor(
    private val rsiCalculator: RsiCalculator,
    private val maCalculator: MovingAverageCalculator,
    private val macdCalculator: MacdCalculator,
    private val bollingerCalculator: BollingerBandsCalculator,
    private val atrCalculator: AtrCalculator,
    private val stochasticCalculator: StochasticCalculator,
    private val volumeCalculator: VolumeIndicatorCalculator,
    private val indicatorCache: IndicatorCache,
    private val priceHistoryManager: PriceHistoryManager,
    private val marketDataAdapter: MarketDataAdapter
) {

    companion object {
        private const val MIN_HISTORY_SIZE = 30
        private const val TAG = "StrategyEvaluatorV2"
    }

    /**
     * Update price history for a trading pair
     *
     * Converts MarketTicker to Candle format and stores in PriceHistoryManager
     *
     * @param pair Trading pair identifier
     * @param marketData Current market ticker data
     */
    fun updatePriceHistory(pair: String, marketData: MarketTicker) {
        val candle = marketDataAdapter.toCandle(marketData)
        priceHistoryManager.updateHistory(pair, candle)

        if (FeatureFlags.LOG_CACHE_PERFORMANCE) {
            val historySize = priceHistoryManager.getHistorySize(pair)
            Timber.d("[$TAG] Updated history for $pair: $historySize candles")
        }
    }

    /**
     * Evaluate entry conditions for a strategy
     *
     * @param strategy Strategy containing entry conditions
     * @param marketData Current market ticker data
     * @return True if all entry conditions are met, false otherwise
     */
    fun evaluateEntryConditions(
        strategy: Strategy,
        marketData: MarketTicker
    ): Boolean {
        return try {
            // Update price history with latest data
            updatePriceHistory(marketData.pair, marketData)

            // Get candle history
            val candles = priceHistoryManager.getHistory(marketData.pair)
            if (candles.size < MIN_HISTORY_SIZE) {
                Timber.d("[$TAG] Not enough history for ${marketData.pair}: ${candles.size}/$MIN_HISTORY_SIZE")
                return false
            }

            // Evaluate all entry conditions (ALL must be true)
            val result = strategy.entryConditions.all { condition ->
                evaluateCondition(condition, marketData, candles)
            }

            Timber.d("[$TAG] Entry conditions for ${strategy.name} on ${marketData.pair}: $result")
            result

        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Error evaluating entry conditions")
            false
        }
    }

    /**
     * Evaluate exit conditions for a strategy
     *
     * @param strategy Strategy containing exit conditions
     * @param marketData Current market ticker data
     * @return True if any exit condition is met, false otherwise
     */
    fun evaluateExitConditions(
        strategy: Strategy,
        marketData: MarketTicker
    ): Boolean {
        return try {
            // Get candle history (no update needed, already done in entry evaluation)
            val candles = priceHistoryManager.getHistory(marketData.pair)
            if (candles.size < MIN_HISTORY_SIZE) {
                return false
            }

            // Evaluate exit conditions (ANY can be true)
            val result = strategy.exitConditions.any { condition ->
                evaluateCondition(condition, marketData, candles)
            }

            if (result) {
                Timber.d("[$TAG] Exit condition triggered for ${strategy.name} on ${marketData.pair}")
            }
            result

        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Error evaluating exit conditions")
            false
        }
    }

    /**
     * Evaluate a single condition string
     *
     * Supports patterns like:
     * - "RSI < 30" or "RSI > 70"
     * - "SMA_20 > SMA_50" or "EMA_12 > EMA_26"
     * - "Price > Bollinger_Upper"
     * - "MACD_crossover"
     * - "Volume > average"
     * - "ATR > 2.0"
     *
     * @param condition Condition string to evaluate
     * @param marketData Current market ticker data
     * @param candles Historical candle data
     * @return True if condition is met, false otherwise
     */
    private fun evaluateCondition(
        condition: String,
        marketData: MarketTicker,
        candles: List<Candle>
    ): Boolean {
        val normalizedCondition = condition.trim().lowercase()

        return try {
            val result = when {
                // RSI conditions
                normalizedCondition.contains("rsi") -> evaluateRSI(normalizedCondition, candles)

                // EMA conditions (check before "ma" to avoid false matches)
                normalizedCondition.contains("ema") -> {
                    evaluateExponentialMovingAverage(normalizedCondition, candles, marketData.last)
                }

                // Moving Average conditions (SMA)
                normalizedCondition.contains("sma") || normalizedCondition.contains("ma") -> {
                    evaluateMovingAverage(normalizedCondition, candles, marketData.last)
                }

                // MACD conditions
                normalizedCondition.contains("macd") -> evaluateMACD(normalizedCondition, candles)

                // Bollinger Bands conditions
                normalizedCondition.contains("bollinger") -> {
                    evaluateBollingerBands(normalizedCondition, candles, marketData.last)
                }

                // ATR (Average True Range) conditions
                normalizedCondition.contains("atr") -> {
                    evaluateATR(normalizedCondition, candles, marketData)
                }

                // Price momentum conditions
                normalizedCondition.contains("momentum") || normalizedCondition.contains("change") -> {
                    evaluateMomentum(normalizedCondition, marketData)
                }

                // Volume conditions
                normalizedCondition.contains("volume") -> {
                    evaluateVolume(normalizedCondition, marketData, candles)
                }

                // Price position conditions
                normalizedCondition.contains("price") -> {
                    evaluatePricePosition(normalizedCondition, marketData, candles)
                }

                // Stop loss / take profit
                normalizedCondition.contains("stop") || normalizedCondition.contains("takeprofit") -> {
                    // This will be handled by position management
                    true
                }

                else -> {
                    Timber.w("[$TAG] Unknown condition pattern: $condition")
                    false
                }
            }

            Timber.d("[$TAG] Condition '$condition' = $result")
            result

        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Error evaluating condition: $condition")
            false
        }
    }

    /**
     * Evaluate RSI (Relative Strength Index) conditions
     *
     * @param condition Condition string (e.g., "RSI < 30")
     * @param candles Historical candle data
     * @return True if RSI condition is met
     */
    private fun evaluateRSI(condition: String, candles: List<Candle>): Boolean {
        val closes = candles.map { it.close }
        val rsiValues = rsiCalculator.calculate(closes, period = 14)
        val rsi = rsiValues.lastOrNull() ?: return false

        if (FeatureFlags.LOG_CACHE_PERFORMANCE) {
            Timber.d("[$TAG] RSI calculated: $rsi")
        }

        return when {
            condition.contains("<") -> {
                val threshold = extractNumber(condition) ?: 30.0
                rsi < threshold
            }
            condition.contains(">") -> {
                val threshold = extractNumber(condition) ?: 70.0
                rsi > threshold
            }
            condition.contains("oversold") -> rsi < 30.0
            condition.contains("overbought") -> rsi > 70.0
            else -> false
        }
    }

    /**
     * Evaluate Simple Moving Average (SMA) conditions
     *
     * @param condition Condition string (e.g., "SMA_20 > SMA_50")
     * @param candles Historical candle data
     * @param currentPrice Current price for comparison
     * @return True if SMA condition is met
     */
    private fun evaluateMovingAverage(
        condition: String,
        candles: List<Candle>,
        currentPrice: Double
    ): Boolean {
        val closes = candles.map { it.close }
        val numbers = Regex("\\d+").findAll(condition).map { it.value.toInt() }.toList()

        return when {
            numbers.size >= 2 -> {
                // Compare two SMAs
                val sma1Values = maCalculator.calculateSMA(closes, numbers[0])
                val sma2Values = maCalculator.calculateSMA(closes, numbers[1])

                val sma1 = sma1Values.lastOrNull() ?: return false
                val sma2 = sma2Values.lastOrNull() ?: return false

                when {
                    condition.contains(">") || condition.contains("above") -> sma1 > sma2
                    condition.contains("<") || condition.contains("below") -> sma1 < sma2
                    condition.contains("cross") -> {
                        // Golden cross or death cross
                        if (candles.size < numbers.maxOrNull()!! + 2) return false

                        val prevCloses = closes.dropLast(1)
                        val prevSma1Values = maCalculator.calculateSMA(prevCloses, numbers[0])
                        val prevSma2Values = maCalculator.calculateSMA(prevCloses, numbers[1])

                        val prevSma1 = prevSma1Values.lastOrNull() ?: return false
                        val prevSma2 = prevSma2Values.lastOrNull() ?: return false

                        // Bullish cross: short MA crosses above long MA
                        (prevSma1 < prevSma2 && sma1 > sma2)
                    }
                    else -> false
                }
            }
            numbers.size == 1 -> {
                // Compare price to SMA
                val smaValues = maCalculator.calculateSMA(closes, numbers[0])
                val sma = smaValues.lastOrNull() ?: return false

                when {
                    condition.contains(">") || condition.contains("above") -> currentPrice > sma
                    condition.contains("<") || condition.contains("below") -> currentPrice < sma
                    else -> false
                }
            }
            else -> false
        }
    }

    /**
     * Evaluate Exponential Moving Average (EMA) conditions
     *
     * Similar to SMA but with exponential weighting
     *
     * @param condition Condition string (e.g., "EMA_12 > EMA_26")
     * @param candles Historical candle data
     * @param currentPrice Current price for comparison
     * @return True if EMA condition is met
     */
    private fun evaluateExponentialMovingAverage(
        condition: String,
        candles: List<Candle>,
        currentPrice: Double
    ): Boolean {
        val closes = candles.map { it.close }
        val numbers = Regex("\\d+").findAll(condition).map { it.value.toInt() }.toList()

        return when {
            numbers.size >= 2 -> {
                // Compare two EMAs
                val ema1Values = maCalculator.calculateEMA(closes, numbers[0])
                val ema2Values = maCalculator.calculateEMA(closes, numbers[1])

                val ema1 = ema1Values.lastOrNull() ?: return false
                val ema2 = ema2Values.lastOrNull() ?: return false

                when {
                    condition.contains(">") || condition.contains("above") -> ema1 > ema2
                    condition.contains("<") || condition.contains("below") -> ema1 < ema2
                    condition.contains("cross") -> {
                        // EMA crossover
                        if (candles.size < numbers.maxOrNull()!! + 2) return false

                        val prevCloses = closes.dropLast(1)
                        val prevEma1Values = maCalculator.calculateEMA(prevCloses, numbers[0])
                        val prevEma2Values = maCalculator.calculateEMA(prevCloses, numbers[1])

                        val prevEma1 = prevEma1Values.lastOrNull() ?: return false
                        val prevEma2 = prevEma2Values.lastOrNull() ?: return false

                        // Bullish cross: fast EMA crosses above slow EMA
                        (prevEma1 < prevEma2 && ema1 > ema2)
                    }
                    else -> false
                }
            }
            numbers.size == 1 -> {
                // Compare price to EMA
                val emaValues = maCalculator.calculateEMA(closes, numbers[0])
                val ema = emaValues.lastOrNull() ?: return false

                when {
                    condition.contains(">") || condition.contains("above") -> currentPrice > ema
                    condition.contains("<") || condition.contains("below") -> currentPrice < ema
                    else -> false
                }
            }
            else -> false
        }
    }

    /**
     * Evaluate MACD (Moving Average Convergence Divergence) conditions
     *
     * @param condition Condition string (e.g., "MACD_crossover", "MACD > 0")
     * @param candles Historical candle data
     * @return True if MACD condition is met
     */
    private fun evaluateMACD(condition: String, candles: List<Candle>): Boolean {
        val closes = candles.map { it.close }
        val macdResult = macdCalculator.calculate(closes)

        val macdLine = macdResult.macdLine.lastOrNull() ?: return false
        val signalLine = macdResult.signalLine.lastOrNull() ?: return false
        val histogram = macdResult.histogram.lastOrNull() ?: return false

        if (FeatureFlags.LOG_CACHE_PERFORMANCE) {
            Timber.d("[$TAG] MACD: line=$macdLine, signal=$signalLine, histogram=$histogram")
        }

        return when {
            condition.contains("positive") -> histogram > 0
            condition.contains("negative") -> histogram < 0
            condition.contains("crossover") || condition.contains("cross") -> {
                if (candles.size < 27) return false

                val prevCloses = closes.dropLast(1)
                val prevMacdResult = macdCalculator.calculate(prevCloses)

                val prevMacdLine = prevMacdResult.macdLine.lastOrNull() ?: return false
                val prevSignalLine = prevMacdResult.signalLine.lastOrNull() ?: return false

                // Bullish crossover: MACD line crosses above signal line
                (prevMacdLine < prevSignalLine && macdLine > signalLine)
            }
            condition.contains(">") -> macdLine > signalLine
            condition.contains("<") -> macdLine < signalLine
            else -> histogram > 0 // Default: positive histogram
        }
    }

    /**
     * Evaluate Bollinger Bands conditions
     *
     * @param condition Condition string (e.g., "Price > Bollinger_Upper")
     * @param candles Historical candle data
     * @param currentPrice Current price
     * @return True if Bollinger Bands condition is met
     */
    private fun evaluateBollingerBands(
        condition: String,
        candles: List<Candle>,
        currentPrice: Double
    ): Boolean {
        val closes = candles.map { it.close }
        val bandsResult = bollingerCalculator.calculate(closes, period = 20, stdDev = 2.0)

        val upper = bandsResult.upperBand.lastOrNull() ?: return false
        val middle = bandsResult.middleBand.lastOrNull() ?: return false
        val lower = bandsResult.lowerBand.lastOrNull() ?: return false

        if (FeatureFlags.LOG_CACHE_PERFORMANCE) {
            Timber.d("[$TAG] Bollinger Bands: upper=$upper, middle=$middle, lower=$lower")
        }

        return when {
            condition.contains("upper") -> currentPrice > upper
            condition.contains("lower") -> currentPrice < lower
            condition.contains("outside") -> currentPrice > upper || currentPrice < lower
            condition.contains("middle") -> {
                val range = upper - lower
                kotlin.math.abs(currentPrice - middle) < (range * 0.1)
            }
            else -> false
        }
    }

    /**
     * Evaluate ATR (Average True Range) conditions for volatility
     *
     * @param condition Condition string (e.g., "ATR > 2.0")
     * @param candles Historical candle data
     * @param marketData Current market data
     * @return True if ATR condition is met
     */
    private fun evaluateATR(
        condition: String,
        candles: List<Candle>,
        marketData: MarketTicker
    ): Boolean {
        if (candles.size < 14) return false

        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val closes = candles.map { it.close }

        val atrValues = atrCalculator.calculate(highs, lows, closes, period = 14)
        val atr = atrValues.lastOrNull() ?: return false

        if (FeatureFlags.LOG_CACHE_PERFORMANCE) {
            Timber.d("[$TAG] ATR calculated: $atr")
        }

        val threshold = extractNumber(condition) ?: 1.0

        return when {
            condition.contains(">") || condition.contains("high") -> atr > threshold
            condition.contains("<") || condition.contains("low") -> atr < threshold
            else -> atr > threshold // Default: high volatility
        }
    }

    /**
     * Evaluate momentum/price change conditions
     *
     * @param condition Condition string (e.g., "Momentum > 2")
     * @param marketData Current market data
     * @return True if momentum condition is met
     */
    private fun evaluateMomentum(condition: String, marketData: MarketTicker): Boolean {
        val threshold = extractNumber(condition) ?: 2.0

        return when {
            condition.contains(">") || condition.contains("positive") -> {
                marketData.changePercent24h > threshold
            }
            condition.contains("<") || condition.contains("negative") -> {
                marketData.changePercent24h < -threshold
            }
            else -> marketData.changePercent24h > threshold
        }
    }

    /**
     * Evaluate volume conditions
     *
     * @param condition Condition string (e.g., "Volume > average")
     * @param marketData Current market data
     * @param candles Historical candle data
     * @return True if volume condition is met
     */
    private fun evaluateVolume(
        condition: String,
        marketData: MarketTicker,
        candles: List<Candle>
    ): Boolean {
        val volumes = candles.map { it.volume }
        val currentVolume = marketData.volume24h

        // Calculate average volume using calculator
        val avgVolumeValues = volumeCalculator.calculateAverageVolume(volumes, period = 20)
        val avgVolume = avgVolumeValues.lastOrNull() ?: currentVolume

        if (FeatureFlags.LOG_CACHE_PERFORMANCE) {
            Timber.d("[$TAG] Volume: current=$currentVolume, average=$avgVolume")
        }

        return when {
            condition.contains("high") || condition.contains(">") -> {
                currentVolume > avgVolume * 1.5
            }
            condition.contains("low") || condition.contains("<") -> {
                currentVolume < avgVolume * 0.5
            }
            else -> true
        }
    }

    /**
     * Evaluate price position conditions
     *
     * @param condition Condition string (e.g., "Price near high")
     * @param marketData Current market data
     * @param candles Historical candle data
     * @return True if price position condition is met
     */
    private fun evaluatePricePosition(
        condition: String,
        marketData: MarketTicker,
        candles: List<Candle>
    ): Boolean {
        val currentPrice = marketData.last

        return when {
            condition.contains("near high") || condition.contains("high") -> {
                currentPrice >= marketData.high24h * 0.98
            }
            condition.contains("near low") || condition.contains("low") -> {
                currentPrice <= marketData.low24h * 1.02
            }
            condition.contains(">") -> {
                val threshold = extractNumber(condition) ?: return false
                currentPrice > threshold
            }
            condition.contains("<") -> {
                val threshold = extractNumber(condition) ?: return false
                currentPrice < threshold
            }
            else -> false
        }
    }

    /**
     * Extract first number from a string
     *
     * @param text Text to extract number from
     * @return Extracted number or null if not found
     */
    private fun extractNumber(text: String): Double? {
        return Regex("\\d+(\\.\\d+)?").find(text)?.value?.toDoubleOrNull()
    }

    /**
     * Get price history status for a trading pair
     *
     * @param pair Trading pair identifier
     * @return Pair of (current size, required size)
     */
    fun getPriceHistoryStatus(pair: String): Pair<Int, Int> {
        val currentSize = priceHistoryManager.getHistorySize(pair)
        return Pair(currentSize, MIN_HISTORY_SIZE)
    }

    /**
     * Check if pair has enough price history
     *
     * @param pair Trading pair identifier
     * @return True if enough history exists, false otherwise
     */
    fun hasEnoughHistory(pair: String): Boolean {
        return priceHistoryManager.getHistorySize(pair) >= MIN_HISTORY_SIZE
    }

    /**
     * Get all pairs with their history status
     *
     * @return Map of pair to (current size, required size)
     */
    fun getAllPriceHistoryStatus(): Map<String, Pair<Int, Int>> {
        return priceHistoryManager.getStorageStats().mapValues { (_, size) ->
            Pair(size, MIN_HISTORY_SIZE)
        }
    }

    /**
     * Clear price history (useful for testing)
     */
    fun clearHistory() {
        priceHistoryManager.clearAllHistory()
        if (FeatureFlags.LOG_CACHE_PERFORMANCE) {
            Timber.d("[$TAG] All price history cleared")
        }
    }

    /**
     * Clear history for a specific pair
     *
     * @param pair Trading pair identifier
     */
    fun clearHistory(pair: String) {
        priceHistoryManager.clearHistory(pair)
        if (FeatureFlags.LOG_CACHE_PERFORMANCE) {
            Timber.d("[$TAG] Price history cleared for $pair")
        }
    }
}
