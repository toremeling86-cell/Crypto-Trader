package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * OHLC Bar Entity - Stores historical candlestick data
 *
 * Composite primary key: (asset, timeframe, timestamp)
 * Indexed for efficient querying by date range and asset
 *
 * Version: 13+ (Database migration required)
 * Version: 14+ (Added dataTier for quality separation)
 */
@Entity(
    tableName = "ohlc_bars",
    primaryKeys = ["asset", "timeframe", "timestamp"],
    indices = [
        Index(value = ["asset", "timeframe", "timestamp"], unique = true),
        Index(value = ["asset", "timestamp"]),
        Index(value = ["timestamp"]),
        Index(value = ["dataTier", "asset"]), // Query by tier
        Index(value = ["source", "dataTier"])  // Filter by source + tier
    ]
)
data class OHLCBarEntity(
    val asset: String,              // e.g., "XXBTZUSD", "SOLUSD", "ETHUSD"
    val timeframe: String,          // e.g., "1m", "5m", "15m", "1h", "4h", "1d"
    val timestamp: Long,            // Unix timestamp in milliseconds
    val open: Double,               // Opening price
    val high: Double,               // Highest price
    val low: Double,                // Lowest price
    val close: Double,              // Closing price
    val volume: Double,             // Trading volume
    val trades: Int = 0,            // Number of trades (if available)
    val source: String = "UNKNOWN", // Data source: "KRAKEN_API", "CRYPTOLAKE_CSV", "CRYPTOLAKE_PARQUET"
    val dataTier: String = "TIER_4_BASIC", // Data quality tier (version 14+)
    val importedAt: Long = System.currentTimeMillis() // When this data was imported
)
