package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Technical Indicator Entity - Stores pre-calculated technical indicators
 *
 * Links to OHLC bars via composite key (asset, timeframe, timestamp)
 * Stores various indicator types: RSI, MACD, EMA, SMA, Bollinger Bands, etc.
 *
 * Version: 13+ (Database migration required)
 */
@Entity(
    tableName = "technical_indicators",
    indices = [
        Index(value = ["asset", "timeframe", "timestamp"]),
        Index(value = ["indicatorType", "asset", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class TechnicalIndicatorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Link to OHLC bar
    val asset: String,              // e.g., "XXBTZUSD", "SOLUSD"
    val timeframe: String,          // e.g., "1m", "5m", "1h"
    val timestamp: Long,            // Unix timestamp matching OHLC bar

    // Indicator details
    val indicatorType: String,      // e.g., "RSI", "MACD", "EMA_20", "SMA_50", "BB_UPPER"
    val value: Double,              // Indicator value
    val parameters: String = "",    // JSON string of indicator parameters (e.g., {"period": 14, "smoothing": 3})

    // Metadata
    val source: String = "UNKNOWN", // Data source: "CALCULATED", "CRYPTOLAKE_PARQUET", "FREEDOMBOT"
    val calculatedAt: Long = System.currentTimeMillis()
)
