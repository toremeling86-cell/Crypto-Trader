package com.cryptotrader.domain.model

import com.cryptotrader.utils.toBigDecimalMoney
import java.math.BigDecimal

/**
 * Portfolio state with balance and performance metrics
 *
 * BigDecimal Migration (Phase 2.9):
 * - All monetary fields now use BigDecimal for exact calculations
 * - Double fields deprecated but kept for backward compatibility
 * - New code should use *Decimal fields exclusively
 */
data class Portfolio(
    // Total value - exact decimal arithmetic
    @Deprecated("Use totalValueDecimal for exact calculations", ReplaceWith("totalValueDecimal"))
    val totalValue: Double,
    val totalValueDecimal: BigDecimal = totalValue.toBigDecimalMoney(),

    // Available balance - exact decimal arithmetic
    @Deprecated("Use availableBalanceDecimal for exact calculations", ReplaceWith("availableBalanceDecimal"))
    val availableBalance: Double,
    val availableBalanceDecimal: BigDecimal = availableBalance.toBigDecimalMoney(),

    val balances: Map<String, AssetBalance>,

    // Profit - exact decimal arithmetic
    @Deprecated("Use totalProfitDecimal for exact calculations", ReplaceWith("totalProfitDecimal"))
    val totalProfit: Double,
    val totalProfitDecimal: BigDecimal = totalProfit.toBigDecimalMoney(),

    @Deprecated("Use totalProfitPercentDecimal for exact calculations", ReplaceWith("totalProfitPercentDecimal"))
    val totalProfitPercent: Double,
    val totalProfitPercentDecimal: BigDecimal = totalProfitPercent.toBigDecimalMoney(),

    @Deprecated("Use dayProfitDecimal for exact calculations", ReplaceWith("dayProfitDecimal"))
    val dayProfit: Double,
    val dayProfitDecimal: BigDecimal = dayProfit.toBigDecimalMoney(),

    @Deprecated("Use dayProfitPercentDecimal for exact calculations", ReplaceWith("dayProfitPercentDecimal"))
    val dayProfitPercent: Double,
    val dayProfitPercentDecimal: BigDecimal = dayProfitPercent.toBigDecimalMoney(),

    val openPositions: Int,
    val timestamp: Long = System.currentTimeMillis(),

    // EUR values (calculated from USD values using EUR/USD rate) - exact decimal arithmetic
    @Deprecated("Use totalValueEURDecimal for exact calculations", ReplaceWith("totalValueEURDecimal"))
    val totalValueEUR: Double = 0.0,
    val totalValueEURDecimal: BigDecimal = totalValueEUR.toBigDecimalMoney(),

    @Deprecated("Use availableBalanceEURDecimal for exact calculations", ReplaceWith("availableBalanceEURDecimal"))
    val availableBalanceEUR: Double = 0.0,
    val availableBalanceEURDecimal: BigDecimal = availableBalanceEUR.toBigDecimalMoney(),

    @Deprecated("Use totalProfitEURDecimal for exact calculations", ReplaceWith("totalProfitEURDecimal"))
    val totalProfitEUR: Double = 0.0,
    val totalProfitEURDecimal: BigDecimal = totalProfitEUR.toBigDecimalMoney(),

    @Deprecated("Use dayProfitEURDecimal for exact calculations", ReplaceWith("dayProfitEURDecimal"))
    val dayProfitEUR: Double = 0.0,
    val dayProfitEURDecimal: BigDecimal = dayProfitEUR.toBigDecimalMoney(),

    @Deprecated("Use eurUsdRateDecimal for exact calculations", ReplaceWith("eurUsdRateDecimal"))
    val eurUsdRate: Double = 1.08, // Current EUR/USD exchange rate used for conversion
    val eurUsdRateDecimal: BigDecimal = eurUsdRate.toBigDecimalMoney(),

    // NOK values (calculated from USD values using USD/NOK rate) - exact decimal arithmetic
    @Deprecated("Use totalValueNOKDecimal for exact calculations", ReplaceWith("totalValueNOKDecimal"))
    val totalValueNOK: Double = 0.0,
    val totalValueNOKDecimal: BigDecimal = totalValueNOK.toBigDecimalMoney(),

    @Deprecated("Use availableBalanceNOKDecimal for exact calculations", ReplaceWith("availableBalanceNOKDecimal"))
    val availableBalanceNOK: Double = 0.0,
    val availableBalanceNOKDecimal: BigDecimal = availableBalanceNOK.toBigDecimalMoney(),

    @Deprecated("Use totalProfitNOKDecimal for exact calculations", ReplaceWith("totalProfitNOKDecimal"))
    val totalProfitNOK: Double = 0.0,
    val totalProfitNOKDecimal: BigDecimal = totalProfitNOK.toBigDecimalMoney(),

    @Deprecated("Use dayProfitNOKDecimal for exact calculations", ReplaceWith("dayProfitNOKDecimal"))
    val dayProfitNOK: Double = 0.0,
    val dayProfitNOKDecimal: BigDecimal = dayProfitNOK.toBigDecimalMoney(),

    @Deprecated("Use usdNokRateDecimal for exact calculations", ReplaceWith("usdNokRateDecimal"))
    val usdNokRate: Double = 10.50, // Current USD/NOK exchange rate used for conversion
    val usdNokRateDecimal: BigDecimal = usdNokRate.toBigDecimalMoney()
)

