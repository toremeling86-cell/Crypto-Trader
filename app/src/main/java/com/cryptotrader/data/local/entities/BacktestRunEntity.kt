package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Backtest Run Entity - Stores backtest execution results
 *
 * Links strategy to the data coverage used for testing
 * Enables AI chat to answer: "Which data was this strategy tested on?"
 * Tracks performance metrics for historical comparison
 *
 * Version: 13+ (Database migration required)
 * Version: 14+ (Added dataTier tracking - CRITICAL for quality control)
 * Version: 17+ (Added data provenance tracking - P1-4)
 */
@Entity(
    tableName = "backtest_runs",
    indices = [
        Index(value = ["strategyId"]),
        Index(value = ["asset", "timeframe"]),
        Index(value = ["executedAt"]),
        Index(value = ["winRate"]),
        Index(value = ["totalPnLPercent"]),
        Index(value = ["dataTier"]),           // Query by tier (version 14+)
        Index(value = ["dataTier", "status"])  // Filter tier + status (version 14+)
    ]
)
data class BacktestRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Link to strategy
    val strategyId: String,         // Foreign key to strategies table

    // Data coverage used
    val asset: String,              // e.g., "XXBTZUSD", "SOLUSD"
    val timeframe: String,          // e.g., "1m", "5m", "1h"
    val startTimestamp: Long,       // Backtest start time
    val endTimestamp: Long,         // Backtest end time
    val totalBarsUsed: Long,        // Number of OHLC bars used in backtest

    // Backtest results
    val totalTrades: Int,           // Number of trades executed
    val winningTrades: Int,         // Number of profitable trades
    val losingTrades: Int,          // Number of losing trades
    val winRate: Double,            // Win rate percentage
    val totalPnL: Double,           // Total profit/loss in dollars
    val totalPnLPercent: Double,    // Total return percentage
    val sharpeRatio: Double,        // Risk-adjusted return
    val maxDrawdown: Double,        // Maximum drawdown percentage
    val profitFactor: Double,       // Gross profit / gross loss

    // Validation status
    val status: String,             // "EXCELLENT", "GOOD", "ACCEPTABLE", "FAILED"
    val dataQualityScore: Double,   // Quality of data used (0.0 - 1.0)

    // Data tier tracking (version 14+)
    val dataTier: String = "TIER_4_BASIC", // Data quality tier used for backtest
    val tierValidated: Boolean = false,     // True if tier consistency was validated

    // Metadata
    val dataSource: String,         // "KRAKEN_API", "CRYPTOLAKE", "FREEDOMBOT", "MIXED"
    val executedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,       // Backtest execution time in milliseconds

    // Data provenance (version 17+)
    val dataFileHashes: String = "[]",      // JSON array of SHA-256 hashes for input files
    val parserVersion: String = "",         // Semver of data parser (e.g., "1.0.0")
    val engineVersion: String = ""          // Semver of backtest engine (e.g., "1.0.0")
)
