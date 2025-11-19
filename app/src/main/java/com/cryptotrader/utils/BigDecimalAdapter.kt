package com.cryptotrader.utils

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.math.BigDecimal

/**
 * Moshi JsonAdapter for BigDecimal serialization/deserialization
 *
 * Converts BigDecimal to/from JSON strings to maintain precision
 * instead of using JSON numbers which may lose precision.
 *
 * Usage:
 * ```kotlin
 * val moshi = Moshi.Builder()
 *     .add(BigDecimalAdapter())
 *     .build()
 * ```
 */
class BigDecimalAdapter {

    /**
     * Convert BigDecimal to JSON string
     *
     * Example: BigDecimal("50000.12345678") → "50000.12345678"
     */
    @ToJson
    fun toJson(value: BigDecimal): String {
        return value.toPlainString()
    }

    /**
     * Convert JSON string to BigDecimal
     *
     * Example: "50000.12345678" → BigDecimal("50000.12345678")
     */
    @FromJson
    fun fromJson(value: String): BigDecimal {
        return BigDecimal(value)
    }
}
