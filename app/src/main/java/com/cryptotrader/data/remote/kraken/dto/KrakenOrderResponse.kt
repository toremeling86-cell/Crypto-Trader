package com.cryptotrader.data.remote.kraken.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from Kraken AddOrder endpoint
 *
 * Note: Uses OrderDescription from KrakenResponse.kt
 */
@JsonClass(generateAdapter = true)
data class KrakenOrderResponse(
    @Json(name = "error") val error: List<String> = emptyList(),
    @Json(name = "result") val result: OrderResult? = null
)

/**
 * Order result containing order details and transaction IDs
 */
@JsonClass(generateAdapter = true)
data class OrderResult(
    @Json(name = "descr") val descr: OrderDescription,
    @Json(name = "txid") val txid: List<String> = emptyList()
)
