package com.cryptotrader.data.remote.kraken.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Base Kraken API response structure
 */
@JsonClass(generateAdapter = true)
data class KrakenResponse<T>(
    @Json(name = "error") val error: List<String> = emptyList(),
    @Json(name = "result") val result: T? = null
)

/**
 * Ticker data response
 */
@JsonClass(generateAdapter = true)
data class TickerData(
    @Json(name = "a") val ask: List<String> = emptyList(), // [price, whole lot volume, lot volume]
    @Json(name = "b") val bid: List<String> = emptyList(), // [price, whole lot volume, lot volume]
    @Json(name = "c") val lastTradeClosed: List<String> = emptyList(), // [price, lot volume]
    @Json(name = "v") val volume: List<String> = emptyList(), // [today, last 24 hours]
    @Json(name = "p") val vwap: List<String> = emptyList(), // [today, last 24 hours]
    @Json(name = "t") val numberOfTrades: List<Int> = emptyList(), // [today, last 24 hours]
    @Json(name = "l") val low: List<String> = emptyList(), // [today, last 24 hours]
    @Json(name = "h") val high: List<String> = emptyList(), // [today, last 24 hours]
    @Json(name = "o") val openingPrice: String = "0" // Today's opening price
)

/**
 * Account balance response
 */
@JsonClass(generateAdapter = true)
data class BalanceData(
    val balances: Map<String, String> = emptyMap()
)

/**
 * Trade balance response
 */
@JsonClass(generateAdapter = true)
data class TradeBalanceData(
    @Json(name = "eb") val equivalentBalance: String = "0", // Equivalent balance (combined balance of all currencies)
    @Json(name = "tb") val tradeBalance: String = "0", // Trade balance (combined balance of all equity currencies)
    @Json(name = "m") val margin: String = "0", // Margin amount of open positions
    @Json(name = "n") val unrealizedNetPL: String = "0", // Unrealized net profit/loss of open positions
    @Json(name = "c") val costBasis: String = "0", // Cost basis of open positions
    @Json(name = "v") val currentValuation: String = "0", // Current floating valuation of open positions
    @Json(name = "e") val equity: String = "0", // Equity = trade balance + unrealized net profit/loss
    @Json(name = "mf") val freeMargin: String = "0" // Free margin = equity - initial margin (maximum margin available to open new positions)
)

/**
 * Note: OrderRequest is now defined in OrderRequest.kt
 */

/**
 * Add order request (deprecated, use OrderRequest)
 */
@JsonClass(generateAdapter = true)
data class AddOrderRequest(
    @Json(name = "pair") val pair: String,
    @Json(name = "type") val type: String, // "buy" or "sell"
    @Json(name = "ordertype") val orderType: String, // "market", "limit", "stop-loss", "take-profit"
    @Json(name = "price") val price: String? = null, // Required for limit orders
    @Json(name = "volume") val volume: String,
    @Json(name = "validate") val validate: Boolean? = false, // Validate only, don't place order
    @Json(name = "userref") val userRef: String? = null // User reference ID
)

/**
 * Order response
 */
@JsonClass(generateAdapter = true)
data class AddOrderResponse(
    @Json(name = "descr") val description: OrderDescription? = null,
    @Json(name = "txid") val transactionIds: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OrderDescription(
    @Json(name = "order") val order: String = "",
    @Json(name = "close") val close: String? = null,
    @Json(name = "pair") val pair: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "ordertype") val orderType: String? = null,
    @Json(name = "price") val price: String? = null,
    @Json(name = "price2") val price2: String? = null,
    @Json(name = "leverage") val leverage: String? = null
)

/**
 * Open orders response
 */
