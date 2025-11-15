package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
    val quantity: Double,

    // Entry
    val entryPrice: Double,
    val entryTradeId: String,
    val openedAt: Long,

    // Risk management
    val stopLossPrice: Double?,
    val takeProfitPrice: Double?,
    val stopLossOrderId: String?,
    val takeProfitOrderId: String?,

    // Exit
    val exitPrice: Double? = null,
    val exitTradeId: String? = null,
    val closedAt: Long? = null,
    val closeReason: String? = null, // "STOP_LOSS", "TAKE_PROFIT", "MANUAL", "STRATEGY_EXIT"

    // P&L
    val unrealizedPnL: Double = 0.0,
    val unrealizedPnLPercent: Double = 0.0,
    val realizedPnL: Double? = null,
    val realizedPnLPercent: Double? = null,

    // Status
    val status: String = "OPEN", // "OPEN", "CLOSED"

    // Tracking
    val lastUpdated: Long = System.currentTimeMillis()
)
