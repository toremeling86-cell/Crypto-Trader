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

    private val parser = KrakenWebSocketParser(moshi)
    private val webSocket = AtomicReference<WebSocket?>(null)
    private val connectionState = AtomicReference(ConnectionState.DISCONNECTED)
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val baseReconnectDelayMs = 1000L

    // Lifecycle management
    private var isActivelyUsed = false
    private var lastActivityTimestamp = System.currentTimeMillis()

    /**
     * Get current connection state
     */
    fun getConnectionState(): ConnectionState = connectionState.get()

    /**
     * Subscribe to ticker updates for specific pairs with auto-reconnect
     */
    fun subscribeToTicker(pairs: List<String>): Flow<TickerUpdate> = callbackFlow {
        markAsActivelyUsed()
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
                        lastActivityTimestamp = System.currentTimeMillis()

                        when (val message = parser.parseMessage(text)) {
                            is ParsedMessage.Ticker -> {
                                trySend(message.update)
                            }
                            is ParsedMessage.SystemStatus -> {
                                Timber.d("System status: ${message.message.status}")
                            }
                            is ParsedMessage.SubscriptionStatus -> {
                                if (message.message.status == "subscribed") {
                                    Timber.i("Subscribed to ${message.message.channelName} for ${message.message.pair}")
                                } else if (message.message.errorMessage != null) {
                                    Timber.e("Subscription error: ${message.message.errorMessage}")
                                }
                            }
                            null -> {
                                // Heartbeat or unknown message - ignore
                            }
                            else -> {
                                // Other message types
                            }
                        }
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
            markAsInactive()
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
                    lastActivityTimestamp = System.currentTimeMillis()

                    when (val message = parser.parseMessage(text)) {
                        is ParsedMessage.OHLC -> {
                            trySend(message.update)
                        }
                        is ParsedMessage.SystemStatus -> {
                            Timber.d("System status: ${message.message.status}")
                        }
                        is ParsedMessage.SubscriptionStatus -> {
                            if (message.message.status == "subscribed") {
                                Timber.i("Subscribed to ${message.message.channelName} for ${message.message.pair}")
                            } else if (message.message.errorMessage != null) {
                                Timber.e("Subscription error: ${message.message.errorMessage}")
                            }
                        }
                        null -> {
                            // Heartbeat or unknown message - ignore
                        }
                        else -> {
                            // Other message types
                        }
                    }
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

    /**
     * Mark WebSocket as actively used (prevents auto-disconnect)
     */
    fun markAsActivelyUsed() {
        isActivelyUsed = true
        lastActivityTimestamp = System.currentTimeMillis()
    }

    /**
     * Mark WebSocket as no longer actively used (allows auto-disconnect after timeout)
     */
    fun markAsInactive() {
        isActivelyUsed = false
    }

    /**
     * Check if WebSocket should auto-disconnect (no activity for 5 minutes)
     */
    private fun shouldAutoDisconnect(): Boolean {
        if (isActivelyUsed) return false
        val inactiveMs = System.currentTimeMillis() - lastActivityTimestamp
        return inactiveMs > 5 * 60 * 1000 // 5 minutes
    }

    private fun List<String>.toJsonArray(): String {
        return this.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    }
}
