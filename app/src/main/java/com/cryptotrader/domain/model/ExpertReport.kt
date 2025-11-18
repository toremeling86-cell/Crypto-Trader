package com.cryptotrader.domain.model

/**
 * Domain model for expert trading reports
 *
 * Represents a trading expert's analysis report that can be uploaded
 * for Claude to analyze and synthesize into trading strategies
 */
data class ExpertReport(
    val id: Long = 0,
    val title: String,
    val content: String,
    val author: String? = null,
    val source: String? = null,
    val category: ReportCategory,
    val uploadDate: Long = System.currentTimeMillis(),

    // File information
    val filePath: String? = null,
    val filename: String? = null,
    val fileSize: Long = 0,

    // Meta-analysis tracking
    val analyzed: Boolean = false,
    val metaAnalysisId: Long? = null,

    // Tags for categorization
    val tags: List<String> = emptyList(),

    // Smart analysis fields (Phase 3A)
    val sentiment: ReportSentiment? = null,
    val sentimentScore: Double? = null, // -1.0 to 1.0
    val assets: List<String> = emptyList(), // ["BTC", "ETH", "SOL"]
    val tradingPairs: List<String> = emptyList(), // ["BTC/USD", "ETH/USD"]
    val publishedDate: Long? = null, // Actual publication date from content
    val usedInStrategies: Int = 0, // How many strategies reference this
    val impactScore: Double? = null // 0.0-1.0: Influence on winning strategies
)

/**
 * Categories for expert reports
 */
enum class ReportCategory(val displayName: String) {
    MARKET_ANALYSIS("Market Analysis"),
    TECHNICAL_ANALYSIS("Technical Analysis"),
    FUNDAMENTAL("Fundamental Analysis"),
    NEWS("News & Events"),
    SENTIMENT("Market Sentiment"),
    OTHER("Other");

    override fun toString(): String = name

    companion object {
        fun fromString(value: String): ReportCategory {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                OTHER
            }
        }
    }
}

/**
 * Market sentiment from expert report
 */
enum class ReportSentiment(val displayName: String, val color: String) {
    BULLISH("Bullish", "Green"),
    BEARISH("Bearish", "Red"),
    NEUTRAL("Neutral", "Gray");

    override fun toString(): String = name

    companion object {
        fun fromString(value: String): ReportSentiment {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                NEUTRAL
            }
        }
    }
}
