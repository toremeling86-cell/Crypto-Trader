package com.cryptotrader.data.remote.kraken.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Order request for placing new orders via Kraken API
 *
 * This is a simplified request model for application use.
 * The actual API call uses individual fields passed via @Field annotations.
 */
@JsonClass(generateAdapter = true)
data class OrderRequest(
    @Json(name = "pair") val pair: String,
    @Json(name = "type") val type: String, // "buy" or "sell"
    @Json(name = "ordertype") val orderType: String, // "market", "limit", "stop-loss", "take-profit", "stop-loss-limit", "take-profit-limit"
    @Json(name = "volume") val volume: Double,
    @Json(name = "price") val price: Double? = null,
    @Json(name = "price2") val price2: Double? = null,
    @Json(name = "leverage") val leverage: Int? = null,
    @Json(name = "validate") val validate: Boolean = false
)
