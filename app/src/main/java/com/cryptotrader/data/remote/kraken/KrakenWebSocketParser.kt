package com.cryptotrader.data.remote.kraken

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import timber.log.Timber

/**
 * Parser for Kraken WebSocket messages
 *
 * Kraken uses array-based message format:
 * - System messages: {"event": "...", ...}
 * - Ticker: [channelID, {data}, "ticker", "PAIR"]
 * - OHLC: [channelID, [data], "ohlc-{interval}", "PAIR"]
 */
class KrakenWebSocketParser(private val moshi: Moshi) {

    private val systemStatusAdapter: JsonAdapter<SystemStatusMessage> =
        moshi.adapter(SystemStatusMessage::class.java)

    private val subscriptionStatusAdapter: JsonAdapter<SubscriptionStatusMessage> =
        moshi.adapter(SubscriptionStatusMessage::class.java)

    /**
     * Parse incoming WebSocket message
     */
    fun parseMessage(json: String): ParsedMessage? {
        return try {
            when {
                json.startsWith("{") -> parseSystemMessage(json)
                json.startsWith("[") -> parseDataMessage(json)
                else -> {
                    Timber.w("Unknown message format: $json")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse WebSocket message: $json")
            null
        }
    }

    private fun parseSystemMessage(json: String): ParsedMessage? {
        return try {
            // Try system status first
            systemStatusAdapter.fromJson(json)?.let {
                return ParsedMessage.SystemStatus(it)
            }

            // Try subscription status
            subscriptionStatusAdapter.fromJson(json)?.let {
                return ParsedMessage.SubscriptionStatus(it)
            }

            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse system message")
            null
        }
    }

    private fun parseDataMessage(json: String): ParsedMessage? {
        return try {
            // Remove outer brackets and parse
            val jsonArray = moshi.adapter<List<Any>>(
                Types.newParameterizedType(List::class.java, Any::class.java)
            ).fromJson(json) ?: return null

            if (jsonArray.size < 4) return null

            val channelName = jsonArray.getOrNull(2) as? String ?: return null
            val pair = jsonArray.getOrNull(3) as? String ?: return null

            when {
                channelName == "ticker" -> parseTickerMessage(jsonArray, pair)
                channelName.startsWith("ohlc-") -> parseOHLCMessage(jsonArray, pair)
                else -> {
                    Timber.d("Unknown channel: $channelName")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse data message")
            null
        }
    }

    private fun parseTickerMessage(jsonArray: List<Any>, pair: String): ParsedMessage.Ticker? {
        return try {
            val data = jsonArray[1] as? Map<*, *> ?: return null

            // Parse ticker data
            val ask = parsePrice(data["a"])
            val bid = parsePrice(data["b"])
            val close = parsePrice(data["c"])
            val volume = parseDayToday(data["v"])
            val high = parseDayToday(data["h"])
            val low = parseDayToday(data["l"])

            if (ask == null || bid == null || close == null) {
                Timber.w("Missing required ticker fields")
                return null
            }

            ParsedMessage.Ticker(
                TickerUpdate(
                    pair = pair,
                    ask = ask,
                    bid = bid,
                    last = close,
                    volume24h = volume?.last24h ?: 0.0,
                    high24h = high?.last24h ?: close,
                    low24h = low?.last24h ?: close,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse ticker data")
            null
        }
    }

    private fun parseOHLCMessage(jsonArray: List<Any>, pair: String): ParsedMessage.OHLC? {
        return try {
            val data = jsonArray[1] as? List<*> ?: return null

            if (data.size < 9) return null

            val time = (data[0] as? String)?.toDoubleOrNull() ?: return null
            val endTime = (data[1] as? String)?.toDoubleOrNull() ?: return null
            val open = (data[2] as? String)?.toDoubleOrNull() ?: return null
            val high = (data[3] as? String)?.toDoubleOrNull() ?: return null
            val low = (data[4] as? String)?.toDoubleOrNull() ?: return null
            val close = (data[5] as? String)?.toDoubleOrNull() ?: return null
            val vwap = (data[6] as? String)?.toDoubleOrNull() ?: return null
            val volume = (data[7] as? String)?.toDoubleOrNull() ?: return null

            ParsedMessage.OHLC(
                OHLCUpdate(
                    pair = pair,
                    time = (time * 1000).toLong(),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse OHLC data")
            null
        }
    }

    private fun parsePrice(data: Any?): Double? {
        val list = data as? List<*> ?: return null
        return (list.getOrNull(0) as? String)?.toDoubleOrNull()
    }

    private fun parseDayToday(data: Any?): DayToday? {
        val list = data as? List<*> ?: return null
        val today = (list.getOrNull(0) as? String)?.toDoubleOrNull() ?: return null
        val last24h = (list.getOrNull(1) as? String)?.toDoubleOrNull() ?: return null
        return DayToday(today, last24h)
    }
}

/**
 * Parsed WebSocket message types
 */
sealed class ParsedMessage {
    data class SystemStatus(val message: SystemStatusMessage) : ParsedMessage()
    data class SubscriptionStatus(val message: SubscriptionStatusMessage) : ParsedMessage()
    data class Ticker(val update: TickerUpdate) : ParsedMessage()
    data class OHLC(val update: OHLCUpdate) : ParsedMessage()
}
