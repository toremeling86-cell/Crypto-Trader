package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trades",
    foreignKeys = [
        ForeignKey(
            entity = StrategyEntity::class,
            parentColumns = ["id"],
            childColumns = ["strategyId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("strategyId"), Index("timestamp"), Index("pair")]
)
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,
    val pair: String,
    val type: String, // "buy" or "sell"
    val price: Double,
    val volume: Double,
    val cost: Double,
    val fee: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val strategyId: String? = null,
    val status: String = "executed", // executed, pending, failed, cancelled
    val profit: Double? = null, // Calculated profit/loss
    val notes: String? = null
)
