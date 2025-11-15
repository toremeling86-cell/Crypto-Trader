package com.cryptotrader.utils

/**
 * Feature flags for controlling Phase 2 migration and advanced features
 *
 * These flags enable safe, gradual rollout of the calculator-based indicator system
 * and provide debugging capabilities during the migration process.
 *
 * Usage:
 * ```kotlin
 * if (FeatureFlags.USE_ADVANCED_INDICATORS) {
 *     // Use new calculator-based system
 * } else {
 *     // Use legacy indicator system
 * }
 * ```
 */
object FeatureFlags {

    /**
     * Enable advanced calculator-based indicator system
     *
     * When true: Uses new MarketDataAdapter, PriceHistoryManager, and calculator-based indicators
     * When false: Uses legacy indicator calculation methods
     *
     * Start with false for safety - switch to true after:
     * - All calculator implementations are complete
     * - Unit tests pass
     * - A/B testing shows equivalent or better results
     */
    const val USE_ADVANCED_INDICATORS = true

    /**
     * Enable cache performance logging
     *
     * When true: Logs cache hits, misses, and performance metrics
     * Useful for:
     * - Monitoring cache effectiveness
     * - Identifying performance bottlenecks
     * - Tuning cache size and eviction policies
     *
     * Safe to enable in production - uses structured logging
     */
    const val LOG_CACHE_PERFORMANCE = true

    /**
     * Enable A/B comparison between old and new indicator outputs
     *
     * When true: Calculates indicators using both systems and logs differences
     * Use for:
     * - Validating new calculator implementations
     * - Detecting numerical discrepancies
     * - Ensuring backward compatibility
     *
     * WARNING: Doubles computation cost - use only during testing/validation
     */
    const val COMPARE_INDICATOR_OUTPUTS = false

    /**
     * Enable Kraken WebSocket for real-time price updates
     *
     * When true: Uses WebSocket for live market data streaming
     * - Real-time price updates
     * - Lower latency for price changes
     * - Persistent connection to Kraken
     * - ⚠️ WARNING: Can cause API lockout if authentication fails repeatedly!
     *
     * When false: Uses REST API only (getTicker() calls)
     * - Polling-based updates (manual refresh or periodic)
     * - More reliable and stable
     * - No persistent connection
     * - Recommended for testing and initial setup
     *
     * Default: false (safer, prevents lockout issues)
     * Recommended: false until you verify REST API works perfectly
     *
     * IMPORTANT: You must also enable "WebSocket interface" in Kraken API settings!
     * If WebSocket is OFF in Kraken settings, this flag won't work even if true.
     */
    const val ENABLE_KRAKEN_WEBSOCKET = false
}
