package com.cryptotrader.domain.backtesting

data class ArtifactIndex(
    val runId: String,
    val timestamp: Long,
    val eventsPath: String?,
    val equityPath: String?,
    val summaryPath: String?
)
