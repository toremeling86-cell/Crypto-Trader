package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * Data Quarter Coverage Entity
 *
 * Tracks which quarters of historical data have been downloaded from cloud storage.
 * Used by CloudDataRepository to avoid re-downloading data.
 *
 * Separate from DataCoverageEntity which tracks overall quality metrics.
 * This entity is specifically for cloud download tracking.
 */
@Entity(
    tableName = "data_quarter_coverage",
    primaryKeys = ["asset", "timeframe", "dataTier", "quarter"],
    indices = [
        Index(value = ["asset", "timeframe"]),
        Index(value = ["dataTier"]),
        Index(value = ["lastUpdated"])
    ]
)
data class DataQuarterCoverageEntity(
    val asset: String,              // e.g., "XXBTZUSD"
    val timeframe: String,          // e.g., "1h", "1d"
    val dataTier: String,           // e.g., "TIER_1_PREMIUM", "TIER_2_STANDARD"
    val quarter: String,            // e.g., "2024-Q2", "2023-Q4"

    // Quarter metadata
    val startTime: Long,            // First timestamp in this quarter
    val endTime: Long,              // Last timestamp in this quarter
    val barCount: Int,              // Number of bars imported for this quarter

    // Download tracking
    val downloadedAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val source: String = "CLOUDFLARE_R2"  // Cloud storage source
)
