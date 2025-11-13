package com.cryptotrader.data.remote.kraken

import com.cryptotrader.data.remote.kraken.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Kraken REST API Service
 * Documentation: https://docs.kraken.com/rest/
 */
interface KrakenApiService {

    // ===== PUBLIC ENDPOINTS (No Authentication) =====

    /**
     * Get server time
     */
    @GET("0/public/Time")
    suspend fun getServerTime(): Response<KrakenResponse<Map<String, Any>>>

    /**
     * Get system status
     */
    @GET("0/public/SystemStatus")
    suspend fun getSystemStatus(): Response<KrakenResponse<Map<String, String>>>

    /**
     * Get ticker information for specific pairs
     * @param pair Comma-delimited list of asset pairs (e.g., "XBTUSD,ETHUSD")
     */
    @GET("0/public/Ticker")
    suspend fun getTicker(
        @Query("pair") pair: String
    ): Response<KrakenResponse<Map<String, TickerData>>>

    /**
     * Get tradable asset pairs
     */
    @GET("0/public/AssetPairs")
    suspend fun getAssetPairs(): Response<KrakenResponse<Map<String, AssetPairInfo>>>

    /**
     * Get OHLC data
     * @param pair Asset pair
     * @param interval Time frame interval in minutes (default 1)
     * @param since Return committed OHLC data since given timestamp
     */
    @GET("0/public/OHLC")
    suspend fun getOHLC(
        @Query("pair") pair: String,
        @Query("interval") interval: Int? = 1,
        @Query("since") since: Long? = null
    ): Response<KrakenResponse<Map<String, Any>>>

    /**
     * Get order book
     * @param pair Asset pair
     * @param count Maximum number of asks/bids (default 100)
     */
    @GET("0/public/Depth")
    suspend fun getOrderBook(
        @Query("pair") pair: String,
        @Query("count") count: Int? = 100
    ): Response<KrakenResponse<Map<String, Any>>>

    /**
     * Get recent trades
     * @param pair Asset pair
     * @param since Return trades since given timestamp
     */
    @GET("0/public/Trades")
    suspend fun getRecentTrades(
        @Query("pair") pair: String,
        @Query("since") since: Long? = null
    ): Response<KrakenResponse<Map<String, Any>>>

    // ===== PRIVATE ENDPOINTS (Require Authentication) =====

    /**
     * Get account balance
     */
    @FormUrlEncoded
    @POST("0/private/Balance")
    suspend fun getBalance(
        @Field("nonce") nonce: String
    ): Response<KrakenResponse<Map<String, String>>>

    /**
     * Get trade balance
     */
    @FormUrlEncoded
    @POST("0/private/TradeBalance")
    suspend fun getTradeBalance(
        @Field("nonce") nonce: String,
        @Field("asset") asset: String? = null
    ): Response<KrakenResponse<TradeBalanceData>>

    /**
     * Get open orders
     */
    @FormUrlEncoded
    @POST("0/private/OpenOrders")
    suspend fun getOpenOrders(
        @Field("nonce") nonce: String,
        @Field("trades") includeTrades: Boolean? = false
    ): Response<KrakenResponse<OpenOrdersData>>

    /**
     * Get closed orders
     */
    @FormUrlEncoded
    @POST("0/private/ClosedOrders")
    suspend fun getClosedOrders(
        @Field("nonce") nonce: String,
        @Field("trades") includeTrades: Boolean? = false,
        @Field("start") start: Long? = null,
        @Field("end") end: Long? = null
    ): Response<KrakenResponse<Map<String, Any>>>

    /**
     * Query orders info
     */
    @FormUrlEncoded
    @POST("0/private/QueryOrders")
    suspend fun queryOrders(
        @Field("nonce") nonce: String,
        @Field("txid") transactionIds: String, // Comma-delimited list
        @Field("trades") includeTrades: Boolean? = false
    ): Response<KrakenResponse<Map<String, OrderInfo>>>

    /**
     * Get trades history
     */
    @FormUrlEncoded
    @POST("0/private/TradesHistory")
    suspend fun getTradesHistory(
        @Field("nonce") nonce: String,
        @Field("type") type: String? = null, // "all", "any position", "closed position", "closing position", "no position"
        @Field("trades") includeTrades: Boolean? = false,
        @Field("start") start: Long? = null,
        @Field("end") end: Long? = null
    ): Response<KrakenResponse<TradesHistoryData>>

    /**
     * Add standard order
     */
    @FormUrlEncoded
    @POST("0/private/AddOrder")
    suspend fun addOrder(
        @Field("nonce") nonce: String,
        @Field("pair") pair: String,
        @Field("type") type: String, // "buy" or "sell"
        @Field("ordertype") orderType: String, // "market", "limit", "stop-loss", "take-profit", etc.
        @Field("price") price: String? = null,
        @Field("volume") volume: String,
        @Field("validate") validate: Boolean? = false,
        @Field("userref") userRef: String? = null,
        @Field("oflags") orderFlags: String? = null // "post" for post-only, "fcib" for prefer fee in base currency, etc.
    ): Response<KrakenResponse<AddOrderResponse>>

    /**
     * Cancel order
     */
    @FormUrlEncoded
    @POST("0/private/CancelOrder")
    suspend fun cancelOrder(
        @Field("nonce") nonce: String,
        @Field("txid") transactionId: String
    ): Response<KrakenResponse<Map<String, Any>>>

    /**
     * Cancel all orders
     */
    @FormUrlEncoded
    @POST("0/private/CancelAll")
    suspend fun cancelAllOrders(
        @Field("nonce") nonce: String
    ): Response<KrakenResponse<Map<String, Any>>>
}
