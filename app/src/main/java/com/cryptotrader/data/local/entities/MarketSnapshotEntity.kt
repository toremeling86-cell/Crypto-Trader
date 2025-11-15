package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Market snapshot entity for live crypto price tracking
 * Stores price, volume, and change data for cryptocurrencies
 */
@Entity(
    tableName = "market_snapshots",
    indices = [Index("symbol"), Index("timestamp")]
)
data class MarketSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String, // "BTC/USD", "ETH/USD", "SOL/USD", etc.
    val price: Double,
    val volume24h: Double,
    val high24h: Double,
    val low24h: Double,
    val changePercent24h: Double,
    val timestamp: Long = System.currentTimeMillis()
)
