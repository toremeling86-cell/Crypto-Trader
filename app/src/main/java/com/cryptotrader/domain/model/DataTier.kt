package com.cryptotrader.domain.model

import kotlinx.serialization.Serializable

/**
 * Data Quality Tiers - Hedge Fund Standard
 *
 * Separates data by quality level to prevent mixing high/low quality data in backtests.
 * Each tier has different characteristics and use cases.
 *
 * CRITICAL: Never mix data from different tiers in the same backtest!
 */
@Serializable
enum class DataTier(
    val tierName: String,
    val qualityScore: Double,
    val description: String,
    val useCase: String
) {
    /**
     * TIER 1: PREMIUM - Order Book Data (Level 20)
     *
     * - crypto_lake order book snapshots
     * - 20 levels bid/ask depth
     * - Nanosecond precision timestamps
     * - ~45 snapshots per second
     * - 85 columns per snapshot
     *
     * Use cases:
     * - Market microstructure analysis
     * - Slippage modeling
     * - Liquidity analysis
     * - High-frequency strategies
     * - Order flow analysis
     */
    TIER_1_PREMIUM(
        tierName = "PREMIUM",
        qualityScore = 0.99,
        description = "Order book Level 20 depth with nanosecond precision",
        useCase = "Market microstructure, HFT, slippage modeling"
    ),

    /**
     * TIER 2: PROFESSIONAL - Tick-by-Tick Trades
     *
     * - crypto_lake individual trades
     * - Nanosecond precision timestamps
     * - Buy/Sell aggressor side
     * - Individual trade IDs
     * - ~970 trades per minute (BTC)
     *
     * Use cases:
     * - Order flow analysis
     * - VWAP strategies
     * - Volume profile
     * - Tape reading strategies
     */
    TIER_2_PROFESSIONAL(
        tierName = "PROFESSIONAL",
        qualityScore = 0.95,
        description = "Tick-by-tick trades with aggressor identification",
        useCase = "Order flow, VWAP, volume analysis"
    ),

    /**
     * TIER 3: STANDARD - Aggregated Trades
     *
     * - Binance aggTrades (aggregated)
     * - Millisecond precision
     * - Historical data (2024)
     * - CSV format
     *
     * Use cases:
     * - Standard backtesting
     * - Long-term strategy validation
     * - Robustness testing
     */
    TIER_3_STANDARD(
        tierName = "STANDARD",
        qualityScore = 0.85,
        description = "Aggregated trades from Binance (millisecond precision)",
        useCase = "Standard backtesting, long-term validation"
    ),

    /**
     * TIER 4: BASIC - OHLCV Candles
     *
     * - Pre-processed OHLCV bars
     * - 1m, 5m, 15m, 1h, 4h, 1d timeframes
     * - Basic technical analysis
     *
     * Use cases:
     * - Retail-level backtesting
     * - Quick strategy prototyping
     * - Educational purposes
     */
    TIER_4_BASIC(
        tierName = "BASIC",
        qualityScore = 0.70,
        description = "Pre-processed OHLCV candles (standard timeframes)",
        useCase = "Retail backtesting, quick prototyping"
    );

    /**
     * Check if this tier is suitable for production trading
     */
    fun isProductionGrade(): Boolean {
        return this == TIER_1_PREMIUM || this == TIER_2_PROFESSIONAL
    }

    /**
     * Check if this tier supports high-frequency strategies
     */
    fun supportsHighFrequency(): Boolean {
        return this == TIER_1_PREMIUM || this == TIER_2_PROFESSIONAL
    }

    /**
     * Get recommended minimum quality score for backtesting
     */
    fun getMinimumQualityThreshold(): Double {
        return when (this) {
            TIER_1_PREMIUM -> 0.98
            TIER_2_PROFESSIONAL -> 0.93
            TIER_3_STANDARD -> 0.80
            TIER_4_BASIC -> 0.65
        }
    }

    companion object {
        /**
         * Parse data tier from string
         */
        fun fromString(value: String): DataTier {
            return when (value.uppercase()) {
                "TIER_1_PREMIUM", "PREMIUM", "TIER1" -> TIER_1_PREMIUM
                "TIER_2_PROFESSIONAL", "PROFESSIONAL", "TIER2" -> TIER_2_PROFESSIONAL
                "TIER_3_STANDARD", "STANDARD", "TIER3" -> TIER_3_STANDARD
                "TIER_4_BASIC", "BASIC", "TIER4" -> TIER_4_BASIC
                else -> TIER_4_BASIC // Default to lowest tier
            }
        }

        /**
         * Detect tier from data source string
         */
        fun fromDataSource(source: String): DataTier {
            return when {
                source.contains("book", ignoreCase = true) -> TIER_1_PREMIUM
                source.contains("trades", ignoreCase = true) &&
                    source.contains("crypto_lake", ignoreCase = true) -> TIER_2_PROFESSIONAL
                source.contains("aggTrades", ignoreCase = true) ||
                    source.contains("binance_raw", ignoreCase = true) -> TIER_3_STANDARD
                source.contains("ohlcv", ignoreCase = true) ||
                    source.contains("candle", ignoreCase = true) -> TIER_4_BASIC
                else -> TIER_4_BASIC
            }
        }

        /**
         * Get all production-grade tiers
         */
        fun getProductionTiers(): List<DataTier> {
            return values().filter { it.isProductionGrade() }
        }
    }
}

/**
 * Data Tier Metadata
 *
 * Stored as metadata.json in each tier folder
 */
@Serializable
data class DataTierMetadata(
    val tier: DataTier,
    val qualityScore: Double,
    val dataType: String,
    val source: String,
    val timestampPrecision: String,
    val features: List<String>,
    val validated: Boolean,
    val validationDate: String,
    val assets: List<String>,
    val dateRangeStart: Long,
    val dateRangeEnd: Long,
    val totalFileSizeBytes: Long,
    val fileCount: Int,
    val notes: String = ""
)

/**
 * Exception thrown when mixing data tiers
 */
class DataTierMixingException(message: String) : Exception(message)

/**
 * Exception thrown when data quality is below threshold
 */
class InsufficientDataQualityException(
    val tier: DataTier,
    val actualQuality: Double,
    val requiredQuality: Double
) : Exception(
    "Data quality insufficient for ${tier.tierName}: " +
    "actual=$actualQuality, required=$requiredQuality"
)
