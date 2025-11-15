package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.TradeDao
import com.cryptotrader.data.local.entities.TradeEntity
import com.cryptotrader.data.remote.kraken.KrakenApiService
import com.cryptotrader.data.remote.kraken.KrakenWebSocketClient
import com.cryptotrader.data.remote.kraken.RateLimiter
import com.cryptotrader.data.remote.kraken.TickerUpdate
import com.cryptotrader.domain.model.*
import com.cryptotrader.domain.usecase.TradeRequest
import com.cryptotrader.utils.CryptoUtils
import com.cryptotrader.utils.FeatureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KrakenRepository @Inject constructor(
    private val krakenApi: KrakenApiService,
    private val webSocketClient: KrakenWebSocketClient,
    private val tradeDao: TradeDao,
    private val rateLimiter: RateLimiter,
    private val paperTradingManager: com.cryptotrader.domain.trading.PaperTradingManager,
    private val context: android.content.Context
) {

    suspend fun getBalance(): Result<Map<String, String>> {
        // Check if paper trading mode is enabled
        if (com.cryptotrader.utils.CryptoUtils.isPaperTradingMode(context)) {
            Timber.d("📄 Using paper trading balance")
            return paperTradingManager.simulateGetBalance()
        }

        return try {
            // Rate limit for private API
            rateLimiter.waitForPrivateApiPermission()

            val nonce = CryptoUtils.generateNonce()
            val response = krakenApi.getBalance(nonce)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error.isEmpty()) {
                    Result.success(body.result ?: emptyMap())
                } else {
                    Result.failure(Exception("Kraken API Error: ${body.error.joinToString()}"))
                }
            } else {
                Result.failure(Exception("API call failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting balance")
            Result.failure(e)
        }
    }

    suspend fun getTicker(pair: String): Result<MarketTicker> {
        return try {
            // Rate limit for public API
            rateLimiter.waitForPublicApiPermission()

            val response = krakenApi.getTicker(pair)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error.isEmpty() && body.result != null) {
                    val tickerData = body.result.values.firstOrNull()
                        ?: return Result.failure(Exception("No ticker data"))

                    val last = tickerData.lastTradeClosed.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                    val high = tickerData.high.getOrNull(0)?.toDoubleOrNull() ?: last
                    val low = tickerData.low.getOrNull(0)?.toDoubleOrNull() ?: last
                    val volume = tickerData.volume.getOrNull(0)?.toDoubleOrNull() ?: 0.0

                    val ticker = MarketTicker(
                        pair = pair,
                        ask = tickerData.ask.getOrNull(0)?.toDoubleOrNull() ?: last,
                        bid = tickerData.bid.getOrNull(0)?.toDoubleOrNull() ?: last,
                        last = last,
                        volume24h = volume,
                        high24h = high,
                        low24h = low,
                        change24h = last - low,
                        changePercent24h = if (low > 0) ((last - low) / low * 100) else 0.0
                    )

                    Result.success(ticker)
                } else {
                    Result.failure(Exception("Kraken API Error: ${body.error.joinToString()}"))
                }
            } else {
                Result.failure(Exception("API call failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting ticker")
            Result.failure(e)
        }
    }

    suspend fun placeOrder(request: TradeRequest, retryCount: Int = 0): Result<Trade> {
        // Check if paper trading mode is enabled
        if (com.cryptotrader.utils.CryptoUtils.isPaperTradingMode(context)) {
            Timber.d("📄 Simulating paper trade: ${request.type} ${request.volume} ${request.pair}")

            // Get current market price
            val tickerResult = getTicker(request.pair)
            val currentPrice = if (tickerResult.isSuccess) {
                tickerResult.getOrNull()?.last ?: request.price
            } else {
                request.price
            }

            val tradeResult = paperTradingManager.simulatePlaceOrder(request, currentPrice)

            // Save paper trade to database
            if (tradeResult.isSuccess) {
                val trade = tradeResult.getOrNull()!!
                val tradeEntity = trade.toEntity()
                tradeDao.insertTrade(tradeEntity)
            }

            return tradeResult
        }

        return try {
            // Rate limit for private API (placing orders is the most critical operation)
            rateLimiter.waitForPrivateApiPermission()

            val nonce = CryptoUtils.generateNonce()

            val response = krakenApi.addOrder(
                nonce = nonce,
                pair = request.pair,
                type = request.type.toString(),
                orderType = request.orderType.name.lowercase().replace("_", "-"),
                price = if (request.orderType == com.cryptotrader.domain.usecase.OrderType.LIMIT) {
                    request.price.toString()
                } else null,
                volume = request.volume.toString(),
                validate = false
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error.isEmpty() && body.result != null) {
                    val orderId = body.result.transactionIds.firstOrNull()
                        ?: return Result.failure(Exception("No order ID returned"))

                    val trade = Trade(
                        orderId = orderId,
                        pair = request.pair,
                        type = request.type,
                        price = request.price,
                        volume = request.volume,
                        cost = request.price * request.volume,
                        fee = 0.0, // Will be updated later
                        timestamp = System.currentTimeMillis(),
                        strategyId = request.strategyId,
                        status = TradeStatus.EXECUTED
                    )

                    // Save to database
                    val tradeEntity = trade.toEntity()
                    tradeDao.insertTrade(tradeEntity)

                    Timber.d("Order placed successfully: $orderId")
                    Result.success(trade)
                } else {
                    val errorMessage = body.error.joinToString()

                    // Check if error is retryable
                    if (isRetryableError(errorMessage) && retryCount < MAX_RETRY_ATTEMPTS) {
                        val delayMs = calculateRetryDelay(retryCount)
                        Timber.w("Retryable error, attempt ${retryCount + 1}/$MAX_RETRY_ATTEMPTS. Retrying in ${delayMs}ms: $errorMessage")
                        kotlinx.coroutines.delay(delayMs)
                        return placeOrder(request, retryCount + 1)
                    }

                    Result.failure(Exception("Kraken API Error: $errorMessage"))
                }
            } else {
                // Network/HTTP errors - retry if possible
                if (isRetryableHttpCode(response.code()) && retryCount < MAX_RETRY_ATTEMPTS) {
                    val delayMs = calculateRetryDelay(retryCount)
                    Timber.w("HTTP ${response.code()}, attempt ${retryCount + 1}/$MAX_RETRY_ATTEMPTS. Retrying in ${delayMs}ms")
                    kotlinx.coroutines.delay(delayMs)
                    return placeOrder(request, retryCount + 1)
                }

                Result.failure(Exception("API call failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            // Network exceptions - retry if possible
            if (isRetryableException(e) && retryCount < MAX_RETRY_ATTEMPTS) {
                val delayMs = calculateRetryDelay(retryCount)
                Timber.w(e, "Network exception, attempt ${retryCount + 1}/$MAX_RETRY_ATTEMPTS. Retrying in ${delayMs}ms")
                kotlinx.coroutines.delay(delayMs)
                return placeOrder(request, retryCount + 1)
            }

            Timber.e(e, "Error placing order after $retryCount retries")
            Result.failure(e)
        }
    }

    /**
     * Check if API error message indicates a retryable condition
     */
    private fun isRetryableError(errorMessage: String): Boolean {
        val retryableErrors = listOf(
            "Service:Unavailable",
            "Service:Busy",
            "EAPI:Rate limit exceeded",
            "Service temporarily unavailable",
            "Timeout"
        )
        return retryableErrors.any { errorMessage.contains(it, ignoreCase = true) }
    }

    /**
     * Check if HTTP status code is retryable
     */
    private fun isRetryableHttpCode(code: Int): Boolean {
        return code in listOf(408, 429, 500, 502, 503, 504)
    }

    /**
     * Check if exception is retryable (network issues)
     */
    private fun isRetryableException(e: Exception): Boolean {
        return e is java.net.SocketTimeoutException ||
               e is java.net.UnknownHostException ||
               e is java.io.IOException
    }

    /**
     * Calculate retry delay with exponential backoff
     * Attempt 0: 1s, Attempt 1: 2s, Attempt 2: 4s
     */
    private fun calculateRetryDelay(retryCount: Int): Long {
        return (1000L * Math.pow(2.0, retryCount.toDouble())).toLong()
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    fun getRecentTrades(limit: Int = 50): Flow<List<Trade>> {
        return tradeDao.getRecentTrades(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun subscribeToTickerUpdates(pairs: List<String>): Flow<TickerUpdate> {
        return if (FeatureFlags.ENABLE_KRAKEN_WEBSOCKET) {
            Timber.d("WebSocket enabled - subscribing to ticker updates for pairs: $pairs")
            webSocketClient.subscribeToTicker(pairs)
        } else {
            Timber.d("WebSocket disabled via feature flag - skipping ticker subscription for pairs: $pairs")
            emptyFlow() // Return empty flow when WebSocket is disabled
        }
    }

    private fun Trade.toEntity() = TradeEntity(
        orderId = orderId,
        pair = pair,
        type = type.toString(),
        price = price,
        volume = volume,
        cost = cost,
        fee = fee,
        timestamp = timestamp,
        strategyId = strategyId,
        status = status.toString(),
        profit = profit
    )

    private fun TradeEntity.toDomain() = Trade(
        id = id,
        orderId = orderId,
        pair = pair,
        type = TradeType.fromString(type),
        price = price,
        volume = volume,
        cost = cost,
        fee = fee,
        timestamp = timestamp,
        strategyId = strategyId,
        status = TradeStatus.fromString(status),
        profit = profit
    )
}
