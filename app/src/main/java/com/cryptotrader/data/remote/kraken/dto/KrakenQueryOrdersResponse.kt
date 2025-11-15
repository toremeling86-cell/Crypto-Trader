package com.cryptotrader.data.remote.kraken.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from Kraken QueryOrders endpoint
 *
 * Note: Uses OrderInfo and OrderDescription from KrakenResponse.kt
 */
@JsonClass(generateAdapter = true)
data class KrakenQueryOrdersResponse(
    @Json(name = "error") val error: List<String> = emptyList(),
    @Json(name = "result") val result: Map<String, OrderInfo>? = null
)
