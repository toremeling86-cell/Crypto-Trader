package com.cryptotrader.domain.model

import java.time.Instant

/**
 * Represents a managed dataset for backtesting
 *
 * Allows users to select specific data sources (e.g., "ETH 2023", "Bull Market 2021")
 * instead of just generic "historical data".
 */
data class ManagedDataset(
    val id: String,
    val name: String,
    val description: String,
    val asset: String,
    val timeframe: String,
    val dataTier: DataTier,
    val startDate: Long,
    val endDate: Long,
    val barCount: Int,
    val source: DatasetSource,
    val filePath: String? = null, // For local CSV imports
    val isFavorite: Boolean = false,
    val lastUsed: Long = 0
)

enum class DatasetSource {
    KRAKEN_API,
    LOCAL_CSV,
    GENERATED_SYNTHETIC,
    CRYPTOLAKE_IMPORT
}
