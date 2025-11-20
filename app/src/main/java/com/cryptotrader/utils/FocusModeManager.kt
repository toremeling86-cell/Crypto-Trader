package com.cryptotrader.utils

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Focus Mode Manager - Reduces emotional trading by hiding dollar amounts
 *
 * Purpose: Professional traders know that seeing large dollar swings creates
 * emotional responses that lead to poor decisions. Focus mode shows only percentages.
 *
 * Benefits:
 * - Reduces emotional attachment to specific dollar amounts
 * - Encourages percentage-based thinking (more scalable)
 * - Helps maintain discipline during volatility
 * - Professional trader mindset
 *
 * Integration Points:
 * - Portfolio screens (all P&L displays)
 * - Position screens (unrealized P&L)
 * - Performance charts (portfolio value)
 * - Analytics (total returns)
 */
@Singleton
class FocusModeManager @Inject constructor(
    private val preferences: SharedPreferences
) {
    private val _focusModeEnabled = MutableStateFlow(false)
    val focusModeEnabled: StateFlow<Boolean> = _focusModeEnabled.asStateFlow()

    companion object {
        private const val FOCUS_MODE_KEY = "focus_mode_enabled"
    }

    init {
        // Load saved preference
        _focusModeEnabled.value = preferences.getBoolean(FOCUS_MODE_KEY, false)
    }

    /**
     * Toggle focus mode on/off
     * Persists to SharedPreferences
     */
    fun toggleFocusMode() {
        val enabled = !_focusModeEnabled.value
        _focusModeEnabled.value = enabled
        preferences.edit().putBoolean(FOCUS_MODE_KEY, enabled).apply()
    }

    /**
     * Set focus mode explicitly
     */
    fun setFocusMode(enabled: Boolean) {
        _focusModeEnabled.value = enabled
        preferences.edit().putBoolean(FOCUS_MODE_KEY, enabled).apply()
    }

    /**
     * Format P&L for display
     *
     * Focus Mode OFF: "$123.45 (5.67%)"
     * Focus Mode ON: "5.67%"
     *
     * @param amount Dollar amount
     * @param percentage Percentage change
     * @return Formatted string
     */
    fun formatPnL(amount: BigDecimal, percentage: Double): String {
        return if (_focusModeEnabled.value) {
            // Show only percentage
            String.format("%.2f%%", percentage)
        } else {
            // Show both dollar and percentage
            "$${String.format("%.2f", amount)} (${String.format("%.2f", percentage)}%)"
        }
    }

    /**
     * Format portfolio value
     *
     * Focus Mode OFF: "$12,345.67"
     * Focus Mode ON: "••••••" (hidden)
     *
     * @param value Portfolio value
     * @return Formatted string or hidden placeholder
     */
    fun formatValue(value: BigDecimal): String {
        return if (_focusModeEnabled.value) {
            "••••••"
        } else {
            "$${String.format("%,.2f", value)}"
        }
    }

    /**
     * Format simple P&L amount
     *
     * Focus Mode OFF: "$123.45"
     * Focus Mode ON: "••••••"
     *
     * @param amount P&L amount
     * @return Formatted string or hidden
     */
    fun formatAmount(amount: BigDecimal): String {
        return if (_focusModeEnabled.value) {
            "••••••"
        } else {
            "$${String.format("%.2f", amount)}"
        }
    }

    /**
     * Format percentage only (always shown, even in focus mode)
     *
     * @param percentage Percentage value
     * @return Formatted percentage string
     */
    fun formatPercentage(percentage: Double): String {
        return String.format("%.2f%%", percentage)
    }

    /**
     * Check if currently in focus mode
     */
    fun isEnabled(): Boolean = _focusModeEnabled.value
}
