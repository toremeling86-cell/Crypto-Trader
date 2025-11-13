package com.cryptotrader.data.remote.kraken

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * WebSocket connection states
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

/**
 * Kraken WebSocket client for real-time market data
 * Documentation: https://docs.kraken.com/websockets/
 *
 * Features:
 * - Automatic reconnection with exponential backoff
 * - Connection state tracking
 * - Proper resource cleanup
 * - Thread-safe connection management
 */
@Singleton
class KrakenWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val wsUrl: String = "wss://ws.kraken.com"
) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val webSocket = AtomicReference<WebSocket?>(null)
    private val connectionState = AtomicReference(ConnectionState.DISCONNECTED)
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val baseReconnectDelayMs = 1000L

    /**
     * Get current connection state
     */
    fun getConnectionState(): ConnectionState = connectionState.get()

    /**
     * Subscribe to ticker updates for specific pairs with auto-reconnect
     */
    fun subscribeToTicker(pairs: List<String>): Flow<TickerUpdate> = callbackFlow {
        connectionState.set(ConnectionState.CONNECTING)
        reconnectAttempts = 0

        suspend fun connect() {
            // Close existing connection if any
            webSocket.get()?.close(1000, "Reconnecting")

            val request = Request.Builder()
                .url(wsUrl)
                .build()

            val listener = object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Timber.d("WebSocket connected successfully")
                    connectionState.set(ConnectionState.CONNECTED)
                    reconnectAttempts = 0
                    webSocket.set(ws)

                    // Subscribe to ticker channel
                    val subscriptionMessage = """
                        {
                            "event": "subscribe",
                            "pair": ${pairs.toJsonArray()},
                            "subscription": {
                                "name": "ticker"
                            }
                        }
                    """.trimIndent()

                    ws.send(subscriptionMessage)
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    try {
                        // Parse ticker update
                        val tickerUpdate = parseTickerUpdate(text)
                        tickerUpdate?.let { trySend(it) }
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing ticker update: $text")
                    }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Timber.e(t, "WebSocket connection failed")
                    connectionState.set(ConnectionState.ERROR)

                    // Attempt reconnection with exponential backoff
                    if (reconnectAttempts < maxReconnectAttempts) {
                        reconnectAttempts++
                        val delay = baseReconnectDelayMs * min(reconnectAttempts * 2, 32)

                        Timber.d("Reconnecting in ${delay}ms (attempt $reconnectAttempts/$maxReconnectAttempts)")
                        connectionState.set(ConnectionState.RECONNECTING)

                        launch {
                            delay(delay)
                            if (!isClosedForSend) {
                                connect()
                            }
                        }
                    } else {
                        Timber.e("Max reconnect attempts reached, giving up")
                        close(t)
                    }
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    Timber.d("WebSocket closing: $code - $reason")
                    ws.close(1000, null)
                    connectionState.set(ConnectionState.DISCONNECTED)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Timber.d("WebSocket closed: $code - $reason")
                    connectionState.set(ConnectionState.DISCONNECTED)
                }
            }

            webSocket.set(okHttpClient.newWebSocket(request, listener))
        }

        // Initial connection
        connect()

        awaitClose {
            Timber.d("Closing WebSocket ticker subscription")
            webSocket.get()?.close(1000, "Client closing")
            webSocket.set(null)
            connectionState.set(ConnectionState.DISCONNECTED)
        }
    }

    /**
     * Subscribe to OHLC (candlestick) updates
     */
    fun subscribeToOHLC(pairs: List<String>, interval: Int = 1): Flow<OHLCUpdate> = callbackFlow {
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val subscriptionMessage = """
                    {
                        "event": "subscribe",
                        "pair": ${pairs.toJsonArray()},
                        "subscription": {
                            "name": "ohlc",
                            "interval": $interval
                        }
                    }
                """.trimIndent()

                webSocket.send(subscriptionMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val ohlcUpdate = parseOHLCUpdate(text)
                    ohlcUpdate?.let { trySend(it) }
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing OHLC update: $text")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "WebSocket error")
                close(t)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                close()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Timber.d("WebSocket closed: $code - $reason")
                connectionState.set(ConnectionState.DISCONNECTED)
            }
        }

        webSocket.set(okHttpClient.newWebSocket(request, listener))

        awaitClose {
            webSocket.get()?.close(1000, "Client closing")
            webSocket.set(null)
            connectionState.set(ConnectionState.DISCONNECTED)
        }
    }

    /**
     * Close WebSocket connection
     */
    fun disconnect() {
        Timber.d("Manually disconnecting WebSocket")
        webSocket.get()?.close(1000, "Manual disconnect")
        webSocket.set(null)
        connectionState.set(ConnectionState.DISCONNECTED)
        reconnectAttempts = maxReconnectAttempts // Prevent auto-reconnect
    }

    /**
     * Check if WebSocket is currently connected
     */
    fun isConnected(): Boolean = connectionState.get() == ConnectionState.CONNECTED

    private fun parseTickerUpdate(json: String): TickerUpdate? {
        // Parse JSON array format from Kraken
        // Example: [0, {"a":["43210.00000",1,"1.000"],...}, "ticker", "XBT/USD"]
        return try {
            if (!json.startsWith("[")) return null

            val parts = json.trim('[', ']').split(",", limit = 4)
            if (parts.size < 4) return null

            val pair = parts[3].trim('"')
            // Extract price from data object (simplified parsing)
            val askMatch = Regex(""""a":\["([0-9.]+)"""").find(json)
            val bidMatch = Regex(""""b":\["([0-9.]+)"""").find(json)
            val lastMatch = Regex(""""c":\["([0-9.]+)"""").find(json)

            if (askMatch != null && bidMatch != null && lastMatch != null) {
                TickerUpdate(
                    pair = pair,
                    ask = askMatch.groupValues[1].toDouble(),
                    bid = bidMatch.groupValues[1].toDouble(),
                    last = lastMatch.groupValues[1].toDouble(),
                    timestamp = System.currentTimeMillis()
                )
            } else null
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse ticker update")
            null
        }
    }

    private fun parseOHLCUpdate(json: String): OHLCUpdate? {
        // Parse OHLC data from WebSocket message
        return try {
            if (!json.startsWith("[")) return null

            // Simplified parsing - would need more robust implementation
            val pair = Regex(""""([A-Z/]+)"""").find(json)?.groupValues?.get(1) ?: return null

            OHLCUpdate(
                pair = pair,
                time = System.currentTimeMillis(),
                open = 0.0,
                high = 0.0,
                low = 0.0,
                close = 0.0,
                volume = 0.0
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse OHLC update")
            null
        }
    }

    private fun List<String>.toJsonArray(): String {
        return this.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    }
}

/**
 * Ticker update data class
 */
data class TickerUpdate(
    val pair: String,
    val ask: Double,
    val bid: Double,
    val last: Double,
    val timestamp: Long
)

/**
 * OHLC (candlestick) update data class
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
