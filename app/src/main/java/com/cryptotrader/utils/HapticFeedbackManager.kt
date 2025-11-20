package com.cryptotrader.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Haptic Feedback Manager - Tactile confirmation for trading events
 *
 * Purpose: Provide subtle physical feedback for key trading events without
 * requiring users to look at the screen. Especially useful during volatile markets.
 *
 * Vibration Patterns:
 * - Trade Executed: Single subtle pulse (50ms) - Confirmation
 * - Stop Loss Hit: Double pulse (100-50-100ms) - Warning pattern
 * - Take Profit Hit: Success pattern (50-30-50-30-100ms) - Celebration
 * - Error: Sharp buzz (200ms) - Alert
 * - Button Press: Light tap (10ms) - Interaction feedback
 *
 * Settings:
 * - Master toggle (enable/disable all haptics)
 * - Intensity level (Low/Medium/High)
 * - Respects system vibration settings
 *
 * Integration Points:
 * - TradingEngine: tradeExecuted() when order fills
 * - RiskManager: stopLossHit() when SL triggered
 * - RiskManager: takeProfitHit() when TP triggered
 * - ErrorHandler: error() on critical failures
 * - UI Components: buttonPress() on important buttons (optional)
 */
@Singleton
class HapticFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: SharedPreferences
) {
    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    companion object {
        private const val HAPTICS_ENABLED_KEY = "haptics_enabled"
        private const val HAPTICS_INTENSITY_KEY = "haptics_intensity"
    }

    /**
     * Haptic intensity levels
     */
    enum class HapticIntensity(val multiplier: Double) {
        LOW(0.5),
        MEDIUM(1.0),
        HIGH(1.5)
    }

    /**
     * Check if haptic feedback is enabled
     */
    fun isEnabled(): Boolean {
        return preferences.getBoolean(HAPTICS_ENABLED_KEY, true) && vibrator.hasVibrator()
    }

    /**
     * Get current intensity setting
     */
    fun getIntensity(): HapticIntensity {
        val level = preferences.getInt(HAPTICS_INTENSITY_KEY, 1) // Default: MEDIUM
        return HapticIntensity.values().getOrElse(level) { HapticIntensity.MEDIUM }
    }

    /**
     * Set haptic feedback enabled/disabled
     */
    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(HAPTICS_ENABLED_KEY, enabled).apply()
    }

    /**
     * Set haptic intensity
     */
    fun setIntensity(intensity: HapticIntensity) {
        preferences.edit().putInt(HAPTICS_INTENSITY_KEY, intensity.ordinal).apply()
    }

    /**
     * Trade executed - Single subtle pulse
     * Triggered when an order is filled
     */
    fun tradeExecuted() {
        if (!isEnabled()) return
        val duration = (50 * getIntensity().multiplier).toLong()
        vibrate(duration)
    }

    /**
     * Stop loss hit - Double warning pulse
     * Triggered when stop loss is hit (losing trade)
     */
    fun stopLossHit() {
        if (!isEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 100, 50, 100),
                    -1 // No repeat
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 50, 100), -1)
        }
    }

    /**
     * Take profit hit - Success celebration pattern
     * Triggered when take profit target is hit (winning trade)
     */
    fun takeProfitHit() {
        if (!isEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 50, 30, 50, 30, 100),
                    -1 // No repeat
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 50, 30, 50, 30, 100), -1)
        }
    }

    /**
     * Error occurred - Sharp alert buzz
     * Triggered on critical errors or failed operations
     */
    fun error() {
        if (!isEnabled()) return
        vibrate(200)
    }

    /**
     * Button press - Light tap feedback
     * Optional: Use on important buttons for tactile confirmation
     */
    fun buttonPress() {
        if (!isEnabled()) return
        val duration = (10 * getIntensity().multiplier).toLong()
        vibrate(duration)
    }

    /**
     * Strategy activated - Success pattern
     * Triggered when a strategy is activated in LIVE mode
     */
    fun strategyActivated() {
        if (!isEnabled()) return
        takeProfitHit() // Reuse success pattern
    }

    /**
     * Strategy paused - Warning pattern
     * Triggered when a strategy is auto-paused due to risk
     */
    fun strategyPaused() {
        if (!isEnabled()) return
        stopLossHit() // Reuse warning pattern
    }

    /**
     * Generic success - Short positive feedback
     */
    fun success() {
        if (!isEnabled()) return
        val duration = (75 * getIntensity().multiplier).toLong()
        vibrate(duration)
    }

    /**
     * Generic warning - Short alert
     */
    fun warning() {
        if (!isEnabled()) return
        val duration = (150 * getIntensity().multiplier).toLong()
        vibrate(duration)
    }

    /**
     * Base vibration method
     * Handles API level differences
     */
    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: SecurityException) {
            // Permission not granted - fail silently
            timber.log.Timber.w("Haptic feedback failed: Missing VIBRATE permission")
        } catch (e: Exception) {
            // Other vibration errors - fail silently
            timber.log.Timber.w(e, "Haptic feedback failed")
        }
    }

    /**
     * Cancel any ongoing vibration
     */
    fun cancel() {
        vibrator.cancel()
    }
}
