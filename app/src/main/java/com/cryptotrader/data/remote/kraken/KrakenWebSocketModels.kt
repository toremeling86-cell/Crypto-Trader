package com.cryptotrader.data.remote.kraken

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Kraken WebSocket message types
 * Documentation: https://docs.kraken.com/websockets/
 */

/**
 * System status message
 */
@JsonClass(generateAdapter = true)
data class SystemStatusMessage(
    @Json(name = "event") val event: String,
    @Json(name = "connectionID") val connectionId: Long?,
    @Json(name = "status") val status: String?,
    @Json(name = "version") val version: String?
)

/**
 * Subscription status message
 */
@JsonClass(generateAdapter = true)
data class SubscriptionStatusMessage(
    @Json(name = "event") val event: String,
    @Json(name = "channelID") val channelId: Int?,
    @Json(name = "channelName") val channelName: String?,
    @Json(name = "pair") val pair: String?,
    @Json(name = "status") val status: String?,
    @Json(name = "subscription") val subscription: SubscriptionInfo?,
    @Json(name = "errorMessage") val errorMessage: String?
)

@JsonClass(generateAdapter = true)
data class SubscriptionInfo(
    @Json(name = "name") val name: String,
    @Json(name = "interval") val interval: Int? = null
)

/**
 * Ticker data from WebSocket
 * Format: [channelID, tickerData, "ticker", "PAIR"]
 */
data class WebSocketTickerData(
    val ask: PriceVolume,        // a: ask [price, wholeLotVolume, lotVolume]
    val bid: PriceVolume,        // b: bid [price, wholeLotVolume, lotVolume]
    val close: PriceVolume,      // c: close [price, lotVolume]
    val volume: DayToday,        // v: volume [today, last24h]
    val vwap: DayToday,          // p: volume weighted average price [today, last24h]
    val trades: TradeCount,      // t: number of trades [today, last24h]
    val low: DayToday,           // l: low [today, last24h]
    val high: DayToday,          // h: high [today, last24h]
    val open: DayToday           // o: open [today, last24h]
)

data class PriceVolume(
    val price: Double,
    val wholeLotVolume: Int,
    val lotVolume: Double
)

data class DayToday(
    val today: Double,
    val last24h: Double
)

data class TradeCount(
    val today: Int,
    val last24h: Int
)

/**
 * OHLC data from WebSocket
 * Format: [channelID, ohlcData, "ohlc-{interval}", "PAIR"]
 */
data class OHLCData(
    val time: Double,
    val endTime: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val vwap: Double,
    val volume: Double,
    val count: Int
)

/**
 * Ticker update (output to Flow)
 */
data class TickerUpdate(
    val pair: String,
    val ask: Double,
    val bid: Double,
    val last: Double,
    val volume24h: Double,
    val high24h: Double,
    val low24h: Double,
    val timestamp: Long
)

/**
 * OHLC update (output to Flow)
 */
data class OHLCUpdate(
    val pair: String,
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)
