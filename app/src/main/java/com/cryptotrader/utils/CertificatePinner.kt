package com.cryptotrader.utils

import okhttp3.CertificatePinner
import timber.log.Timber

/**
 * Certificate pinning configuration for secure API connections
 *
 * Prevents man-in-the-middle attacks by validating SSL certificates
 * against known public keys (pins)
 */
object CertificatePinnerConfig {

    /**
     * Build certificate pinner for Kraken and Claude APIs
     *
     * PRODUCTION: Update these pins periodically (every 60-90 days)
     * Get pins using: openssl s_client -connect api.kraken.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
     */
    fun build(): CertificatePinner {
        return CertificatePinner.Builder()
            // Kraken API pins (api.kraken.com)
            .add(
                "api.kraken.com",
                // Primary certificate pin
                "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", // REPLACE WITH ACTUAL PIN
                // Backup certificate pin (in case of rotation)
                "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="  // REPLACE WITH ACTUAL PIN
            )
            // Claude API pins (api.anthropic.com)
            .add(
                "api.anthropic.com",
                // Primary certificate pin
                "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=", // REPLACE WITH ACTUAL PIN
                // Backup certificate pin
                "sha256/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD="  // REPLACE WITH ACTUAL PIN
            )
            .build()
    }

    /**
     * Get current certificate pins for monitoring
     *
     * IMPORTANT: Monitor these pins and update if they change
     */
    fun getPinInfo(): Map<String, List<String>> {
        return mapOf(
            "api.kraken.com" to listOf(
                "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
            ),
            "api.anthropic.com" to listOf(
                "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=",
                "sha256/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD="
            )
        )
    }

    /**
     * Validate certificate pinning is enabled
     */
    fun isEnabled(): Boolean {
        // In debug mode, we can disable pinning for testing
        // In production, it should ALWAYS be enabled
        return !com.cryptotrader.BuildConfig.DEBUG
    }
}

/**
 * INSTRUCTIONS FOR GETTING CERTIFICATE PINS:
 *
 * 1. For Kraken API (api.kraken.com):
 *    openssl s_client -servername api.kraken.com -connect api.kraken.com:443 | \
 *    openssl x509 -pubkey -noout | \
 *    openssl pkey -pubin -outform der | \
 *    openssl dgst -sha256 -binary | \
 *    openssl enc -base64
 *
 * 2. For Claude API (api.anthropic.com):
 *    openssl s_client -servername api.anthropic.com -connect api.anthropic.com:443 | \
 *    openssl x509 -pubkey -noout | \
 *    openssl pkey -pubin -outform der | \
 *    openssl dgst -sha256 -binary | \
 *    openssl enc -base64
 *
 * 3. Always pin at least 2 certificates:
 *    - Primary: Current certificate
 *    - Backup: Next certificate in rotation (ask provider)
 *
 * 4. Pin rotation schedule:
 *    - Check pins every 60 days
 *    - Update before expiration
 *    - Test in staging first
 *
 * 5. Pin format: sha256/[base64-encoded-public-key-hash]=
 *
 * SECURITY NOTES:
 * - DO NOT disable pinning in production
 * - Monitor pin expiration dates
 * - Have backup pins ready
 * - Test pin updates in staging environment
 * - Document pin rotation process
 */