@JsonClass(generateAdapter = true)
data class OpenOrdersData(
    @Json(name = "open") val open: Map<String, OrderInfo> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class OrderInfo(
    @Json(name = "refid") val refId: String? = null,
    @Json(name = "userref") val userRef: Int? = null,
    @Json(name = "status") val status: String = "", // pending, open, closed, cancelled, expired
    @Json(name = "opentm") val openTime: Double = 0.0,
    @Json(name = "closetm") val closeTime: Double? = null,
    @Json(name = "starttm") val startTime: Double? = null,
    @Json(name = "expiretm") val expireTime: Double? = null,
    @Json(name = "descr") val description: OrderDescription? = null,
    @Json(name = "vol") val volume: String = "0",
    @Json(name = "vol_exec") val volumeExecuted: String = "0",
    @Json(name = "cost") val cost: String = "0",
    @Json(name = "fee") val fee: String = "0",
    @Json(name = "price") val price: String = "0",
    @Json(name = "stopprice") val stopPrice: String? = null,
    @Json(name = "limitprice") val limitPrice: String? = null,
    @Json(name = "misc") val misc: String = "",
    @Json(name = "oflags") val orderFlags: String = "",
    @Json(name = "trades") val trades: List<String>? = null
)

/**
 * Trades history response
 */
@JsonClass(generateAdapter = true)
data class TradesHistoryData(
    @Json(name = "trades") val trades: Map<String, TradeInfo> = emptyMap(),
    @Json(name = "count") val count: Int = 0
)

@JsonClass(generateAdapter = true)
data class TradeInfo(
    @Json(name = "ordertxid") val orderTxId: String = "",
    @Json(name = "pair") val pair: String = "",
    @Json(name = "time") val time: Double = 0.0,
    @Json(name = "type") val type: String = "", // buy or sell
    @Json(name = "ordertype") val orderType: String = "", // market, limit, etc.
    @Json(name = "price") val price: String = "0",
    @Json(name = "cost") val cost: String = "0",
    @Json(name = "fee") val fee: String = "0",
    @Json(name = "vol") val volume: String = "0",
    @Json(name = "margin") val margin: String = "0",
    @Json(name = "misc") val misc: String = ""
)

/**
 * Asset pairs response
 */
@JsonClass(generateAdapter = true)
data class AssetPairsData(
    val pairs: Map<String, AssetPairInfo> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class AssetPairInfo(
    @Json(name = "altname") val altName: String = "",
    @Json(name = "wsname") val wsName: String? = null,
    @Json(name = "aclass_base") val baseAssetClass: String = "",
    @Json(name = "base") val base: String = "",
    @Json(name = "aclass_quote") val quoteAssetClass: String = "",
    @Json(name = "quote") val quote: String = "",
    @Json(name = "pair_decimals") val pairDecimals: Int = 0,
    @Json(name = "lot_decimals") val lotDecimals: Int = 0,
    @Json(name = "lot_multiplier") val lotMultiplier: Int = 1,
    @Json(name = "ordermin") val orderMin: String = "0"
)

/**
 * OHLC (candlestick) data
 * Array format: [time, open, high, low, close, vwap, volume, count]
 */
data class OHLCData(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val vwap: Double, // Volume-weighted average price
    val volume: Double,
    val count: Int // Number of trades
) {
    companion object {
        /**
         * Parse OHLC data from Kraken's array format
         * Format: [time, open, high, low, close, vwap, volume, count]
         */
        fun fromList(data: List<Any>): OHLCData? {
            return try {
                OHLCData(
                    time = (data[0] as? Number)?.toLong() ?: 0L,
                    open = (data[1] as? String)?.toDoubleOrNull() ?: 0.0,
                    high = (data[2] as? String)?.toDoubleOrNull() ?: 0.0,
                    low = (data[3] as? String)?.toDoubleOrNull() ?: 0.0,
                    close = (data[4] as? String)?.toDoubleOrNull() ?: 0.0,
                    vwap = (data[5] as? String)?.toDoubleOrNull() ?: 0.0,
                    volume = (data[6] as? String)?.toDoubleOrNull() ?: 0.0,
                    count = (data[7] as? Number)?.toInt() ?: 0
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Cancel order response
 */
@JsonClass(generateAdapter = true)
data class CancelOrderResponse(
    @Json(name = "count") val count: Int = 0,
    @Json(name = "pending") val pending: Boolean? = null
)

/**
 * Query orders response - maps order ID to OrderInfo
 * The result from Kraken is a map where keys are transaction IDs
 */
typealias QueryOrdersData = Map<String, OrderInfo>

/**
 * Closed orders response
 */
@JsonClass(generateAdapter = true)
data class ClosedOrdersData(
    @Json(name = "closed") val closed: Map<String, OrderInfo> = emptyMap(),
    @Json(name = "count") val count: Int = 0
)
