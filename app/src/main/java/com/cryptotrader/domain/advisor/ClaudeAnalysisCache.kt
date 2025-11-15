package com.cryptotrader.domain.advisor

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Claude Analysis Cache
 *
 * LRU cache for Claude API responses to:
 * - Reduce API costs by caching recent analyses
 * - Improve response times for repeated queries
 * - Provide fallback data during API outages
 *
 * Features:
 * - Thread-safe LRU eviction
 * - Configurable TTL (time-to-live)
 * - Size-based eviction
 * - Cache hit/miss metrics
 */
@Singleton
class ClaudeAnalysisCache @Inject constructor() {

    // LRU cache with access order
    private val synthesisCache = LruCache<String, CacheEntry<ClaudeFastSynthesis>>(
        maxSize = DEFAULT_SYNTHESIS_CACHE_SIZE
    )

    private val validationCache = LruCache<String, CacheEntry<ClaudeDeepValidation>>(
        maxSize = DEFAULT_VALIDATION_CACHE_SIZE
    )

    // Cache metrics
    private var synthesisHits = 0L
    private var synthesisMisses = 0L
    private var validationHits = 0L
    private var validationMisses = 0L

    /**
     * Get cached fast synthesis result
     *
     * @param cacheKey Unique key for this synthesis request
     * @param maxAgeMs Maximum age in milliseconds (default 5 minutes)
     * @return Cached synthesis or null if not found or expired
     */
    fun getSynthesis(cacheKey: String, maxAgeMs: Long = DEFAULT_SYNTHESIS_TTL_MS): ClaudeFastSynthesis? {
        val entry = synthesisCache.get(cacheKey)

        if (entry == null) {
            synthesisMisses++
            Timber.d("Cache miss for synthesis: $cacheKey")
            return null
        }

        // Check if entry has expired
        val age = System.currentTimeMillis() - entry.timestamp
        if (age > maxAgeMs) {
            synthesisCache.remove(cacheKey)
            synthesisMisses++
            Timber.d("Cache expired for synthesis: $cacheKey (age: ${age}ms)")
            return null
        }

        synthesisHits++
        Timber.d("Cache hit for synthesis: $cacheKey (age: ${age}ms)")
        return entry.data
    }

    /**
     * Put fast synthesis result in cache
     */
    fun putSynthesis(cacheKey: String, synthesis: ClaudeFastSynthesis) {
        val entry = CacheEntry(
            data = synthesis,
            timestamp = System.currentTimeMillis()
        )
        synthesisCache.put(cacheKey, entry)
        Timber.d("Cached synthesis: $cacheKey")
    }

    /**
     * Get cached deep validation result
     *
     * @param cacheKey Unique key for this validation request
     * @param maxAgeMs Maximum age in milliseconds (default 10 minutes)
     * @return Cached validation or null if not found or expired
     */
    fun getValidation(cacheKey: String, maxAgeMs: Long = DEFAULT_VALIDATION_TTL_MS): ClaudeDeepValidation? {
        val entry = validationCache.get(cacheKey)

        if (entry == null) {
            validationMisses++
            Timber.d("Cache miss for validation: $cacheKey")
            return null
        }

        // Check if entry has expired
        val age = System.currentTimeMillis() - entry.timestamp
        if (age > maxAgeMs) {
            validationCache.remove(cacheKey)
            validationMisses++
            Timber.d("Cache expired for validation: $cacheKey (age: ${age}ms)")
            return null
        }

        validationHits++
        Timber.d("Cache hit for validation: $cacheKey (age: ${age}ms)")
        return entry.data
    }

    /**
     * Put deep validation result in cache
     */
    fun putValidation(cacheKey: String, validation: ClaudeDeepValidation) {
        val entry = CacheEntry(
            data = validation,
            timestamp = System.currentTimeMillis()
        )
        validationCache.put(cacheKey, entry)
        Timber.d("Cached validation: $cacheKey")
    }

    /**
     * Generate cache key for synthesis request
     *
     * Key includes:
     * - Agent signals (sorted by name for consistency)
     * - Market symbols being analyzed
     * - Time bucket (5-minute buckets to allow some caching)
     */
    fun generateSynthesisKey(
        agentSignals: Map<String, AgentSignal>,
        symbols: List<String>
    ): String {
        // Create time bucket (5-minute intervals)
        val timeBucket = System.currentTimeMillis() / SYNTHESIS_TIME_BUCKET_MS

        // Sort signals for consistent key generation
        val sortedSignals = agentSignals.entries
            .sortedBy { it.key }
            .joinToString("|") { (agent, signal) ->
                "$agent:${signal.signal}:${String.format("%.2f", signal.confidence)}"
            }

        val sortedSymbols = symbols.sorted().joinToString(",")

        return "synthesis:$timeBucket:$sortedSymbols:${sortedSignals.hashCode()}"
    }

