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
     * CRITICAL: Prevents look-ahead bias in backtesting
     *
     * When isBacktesting=true, only uses COMPLETED candles (excludes current incomplete candle)
     * to prevent seeing future data. This ensures backtest results match live trading performance.
     *
     * @param strategy Strategy containing entry conditions
     * @param marketData Current market ticker data
     * @param isBacktesting If true, excludes current candle from calculations (prevents look-ahead bias)
     * @return True if all entry conditions are met, false otherwise
     */
    fun evaluateEntryConditions(
        strategy: Strategy,
        marketData: MarketTicker,
        isBacktesting: Boolean = false
    ): Boolean {
        return try {
            if (isBacktesting) {
                // BACKTEST MODE: Use only COMPLETED candles (exclude current)
                // Do NOT update price history with current candle - it's incomplete!
                val candles = priceHistoryManager.getHistory(marketData.pair)

                if (candles.size < MIN_HISTORY_SIZE) {
                    Timber.d("[$TAG] BACKTEST: Not enough history for ${marketData.pair}: ${candles.size}/$MIN_HISTORY_SIZE")
                    return false
                }

                Timber.d("[$TAG] BACKTEST MODE: Evaluating ${strategy.name} with ${candles.size} COMPLETED candles only (excluding current)")
                if (candles.isNotEmpty()) {
                    Timber.d("[$TAG] BACKTEST: Last completed candle timestamp: ${candles.last().timestamp}")
                }

                // Evaluate all entry conditions using ONLY completed candles
                val conditionResults = mutableListOf<Pair<String, Boolean>>()
                val result = strategy.entryConditions.all { condition ->
                    val conditionResult = evaluateCondition(
                        condition = condition,
                        marketData = marketData,
                        candles = candles,
                        useCompletedOnly = true  // CRITICAL: Only use completed candles
                    )
                    conditionResults.add(Pair(condition, conditionResult))
                    conditionResult
                }

                // Log detailed results
                if (!result || candles.size <= MIN_HISTORY_SIZE + 5) {
                    Timber.i("[$TAG] BACKTEST Entry evaluation for ${strategy.name} (${candles.size} completed candles): $result")
                    conditionResults.forEach { (condition, condResult) ->
                        Timber.i("[$TAG]   - '$condition' = $condResult")
                    }
                }

                result

            } else {
                // LIVE TRADING MODE: Can use current candle
                // Update price history with latest data
                updatePriceHistory(marketData.pair, marketData)

                // Get candle history
                val candles = priceHistoryManager.getHistory(marketData.pair)
                if (candles.size < MIN_HISTORY_SIZE) {
                    Timber.d("[$TAG] LIVE: Not enough history for ${marketData.pair}: ${candles.size}/$MIN_HISTORY_SIZE")
                    return false
                }

                Timber.d("[$TAG] LIVE MODE: Evaluating ${strategy.name} with ${candles.size} candles (including current)")

                // Evaluate all entry conditions (ALL must be true)
                val conditionResults = mutableListOf<Pair<String, Boolean>>()
                val result = strategy.entryConditions.all { condition ->
                    val conditionResult = evaluateCondition(
                        condition = condition,
                        marketData = marketData,
                        candles = candles,
                        useCompletedOnly = false  // Can use current candle in live trading
                    )
                    conditionResults.add(Pair(condition, conditionResult))
                    conditionResult
                }

                // Log detailed results
                if (!result || candles.size <= MIN_HISTORY_SIZE + 5) {
                    Timber.i("[$TAG] LIVE Entry evaluation for ${strategy.name} (${candles.size} candles): $result")
                    conditionResults.forEach { (condition, condResult) ->
                        Timber.i("[$TAG]   - '$condition' = $condResult")
                    }
                }

                result
            }

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
     * @param useCompletedOnly If true, excludes last candle (for backtesting to prevent look-ahead bias)
     * @return True if condition is met, false otherwise
     */
    private fun evaluateCondition(
        condition: String,
        marketData: MarketTicker,
        candles: List<Candle>,
        useCompletedOnly: Boolean = false
    ): Boolean {
        val normalizedCondition = condition.trim().lowercase()

        return try {
            val result = when {
                // RSI conditions
                normalizedCondition.contains("rsi") -> evaluateRSI(normalizedCondition, candles, useCompletedOnly)

                // MACD conditions (check BEFORE "ma" to avoid false matches since "macd" contains "ma")
                normalizedCondition.contains("macd") -> evaluateMACD(normalizedCondition, candles, useCompletedOnly)

                // EMA conditions (check before "ma" to avoid false matches)
                normalizedCondition.contains("ema") -> {
                    evaluateExponentialMovingAverage(normalizedCondition, candles, marketData.last, useCompletedOnly)
                }

                // Moving Average conditions (SMA)
                normalizedCondition.contains("sma") || normalizedCondition.contains("ma") -> {
                    evaluateMovingAverage(normalizedCondition, candles, marketData.last, useCompletedOnly)
                }

                // Bollinger Bands conditions
                normalizedCondition.contains("bollinger") -> {
                    evaluateBollingerBands(normalizedCondition, candles, marketData.last, useCompletedOnly)
                }

                // ATR (Average True Range) conditions
                normalizedCondition.contains("atr") -> {
                    evaluateATR(normalizedCondition, candles, marketData, useCompletedOnly)
                }

                // Price momentum conditions
                normalizedCondition.contains("momentum") || normalizedCondition.contains("change") -> {
                    evaluateMomentum(normalizedCondition, marketData)
                }

                // Volume conditions
                normalizedCondition.contains("volume") -> {
                    evaluateVolume(normalizedCondition, marketData, candles, useCompletedOnly)
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
     * @param useCompletedOnly If true, excludes last candle to prevent look-ahead bias
     * @return True if RSI condition is met
     */
    private fun evaluateRSI(condition: String, candles: List<Candle>, useCompletedOnly: Boolean = false): Boolean {
        // Exclude current incomplete candle if in backtest mode
        val candlesToUse = if (useCompletedOnly && candles.size > 14) {
            candles.dropLast(1)
        } else {
            candles
        }

        if (candlesToUse.size < 14) return false  // Need at least 14 candles for RSI

        val closes = candlesToUse.map { it.close }
        val rsiValues = rsiCalculator.calculate(closes, period = 14)
        val rsi = rsiValues.lastOrNull() ?: return false

        if (FeatureFlags.LOG_CACHE_PERFORMANCE) {
            Timber.d("[$TAG] RSI calculated: $rsi")
        }

        return when {
            condition.contains("<") -> {
                val threshold = extractThreshold(condition) ?: 30.0
                Timber.d("[$TAG] RSI comparison: $rsi < $threshold = ${rsi < threshold}")
                rsi < threshold
            }
            condition.contains(">") -> {
                val threshold = extractThreshold(condition) ?: 70.0
                Timber.d("[$TAG] RSI comparison: $rsi > $threshold = ${rsi > threshold}")
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
     * @param useCompletedOnly If true, excludes last candle to prevent look-ahead bias
     * @return True if SMA condition is met
     */
    private fun evaluateMovingAverage(
        condition: String,
        candles: List<Candle>,
        currentPrice: Double,
        useCompletedOnly: Boolean = false
    ): Boolean {
        val numbers = Regex("\\d+").findAll(condition).map { it.value.toInt() }.toList()
        val maxPeriod = numbers.maxOrNull() ?: 20

        // Exclude current incomplete candle if in backtest mode
        val candlesToUse = if (useCompletedOnly && candles.size > maxPeriod) {
            candles.dropLast(1)
        } else {
            candles
        }

        if (candlesToUse.size < maxPeriod) return false  // Need enough candles

        val closes = candlesToUse.map { it.close }

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
     * @param useCompletedOnly If true, excludes last candle to prevent look-ahead bias
     * @return True if EMA condition is met
     */
    private fun evaluateExponentialMovingAverage(
        condition: String,
        candles: List<Candle>,
        currentPrice: Double,
        useCompletedOnly: Boolean = false
    ): Boolean {
        val numbers = Regex("\\d+").findAll(condition).map { it.value.toInt() }.toList()
        val maxPeriod = numbers.maxOrNull() ?: 26

        // Exclude current incomplete candle if in backtest mode
        val candlesToUse = if (useCompletedOnly && candles.size > maxPeriod) {
            candles.dropLast(1)
        } else {
            candles
        }

        if (candlesToUse.size < maxPeriod) return false  // Need enough candles

        val closes = candlesToUse.map { it.close }

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
     * @param useCompletedOnly If true, excludes last candle to prevent look-ahead bias
     * @return True if MACD condition is met
     */
    private fun evaluateMACD(condition: String, candles: List<Candle>, useCompletedOnly: Boolean = false): Boolean {
        val minPeriod = 26  // MACD needs at least 26 periods

        // Exclude current incomplete candle if in backtest mode
        val candlesToUse = if (useCompletedOnly && candles.size > minPeriod) {
            candles.dropLast(1)
        } else {
            candles
        }

        if (candlesToUse.size < minPeriod) return false  // Need enough candles for MACD

        val closes = candlesToUse.map { it.close }
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
     * @param useCompletedOnly If true, excludes last candle to prevent look-ahead bias
     * @return True if Bollinger Bands condition is met
     */
    private fun evaluateBollingerBands(
        condition: String,
        candles: List<Candle>,
        currentPrice: Double,
        useCompletedOnly: Boolean = false
    ): Boolean {
        val period = 20

        // Exclude current incomplete candle if in backtest mode
        val candlesToUse = if (useCompletedOnly && candles.size > period) {
            candles.dropLast(1)
        } else {
            candles
        }

        if (candlesToUse.size < period) return false  // Need enough candles

        val closes = candlesToUse.map { it.close }
        val bandsResult = bollingerCalculator.calculate(closes, period = period, stdDev = 2.0)

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
     * @param useCompletedOnly If true, excludes last candle to prevent look-ahead bias
     * @return True if ATR condition is met
     */
    private fun evaluateATR(
        condition: String,
        candles: List<Candle>,
        marketData: MarketTicker,
        useCompletedOnly: Boolean = false
    ): Boolean {
        val period = 14

        // Exclude current incomplete candle if in backtest mode
        val candlesToUse = if (useCompletedOnly && candles.size > period) {
            candles.dropLast(1)
        } else {
            candles
        }

        if (candlesToUse.size < period) return false

        val highs = candlesToUse.map { it.high }
        val lows = candlesToUse.map { it.low }
        val closes = candlesToUse.map { it.close }

        val atrValues = atrCalculator.calculate(highs, lows, closes, period = period)
        val atr = atrValues.lastOrNull() ?: return false

        if (FeatureFlags.LOG_CACHE_PERFORMANCE) {
            Timber.d("[$TAG] ATR calculated: $atr")
        }

        val threshold = extractThreshold(condition) ?: 1.0

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
        val threshold = extractThreshold(condition) ?: 2.0

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
     * @param useCompletedOnly If true, excludes last candle to prevent look-ahead bias
     * @return True if volume condition is met
     */
    private fun evaluateVolume(
        condition: String,
        marketData: MarketTicker,
        candles: List<Candle>,
        useCompletedOnly: Boolean = false
    ): Boolean {
        val period = 20

        // Exclude current incomplete candle if in backtest mode
        val candlesToUse = if (useCompletedOnly && candles.size > period) {
            candles.dropLast(1)
        } else {
            candles
        }

        if (candlesToUse.size < period) return false

        val volumes = candlesToUse.map { it.volume }
        val currentVolume = marketData.volume24h

        // Calculate average volume using calculator
        val avgVolumeValues = volumeCalculator.calculateAverageVolume(volumes, period = period)
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
                val threshold = extractThreshold(condition) ?: return false
                currentPrice > threshold
            }
            condition.contains("<") -> {
                val threshold = extractThreshold(condition) ?: return false
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
     * Extract threshold number after comparison operator
     *
     * For conditions like "RSI(14) > 75", this extracts 75, not 14
     * For conditions like "ATR < 2.5", this extracts 2.5
     *
     * @param condition Condition string with operator (< or >)
     * @return Threshold value or null if not found
     */
    private fun extractThreshold(condition: String): Double? {
        // Find the operator first
        val operatorIndex = condition.indexOfAny(charArrayOf('<', '>'))
        if (operatorIndex == -1) return null

        // Extract number after the operator
        val textAfterOperator = condition.substring(operatorIndex + 1)
        return Regex("\\d+(\\.\\d+)?").find(textAfterOperator)?.value?.toDoubleOrNull()
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
