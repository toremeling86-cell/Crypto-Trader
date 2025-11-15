package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Expert report entity for storing trading expert reports
 * Allows users to upload/paste markdown reports for Claude to analyze
 */
@Entity(
    tableName = "expert_reports",
    indices = [Index("uploadDate"), Index("category"), Index("isSentToClaude")]
)
data class ExpertReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String, // Markdown content of the report
    val author: String? = null, // Optional author name
    val source: String? = null, // Optional source/publication
    val category: String, // "MARKET_ANALYSIS", "TECHNICAL_ANALYSIS", "FUNDAMENTAL", "NEWS", "OTHER"
    val uploadDate: Long = System.currentTimeMillis(),
    val isSentToClaude: Boolean = false,
    val claudeAnalysisId: Long? = null, // FK to AIMarketAnalysisEntity if analyzed
    val tags: String? = null // JSON array of tags
)
