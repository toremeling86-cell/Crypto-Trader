package com.cryptotrader.domain.trading

import com.cryptotrader.domain.model.MarketTicker
import com.cryptotrader.domain.model.Strategy
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates trading strategy conditions using technical indicators
 */
@Singleton
class StrategyEvaluator @Inject constructor() {

    // Historical price cache for calculating indicators
    private val priceHistory = mutableMapOf<String, MutableList<Double>>()
    private val maxHistorySize = 200 // Keep last 200 prices for calculations

    /**
     * Update price history for a trading pair
     */
    fun updatePriceHistory(pair: String, price: Double) {
        val history = priceHistory.getOrPut(pair) { mutableListOf() }
        history.add(price)

        // Keep only recent prices
        if (history.size > maxHistorySize) {
            history.removeAt(0)
        }
    }

    /**
     * Evaluate entry conditions for a strategy
     */
    fun evaluateEntryConditions(
        strategy: Strategy,
        marketData: MarketTicker
    ): Boolean {
        try {
            // Update price history
            updatePriceHistory(marketData.pair, marketData.last)

            val prices = priceHistory[marketData.pair] ?: return false
            if (prices.size < 30) {
                // Need at least 30 data points for most indicators
                Timber.d("Not enough price history for ${marketData.pair}")
                return false
            }

            // Evaluate each condition
            return strategy.entryConditions.all { condition ->
                evaluateCondition(condition, marketData, prices)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error evaluating entry conditions")
            return false
        }
    }

    /**
     * Evaluate exit conditions for a strategy
     */
    fun evaluateExitConditions(
        strategy: Strategy,
        marketData: MarketTicker
    ): Boolean {
        try {
            val prices = priceHistory[marketData.pair] ?: return false
            if (prices.size < 30) return false

            // Evaluate each condition
            return strategy.exitConditions.any { condition ->
                evaluateCondition(condition, marketData, prices)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error evaluating exit conditions")
            return false
        }
    }

    /**
     * Evaluate a single condition string
     * Supports patterns like:
     * - "RSI < 30"
     * - "SMA_20 > SMA_50" or "EMA_12 > EMA_26"
     * - "Price > Bollinger_Upper"
     * - "MACD_crossover"
     * - "Volume > average"
     * - "ATR > 2.0"
     */
    private fun evaluateCondition(
        condition: String,
        marketData: MarketTicker,
        prices: List<Double>
    ): Boolean {
        val normalizedCondition = condition.trim().lowercase()

        return try {
            val result = when {
                // RSI conditions
                normalizedCondition.contains("rsi") -> evaluateRSI(normalizedCondition, prices)

                // EMA conditions (check before "ma" to avoid false matches)
                normalizedCondition.contains("ema") -> {
                    evaluateExponentialMovingAverage(normalizedCondition, prices, marketData.last)
                }

                // Moving Average conditions (SMA)
                normalizedCondition.contains("sma") || normalizedCondition.contains("ma") -> {
                    evaluateMovingAverage(normalizedCondition, prices, marketData.last)
                }

                // MACD conditions
                normalizedCondition.contains("macd") -> evaluateMACD(normalizedCondition, prices)

                // Bollinger Bands conditions
                normalizedCondition.contains("bollinger") -> {
                    evaluateBollingerBands(normalizedCondition, prices, marketData.last)
                }

                // ATR (Average True Range) conditions
                normalizedCondition.contains("atr") -> {
                    evaluateATR(normalizedCondition, prices, marketData)
                }

                // Price momentum conditions
                normalizedCondition.contains("momentum") || normalizedCondition.contains("change") -> {
                    evaluateMomentum(normalizedCondition, marketData)
                }

                // Volume conditions
                normalizedCondition.contains("volume") -> {
                    evaluateVolume(normalizedCondition, marketData)
                }

                // Price position conditions
                normalizedCondition.contains("price") -> {
                    evaluatePricePosition(normalizedCondition, marketData, prices)
                }

                // Stop loss / take profit
                normalizedCondition.contains("stop") || normalizedCondition.contains("takeprofit") -> {
                    // This will be handled by position management, but allow it
                    true
                }

                else -> {
                    Timber.w("⚠️ Unknown condition pattern: $condition")
                    false
                }
            }

            // Log condition evaluation for debugging
            Timber.d("Condition '$condition' = $result")
            result

        } catch (e: Exception) {
            Timber.e(e, "❌ Error evaluating condition: $condition")
            false
        }
    }

    private fun evaluateRSI(condition: String, prices: List<Double>): Boolean {
        val rsi = TechnicalIndicators.calculateRSI(prices) ?: return false

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

    private fun evaluateMovingAverage(
        condition: String,
        prices: List<Double>,
        currentPrice: Double
    ): Boolean {
        // Parse periods from condition like "SMA_20 > SMA_50"
        val numbers = Regex("\\d+").findAll(condition).map { it.value.toInt() }.toList()

        return when {
            numbers.size >= 2 -> {
                // Compare two MAs
                val sma1 = TechnicalIndicators.calculateSMA(prices, numbers[0]) ?: return false
                val sma2 = TechnicalIndicators.calculateSMA(prices, numbers[1]) ?: return false

                when {
                    condition.contains(">") || condition.contains("above") -> sma1 > sma2
                    condition.contains("<") || condition.contains("below") -> sma1 < sma2
                    condition.contains("cross") -> {
                        // Golden cross or death cross
                        if (prices.size < numbers.maxOrNull()!! + 2) return false
                        val prevSma1 = TechnicalIndicators.calculateSMA(
                            prices.dropLast(1), numbers[0]
                        ) ?: return false
                        val prevSma2 = TechnicalIndicators.calculateSMA(
                            prices.dropLast(1), numbers[1]
                        ) ?: return false

                        (prevSma1 < prevSma2 && sma1 > sma2) // Bullish cross
                    }
                    else -> false
                }
            }
            numbers.size == 1 -> {
                // Compare price to MA
                val sma = TechnicalIndicators.calculateSMA(prices, numbers[0]) ?: return false

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
     * Evaluate EMA (Exponential Moving Average) conditions
     * Similar to SMA but with exponential weighting
     */
    private fun evaluateExponentialMovingAverage(
        condition: String,
        prices: List<Double>,
        currentPrice: Double
    ): Boolean {
        // Parse periods from condition like "EMA_12 > EMA_26"
        val numbers = Regex("\\d+").findAll(condition).map { it.value.toInt() }.toList()

        return when {
            numbers.size >= 2 -> {
                // Compare two EMAs
                val ema1 = TechnicalIndicators.calculateEMA(prices, numbers[0]) ?: return false
                val ema2 = TechnicalIndicators.calculateEMA(prices, numbers[1]) ?: return false

                when {
                    condition.contains(">") || condition.contains("above") -> ema1 > ema2
                    condition.contains("<") || condition.contains("below") -> ema1 < ema2
                    condition.contains("cross") -> {
                        // EMA crossover
                        if (prices.size < numbers.maxOrNull()!! + 2) return false
                        val prevEma1 = TechnicalIndicators.calculateEMA(
                            prices.dropLast(1), numbers[0]
                        ) ?: return false
                        val prevEma2 = TechnicalIndicators.calculateEMA(
                            prices.dropLast(1), numbers[1]
                        ) ?: return false

                        (prevEma1 < prevEma2 && ema1 > ema2) // Bullish cross
                    }
                    else -> false
                }
            }
            numbers.size == 1 -> {
                // Compare price to EMA
                val ema = TechnicalIndicators.calculateEMA(prices, numbers[0]) ?: return false

                when {
                    condition.contains(">") || condition.contains("above") -> currentPrice > ema
                    condition.contains("<") || condition.contains("below") -> currentPrice < ema
                    else -> false
                }
            }
            else -> false
        }
    }

    private fun evaluateMACD(condition: String, prices: List<Double>): Boolean {
        val macd = TechnicalIndicators.calculateMACD(prices) ?: return false
        val (macdLine, signalLine, histogram) = macd

        return when {
            condition.contains("positive") -> histogram > 0
            condition.contains("negative") -> histogram < 0
            condition.contains("crossover") || condition.contains("cross") -> {
                if (prices.size < 27) return false
                val prevMACD = TechnicalIndicators.calculateMACD(prices.dropLast(1)) ?: return false
                val (prevMacdLine, prevSignalLine, _) = prevMACD

                // Bullish crossover
                (prevMacdLine < prevSignalLine && macdLine > signalLine)
            }
            condition.contains(">") -> macdLine > signalLine
            condition.contains("<") -> macdLine < signalLine
            else -> histogram > 0 // Default: positive histogram
        }
    }

    private fun evaluateBollingerBands(
        condition: String,
        prices: List<Double>,
        currentPrice: Double
    ): Boolean {
        val bands = TechnicalIndicators.calculateBollingerBands(prices) ?: return false
        val (upper, middle, lower) = bands

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

    private fun evaluateVolume(condition: String, marketData: MarketTicker): Boolean {
        // Simple volume check - could be enhanced with volume history
        val avgVolume = marketData.volume24h // This is already 24h average

        return when {
            condition.contains("high") || condition.contains(">") -> {
                marketData.volume24h > avgVolume * 1.5
            }
            condition.contains("low") || condition.contains("<") -> {
                marketData.volume24h < avgVolume * 0.5
            }
            else -> true
        }
    }

    /**
     * Evaluate ATR (Average True Range) conditions for volatility
     * Used for volatility-adjusted stop-loss and position sizing
     */
    private fun evaluateATR(
        condition: String,
        prices: List<Double>,
        marketData: MarketTicker
    ): Boolean {
        // Note: Proper ATR calculation requires high/low/close data
        // For now, we'll use price volatility as a proxy
        if (prices.size < 14) return false

        // Calculate simple price volatility as ATR proxy
        val returns = prices.zipWithNext { a, b -> kotlin.math.abs((b - a) / a) }
        val avgVolatility = returns.takeLast(14).average()
        val atr = avgVolatility * marketData.last // ATR in price units

        val threshold = extractNumber(condition) ?: 1.0

        return when {
            condition.contains(">") || condition.contains("high") -> {
                atr > threshold
            }
            condition.contains("<") || condition.contains("low") -> {
                atr < threshold
            }
            else -> atr > threshold // Default: high volatility
        }
    }

    private fun evaluatePricePosition(
        condition: String,
        marketData: MarketTicker,
        prices: List<Double>
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
     */
    private fun extractNumber(text: String): Double? {
        return Regex("\\d+(\\.\\d+)?").find(text)?.value?.toDoubleOrNull()
    }

    /**
     * Get price history status for a trading pair
     * Returns a pair of (current size, required size)
     */
    fun getPriceHistoryStatus(pair: String): Pair<Int, Int> {
        val currentSize = priceHistory[pair]?.size ?: 0
        return Pair(currentSize, 30) // 30 is minimum required
    }

    /**
     * Check if pair has enough price history
     */
    fun hasEnoughHistory(pair: String): Boolean {
        return (priceHistory[pair]?.size ?: 0) >= 30
    }

    /**
     * Get all pairs with their history status
     */
    fun getAllPriceHistoryStatus(): Map<String, Pair<Int, Int>> {
        return priceHistory.mapValues { (_, prices) ->
            Pair(prices.size, 30)
        }
    }

    /**
     * Clear price history (useful for testing)
     */
    fun clearHistory() {
        priceHistory.clear()
    }
}
