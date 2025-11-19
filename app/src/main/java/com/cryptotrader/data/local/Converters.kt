package com.cryptotrader.data.local

import androidx.room.TypeConverter
import java.math.BigDecimal

/**
 * Room TypeConverters for custom data types
 *
 * Room doesn't natively support BigDecimal, so we need to convert:
 * - BigDecimal ↔ String (for database storage)
 *
 * String is used instead of REAL/DOUBLE to maintain exact precision
 * for monetary values.
 */
class Converters {

    /**
     * Convert BigDecimal to String for database storage
     *
     * Stores the exact decimal value without floating-point conversion
     */
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? {
        return value?.toPlainString()
    }

    /**
     * Convert String from database to BigDecimal
     *
     * Reconstructs the exact decimal value from storage
     */
    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        return value?.let { BigDecimal(it) }
    }

    // Future converters can be added here as needed
    // For example: LocalDateTime, Duration, etc.
}
