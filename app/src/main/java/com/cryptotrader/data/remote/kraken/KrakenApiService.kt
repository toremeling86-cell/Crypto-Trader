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
     * @param nonce Unique nonce for request
     * @param trades Whether to include trades in output
     * @param userref Filter results by user reference id
     */
    @FormUrlEncoded
    @POST("0/private/OpenOrders")
    suspend fun openOrders(
        @Field("nonce") nonce: String,
        @Field("trades") trades: Boolean? = false,
        @Field("userref") userref: String? = null
    ): Response<KrakenResponse<OpenOrdersData>>

    /**
     * Get closed orders
     * @param nonce Unique nonce for request
     * @param trades Whether to include trades in output
     * @param userref Filter results by user reference id
     * @param start Starting unix timestamp or order tx id
     * @param end Ending unix timestamp or order tx id
     * @param ofs Result offset
     * @param closetime Which time to use: "open", "close", "both" (default)
     */
    @FormUrlEncoded
    @POST("0/private/ClosedOrders")
    suspend fun closedOrders(
        @Field("nonce") nonce: String,
        @Field("trades") trades: Boolean? = false,
        @Field("userref") userref: String? = null,
        @Field("start") start: String? = null,
        @Field("end") end: String? = null,
        @Field("ofs") ofs: Int? = null,
        @Field("closetime") closetime: String? = null
    ): Response<KrakenResponse<ClosedOrdersData>>

    /**
     * Query orders info
     * @param nonce Unique nonce for request
     * @param txid Comma-delimited list of transaction ids to query
     * @param trades Whether to include trades in output
     * @param userref Filter results by user reference id
     */
    @FormUrlEncoded
    @POST("0/private/QueryOrders")
    suspend fun queryOrders(
        @Field("nonce") nonce: String,
        @Field("txid") txid: String,
        @Field("trades") trades: Boolean? = false,
        @Field("userref") userref: String? = null
    ): Response<KrakenResponse<QueryOrdersData>>

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
     * @param nonce Unique nonce for request
     * @param pair Asset pair (e.g., "XBTUSD")
     * @param type Order direction: "buy" or "sell"
     * @param orderType Order type: "market", "limit", "stop-loss", "take-profit", "stop-loss-limit", "take-profit-limit"
     * @param volume Order volume in lots
     * @param price Price (required for limit orders)
     * @param price2 Secondary price (for stop-loss-limit, take-profit-limit)
     * @param leverage Desired leverage amount (default: none)
     * @param oflags Order flags: "post" (post-only), "fcib" (prefer fee in base currency), "fciq" (prefer fee in quote currency), "nompp" (no market price protection)
     * @param starttm Scheduled start time (0 for now, +<n> for offset)
     * @param expiretm Expiration time (0 for no expiration, +<n> for offset)
     * @param userref User reference id (32-bit signed number)
     * @param validate Validate inputs only, do not submit order
     */
    @FormUrlEncoded
    @POST("0/private/AddOrder")
    suspend fun addOrder(
        @Field("nonce") nonce: String,
        @Field("pair") pair: String,
        @Field("type") type: String,
        @Field("ordertype") orderType: String,
        @Field("volume") volume: String,
        @Field("price") price: String? = null,
        @Field("price2") price2: String? = null,
        @Field("leverage") leverage: String? = null,
        @Field("oflags") oflags: String? = null,
        @Field("starttm") starttm: String? = null,
        @Field("expiretm") expiretm: String? = null,
        @Field("userref") userref: String? = null,
        @Field("validate") validate: Boolean? = false
    ): Response<KrakenResponse<AddOrderResponse>>

    /**
     * Cancel order
     * @param nonce Unique nonce for request
     * @param txid Transaction ID of order to cancel
     */
    @FormUrlEncoded
    @POST("0/private/CancelOrder")
    suspend fun cancelOrder(
        @Field("nonce") nonce: String,
        @Field("txid") txid: String
    ): Response<KrakenResponse<CancelOrderResponse>>

    /**
     * Cancel all orders
     */
    @FormUrlEncoded
    @POST("0/private/CancelAll")
    suspend fun cancelAllOrders(
        @Field("nonce") nonce: String
    ): Response<KrakenResponse<Map<String, Any>>>
}
