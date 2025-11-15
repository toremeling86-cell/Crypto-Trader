package com.cryptotrader.domain.indicators.cache

import java.util.LinkedHashMap

/**
 * LRU (Least Recently Used) cache for indicator calculation results
 *
 * This cache helps avoid recalculating indicators when the same parameters
 * and data are used repeatedly.
 *
 * @property maxSize Maximum number of entries to keep in cache
 */
class IndicatorCache(private val maxSize: Int = 100) {

    private val cache = object : LinkedHashMap<String, Any>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?): Boolean {
            return size > maxSize
        }
    }

    /**
     * Generates a cache key from indicator name, parameters, and data hash
     *
     * @param indicatorName Name of the indicator (e.g., "RSI", "MACD")
     * @param parameters Map of parameter names to values
     * @param dataHash Hash of the input data
     * @return Cache key string
     */
    fun generateKey(
        indicatorName: String,
        parameters: Map<String, Any>,
        dataHash: Int
    ): String {
        val paramsString = parameters.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        return "$indicatorName|$paramsString|$dataHash"
    }

    /**
     * Generates a hash for a list of doubles
     *
     * @param data List of double values
     * @return Hash code
     */
    fun hashData(data: List<Double>): Int {
        return data.hashCode()
    }

    /**
     * Generates a hash for multiple lists of doubles (e.g., for OHLC data)
     *
     * @param dataLists Variable number of data lists
     * @return Combined hash code
     */
    fun hashData(vararg dataLists: List<Double>): Int {
        return dataLists.fold(1) { acc, list ->
            31 * acc + list.hashCode()
        }
    }

    /**
     * Retrieves a cached value
     *
     * @param key Cache key
     * @return Cached value or null if not found
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        return synchronized(cache) {
            cache[key] as? T
        }
    }

    /**
     * Stores a value in the cache
     *
     * @param key Cache key
     * @param value Value to cache
     */
    fun put(key: String, value: Any) {
        synchronized(cache) {
            cache[key] = value
        }
    }

    /**
     * Checks if a key exists in the cache
     *
     * @param key Cache key
     * @return True if key exists, false otherwise
     */
    fun contains(key: String): Boolean {
        return synchronized(cache) {
            cache.containsKey(key)
        }
    }

    /**
     * Clears all cached values
     */
    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }

    /**
     * Gets the current number of cached entries
     *
     * @return Number of entries in cache
     */
    fun size(): Int {
        return synchronized(cache) {
            cache.size
        }
    }

    /**
     * Removes a specific entry from the cache
     *
     * @param key Cache key to remove
     */
    fun remove(key: String) {
        synchronized(cache) {
            cache.remove(key)
        }
    }
}

/**
 * Extension function to get or compute a cached value
 *
 * @param key Cache key
 * @param compute Function to compute the value if not cached
 * @return Cached or computed value
 */
inline fun <reified T> IndicatorCache.getOrPut(key: String, compute: () -> T): T {
    val cached = get<T>(key)
    if (cached != null) {
        return cached
    }

    val computed = compute()
    put(key, computed as Any)
    return computed
}
