package com.cryptotrader.data.remote.kraken.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from Kraken ClosedOrders endpoint
 */
@JsonClass(generateAdapter = true)
data class KrakenClosedOrdersResponse(
    @Json(name = "error") val error: List<String> = emptyList(),
    @Json(name = "result") val result: ClosedOrdersResult? = null
)

/**
 * Closed orders result
 */
@JsonClass(generateAdapter = true)
data class ClosedOrdersResult(
    @Json(name = "closed") val closed: Map<String, OrderInfo> = emptyMap(),
    @Json(name = "count") val count: Int = 0
)
