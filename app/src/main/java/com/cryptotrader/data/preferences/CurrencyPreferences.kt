package com.cryptotrader.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Currency preferences manager
 * Stores user's preferred display currency (EUR or USD)
 */
class CurrencyPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Get selected currency (default: EUR to match Kraken)
     */
    fun getSelectedCurrency(): Currency {
        val currencyCode = prefs.getString(KEY_CURRENCY, Currency.EUR.code)
        return Currency.fromCode(currencyCode ?: Currency.EUR.code)
    }

    /**
     * Set selected currency
     */
    fun setSelectedCurrency(currency: Currency) {
        prefs.edit().putString(KEY_CURRENCY, currency.code).apply()
    }

    /**
     * Get the current EUR/USD exchange rate (cached)
     */
    fun getCachedEurUsdRate(): Double {
        return prefs.getFloat(KEY_EUR_USD_RATE, DEFAULT_EUR_USD_RATE).toDouble()
    }

    /**
     * Update the cached EUR/USD exchange rate
     */
    fun updateEurUsdRate(rate: Double) {
        prefs.edit().putFloat(KEY_EUR_USD_RATE, rate.toFloat()).apply()
    }

    companion object {
        private const val PREFS_NAME = "currency_preferences"
        private const val KEY_CURRENCY = "selected_currency"
        private const val KEY_EUR_USD_RATE = "eur_usd_rate"
        private const val DEFAULT_EUR_USD_RATE = 1.08f // Fallback rate
    }
}

/**
 * Supported currencies
 */
enum class Currency(val code: String, val symbol: String) {
    USD("USD", "$"),
    EUR("EUR", "€"),
    NOK("NOK", "kr");

    companion object {
        fun fromCode(code: String): Currency {
            return values().find { it.code == code } ?: EUR
        }
    }
}
