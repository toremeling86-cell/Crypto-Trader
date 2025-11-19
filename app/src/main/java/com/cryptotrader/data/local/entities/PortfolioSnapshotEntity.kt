package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * Portfolio snapshot entity for historical tracking
 * Stores portfolio state at specific points in time
 *
 * BigDecimal Migration (Phase 2.9 - Database v20):
 * - BigDecimal columns added in migration 19→20
 * - Double fields kept for backward compatibility
 */
@Entity(tableName = "portfolio_snapshots")
data class PortfolioSnapshotEntity(
    @PrimaryKey val timestamp: Long,

    // Legacy Double fields (deprecated, kept for backward compatibility)
    val totalValue: Double,
    val totalPnL: Double,
    val totalPnLPercent: Double,

    // BigDecimal fields (version 20+) - exact calculations
    val totalValueDecimal: BigDecimal? = null,
    val totalPnLDecimal: BigDecimal? = null,

    val holdingsJson: String // JSON string of List<PortfolioHolding>
)
