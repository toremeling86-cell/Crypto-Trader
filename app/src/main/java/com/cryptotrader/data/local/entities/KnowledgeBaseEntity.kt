package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Knowledge Base Entity - Cross-Strategy Learning System
 *
 * Stores aggregated insights from multiple meta-analyses and backtest runs
 * to enable AI-powered cross-strategy learning and pattern recognition.
 *
 * Version: 19+ (P0-2: Meta-Analysis Integration)
 *
 * Purpose:
 * - Aggregate learnings from successful and failed strategies
 * - Identify market regime patterns across different assets
 * - Track which indicators/conditions work best in specific market conditions
 * - Enable AI to recommend strategies based on historical performance patterns
 *
 * Flow:
 * 1. MetaAnalysis with learningEnabled=true generates insights
 * 2. Backtest results contribute performance data
 * 3. KnowledgeBase entries created/updated with aggregated learnings
 * 4. AI queries knowledge base when generating new strategies
 * 5. Continuous learning loop improves strategy recommendations over time
 */
@Entity(
    tableName = "knowledge_base",
    indices = [
        Index(value = ["category"]),
        Index(value = ["assetClass"]),
        Index(value = ["marketRegime"]),
        Index(value = ["confidence"]),
        Index(value = ["createdAt"]),
        Index(value = ["lastUpdatedAt"])
    ]
)
data class KnowledgeBaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Classification
    val category: String,              // PATTERN, INDICATOR, RISK_MANAGEMENT, MARKET_REGIME, COST_MODEL, etc.
    val assetClass: String = "CRYPTO", // CRYPTO, FOREX, STOCKS, COMMODITIES, ALL

    // Learning Content
    val title: String,                 // Short title for the learning (e.g., "RSI Divergence in Bull Markets")
    val insight: String,               // Detailed description of the learning
    val recommendation: String,        // Actionable recommendation based on this learning

    // Context
    val marketRegime: String? = null,  // BULL, BEAR, SIDEWAYS, VOLATILE, null = applies to all
    val tradingPairs: String? = null,  // JSON array of applicable pairs, null = all pairs
    val timeframes: String? = null,    // JSON array of applicable timeframes, null = all timeframes

    // Evidence & Confidence
    val confidence: Double,            // 0.0 - 1.0 (how confident are we in this learning)
    val evidenceCount: Int,            // Number of meta-analyses or backtest runs supporting this
    val successRate: Double? = null,   // Win rate when this insight was applied (if applicable)
    val avgReturn: Double? = null,     // Average return when this insight was applied (if applicable)

    // Source Tracking
    val sourceType: String,            // META_ANALYSIS, BACKTEST, MANUAL, AI_DISCOVERY
    val sourceIds: String,             // JSON array of source IDs (meta-analysis IDs, backtest run IDs, etc.)

    // Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "SYSTEM", // SYSTEM, USER, AI

    // Lifecycle
    val isActive: Boolean = true,      // If false, this learning is deprecated/invalidated
    val invalidatedAt: Long? = null,   // When this learning was invalidated
    val invalidationReason: String? = null // Why this learning was invalidated
)

/**
 * Knowledge Category Enum
 */
enum class KnowledgeCategory {
    PATTERN,           // Technical patterns (e.g., "Double bottom works 70% in bull markets")
    INDICATOR,         // Indicator-specific learnings (e.g., "RSI < 30 effective in ranging markets")
    RISK_MANAGEMENT,   // Risk/position sizing learnings
    MARKET_REGIME,     // Market condition patterns
    COST_MODEL,        // Trading cost insights
    STRATEGY_COMBO,    // Which strategies work well together
    TIMING,            // Entry/exit timing patterns
    CORRELATION,       // Asset correlation insights
    GENERAL            // General trading insights
}

/**
 * Source Type Enum
 */
enum class SourceType {
    META_ANALYSIS,  // Learning from meta-analysis of expert reports
    BACKTEST,       // Learning from backtest results
    MANUAL,         // Manually added by user
    AI_DISCOVERY    // Discovered by AI pattern recognition
}

/**
 * Market Regime Enum (for knowledge base filtering)
 */
enum class MarketRegimeType {
    BULL,
    BEAR,
    SIDEWAYS,
    VOLATILE,
    ALL            // Applies to all market regimes
}
