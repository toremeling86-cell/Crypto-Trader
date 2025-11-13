package com.cryptotrader.utils

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

/**
 * Secure HTTP logging interceptor that redacts sensitive information
 *
 * Redacts:
 * - API-Key header
 * - API-Sign header
 * - Authorization header
 * - API keys in request/response bodies
 * - Private keys and signatures
 */
class SecureLoggingInterceptor(
    private val level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.HEADERS
) : Interceptor {

    companion object {
        private val SENSITIVE_HEADERS = setOf(
            "api-key",
            "api-sign",
            "authorization",
            "x-api-key",
            "api-secret"
        )

        private const val REDACTED = "***REDACTED***"

        // Patterns to redact in body
        private val SENSITIVE_PATTERNS = listOf(
            Regex("\"apiKey\"\\s*:\\s*\"[^\"]+\""),
            Regex("\"apiSecret\"\\s*:\\s*\"[^\"]+\""),
            Regex("\"api_key\"\\s*:\\s*\"[^\"]+\""),
            Regex("\"signature\"\\s*:\\s*\"[^\"]+\""),
            Regex("\"nonce\"\\s*:\\s*\"\\d+\"")
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Log redacted request
        if (level != HttpLoggingInterceptor.Level.NONE) {
            Timber.tag("HTTP").d("→ ${request.method} ${request.url}")

            if (level == HttpLoggingInterceptor.Level.HEADERS || level == HttpLoggingInterceptor.Level.BODY) {
                logRedactedHeaders(request.headers)
            }
        }

        val response = chain.proceed(request)

        // Log redacted response
        if (level != HttpLoggingInterceptor.Level.NONE) {
            Timber.tag("HTTP").d("← ${response.code} ${response.request.url}")

            if (level == HttpLoggingInterceptor.Level.HEADERS || level == HttpLoggingInterceptor.Level.BODY) {
                logRedactedHeaders(response.headers)
            }
        }

        return response
    }

    private fun logRedactedHeaders(headers: Headers) {
        headers.names().forEach { name ->
            val value = if (name.lowercase() in SENSITIVE_HEADERS) {
                REDACTED
            } else {
                headers[name]
            }
            Timber.tag("HTTP").v("  $name: $value")
        }
    }

    private fun redactBody(body: String): String {
        var redacted = body
        SENSITIVE_PATTERNS.forEach { pattern ->
            redacted = redacted.replace(pattern) { matchResult ->
                val key = matchResult.value.substringBefore(":")
                "$key: \"$REDACTED\""
            }
        }
        return redacted
    }
}
