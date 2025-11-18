package com.cryptotrader.diagnostics

import com.cryptotrader.domain.model.ApprovalStatus
import com.cryptotrader.domain.model.RiskLevel
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.domain.model.StrategySource

/**
 * Diagnostic-only strategy for CI smoke testing.
 * Hidden from standard strategy lists and used only in automated pipelines.
 */
fun diagnosticRsiStrategy(): Strategy {
    return Strategy(
        id = "diagnostic_rsi_strategy",
        name = "Diagnostic RSI",
        description = "CI-only RSI diagnostic strategy (hidden)",
        entryConditions = listOf("RSI < 30"),
        exitConditions = listOf("RSI > 70"),
        positionSizePercent = 5.0,
        stopLossPercent = 2.0,
        takeProfitPercent = 4.0,
        tradingPairs = listOf("XXBTZUSD"),
        isActive = true,
        approvalStatus = ApprovalStatus.APPROVED,
        source = StrategySource.USER,
        riskLevel = RiskLevel.LOW,
        analysisReport = "diagnostic=true"
    )
}
