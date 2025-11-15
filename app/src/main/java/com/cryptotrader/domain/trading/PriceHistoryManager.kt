package com.cryptotrader.domain.trading

import com.cryptotrader.domain.indicators.Candle
import com.cryptotrader.domain.indicators.cache.IndicatorCache
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages price history storage for trading pairs in Candle format
 *
 * This manager provides thread-safe storage and retrieval of candle data
 * for each trading pair. It integrates with IndicatorCache to persist data
 * and maintains a maximum of 200 candles per pair to prevent memory issues.
 *
 * The implementation uses ConcurrentHashMap for thread-safe operations and
 * LinkedList for efficient insertion and removal of candles.
 */
@Singleton
class PriceHistoryManager @Inject constructor(
    private val indicatorCache: IndicatorCache
) {
    /**
     * Maximum number of candles to store per trading pair
     */
    private val maxCandlesPerPair = 200

    /**
     * In-memory storage for candle history per trading pair
     * Key: Trading pair (e.g., "BTC/USD")
     * Value: Mutable list of candles (newest last)
     */
    private val historyMap = ConcurrentHashMap<String, MutableList<Candle>>()

    /**
     * Updates the price history for a trading pair by adding a new candle
     *
     * The new candle is added to the end of the history (representing the most recent data).
     * If the history exceeds the maximum size, the oldest candle is removed.
     *
     * This method is thread-safe and can be called from multiple threads simultaneously.
     *
     * @param pair The trading pair identifier (e.g., "BTC/USD")
     * @param candle The new candle to add to the history
     */
    fun updateHistory(pair: String, candle: Candle) {
        synchronized(historyMap) {
            // Get or create the history list for this pair
            val history = historyMap.getOrPut(pair) { mutableListOf() }

            // Add the new candle to the end (newest)
            history.add(candle)

            // Remove oldest candles if we exceed the maximum size
            while (history.size > maxCandlesPerPair) {
                history.removeAt(0) // Remove from beginning (oldest)
            }

            // Update the cache with the new history
            updateCache(pair, history)
        }
    }

    /**
     * Retrieves the complete price history for a trading pair
     *
     * @param pair The trading pair identifier (e.g., "BTC/USD")
     * @return List of candles ordered from oldest to newest (immutable copy)
     */
    fun getHistory(pair: String): List<Candle> {
        return synchronized(historyMap) {
            // Try to get from memory first
            val history = historyMap[pair]

            if (history != null) {
                // Return a copy to prevent external modification
                history.toList()
            } else {
                // Try to load from cache
                val cachedHistory = loadFromCache(pair)
                if (cachedHistory.isNotEmpty()) {
                    // Store in memory for faster future access
                    historyMap[pair] = cachedHistory.toMutableList()
                    cachedHistory
                } else {
                    // No history available
                    emptyList()
                }
            }
        }
    }

    /**
     * Retrieves the last N candles for a trading pair
     *
     * This method is useful when you only need recent data for indicator calculations
     * and want to avoid processing the entire history.
     *
     * @param pair The trading pair identifier (e.g., "BTC/USD")
     * @param count Number of most recent candles to retrieve
     * @return List of the last N candles ordered from oldest to newest
     */
    fun getHistory(pair: String, count: Int): List<Candle> {
        require(count > 0) { "Count must be positive, got $count" }

        return synchronized(historyMap) {
            val fullHistory = getHistory(pair)

            // Return the last 'count' candles
            if (fullHistory.size <= count) {
                fullHistory
            } else {
                fullHistory.takeLast(count)
            }
        }
    }

    /**
     * Clears all history for a specific trading pair
     *
     * This is useful when switching strategies or when you want to start
     * fresh with new data.
     *
     * @param pair The trading pair identifier (e.g., "BTC/USD")
     */
    fun clearHistory(pair: String) {
        synchronized(historyMap) {
            historyMap.remove(pair)
            clearCache(pair)
        }
    }

    /**
     * Clears all history for all trading pairs
     *
     * Use this method with caution as it will remove all stored candle data.
     */
    fun clearAllHistory() {
        synchronized(historyMap) {
            historyMap.clear()
            indicatorCache.clear()
        }
    }

    /**
     * Gets the number of candles currently stored for a trading pair
     *
     * @param pair The trading pair identifier
     * @return Number of candles in the history, or 0 if no history exists
     */
    fun getHistorySize(pair: String): Int {
        return synchronized(historyMap) {
            historyMap[pair]?.size ?: 0
        }
    }

    /**
     * Checks if history exists for a trading pair
     *
     * @param pair The trading pair identifier
     * @return True if at least one candle exists for this pair, false otherwise
     */
    fun hasHistory(pair: String): Boolean {
        return getHistorySize(pair) > 0
    }

    /**
     * Gets all trading pairs that have history stored
     *
     * @return Set of trading pair identifiers
     */
    fun getTrackedPairs(): Set<String> {
        return synchronized(historyMap) {
            historyMap.keys.toSet()
        }
    }

    /**
     * Batch update: adds multiple candles at once
     *
     * This is more efficient than calling updateHistory multiple times
     * as it only acquires the lock once.
     *
     * @param pair The trading pair identifier
     * @param candles List of candles to add (should be ordered oldest to newest)
     */
    fun updateHistoryBatch(pair: String, candles: List<Candle>) {
        if (candles.isEmpty()) return

        synchronized(historyMap) {
            val history = historyMap.getOrPut(pair) { mutableListOf() }

            // Add all new candles
            history.addAll(candles)

            // Remove oldest candles if we exceed the maximum size
            while (history.size > maxCandlesPerPair) {
                history.removeAt(0)
            }

            // Update the cache with the new history
            updateCache(pair, history)
        }
    }

    /**
     * Replaces the entire history for a trading pair
     *
     * Use this when you want to load a fresh dataset, such as when
     * switching timeframes or loading historical data from an API.
     *
     * @param pair The trading pair identifier
     * @param candles Complete list of candles to set as the new history
     */
    fun setHistory(pair: String, candles: List<Candle>) {
        synchronized(historyMap) {
            // Take only the most recent candles if the list is too large
            val limitedCandles = if (candles.size > maxCandlesPerPair) {
                candles.takeLast(maxCandlesPerPair)
            } else {
                candles
            }

            historyMap[pair] = limitedCandles.toMutableList()
            updateCache(pair, limitedCandles)
        }
    }

    // Private helper methods

    /**
     * Generates a cache key for price history
     */
    private fun generateCacheKey(pair: String): String {
        return "price_history_$pair"
    }

    /**
     * Updates the cache with the current history
     */
    private fun updateCache(pair: String, history: List<Candle>) {
        val cacheKey = generateCacheKey(pair)
        indicatorCache.put(cacheKey, history.toList()) // Store immutable copy
    }

    /**
     * Loads history from cache
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadFromCache(pair: String): List<Candle> {
        val cacheKey = generateCacheKey(pair)
        return indicatorCache.get<List<Candle>>(cacheKey) ?: emptyList()
    }

    /**
     * Clears history from cache
     */
    private fun clearCache(pair: String) {
        val cacheKey = generateCacheKey(pair)
        indicatorCache.remove(cacheKey)
    }

    /**
     * Gets the most recent candle for a trading pair, if available
     *
     * @param pair The trading pair identifier
     * @return The most recent candle, or null if no history exists
     */
    fun getLatestCandle(pair: String): Candle? {
        return synchronized(historyMap) {
            historyMap[pair]?.lastOrNull()
        }
    }

    /**
     * Gets the oldest candle for a trading pair, if available
     *
     * @param pair The trading pair identifier
     * @return The oldest candle, or null if no history exists
     */
    fun getOldestCandle(pair: String): Candle? {
        return synchronized(historyMap) {
            historyMap[pair]?.firstOrNull()
        }
    }

    /**
     * Gets statistics about the stored price history
     *
     * @return Map of pair to candle count
     */
    fun getStorageStats(): Map<String, Int> {
        return synchronized(historyMap) {
            historyMap.mapValues { it.value.size }
        }
    }
}
