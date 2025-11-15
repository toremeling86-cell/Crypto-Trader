package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "strategies")
data class StrategyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val entryConditions: String, // JSON serialized
    val exitConditions: String, // JSON serialized
    val positionSizePercent: Double, // % of portfolio (0-100)
    val stopLossPercent: Double, // % stop loss
    val takeProfitPercent: Double, // % take profit
    val tradingPairs: String, // JSON array of pairs (e.g., ["XXBTZUSD", "XETHZUSD"])
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastExecuted: Long? = null,
    val totalTrades: Int = 0,
    val successfulTrades: Int = 0,
    val failedTrades: Int = 0,
    val winRate: Double = 0.0,
    val totalProfit: Double = 0.0,
    val riskLevel: String = "MEDIUM", // LOW, MEDIUM, HIGH
    // AI Strategy fields
    val analysisReport: String? = null, // Claude's market analysis
    val approvalStatus: String = "APPROVED", // PENDING, APPROVED, REJECTED
    val source: String = "USER", // USER, AI_CLAUDE

    // Execution tracking fields (version 7+)
    val executionStatus: String = "INACTIVE", // INACTIVE, MONITORING, ACTIVE_POSITION, PAUSED
    val lastCheckedTime: Long? = null,
    val triggeredAt: Long? = null,
    val lastExecutionError: String? = null,
    val executionCount: Int = 0
)
