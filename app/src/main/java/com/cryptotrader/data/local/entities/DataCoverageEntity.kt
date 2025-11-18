package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * Data Coverage Entity - Tracks available historical data
 *
 * Metadata table showing which assets, timeframes, and date ranges are available
 * Used by AI chat to show data availability and by BacktestEngine to verify data coverage
 *
 * Version: 13+ (Database migration required)
 */
@Entity(
    tableName = "data_coverage",
    primaryKeys = ["asset", "timeframe"],
    indices = [
        Index(value = ["asset"]),
        Index(value = ["timeframe"]),
        Index(value = ["dataQualityScore"])
    ]
)
data class DataCoverageEntity(
    val asset: String,              // e.g., "XXBTZUSD", "SOLUSD", "ETHUSD"
    val timeframe: String,          // e.g., "1m", "5m", "15m", "1h", "4h", "1d"

    // Date range coverage
    val earliestTimestamp: Long,    // First available data point
    val latestTimestamp: Long,      // Last available data point
    val totalBars: Long,            // Total number of OHLC bars available
    val expectedBars: Long,         // Expected number based on timeframe

    // Data quality metrics
    val dataQualityScore: Double,   // 0.0 - 1.0 (1.0 = perfect, no gaps)
    val gapsCount: Int,             // Number of gaps detected
    val missingBarsCount: Long,     // Number of missing bars (expectedBars - totalBars)

    // Source tracking
    val primarySource: String,      // Primary data source: "KRAKEN_API", "CRYPTOLAKE", "FREEDOMBOT"
    val sources: String,            // JSON array of all sources: ["KRAKEN_API", "CRYPTOLAKE_CSV"]

    // Metadata
    val lastUpdated: Long = System.currentTimeMillis(),
    val lastImportedAt: Long = System.currentTimeMillis()
)
