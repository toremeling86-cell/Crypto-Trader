package com.cryptotrader.utils

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Theme Manager - Manages app theme (Light/Dark mode)
 *
 * Purpose: Provide flexible theming with auto-switching options
 *
 * Theme Options:
 * 1. LIGHT - Always light theme
 * 2. DARK - Always dark theme
 * 3. AUTO - Follow system theme preference
 * 4. AUTO_MARKET_HOURS - Dark during trading hours, otherwise follow system
 *
 * Market Hours Logic:
 * - Crypto markets are 24/7, but peak activity varies
 * - Optional: Auto-dark during user's active trading hours
 * - Reduces eye strain during extended trading sessions
 *
 * Integration:
 * - MainActivity observes theme changes
 * - CryptoTraderTheme wrapper applies MaterialTheme
 * - Settings screen provides theme selector
 */
@Singleton
class ThemeManager @Inject constructor(
    private val preferences: SharedPreferences
) {
    private val _theme = MutableStateFlow(Theme.AUTO)
    val theme: StateFlow<Theme> = _theme.asStateFlow()

    companion object {
        private const val THEME_KEY = "app_theme"
        private const val MARKET_HOURS_START_KEY = "market_hours_start" // Default: 8 AM
        private const val MARKET_HOURS_END_KEY = "market_hours_end" // Default: 10 PM
    }

    /**
     * Theme modes
     */
    enum class Theme {
        LIGHT,
        DARK,
        AUTO,
        AUTO_MARKET_HOURS
    }

    init {
        // Load saved theme
        val savedTheme = preferences.getString(THEME_KEY, Theme.AUTO.name)
        _theme.value = try {
            Theme.valueOf(savedTheme ?: Theme.AUTO.name)
        } catch (e: IllegalArgumentException) {
            Theme.AUTO
        }
    }

    /**
     * Set theme explicitly
     */
    fun setTheme(theme: Theme) {
        _theme.value = theme
        preferences.edit().putString(THEME_KEY, theme.name).apply()
    }

    /**
     * Determine if dark theme should be used
     *
     * @param isSystemDark Current system dark mode state
     * @return True if dark theme should be applied
     */
    fun shouldUseDarkTheme(isSystemDark: Boolean): Boolean {
        return when (_theme.value) {
            Theme.LIGHT -> false
            Theme.DARK -> true
            Theme.AUTO -> isSystemDark
            Theme.AUTO_MARKET_HOURS -> {
                if (isWithinMarketHours()) {
                    true // Always dark during market hours (reduces eye strain)
                } else {
                    isSystemDark // Follow system outside market hours
                }
            }
        }
    }

    /**
     * Check if currently within user's defined market hours
     */
    fun isWithinMarketHours(): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        val startHour = preferences.getInt(MARKET_HOURS_START_KEY, 8) // 8 AM default
        val endHour = preferences.getInt(MARKET_HOURS_END_KEY, 22) // 10 PM default

        return currentHour in startHour..endHour
    }

    /**
     * Set market hours for AUTO_MARKET_HOURS mode
     *
     * @param startHour Hour to start dark mode (0-23)
     * @param endHour Hour to end dark mode (0-23)
     */
    fun setMarketHours(startHour: Int, endHour: Int) {
        require(startHour in 0..23) { "Start hour must be 0-23" }
        require(endHour in 0..23) { "End hour must be 0-23" }

        preferences.edit()
            .putInt(MARKET_HOURS_START_KEY, startHour)
            .putInt(MARKET_HOURS_END_KEY, endHour)
            .apply()
    }

    /**
     * Get current market hours
     *
     * @return Pair of (startHour, endHour)
     */
    fun getMarketHours(): Pair<Int, Int> {
        val start = preferences.getInt(MARKET_HOURS_START_KEY, 8)
        val end = preferences.getInt(MARKET_HOURS_END_KEY, 22)
        return Pair(start, end)
    }

    /**
     * Get current theme setting
     */
    fun getCurrentTheme(): Theme = _theme.value
}
