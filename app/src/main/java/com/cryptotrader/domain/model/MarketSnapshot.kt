package com.cryptotrader.domain.model

/**
 * Domain model for market price snapshot
 * Represents live crypto price data at a specific point in time
 */
data class MarketSnapshot(
    val symbol: String, // Trading pair (e.g., "XBTUSD", "ETHUSD")
    val price: Double,
    val volume24h: Double,
    val high24h: Double,
    val low24h: Double,
    val changePercent24h: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Get display name for symbol (remove Kraken prefixes)
     * XXBTZUSD -> BTC/USD
     * XETHZUSD -> ETH/USD
     */
    fun getDisplayName(): String {
        return symbol
            .replace("XXBT", "BTC")
            .replace("XETH", "ETH")
            .replace("ZUSD", "/USD")
            .replace("ZEUR", "/EUR")
            .replace("USD", "/USD")
            .replace("EUR", "/EUR")
    }

    /**
     * Get base currency (e.g., "BTC" from "XXBTZUSD")
     */
    fun getBaseCurrency(): String {
        val display = getDisplayName()
        return display.substringBefore("/")
    }

    /**
     * Check if price is going up in last 24h
     */
    fun isPositive(): Boolean = changePercent24h > 0

    /**
     * Check if this snapshot is recent (within last 5 minutes)
     */
    fun isRecent(): Boolean {
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        return timestamp > fiveMinutesAgo
    }
}
