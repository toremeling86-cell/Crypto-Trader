package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.UUID

@Entity(
    tableName = "positions",
    foreignKeys = [
        ForeignKey(
            entity = StrategyEntity::class,
            parentColumns = ["id"],
            childColumns = ["strategyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("strategyId"), Index("pair"), Index("status"), Index("openedAt")]
)
data class PositionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // Strategy relationship
    val strategyId: String,

    // Position details
    val pair: String,
    val type: String, // "LONG" or "SHORT"

    // Legacy Double fields (deprecated, kept for backward compatibility)
    val quantity: Double,
    val entryPrice: Double,
    val stopLossPrice: Double?,
    val takeProfitPrice: Double?,
    val exitPrice: Double? = null,
    val unrealizedPnL: Double = 0.0,
    val unrealizedPnLPercent: Double = 0.0,
    val realizedPnL: Double? = null,
    val realizedPnLPercent: Double? = null,

    // BigDecimal fields (version 21+) - exact calculations
    // NOTE: Requires database migration 20→21
    val quantityDecimal: BigDecimal? = null,
    val entryPriceDecimal: BigDecimal? = null,
    val stopLossPriceDecimal: BigDecimal? = null,
    val takeProfitPriceDecimal: BigDecimal? = null,
    val exitPriceDecimal: BigDecimal? = null,
    val unrealizedPnLDecimal: BigDecimal? = null,
    val unrealizedPnLPercentDecimal: BigDecimal? = null,
    val realizedPnLDecimal: BigDecimal? = null,
    val realizedPnLPercentDecimal: BigDecimal? = null,

    // Entry
    val entryTradeId: String,
    val openedAt: Long,

    // Risk management
    val stopLossOrderId: String?,
    val takeProfitOrderId: String?,

    // Exit
    val exitTradeId: String? = null,
    val closedAt: Long? = null,
    val closeReason: String? = null, // "STOP_LOSS", "TAKE_PROFIT", "MANUAL", "STRATEGY_EXIT"

    // Status
    val status: String = "OPEN", // "OPEN", "CLOSED"

    // Tracking
    val lastUpdated: Long = System.currentTimeMillis()
)
