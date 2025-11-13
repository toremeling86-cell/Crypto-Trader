package com.cryptotrader.utils

import android.util.Log
import timber.log.Timber

/**
 * Production-safe Timber Tree that filters sensitive information
 *
 * Features:
 * - Only logs ERROR level in production
 * - Redacts sensitive patterns (API keys, balances, prices)
 * - Truncates long messages
 * - Adds crash reporting integration point
 */
class ProductionTree : Timber.Tree() {

    companion object {
        private const val MAX_LOG_LENGTH = 4000
        private const val MAX_TAG_LENGTH = 23

        // Patterns that indicate sensitive data
        private val SENSITIVE_KEYWORDS = listOf(
            "apikey",
            "api-key",
            "api_key",
            "secret",
            "password",
            "token",
            "signature",
            "balance",
            "profit",
            "loss",
            "p&l",
            "pnl",
            "volume",
            "price",
            "trade"
        )

        private const val REDACTED = "[REDACTED]"
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // In production, only log ERROR and above
        if (priority < Log.ERROR) {
            return
        }

        // Filter sensitive messages
        if (containsSensitiveData(message)) {
            val redactedMessage = redactSensitiveData(message)
            logToSystem(priority, tag, redactedMessage, t)
        } else {
            logToSystem(priority, tag, message, t)
        }

        // Send critical errors to crash reporting
        if (priority == Log.ERROR && t != null) {
            // TODO: Send to Firebase Crashlytics or Sentry
            // Crashlytics.logException(t)
        }
    }

    private fun containsSensitiveData(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return SENSITIVE_KEYWORDS.any { keyword ->
            lowerMessage.contains(keyword)
        }
    }

    private fun redactSensitiveData(message: String): String {
        var redacted = message

        // Redact numeric values that might be balances or prices
        redacted = redacted.replace(Regex("\\$\\d+\\.\\d+"), "$REDACTED")
        redacted = redacted.replace(Regex("\\d+\\.\\d+ USD"), "$REDACTED USD")
        redacted = redacted.replace(Regex("balance[:\\s=]+\\d+\\.?\\d*", RegexOption.IGNORE_CASE), "balance: $REDACTED")
        redacted = redacted.replace(Regex("profit[:\\s=]+[-+]?\\d+\\.?\\d*", RegexOption.IGNORE_CASE), "profit: $REDACTED")
        redacted = redacted.replace(Regex("price[:\\s=]+\\d+\\.?\\d*", RegexOption.IGNORE_CASE), "price: $REDACTED")

        // Redact API keys (anything that looks like a key)
        redacted = redacted.replace(Regex("[A-Za-z0-9+/=]{40,}"), REDACTED)

        return redacted
    }

    private fun logToSystem(priority: Int, tag: String?, message: String, t: Throwable?) {
        val truncatedTag = tag?.take(MAX_TAG_LENGTH) ?: "CryptoTrader"

        // Split long messages
        if (message.length <= MAX_LOG_LENGTH) {
            Log.println(priority, truncatedTag, message)
            t?.let { Log.println(priority, truncatedTag, Log.getStackTraceString(it)) }
        } else {
            // Split into chunks
            var i = 0
            while (i < message.length) {
                val end = minOf(i + MAX_LOG_LENGTH, message.length)
                Log.println(priority, truncatedTag, message.substring(i, end))
                i = end
            }
            t?.let { Log.println(priority, truncatedTag, Log.getStackTraceString(it)) }
        }
    }
}
