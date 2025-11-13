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
