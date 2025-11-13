package com.cryptotrader.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Utilities for cryptographic operations including:
 * - HMAC-SHA512 signature generation for Kraken API
 * - Encrypted storage of API credentials
 */
object CryptoUtils {

    private const val PREFERENCES_NAME = "crypto_trader_encrypted"

    /**
     * Generate HMAC-SHA512 signature for Kraken API authentication
     *
     * Kraken's authentication process:
     * 1. SHA256(nonce + postData)
     * 2. HMAC-SHA512 of (urlPath + SHA256 result) with base64-decoded private key
     * 3. Base64 encode the signature
     *
     * @param urlPath API endpoint (e.g., "/0/private/Balance")
     * @param nonce Timestamp-based unique identifier
     * @param postData URL-encoded POST body
     * @param privateKey Base64-encoded Kraken private API key
     * @return Base64-encoded HMAC-SHA512 signature
     */
    fun generateKrakenSignature(
        urlPath: String,
        nonce: String,
        postData: String,
        privateKey: String
    ): String {
        return try {
            // Step 1: SHA256 hash of nonce + postData
            val sha256 = MessageDigest.getInstance("SHA-256")
            val noncePostData = nonce + postData
            val sha256Hash = sha256.digest(noncePostData.toByteArray(Charsets.UTF_8))

            // Step 2: Concatenate urlPath + SHA256 hash
            val message = urlPath.toByteArray(Charsets.UTF_8) + sha256Hash

            // Step 3: HMAC-SHA512 with base64-decoded private key
            val decodedKey = Base64.decode(privateKey, Base64.NO_WRAP)
            val hmacSha512 = Mac.getInstance("HmacSHA512")
            hmacSha512.init(SecretKeySpec(decodedKey, "HmacSHA512"))
            val signature = hmacSha512.doFinal(message)

            // Step 4: Base64 encode the signature (NO_WRAP to prevent newlines in HTTP headers)
            Base64.encodeToString(signature, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate Kraken signature", e)
        }
    }

    /**
     * Generate current nonce (milliseconds since epoch)
     * Each request must have a unique, increasing nonce
     */
    fun generateNonce(): String = System.currentTimeMillis().toString()

    /**
     * Initialize encrypted SharedPreferences for secure credential storage
     */
    fun getEncryptedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Store encrypted API credentials
     */
    fun saveApiCredentials(
        context: Context,
        publicKey: String,
        privateKey: String
    ) {
        val prefs = getEncryptedPreferences(context)
        prefs.edit().apply {
            putString("public_key", publicKey)
            putString("private_key", privateKey)
            putLong("last_updated", System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Retrieve encrypted API credentials
     */
    fun getApiCredentials(context: Context): Pair<String, String>? {
        return try {
            val prefs = getEncryptedPreferences(context)
            val publicKey = prefs.getString("public_key", null)
            val privateKey = prefs.getString("private_key", null)

            if (publicKey != null && privateKey != null) {
                Pair(publicKey, privateKey)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if API credentials exist
     */
    fun hasApiCredentials(context: Context): Boolean {
        return try {
            val prefs = getEncryptedPreferences(context)
            prefs.contains("public_key") && prefs.contains("private_key")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get public API key only
     */
    fun getPublicKey(context: Context): String? {
        return try {
            val prefs = getEncryptedPreferences(context)
            prefs.getString("public_key", null)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clear stored credentials (logout)
     */
    fun clearApiCredentials(context: Context) {
        val prefs = getEncryptedPreferences(context)
        prefs.edit().clear().apply()
    }

    /**
     * Get Claude API key from encrypted storage
     */
    fun getClaudeApiKey(context: Context): String? {
        return try {
            val prefs = getEncryptedPreferences(context)
            prefs.getString("claude_api_key", null)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save Claude API key to encrypted storage
     */
    fun saveClaudeApiKey(context: Context, apiKey: String) {
        val prefs = getEncryptedPreferences(context)
        prefs.edit().apply {
            putString("claude_api_key", apiKey)
            apply()
        }
    }

    /**
     * Check if paper trading mode is enabled
     * Default: TRUE for safety - require explicit opt-in to live trading
     */
    fun isPaperTradingMode(context: Context): Boolean {
        val prefs = getEncryptedPreferences(context)
        return prefs.getBoolean("paper_trading_mode", true) // Default TRUE
    }

    /**
     * Enable/disable paper trading mode
     * WARNING: Disabling this will use REAL MONEY for trades
     */
    fun setPaperTradingMode(context: Context, enabled: Boolean) {
        val prefs = getEncryptedPreferences(context)
        prefs.edit().apply {
            putBoolean("paper_trading_mode", enabled)
            putLong("mode_changed_at", System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Get paper trading balance (simulated funds)
     * Default: $10,000 USD
     */
    fun getPaperTradingBalance(context: Context): Double {
        val prefs = getEncryptedPreferences(context)
        return prefs.getFloat("paper_trading_balance", 10000f).toDouble()
    }

    /**
     * Update paper trading balance after simulated trade
     */
    fun setPaperTradingBalance(context: Context, balance: Double) {
        val prefs = getEncryptedPreferences(context)
        prefs.edit().apply {
            putFloat("paper_trading_balance", balance.toFloat())
            apply()
        }
    }

    /**
     * Reset paper trading balance to default $10,000
     */
    fun resetPaperTradingBalance(context: Context) {
        setPaperTradingBalance(context, 10000.0)
    }

    /**
     * Check if user has completed minimum paper trading period
     * Requirement: 30 days or 50 trades
     */
    fun hasCompletedPaperTrading(context: Context): Boolean {
        val prefs = getEncryptedPreferences(context)
        val paperTradingStarted = prefs.getLong("paper_trading_started", 0L)
        val paperTradingCount = prefs.getInt("paper_trading_count", 0)

        if (paperTradingStarted == 0L) {
            // First time using paper trading - save start time
            prefs.edit().apply {
                putLong("paper_trading_started", System.currentTimeMillis())
                apply()
            }
            return false
        }

        val daysPassed = (System.currentTimeMillis() - paperTradingStarted) / (1000 * 60 * 60 * 24)
        return daysPassed >= 30 || paperTradingCount >= 50
    }

    /**
     * Increment paper trading trade count
     */
    fun incrementPaperTradingCount(context: Context) {
        val prefs = getEncryptedPreferences(context)
        val current = prefs.getInt("paper_trading_count", 0)
        prefs.edit().apply {
            putInt("paper_trading_count", current + 1)
            apply()
        }
    }

    /**
     * Get paper trading statistics
     */
    fun getPaperTradingStats(context: Context): Triple<Int, Long, Boolean> {
        val prefs = getEncryptedPreferences(context)
        val count = prefs.getInt("paper_trading_count", 0)
        val started = prefs.getLong("paper_trading_started", System.currentTimeMillis())
        val daysPassed = (System.currentTimeMillis() - started) / (1000 * 60 * 60 * 24)
        val completed = hasCompletedPaperTrading(context)

        return Triple(count, daysPassed, completed)
    }

    /**
     * Check if user has accepted terms of service
     */
    fun hasAcceptedTerms(context: Context): Boolean {
        val prefs = getEncryptedPreferences(context)
        return prefs.getBoolean("terms_accepted", false)
    }

    /**
     * Mark terms of service as accepted
     */
    fun acceptTerms(context: Context) {
        val prefs = getEncryptedPreferences(context)
        prefs.edit().apply {
            putBoolean("terms_accepted", true)
            putLong("terms_accepted_at", System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Check if user has accepted risk disclaimer
     */
    fun hasAcceptedRiskDisclaimer(context: Context): Boolean {
        val prefs = getEncryptedPreferences(context)
        return prefs.getBoolean("risk_disclaimer_accepted", false)
    }

    /**
     * Mark risk disclaimer as accepted
     */
    fun acceptRiskDisclaimer(context: Context) {
        val prefs = getEncryptedPreferences(context)
        prefs.edit().apply {
            putBoolean("risk_disclaimer_accepted", true)
            putLong("risk_disclaimer_accepted_at", System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Check if emergency stop is active
     */
    fun isEmergencyStopActive(context: Context): Boolean {
        val prefs = getEncryptedPreferences(context)
        return prefs.getBoolean("emergency_stop_active", false)
    }

    /**
     * Activate emergency stop (halt all trading)
     */
    fun activateEmergencyStop(context: Context) {
        val prefs = getEncryptedPreferences(context)
        prefs.edit().apply {
            putBoolean("emergency_stop_active", true)
            putLong("emergency_stop_activated_at", System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Deactivate emergency stop (resume trading)
     */
    fun deactivateEmergencyStop(context: Context) {
        val prefs = getEncryptedPreferences(context)
        prefs.edit().apply {
            putBoolean("emergency_stop_active", false)
            putLong("emergency_stop_deactivated_at", System.currentTimeMillis())
            apply()
        }
    }
}
