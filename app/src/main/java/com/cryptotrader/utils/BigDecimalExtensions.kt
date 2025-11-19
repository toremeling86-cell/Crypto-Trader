package com.cryptotrader.utils

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * BigDecimal Extensions for Financial Calculations
 *
 * Provides exact decimal arithmetic for all monetary values.
 * Replaces Double calculations to eliminate floating-point precision errors.
 *
 * Usage:
 * ```kotlin
 * val balance = "10000.50".toBigDecimalMoney()
 * val fee = BigDecimal("0.26")
 * val cost = balance.applyPercent(fee)  // Exact: 26.001300
 * ```
 */

/**
 * Standard scale for monetary values (8 decimal places for crypto precision)
 *
 * Why 8 decimals:
 * - Bitcoin uses 8 decimal places (1 satoshi = 0.00000001 BTC)
 * - Most crypto exchanges support up to 8 decimals
 * - Kraken API returns prices with 1-8 decimal precision
 */
const val MONEY_SCALE = 8

/**
 * Standard rounding mode for financial calculations
 *
 * HALF_EVEN (Banker's rounding):
 * - Rounds to nearest even number when exactly halfway
 * - Minimizes rounding bias over many calculations
 * - Standard in financial systems
 * - Examples: 2.5 → 2, 3.5 → 4, 4.5 → 4, 5.5 → 6
 */
val MONEY_ROUNDING: RoundingMode = RoundingMode.HALF_EVEN

/**
 * Create BigDecimal from String with default scale
 *
 * ALWAYS prefer this over Double.toBigDecimal() to avoid precision loss
 *
 * Example:
 * ```kotlin
 * val price = "50000.12345678".toBigDecimalMoney()  // Exact
 * ```
 */
fun String.toBigDecimalMoney(): BigDecimal =
    BigDecimal(this).setScale(MONEY_SCALE, MONEY_ROUNDING)

/**
 * Create BigDecimal from Double (use cautiously, prefer String)
 *
 * WARNING: This may introduce precision errors from the Double representation
 * Only use when you have no choice (e.g., API returns Double)
 *
 * Example:
 * ```kotlin
 * val price = 50000.12.toBigDecimalMoney()  // May have precision issues
 * ```
 */
fun Double.toBigDecimalMoney(): BigDecimal =
    BigDecimal.valueOf(this).setScale(MONEY_SCALE, MONEY_ROUNDING)

/**
 * Create BigDecimal from Int
 */
fun Int.toBigDecimalMoney(): BigDecimal =
    BigDecimal.valueOf(this.toLong()).setScale(MONEY_SCALE, MONEY_ROUNDING)

/**
 * Create BigDecimal from Long
 */
fun Long.toBigDecimalMoney(): BigDecimal =
    BigDecimal.valueOf(this).setScale(MONEY_SCALE, MONEY_ROUNDING)

/**
 * Format BigDecimal as USD currency string
 *
 * Example:
 * ```kotlin
 * BigDecimal("10450.50").toUSDString()  // "$10450.50"
 * ```
 */
fun BigDecimal.toUSDString(): String =
    "$${this.setScale(2, MONEY_ROUNDING)}"

/**
 * Format BigDecimal as EUR currency string
 */
fun BigDecimal.toEURString(): String =
    "€${this.setScale(2, MONEY_ROUNDING)}"

/**
 * Format BigDecimal as NOK currency string
 */
fun BigDecimal.toNOKString(): String =
    "${this.setScale(2, MONEY_ROUNDING)} kr"

/**
 * Format BigDecimal as crypto amount (8 decimals)
 *
 * Example:
 * ```kotlin
 * BigDecimal("0.12345678").toCryptoString()  // "0.12345678"
 * BigDecimal("1.5").toCryptoString()         // "1.50000000"
 * ```
 */
fun BigDecimal.toCryptoString(): String =
    this.setScale(8, MONEY_ROUNDING).toPlainString()

/**
 * Format BigDecimal as percentage
 *
 * Example:
 * ```kotlin
 * BigDecimal("15.75").toPercentString()  // "15.75%"
 * ```
 */
fun BigDecimal.toPercentString(): String =
    "${this.setScale(2, MONEY_ROUNDING)}%"

/**
 * Safe division with scale
 *
 * Prevents division by zero and applies standard scale/rounding
 *
 * Example:
 * ```kotlin
 * val ratio = profit safeDiv cost
 * ```
 */
infix fun BigDecimal.safeDiv(divisor: BigDecimal): BigDecimal {
    if (divisor == BigDecimal.ZERO) return BigDecimal.ZERO
    return this.divide(divisor, MONEY_SCALE, MONEY_ROUNDING)
}

/**
 * Percentage calculation: (value / total) * 100
 *
 * Example:
 * ```kotlin
 * val profit = BigDecimal("500")
 * val cost = BigDecimal("10000")
 * val roi = profit.percentOf(cost)  // 5.00%
 * ```
 */