/**
 * Asset balance with BigDecimal support
 */
data class AssetBalance(
    val asset: String,

    @Deprecated("Use balanceDecimal for exact calculations", ReplaceWith("balanceDecimal"))
    val balance: Double,
    val balanceDecimal: BigDecimal = balance.toBigDecimalMoney(),

    @Deprecated("Use valueInUSDDecimal for exact calculations", ReplaceWith("valueInUSDDecimal"))
    val valueInUSD: Double,
    val valueInUSDDecimal: BigDecimal = valueInUSD.toBigDecimalMoney(),

    @Deprecated("Use percentOfPortfolioDecimal for exact calculations", ReplaceWith("percentOfPortfolioDecimal"))
    val percentOfPortfolio: Double,
    val percentOfPortfolioDecimal: BigDecimal = percentOfPortfolio.toBigDecimalMoney(),

    @Deprecated("Use valueInEURDecimal for exact calculations", ReplaceWith("valueInEURDecimal"))
    val valueInEUR: Double = 0.0, // Value in EUR (calculated from USD value)
    val valueInEURDecimal: BigDecimal = valueInEUR.toBigDecimalMoney()
)

/**
 * Market ticker data
 */
data class MarketTicker(
    val pair: String,
    val ask: Double,
    val bid: Double,
    val last: Double,
    val volume24h: Double,
    val high24h: Double,
    val low24h: Double,
    val change24h: Double,
    val changePercent24h: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Order book entry
 */
data class OrderBookEntry(
    val price: Double,
    val volume: Double
)

data class OrderBook(
    val pair: String,
    val asks: List<OrderBookEntry>,
    val bids: List<OrderBookEntry>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Portfolio holding - individual asset in portfolio
 *
 * BigDecimal Migration (Phase 2.9):
 * All monetary fields use BigDecimal for exact calculations
 */
data class PortfolioHolding(
    val asset: String,
    val assetName: String,

    @Deprecated("Use amountDecimal for exact calculations", ReplaceWith("amountDecimal"))
    val amount: Double,
    val amountDecimal: BigDecimal = amount.toBigDecimalMoney(),

    @Deprecated("Use currentPriceDecimal for exact calculations", ReplaceWith("currentPriceDecimal"))
    val currentPrice: Double,
    val currentPriceDecimal: BigDecimal = currentPrice.toBigDecimalMoney(),

    @Deprecated("Use currentValueDecimal for exact calculations", ReplaceWith("currentValueDecimal"))
    val currentValue: Double,
    val currentValueDecimal: BigDecimal = currentValue.toBigDecimalMoney(),

    @Deprecated("Use percentOfPortfolioDecimal for exact calculations", ReplaceWith("percentOfPortfolioDecimal"))
    val percentOfPortfolio: Double,
    val percentOfPortfolioDecimal: BigDecimal = percentOfPortfolio.toBigDecimalMoney(),

    @Deprecated("Use costBasisDecimal for exact calculations", ReplaceWith("costBasisDecimal"))
    val costBasis: Double = 0.0,
    val costBasisDecimal: BigDecimal = costBasis.toBigDecimalMoney(),

    @Deprecated("Use unrealizedPnLDecimal for exact calculations", ReplaceWith("unrealizedPnLDecimal"))
    val unrealizedPnL: Double = 0.0,
    val unrealizedPnLDecimal: BigDecimal = unrealizedPnL.toBigDecimalMoney(),

    @Deprecated("Use unrealizedPnLPercentDecimal for exact calculations", ReplaceWith("unrealizedPnLPercentDecimal"))
    val unrealizedPnLPercent: Double = 0.0,
    val unrealizedPnLPercentDecimal: BigDecimal = unrealizedPnLPercent.toBigDecimalMoney(),

    val assetType: AssetType = AssetType.CRYPTO
)

enum class AssetType {
    CRYPTO,
    FIAT,
    STOCK,
    OTHER
}

/**
 * Portfolio snapshot for historical tracking
 *
 * BigDecimal Migration (Phase 2.9):
 * All monetary fields use BigDecimal for exact calculations
 */
data class PortfolioSnapshot(
    val timestamp: Long,

    @Deprecated("Use totalValueDecimal for exact calculations", ReplaceWith("totalValueDecimal"))
    val totalValue: Double,
    val totalValueDecimal: BigDecimal = totalValue.toBigDecimalMoney(),

    @Deprecated("Use totalPnLDecimal for exact calculations", ReplaceWith("totalPnLDecimal"))
    val totalPnL: Double,
    val totalPnLDecimal: BigDecimal = totalPnL.toBigDecimalMoney(),

    @Deprecated("Use totalPnLPercentDecimal for exact calculations", ReplaceWith("totalPnLPercentDecimal"))
    val totalPnLPercent: Double,
    val totalPnLPercentDecimal: BigDecimal = totalPnLPercent.toBigDecimalMoney(),

    val holdings: List<PortfolioHolding>
)

/**
 * Performance metrics for portfolio
 */
data class PerformanceMetrics(
    val totalReturn: Double,
    val totalReturnPercent: Double,
    val roi: Double,
    val dailyPnL: Double,
    val dailyPnLPercent: Double,
    val weeklyReturn: Double,
    val monthlyReturn: Double,
    val yearlyReturn: Double,
    val allTimeReturn: Double
)

/**
 * Chart data point for performance visualization
 */
data class ChartPoint(
    val timestamp: Long,
    val value: Double
)

enum class TimePeriod {
    ONE_DAY,
    ONE_WEEK,
    ONE_MONTH,
    THREE_MONTHS,
    SIX_MONTHS,
    ONE_YEAR,
    ALL_TIME
}

/**
 * Portfolio Analytics - advanced metrics
 */
data class PortfolioAnalytics(
    val sharpeRatio: Double,
    val maxDrawdown: Double,
    val maxDrawdownPercent: Double,
    val winRate: Double,
    val profitFactor: Double,
    val bestTrade: Trade?,
    val worstTrade: Trade?,
    val avgHoldTime: Long,
    val monthlyReturns: Map<String, Double>, // "2025-01" -> 5.2%
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val avgWin: Double,
    val avgLoss: Double,
    val largestWin: Double,
    val largestLoss: Double
)

/**
 * Risk metrics for portfolio
 */
data class RiskMetrics(
    val diversificationScore: Double, // 0-100, higher is better
    val exposureByAsset: Map<String, Double>, // Asset -> percentage
    val correlationMatrix: Map<Pair<String, String>, Double>, // (Asset1, Asset2) -> correlation
    val valueAtRisk95: Double, // VaR at 95% confidence
    val valueAtRisk99: Double, // VaR at 99% confidence
    val positionSizes: Map<String, Double>, // Asset -> percentage
    val volatilityScore: Double, // Annualized volatility
    val concentrationRisk: Double, // 0-100, lower is better
    val largestPositionPercent: Double
)
