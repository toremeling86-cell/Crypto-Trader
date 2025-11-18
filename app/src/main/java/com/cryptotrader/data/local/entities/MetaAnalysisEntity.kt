package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Meta-Analysis Entity - Stores results of Opus 4.1 meta-analysis
 *
 * This entity captures the comprehensive analysis performed by Claude Opus 4.1
 * when synthesizing multiple expert reports into actionable trading strategies.
 *
 * Flow:
 * 1. User triggers meta-analysis of N unanalyzed expert reports
 * 2. Opus 4.1 analyzes all reports, finds consensus, contradictions
 * 3. Analysis results and recommended strategy saved here
 * 4. User approves → Strategy created and linked via strategyId
 * 5. Status updated to ACTIVE when strategy starts trading
 *
 * Version: 19+ (Added learningEnabled for knowledge base integration)
 */
@Entity(
    tableName = "meta_analyses",
    indices = [Index("timestamp"), Index("status"), Index("strategyId")]
)
data class MetaAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // Timestamp
    val timestamp: Long = System.currentTimeMillis(),

    // Input: Which reports were analyzed
    val reportIds: String, // JSON array of report IDs: "[1,2,3]"
    val reportCount: Int, // How many reports were analyzed

    // Temporal Analysis (Phase 3B)
    val timeframe: String? = "WEEKLY", // Analysis timeframe: DAILY, WEEKLY, MONTHLY, QUARTERLY, ALL_TIME
    val reportWeights: String? = null, // JSON map of report ID to temporal weight
    val oldestReportDate: Long? = null, // Date of oldest report included in analysis
    val newestReportDate: Long? = null, // Date of newest report included in analysis
    val temporalWeightingApplied: Int = 0, // 1 if temporal weighting was used, 0 otherwise (Boolean stored as Int)

    // Analysis Results
    val findings: String, // Opus 4.1's detailed meta-analysis (can be long)
    val consensus: String? = null, // Key points of agreement across reports
    val contradictions: String? = null, // Key points of disagreement
    val marketOutlook: String? = null, // Overall market sentiment: BULLISH/BEARISH/NEUTRAL

    // Recommended Strategy (JSON format)
    val recommendedStrategyJson: String, // Full strategy as JSON
    val strategyName: String, // Human-readable strategy name
    val tradingPairs: String, // JSON array: ["XBTUSD", "XETHZUSD"]

    // Confidence & Risk
    val confidence: Double, // 0.0 - 1.0 (Opus's confidence in the strategy)
    val riskLevel: String, // LOW, MEDIUM, HIGH, VERY_HIGH
    val expectedReturn: String? = null, // e.g., "15-25% monthly"

    // Status Tracking
    val status: String, // PENDING, COMPLETED, APPROVED, ACTIVE, REJECTED
    val strategyId: Long? = null, // FK to StrategyEntity if approved

    // User Interaction
    val approvedAt: Long? = null, // When user approved the strategy
    val rejectedAt: Long? = null, // When user rejected the strategy
    val rejectionReason: String? = null, // Why user rejected it

    // Opus 4.1 Metadata
    val opusModel: String = "claude-opus-4.1", // Model used for analysis
    val tokensUsed: Int? = null, // API token usage
    val analysisTimeMs: Long? = null, // How long the analysis took

    // Learning & Knowledge Base (version 19+)
    val learningEnabled: Boolean = true // If true, analysis contributes to knowledge base for cross-strategy learning
)

/**
 * Analysis Status enum (stored as String in database)
 */
enum class AnalysisStatus {
    PENDING,      // Analysis is in progress
    COMPLETED,    // Analysis finished, awaiting user review
    APPROVED,     // User approved the recommended strategy
    ACTIVE,       // Strategy is actively trading
    REJECTED      // User rejected the strategy
}

/**
 * Market Outlook enum
 */
enum class MarketOutlook {
    BULLISH,
    BEARISH,
    NEUTRAL,
    VOLATILE,
    UNCERTAIN
}

/**
 * Risk Level enum
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}
