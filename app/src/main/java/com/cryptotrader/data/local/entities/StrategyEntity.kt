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
    val tradingMode: String = "INACTIVE", // INACTIVE, PAPER, LIVE (version 12+)
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
    val executionStatus: String? = null, // INACTIVE, MONITORING, ACTIVE_POSITION, PAUSED
    val lastCheckedTime: Long? = null,
    val triggeredAt: Long? = null,
    val lastExecutionError: String? = null,
    val executionCount: Int? = null,

    // Phase 3C: Performance Tracking & Strategy Lineage (version 9+)
    val metaAnalysisId: Long? = null,
    val sourceReportCount: Int = 0,
    val maxDrawdown: Double = 0.0,
    val avgWinAmount: Double = 0.0,
    val avgLossAmount: Double = 0.0,
    val profitFactor: Double = 0.0,
    val sharpeRatio: Double? = null,
    val largestWin: Double = 0.0,
    val largestLoss: Double = 0.0,
    val currentStreak: Int = 0,
    val longestWinStreak: Int = 0,
    val longestLossStreak: Int = 0,
    val performanceScore: Double = 0.0,
    val isTopPerformer: Int = 0, // 0 = false, 1 = true (Boolean as Int)
    val totalProfitPercent: Double = 0.0,

    // Soft-delete fields (version 16+)
    val isInvalid: Int = 0, // 0 = valid, 1 = invalid (Boolean as Int)
    val invalidReason: String? = null, // Reason for invalidation (e.g., "Hardcoded prices detected")
    val invalidatedAt: Long? = null // Timestamp when strategy was invalidated
)