    /**
     * Generate cache key for validation request
     *
     * Key includes:
     * - Opportunity details (symbol, signal, prices)
     * - Time bucket (10-minute buckets)
     */
    fun generateValidationKey(opportunity: TradingOpportunity): String {
        // Create time bucket (10-minute intervals)
        val timeBucket = System.currentTimeMillis() / VALIDATION_TIME_BUCKET_MS

        return "validation:$timeBucket:${opportunity.symbol}:${opportunity.signal}:" +
            "${String.format("%.2f", opportunity.entryPrice)}:" +
            "${String.format("%.2f", opportunity.synthesisConfidence)}"
    }

    /**
     * Clear all cached entries
     */
    fun clearAll() {
        synthesisCache.clear()
        validationCache.clear()
        Timber.i("All cache entries cleared")
    }

    /**
     * Clear expired entries from all caches
     */
    fun clearExpired() {
        val now = System.currentTimeMillis()

        // Clear expired synthesis entries
        val expiredSynthesisKeys = mutableListOf<String>()
        synthesisCache.forEach { (key, entry) ->
            if (now - entry.timestamp > DEFAULT_SYNTHESIS_TTL_MS) {
                expiredSynthesisKeys.add(key)
            }
        }
        expiredSynthesisKeys.forEach { synthesisCache.remove(it) }

        // Clear expired validation entries
        val expiredValidationKeys = mutableListOf<String>()
        validationCache.forEach { (key, entry) ->
            if (now - entry.timestamp > DEFAULT_VALIDATION_TTL_MS) {
                expiredValidationKeys.add(key)
            }
        }
        expiredValidationKeys.forEach { validationCache.remove(it) }

        Timber.d(
            "Cleared ${expiredSynthesisKeys.size} expired synthesis and " +
            "${expiredValidationKeys.size} expired validation entries"
        )
    }

    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats {
        val synthesisHitRate = if (synthesisHits + synthesisMisses > 0) {
            synthesisHits.toDouble() / (synthesisHits + synthesisMisses)
        } else {
            0.0
        }

        val validationHitRate = if (validationHits + validationMisses > 0) {
            validationHits.toDouble() / (validationHits + validationMisses)
        } else {
            0.0
        }

        return CacheStats(
            synthesisSize = synthesisCache.size(),
            synthesisHits = synthesisHits,
            synthesisMisses = synthesisMisses,
            synthesisHitRate = synthesisHitRate,
            validationSize = validationCache.size(),
            validationHits = validationHits,
            validationMisses = validationMisses,
            validationHitRate = validationHitRate
        )
    }

    /**
     * Reset cache metrics
     */
    fun resetStats() {
        synthesisHits = 0L
        synthesisMisses = 0L
        validationHits = 0L
        validationMisses = 0L
        Timber.d("Cache statistics reset")
    }

    companion object {
        // Cache size limits
        private const val DEFAULT_SYNTHESIS_CACHE_SIZE = 50
        private const val DEFAULT_VALIDATION_CACHE_SIZE = 30

        // TTL (time-to-live) in milliseconds
        private const val DEFAULT_SYNTHESIS_TTL_MS = 5 * 60 * 1000L // 5 minutes
        private const val DEFAULT_VALIDATION_TTL_MS = 10 * 60 * 1000L // 10 minutes

        // Time bucket sizes for key generation
        private const val SYNTHESIS_TIME_BUCKET_MS = 5 * 60 * 1000L // 5 minutes
        private const val VALIDATION_TIME_BUCKET_MS = 10 * 60 * 1000L // 10 minutes
    }
}

/**
 * Cache entry with timestamp
 */
private data class CacheEntry<T>(
    val data: T,
    val timestamp: Long
)

/**
 * Cache statistics
 */
data class CacheStats(
    val synthesisSize: Int,
    val synthesisHits: Long,
    val synthesisMisses: Long,
    val synthesisHitRate: Double,
    val validationSize: Int,
    val validationHits: Long,
    val validationMisses: Long,
    val validationHitRate: Double
) {
    fun toFormattedString(): String {
        return """
            Cache Statistics:
            - Synthesis: $synthesisSize entries, ${String.format("%.1f%%", synthesisHitRate * 100)} hit rate ($synthesisHits hits, $synthesisMisses misses)
            - Validation: $validationSize entries, ${String.format("%.1f%%", validationHitRate * 100)} hit rate ($validationHits hits, $validationMisses misses)
        """.trimIndent()
    }
}

/**
 * Thread-safe LRU cache implementation
 */
private class LruCache<K, V>(private val maxSize: Int) {
    private val cache = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean {
            return size > maxSize
        }
    }
    private val lock = ReentrantReadWriteLock()

    fun get(key: K): V? = lock.read {
        cache[key]
    }

    fun put(key: K, value: V) = lock.write {
        cache[key] = value
    }

    fun remove(key: K): V? = lock.write {
        cache.remove(key)
    }

    fun clear() = lock.write {
        cache.clear()
    }

    fun size(): Int = lock.read {
        cache.size
    }

    fun forEach(action: (Map.Entry<K, V>) -> Unit) = lock.read {
        cache.entries.forEach(action)
    }
}
