package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Expert report entity for storing trading expert reports
 * Allows users to upload/paste markdown reports for Claude to analyze
 *
 * Updated for Meta-Analysis System (Phase 3):
 * - Supports file-based reports from /CryptoTrader/ExpertReports/
 * - Tracks analysis status for Opus 4.1 meta-analysis
 * - Links to MetaAnalysisEntity when analyzed
 */
@Entity(
    tableName = "expert_reports",
    indices = [Index("uploadDate"), Index("category"), Index("analyzed"), Index("metaAnalysisId")]
)
data class ExpertReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String, // Markdown content of the report
    val author: String? = null, // Optional author name
    val source: String? = null, // Optional source/publication
    val category: String, // "MARKET_ANALYSIS", "TECHNICAL_ANALYSIS", "FUNDAMENTAL", "NEWS", "OTHER"
    val uploadDate: Long = System.currentTimeMillis(),

    // File-based report fields
    val filePath: String? = null, // Path to .md file if uploaded from filesystem
    val filename: String? = null, // Original filename
    val fileSize: Long = 0, // File size in bytes for validation

    // Meta-Analysis tracking
    val analyzed: Boolean = false, // True if included in meta-analysis
    val metaAnalysisId: Long? = null, // FK to MetaAnalysisEntity

    // Tags for categorization and search
    val tags: String? = null, // JSON array of tags: ["BTC", "bullish", "Q1-2025"]

    // Smart Analysis fields (Phase 3A)
    val sentiment: String? = null, // "BULLISH", "BEARISH", "NEUTRAL"
    val sentimentScore: Double? = null, // -1.0 (very bearish) to 1.0 (very bullish)
    val assets: String? = null, // JSON array: ["BTC", "ETH", "SOL"]
    val tradingPairs: String? = null, // JSON array: ["BTC/USD", "ETH/USD"]
    val publishedDate: Long? = null, // Actual publication date from content (vs uploadDate)
    val usedInStrategies: Int = 0, // How many strategies reference this report
    val impactScore: Double? = null, // 0.0-1.0: How much this influenced winning strategies

    // Legacy fields (deprecated, kept for migration)
    @Deprecated("Use analyzed instead")
    val isSentToClaude: Boolean = false,
    @Deprecated("Use metaAnalysisId instead")
    val claudeAnalysisId: Long? = null
)
