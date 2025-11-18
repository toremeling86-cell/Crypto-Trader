package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Data Quality Entity - Tracks data validation results
 *
 * Stores quality checks, gap detection, and validation results
 * Used to warn users about insufficient or low-quality data before backtesting
 *
 * Version: 13+ (Database migration required)
 */
@Entity(
    tableName = "data_quality",
    indices = [
        Index(value = ["asset", "timeframe"]),
        Index(value = ["validatedAt"]),
        Index(value = ["overallQualityScore"])
    ]
)
data class DataQualityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Link to data coverage
    val asset: String,              // e.g., "XXBTZUSD", "SOLUSD"
    val timeframe: String,          // e.g., "1m", "5m", "1h"
    val coverageStartTimestamp: Long, // Start of validated range
    val coverageEndTimestamp: Long,   // End of validated range

    // Quality metrics
    val overallQualityScore: Double, // 0.0 - 1.0 (1.0 = perfect quality)
    val completenessScore: Double,   // 0.0 - 1.0 (% of expected bars present)
    val consistencyScore: Double,    // 0.0 - 1.0 (OHLC validation, no price anomalies)
    val gapScore: Double,            // 0.0 - 1.0 (inverse of gap severity)

    // Gap detection
    val totalGaps: Int,              // Number of gaps detected
    val largestGapMs: Long,          // Largest gap in milliseconds
    val averageGapMs: Long,          // Average gap size in milliseconds
    val gapsDetails: String,         // JSON array of gaps: [{"start": 123456, "end": 789012, "durationMs": 345}]

    // Data validation issues
    val invalidBarsCount: Int,       // Bars with OHLC validation errors (high < low, etc.)
    val zeroVolumeBarsCount: Int,    // Bars with zero volume
    val duplicateBarsCount: Int,     // Duplicate timestamps
    val anomaliesCount: Int,         // Price anomalies (sudden spikes > 10%)
    val issuesDetails: String,       // JSON array of issues

    // Recommendations
    val isBacktestSuitable: Boolean, // True if quality is good enough for backtesting
    val warningMessage: String = "", // Warning message if quality is poor
    val recommendedMinDate: Long = 0,// Recommended start date for reliable backtests

    // Metadata
    val validatedAt: Long = System.currentTimeMillis(),
    val validationDurationMs: Long = 0
)
