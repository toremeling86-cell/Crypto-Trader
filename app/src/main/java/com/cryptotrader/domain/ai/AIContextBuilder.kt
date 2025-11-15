package com.cryptotrader.domain.ai

import com.cryptotrader.data.repository.KrakenRepository
import com.cryptotrader.data.repository.MarketSnapshotRepository
import com.cryptotrader.data.repository.PortfolioRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.text.NumberFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds comprehensive context for Claude AI
 * Includes: Market data, Portfolio, Trades, Strategies
 *
 * This is the BRAIN of the AI system - giving Claude full awareness
 */
@Singleton
class AIContextBuilder @Inject constructor(
    private val marketSnapshotRepository: MarketSnapshotRepository,
    private val krakenRepository: KrakenRepository,
    private val portfolioRepository: PortfolioRepository
) {

    /**
     * Build full context string for Claude API calls
     * This runs on EVERY chat message to give Claude up-to-date info
     */
    suspend fun buildFullContext(
        includeMarketData: Boolean = true,
        includePortfolio: Boolean = true,
        includeTrades: Boolean = true
    ): String {
        val sections = mutableListOf<String>()

        try {
            // 1. MARKET DATA - Live prices from Kraken
            if (includeMarketData) {
                val marketContext = buildMarketContext()
                if (marketContext.isNotEmpty()) {
                    sections.add(marketContext)
                }
            }

            // 2. PORTFOLIO - User's holdings and performance
            if (includePortfolio) {
                val portfolioContext = buildPortfolioContext()
                if (portfolioContext.isNotEmpty()) {
                    sections.add(portfolioContext)
                }
            }

            // 3. RECENT TRADES - Last 10 trades for learning patterns
            if (includeTrades) {
                val tradesContext = buildTradesContext()
                if (tradesContext.isNotEmpty()) {
                    sections.add(tradesContext)
                }
            }

            val fullContext = sections.joinToString("\n\n")
            Timber.d("Built AI context: ${fullContext.length} chars, ${sections.size} sections")
            return fullContext

        } catch (e: Exception) {
            Timber.e(e, "Error building AI context")
            return "# CONTEXT UNAVAILABLE\nError loading market/portfolio data. Proceeding without context."
        }
    }

    /**
     * MARKET DATA - Live crypto prices
     */
    private suspend fun buildMarketContext(): String {
        val snapshots = marketSnapshotRepository.getLatestSnapshots(
            MarketSnapshotRepository.DEFAULT_WATCHLIST
        ).first()

        if (snapshots.isEmpty()) {
            return ""
        }

        val lines = mutableListOf<String>()
        lines.add("# LIVE MARKET DATA")
        lines.add("Current cryptocurrency prices from Kraken:")
        lines.add("")

        snapshots.sortedByDescending { it.changePercent24h }.forEach { snapshot ->
            val trend = if (snapshot.changePercent24h >= 0) "↑" else "↓"
            val sign = if (snapshot.changePercent24h >= 0) "+" else ""
            lines.add(
                "- ${snapshot.getBaseCurrency()}: ${formatPrice(snapshot.price)} " +
                "($sign${String.format("%.2f", snapshot.changePercent24h)}% 24h) $trend"
            )
        }

        lines.add("")
        lines.add("Market snapshot time: ${formatTimestamp(System.currentTimeMillis())}")

        return lines.joinToString("\n")
    }

    /**
     * PORTFOLIO - User's holdings and P&L
     */
    private suspend fun buildPortfolioContext(): String {
        val snapshotResult = portfolioRepository.getCurrentSnapshot()

        if (snapshotResult.isFailure) {
            return "# USER PORTFOLIO\nUnable to load portfolio data"
        }

        val portfolio = snapshotResult.getOrNull() ?: return ""

        val lines = mutableListOf<String>()
        lines.add("# USER PORTFOLIO")
        lines.add("")
        lines.add("Total Portfolio Value: ${formatPrice(portfolio.totalValue)}")
        lines.add("Total P&L: ${formatPrice(portfolio.totalPnL)} (${formatPercent(portfolio.totalPnLPercent)})")
        lines.add("")

        if (portfolio.holdings.isNotEmpty()) {
            lines.add("Current Holdings:")
            portfolio.holdings.forEach { holding ->
                val allocation = (holding.currentValue / portfolio.totalValue * 100)
                lines.add(
                    "- ${holding.asset}: ${String.format("%.6f", holding.amount)} " +
                    "(Value: ${formatPrice(holding.currentValue)}, " +
                    "P&L: ${formatPrice(holding.unrealizedPnL)}, " +
                    "Allocation: ${String.format("%.1f", allocation)}%)"
                )
            }
        } else {
            lines.add("No active holdings (100% cash)")
        }

        return lines.joinToString("\n")
    }

    /**
     * TRADES - Recent trade history for pattern learning
     */
    private suspend fun buildTradesContext(): String {
        val trades = krakenRepository.getRecentTrades(10).first()

        if (trades.isEmpty()) {
            return ""
        }

        val lines = mutableListOf<String>()
        lines.add("# RECENT TRADE HISTORY")
        lines.add("Last 10 trades (for learning user's trading patterns):")
        lines.add("")

        trades.forEach { trade ->
            val action = trade.type.toString().uppercase()
            val profitStr = if (trade.profit != null && trade.profit != 0.0) {
                " | P&L: ${formatPrice(trade.profit!!)}"
            } else ""

            lines.add(
                "- ${formatTimestamp(trade.timestamp)}: $action ${String.format("%.6f", trade.volume)} " +
                "${trade.pair} @ ${formatPrice(trade.price)}$profitStr"
            )
        }

        return lines.joinToString("\n")
    }

    /**
     * Build lightweight context for quick responses
     * Used for simple questions that don't need full context
     */
    suspend fun buildLightweightContext(): String {
        return buildFullContext(
            includeMarketData = true,
            includePortfolio = true,
            includeTrades = false
        )
    }

    /**
     * Build context specifically for market analysis
     */
    suspend fun buildMarketAnalysisContext(): String {
        return buildFullContext(
            includeMarketData = true,
            includePortfolio = false,
            includeTrades = false
        )
    }

    // Formatting helpers
    private fun formatPrice(price: Double): String {
        return NumberFormat.getCurrencyInstance(Locale.US).format(price)
    }

    private fun formatPercent(percent: Double): String {
        val sign = if (percent >= 0) "+" else ""
        return "$sign%.2f%%".format(percent)
    }

    private fun formatTimestamp(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return String.format(
            "%02d/%02d/%04d %02d:%02d",
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
    }
}