fun BigDecimal.percentOf(total: BigDecimal): BigDecimal {
    if (total == BigDecimal.ZERO) return BigDecimal.ZERO
    return (this safeDiv total) * BigDecimal("100")
}

/**
 * Apply percentage: value * (percent / 100)
 *
 * Example:
 * ```kotlin
 * val balance = BigDecimal("10000")
 * val feePercent = BigDecimal("0.26")
 * val fee = balance.applyPercent(feePercent)  // 26.00
 * ```
 */
fun BigDecimal.applyPercent(percent: BigDecimal): BigDecimal =
    this * (percent safeDiv BigDecimal("100"))

/**
 * Check if value is positive (> 0)
 */
fun BigDecimal.isPositive(): Boolean = this > BigDecimal.ZERO

/**
 * Check if value is negative (< 0)
 */
fun BigDecimal.isNegative(): Boolean = this < BigDecimal.ZERO

/**
 * Check if value is zero
 */
fun BigDecimal.isZero(): Boolean = this.compareTo(BigDecimal.ZERO) == 0

/**
 * Absolute value
 */
fun BigDecimal.abs(): BigDecimal = this.abs()

/**
 * Return max of this and other
 */
fun BigDecimal.max(other: BigDecimal): BigDecimal =
    if (this > other) this else other

/**
 * Return min of this and other
 */
fun BigDecimal.min(other: BigDecimal): BigDecimal =
    if (this < other) this else other

/**
 * Clamp value between min and max
 */
fun BigDecimal.clamp(min: BigDecimal, max: BigDecimal): BigDecimal =
    this.max(min).min(max)

/**
 * Common BigDecimal constants for monetary calculations
 */
object BigDecimalConstants {
    val ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING)
    val ONE = BigDecimal.ONE.setScale(MONEY_SCALE, MONEY_ROUNDING)
    val TEN = BigDecimal.TEN.setScale(MONEY_SCALE, MONEY_ROUNDING)
    val HUNDRED = BigDecimal("100").setScale(MONEY_SCALE, MONEY_ROUNDING)

    // Kraken fee percentages
    val KRAKEN_MAKER_FEE = BigDecimal("0.16").setScale(MONEY_SCALE, MONEY_ROUNDING)
    val KRAKEN_TAKER_FEE = BigDecimal("0.26").setScale(MONEY_SCALE, MONEY_ROUNDING)

    // Common percentage values
    val PERCENT_1 = BigDecimal("1").setScale(MONEY_SCALE, MONEY_ROUNDING)
    val PERCENT_5 = BigDecimal("5").setScale(MONEY_SCALE, MONEY_ROUNDING)
    val PERCENT_10 = BigDecimal("10").setScale(MONEY_SCALE, MONEY_ROUNDING)
    val PERCENT_25 = BigDecimal("25").setScale(MONEY_SCALE, MONEY_ROUNDING)
    val PERCENT_50 = BigDecimal("50").setScale(MONEY_SCALE, MONEY_ROUNDING)
    val PERCENT_100 = BigDecimal("100").setScale(MONEY_SCALE, MONEY_ROUNDING)

    // Epsilon for floating-point comparisons (very small value)
    val EPSILON = BigDecimal("0.00000001").setScale(MONEY_SCALE, MONEY_ROUNDING)
}

/**
 * Check if two BigDecimals are approximately equal (within epsilon)
 *
 * Useful for comparing values that may have minor rounding differences
 */
fun BigDecimal.approximatelyEquals(other: BigDecimal, epsilon: BigDecimal = BigDecimalConstants.EPSILON): Boolean {
    return (this - other).abs() < epsilon
}

/**
 * Calculate compound growth: principal * (1 + rate)^periods
 *
 * Example:
 * ```kotlin
 * val principal = BigDecimal("10000")
 * val monthlyReturn = BigDecimal("2")  // 2% per month
 * val finalValue = principal.compoundGrowth(monthlyReturn, 12)  // After 12 months
 * ```
 */
fun BigDecimal.compoundGrowth(ratePercent: BigDecimal, periods: Int): BigDecimal {
    if (periods == 0) return this

    val rate = ratePercent safeDiv BigDecimal("100")
    val multiplier = BigDecimal.ONE + rate

    var result = this
    repeat(periods) {
        result *= multiplier
    }

    return result.setScale(MONEY_SCALE, MONEY_ROUNDING)
}

/**
 * Calculate simple interest: principal * (1 + rate * periods / 100)
 *
 * Example:
 * ```kotlin
 * val principal = BigDecimal("10000")
 * val annualRate = BigDecimal("5")  // 5% per year
 * val years = 2
 * val finalValue = principal.simpleInterest(annualRate, years)
 * ```
 */
fun BigDecimal.simpleInterest(ratePercent: BigDecimal, periods: Int): BigDecimal {
    val interest = this.applyPercent(ratePercent) * periods.toBigDecimalMoney()
    return (this + interest).setScale(MONEY_SCALE, MONEY_ROUNDING)
}

// Note: roundTo() already exists in Extensions.kt
// Using HALF_UP instead of HALF_EVEN for compatibility
