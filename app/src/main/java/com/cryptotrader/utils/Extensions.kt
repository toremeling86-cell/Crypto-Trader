package com.cryptotrader.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Extension functions for common operations
 */

// Format BigDecimal as currency
fun Double.formatCurrency(): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    return formatter.format(this)
}

// Format Double as percentage
fun Double.formatPercentage(decimals: Int = 2): String {
    return String.format("%.${decimals}f%%", this)
}

// Format timestamp to readable date
fun Long.formatDate(pattern: String = "MMM dd, yyyy HH:mm"): String {
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    return sdf.format(Date(this))
}

// Format timestamp to time ago
fun Long.formatTimeAgo(): String {
    val now = System.currentTimeMillis()
    val diff = now - this

    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> this.formatDate("MMM dd")
    }
}

// Round BigDecimal to specific decimals
fun BigDecimal.roundTo(decimals: Int): BigDecimal {
    return this.setScale(decimals, RoundingMode.HALF_UP)
}

// Safe division
fun Double.safeDivide(divisor: Double, default: Double = 0.0): Double {
    return if (divisor != 0.0) this / divisor else default
}

// Truncate string with ellipsis
fun String.truncate(maxLength: Int): String {
    return if (this.length > maxLength) {
        "${this.substring(0, maxLength)}..."
    } else {
        this
    }
}

// Validate trading pair format (e.g., "XXBTZUSD")
fun String.isValidTradingPair(): Boolean {
    return this.matches(Regex("^[A-Z]{6,8}$"))
}

// Convert trading pair to display format (e.g., "XXBTZUSD" -> "BTC/USD")
fun String.toDisplayPair(): String {
    return if (this.length >= 6) {
        val base = this.substring(0, this.length / 2).removePrefix("X").removePrefix("Z")
        val quote = this.substring(this.length / 2).removePrefix("Z")
        "$base/$quote"
    } else {
        this
    }
}
