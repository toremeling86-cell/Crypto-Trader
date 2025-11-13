package com.cryptotrader.data.remote.kraken

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rate limiter for Kraken API to prevent hitting rate limits
 *
 * Kraken Rate Limits:
 * - Public API: ~1 request per second
 * - Private API: Counter-based system:
 *   - Each API call has a "cost"
 *   - Counter starts at 0, increases by call cost
 *   - Counter decays by 1 every 3 seconds
 *   - If counter exceeds limit, API returns error
 *
 * This implementation uses a simplified token bucket algorithm:
 * - Public API: 1 token per second, max 5 tokens
 * - Private API: More conservative, 1 token per 2 seconds, max 3 tokens
 */
@Singleton
class RateLimiter @Inject constructor() {

    private val publicMutex = Mutex()
    private val privateMutex = Mutex()

    // Public API rate limiting
    private var publicTokens: Int = 5
    private var publicLastRefill: Long = System.currentTimeMillis()
    private val publicMaxTokens = 5
    private val publicRefillMs = 1000L // 1 token per second

    // Private API rate limiting
    private var privateTokens: Int = 3
    private var privateLastRefill: Long = System.currentTimeMillis()
    private val privateMaxTokens = 3
    private val privateRefillMs = 2000L // 1 token per 2 seconds (conservative)

    /**
     * Wait if necessary before making a public API call
     */
    suspend fun waitForPublicApiPermission() {
        publicMutex.withLock {
            refillPublicTokens()

            if (publicTokens <= 0) {
                val waitTime = publicRefillMs
                Timber.d("Rate limit reached for public API, waiting ${waitTime}ms")
                delay(waitTime)
                refillPublicTokens()
            }

            publicTokens--
            Timber.v("Public API token consumed. Remaining: $publicTokens")
        }
    }

    /**
     * Wait if necessary before making a private API call
     */
    suspend fun waitForPrivateApiPermission() {
        privateMutex.withLock {
            refillPrivateTokens()

            if (privateTokens <= 0) {
                val waitTime = privateRefillMs
                Timber.d("Rate limit reached for private API, waiting ${waitTime}ms")
                delay(waitTime)
                refillPrivateTokens()
            }

            privateTokens--
            Timber.v("Private API token consumed. Remaining: $privateTokens")
        }
    }

    private fun refillPublicTokens() {
        val now = System.currentTimeMillis()
        val elapsed = now - publicLastRefill

        if (elapsed >= publicRefillMs) {
            val tokensToAdd = (elapsed / publicRefillMs).toInt()
            publicTokens = minOf(publicTokens + tokensToAdd, publicMaxTokens)
            publicLastRefill = now
        }
    }

    private fun refillPrivateTokens() {
        val now = System.currentTimeMillis()
        val elapsed = now - privateLastRefill

        if (elapsed >= privateRefillMs) {
            val tokensToAdd = (elapsed / privateRefillMs).toInt()
            privateTokens = minOf(privateTokens + tokensToAdd, privateMaxTokens)
            privateLastRefill = now
        }
    }

    /**
     * Reset rate limiter (useful for testing)
     */
    fun reset() {
        publicTokens = publicMaxTokens
        privateTokens = privateMaxTokens
        publicLastRefill = System.currentTimeMillis()
        privateLastRefill = System.currentTimeMillis()
    }
}
