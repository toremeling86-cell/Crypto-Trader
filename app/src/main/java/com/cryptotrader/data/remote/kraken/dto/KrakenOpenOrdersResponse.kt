package com.cryptotrader.data.remote.kraken.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from Kraken OpenOrders endpoint
 */
@JsonClass(generateAdapter = true)
data class KrakenOpenOrdersResponse(
    @Json(name = "error") val error: List<String> = emptyList(),
    @Json(name = "result") val result: OpenOrdersResult? = null
)

/**
 * Open orders result
 */
@JsonClass(generateAdapter = true)
data class OpenOrdersResult(
    @Json(name = "open") val open: Map<String, OrderInfo> = emptyMap()
)
