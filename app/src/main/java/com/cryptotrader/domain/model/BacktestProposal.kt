package com.cryptotrader.domain.model

import kotlinx.serialization.Serializable

/**
 * Backtest Proposal - AI's suggestion for running a backtest
 *
 * This allows the user to review and approve/modify before execution.
 * Provides educational context about why specific data was chosen.
 */
@Serializable
data class BacktestProposal(
    val strategyId: String,
    val strategyName: String,

    // Proposed data selection
    val proposedDataTier: DataTier,
    val proposedAsset: String,
    val proposedTimeframe: String,
    val proposedStartDate: Long?,
    val proposedEndDate: Long?,

    // AI's reasoning (educational)
    val tierRationale: String,          // Why this tier?
    val timeframeRationale: String,     // Why this timeframe?
    val dateRangeRationale: String,     // Why this date range?
    val overallRecommendation: String,  // Summary of recommendations

    // Data availability info
    val availableDataTiers: List<DataTier>,
    val estimatedBarsCount: Long,
    val dataQualityScore: Double,

    // Risks and considerations
    val warnings: List<String> = emptyList(),
    val educationalNotes: List<String> = emptyList(),

    // Backtest parameters
    val startingBalance: Double = 10000.0,

    // Status
    val proposalId: String = java.util.UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Convert proposal to user-friendly markdown for display in chat
     */
    fun toMarkdown(): String {
        return buildString {
            appendLine("## 📊 Backtest Proposal for ${strategyName}")
            appendLine()
            appendLine("### Recommended Configuration")
            appendLine("- **Data Tier**: ${proposedDataTier.tierName} (Quality: ${proposedDataTier.qualityScore})")
            appendLine("- **Asset**: $proposedAsset")
            appendLine("- **Timeframe**: $proposedTimeframe")
            appendLine("- **Period**: ${formatDate(proposedStartDate)} → ${formatDate(proposedEndDate)}")
            appendLine("- **Starting Balance**: $$startingBalance")
            appendLine()

            appendLine("### 🎓 Why These Settings?")
            appendLine()
            appendLine("**Data Tier Choice:**")
            appendLine(tierRationale)
            appendLine()
            appendLine("**Timeframe Choice:**")
            appendLine(timeframeRationale)
            appendLine()
            appendLine("**Date Range:**")
            appendLine(dateRangeRationale)
            appendLine()

            if (warnings.isNotEmpty()) {
                appendLine("### ⚠️ Important Considerations")
                warnings.forEach { warning ->
                    appendLine("- $warning")
                }
                appendLine()
            }

            if (educationalNotes.isNotEmpty()) {
                appendLine("### 💡 Learning Notes")
                educationalNotes.forEach { note ->
                    appendLine("- $note")
                }
                appendLine()
            }

            appendLine("### 📈 Expected Data Quality")
            appendLine("- Estimated bars: ~$estimatedBarsCount")
            appendLine("- Quality score: ${String.format("%.1f%%", dataQualityScore * 100)}")
            appendLine()

            appendLine("**Overall Recommendation:**")
            appendLine(overallRecommendation)
        }
    }

    private fun formatDate(timestamp: Long?): String {
        if (timestamp == null) return "Latest"
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(timestamp))
    }
}

/**
 * User's decision on backtest proposal
 */
@Serializable
data class BacktestDecision(
    val proposalId: String,
    val approved: Boolean,
    val userNote: String? = null,

    // User modifications (if not approved as-is)
    val modifiedDataTier: DataTier? = null,
    val modifiedAsset: String? = null,
    val modifiedTimeframe: String? = null,
    val modifiedStartDate: Long? = null,
    val modifiedEndDate: Long? = null,
    val modifiedStartingBalance: Double? = null,

    val decidedAt: Long = System.currentTimeMillis()
) {
    val hasModifications: Boolean
        get() = modifiedDataTier != null ||
                modifiedAsset != null ||
                modifiedTimeframe != null ||
                modifiedStartDate != null ||
                modifiedEndDate != null ||
                modifiedStartingBalance != null
}
