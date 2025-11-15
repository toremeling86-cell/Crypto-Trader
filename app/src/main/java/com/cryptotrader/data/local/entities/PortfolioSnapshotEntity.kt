package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Portfolio snapshot entity for historical tracking
 * Stores portfolio state at specific points in time
 */
@Entity(tableName = "portfolio_snapshots")
data class PortfolioSnapshotEntity(
    @PrimaryKey val timestamp: Long,
    val totalValue: Double,
    val totalPnL: Double,
    val totalPnLPercent: Double,
    val holdingsJson: String // JSON string of List<PortfolioHolding>
)
