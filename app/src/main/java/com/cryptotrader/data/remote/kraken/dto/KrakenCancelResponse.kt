package com.cryptotrader.data.remote.kraken.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from Kraken CancelOrder endpoint
 */
@JsonClass(generateAdapter = true)
data class KrakenCancelResponse(
    @Json(name = "error") val error: List<String> = emptyList(),
    @Json(name = "result") val result: CancelResult? = null
)

/**
 * Cancel order result
 */
@JsonClass(generateAdapter = true)
data class CancelResult(
    @Json(name = "count") val count: Int = 0,
    @Json(name = "pending") val pending: Boolean? = null
)
