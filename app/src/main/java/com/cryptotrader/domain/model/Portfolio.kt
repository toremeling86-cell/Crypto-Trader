package com.cryptotrader.domain.model

/**
 * Portfolio state with balance and performance metrics
 */
data class Portfolio(
    val totalValue: Double,
    val availableBalance: Double,
    val balances: Map<String, AssetBalance>,
    val totalProfit: Double,
    val totalProfitPercent: Double,
    val dayProfit: Double,
    val dayProfitPercent: Double,
    val openPositions: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class AssetBalance(
    val asset: String,
    val balance: Double,
    val valueInUSD: Double,
    val percentOfPortfolio: Double
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
 */
data class PortfolioHolding(
    val asset: String,
    val assetName: String,
    val amount: Double,
    val currentPrice: Double,
    val currentValue: Double,
    val percentOfPortfolio: Double,
    val costBasis: Double = 0.0,
    val unrealizedPnL: Double = 0.0,
    val unrealizedPnLPercent: Double = 0.0,
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
 */
data class PortfolioSnapshot(
    val timestamp: Long,
    val totalValue: Double,
    val totalPnL: Double,
    val totalPnLPercent: Double,
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
