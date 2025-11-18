package com.cryptotrader.domain.model

import com.cryptotrader.data.remote.claude.dto.StrategyConfig
import kotlinx.serialization.Serializable

/**
 * Domain model for Meta-Analysis results
 *
 * Represents the result of Claude Opus 4.1's comprehensive analysis
 * of multiple expert trading reports
 */
@Serializable
data class MetaAnalysis(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),

    // Input reports
    val reportIds: List<Long>,
    val reportCount: Int,

    // Temporal analysis (Phase 3B)
    val timeframe: AnalysisTimeframe = AnalysisTimeframe.WEEKLY,
    val oldestReportDate: Long? = null,
    val newestReportDate: Long? = null,
    val temporalWeightingApplied: Boolean = false,

    // Analysis results
    val findings: String,
    val consensus: String? = null,
    val contradictions: String? = null,
    val marketOutlook: MarketOutlook? = null,

    // Recommended strategy
    val recommendedStrategy: RecommendedStrategy,
    val strategyName: String,
    val tradingPairs: List<String>,

    // Confidence & risk
    val confidence: Double,
    val riskLevel: RiskLevel,
    val expectedReturn: String? = null,

    // Status
    val status: AnalysisStatus,
    val strategyId: Long? = null,

    // User interaction
    val approvedAt: Long? = null,
    val rejectedAt: Long? = null,
    val rejectionReason: String? = null,

    // Metadata
    val opusModel: String = "claude-opus-4.1",
    val tokensUsed: Int? = null,
    val analysisTimeMs: Long? = null
)

/**
 * Recommended trading strategy from meta-analysis
 */
@Serializable
data class RecommendedStrategy(
    val name: String,
    val description: String,
    val rationale: String,
    val tradingPairs: List<String>,
    val entryConditions: List<String>,
    val exitConditions: List<String>,
    val positionSizePercent: Double,
    val stopLossPercent: Double,
    val takeProfitPercent: Double,
    val riskLevel: RiskLevel,
    val confidenceScore: Double,
    val expectedReturn: String? = null,
    val keyInsights: List<String> = emptyList(),
    val riskFactors: List<String> = emptyList()
)

/**
 * Market outlook from meta-analysis
 */
@Serializable
enum class MarketOutlook(val displayName: String) {
    BULLISH("Bullish"),
    BEARISH("Bearish"),
    NEUTRAL("Neutral"),
    VOLATILE("Volatile"),
    UNCERTAIN("Uncertain");

    override fun toString(): String = name

    companion object {
        fun fromString(value: String): MarketOutlook {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                UNCERTAIN
            }
        }
    }
}

/**
 * Analysis status
 */
@Serializable
enum class AnalysisStatus(val displayName: String) {
    PENDING("Pending"),
    COMPLETED("Completed"),
    APPROVED("Approved"),
    ACTIVE("Active"),
    REJECTED("Rejected");

    override fun toString(): String = name

    companion object {
        fun fromString(value: String): AnalysisStatus {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                PENDING
            }
        }
    }
}

/**
 * Analysis timeframe for temporal weighting
 */
@Serializable
enum class AnalysisTimeframe(
    val displayName: String,
    val daysBack: Int,
    val description: String
) {
    DAILY("Daily", 1, "Last 24 hours - Ultra-short term signals"),
    WEEKLY("Weekly", 7, "Last 7 days - Short-term momentum"),
    MONTHLY("Monthly", 30, "Last 30 days - Medium-term trends"),
    QUARTERLY("Quarterly", 90, "Last 90 days - Long-term outlook"),
    ALL_TIME("All Reports", Int.MAX_VALUE, "All available reports");

    override fun toString(): String = name

    companion object {
        fun fromString(value: String): AnalysisTimeframe {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                WEEKLY
            }
        }
    }
}

/**
 * Extension function to convert RecommendedStrategy to StrategyConfig (for API)
 */
fun RecommendedStrategy.toStrategyConfig(): StrategyConfig {
    return StrategyConfig(
        id = java.util.UUID.randomUUID().toString(),
        name = name,
        description = description,
        indicators = emptyList(), // Will be populated from entry/exit conditions
        entryConditions = entryConditions,
        exitConditions = exitConditions,
        positionSizing = com.cryptotrader.data.remote.claude.dto.PositionSizing(
            type = "fixed_percentage",
            value = positionSizePercent
        ),
        riskManagement = com.cryptotrader.data.remote.claude.dto.RiskManagement(
            stopLossPercent = stopLossPercent,
            takeProfitPercent = takeProfitPercent,
            maxOpenPositions = 3
        ),
        tradingPairs = tradingPairs,
        riskLevel = riskLevel.toString(),
        confidenceScore = confidenceScore
    )
}
