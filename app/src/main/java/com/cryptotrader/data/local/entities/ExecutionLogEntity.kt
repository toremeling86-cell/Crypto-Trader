package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "execution_logs",
    foreignKeys = [
        ForeignKey(
            entity = StrategyEntity::class,
            parentColumns = ["id"],
            childColumns = ["strategyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("strategyId"), Index("timestamp"), Index("eventType")]
)
data class ExecutionLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // Strategy relationship
    val strategyId: String,

    // Event details
    val eventType: String, // "CONDITION_MET", "ORDER_PLACED", "ORDER_FILLED", "STOP_TRIGGERED", "ERROR", etc.
    val timestamp: Long = System.currentTimeMillis(),
    val details: String, // JSON or text description
    val errorMessage: String? = null,

    // Related entities
    val relatedOrderId: String? = null,
    val relatedPositionId: String? = null,
    val relatedTradeId: String? = null
)
