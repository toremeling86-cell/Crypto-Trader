package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Market correlation entity for tracking correlations between assets
 * Stores correlation coefficients between different trading pairs
 */
@Entity(
    tableName = "market_correlations",
    indices = [Index("timestamp"), Index("symbol1"), Index("symbol2")]
)
data class MarketCorrelationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol1: String, // First asset (e.g., "BTC/USD")
    val symbol2: String, // Second asset (e.g., "ETH/USD")
    val correlationValue: Double, // Pearson correlation coefficient (-1.0 to 1.0)
    val period: String, // "1H", "24H", "7D", "30D"
    val timestamp: Long = System.currentTimeMillis()
)
