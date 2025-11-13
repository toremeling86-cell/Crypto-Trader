package com.cryptotrader.data.remote.kraken

import android.content.Context
import com.cryptotrader.utils.CryptoUtils
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import timber.log.Timber
import java.net.URLEncoder

/**
 * OkHttp Interceptor for Kraken API authentication
 * Adds required authentication headers for private endpoints
 */
class KrakenAuthInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()

        // Only add auth headers for private endpoints
        if (!url.contains("/private/")) {
            return chain.proceed(originalRequest)
        }

        // Get API credentials from encrypted storage
        val credentials = CryptoUtils.getApiCredentials(context)
            ?: throw IllegalStateException("No API credentials found. Please set up your Kraken API keys.")

        val (publicKey, privateKey) = credentials

        // Get URL path for signature (e.g., "/0/private/Balance")
        val urlPath = originalRequest.url.encodedPath

        // Get POST data from request body
        val postData = getPostData(originalRequest)
        Timber.d("🔐 Kraken Auth - POST data: $postData")

        // Extract nonce from POST data
        val nonce = extractNonce(postData)
        Timber.d("🔐 Kraken Auth - Extracted nonce: $nonce")
        Timber.d("🔐 Kraken Auth - URL path: $urlPath")
        Timber.d("🔐 Kraken Auth - Public key: ${publicKey.take(10)}...")

        // Generate signature
        val signature = CryptoUtils.generateKrakenSignature(
            urlPath = urlPath,
            nonce = nonce,
            postData = postData,
            privateKey = privateKey
        )
        Timber.d("🔐 Kraken Auth - Signature: ${signature.take(20)}...")

        // Add authentication headers
        val authenticatedRequest = originalRequest.newBuilder()
            .addHeader("API-Key", publicKey)
            .addHeader("API-Sign", signature)
            .build()

        return chain.proceed(authenticatedRequest)
    }

    /**
     * Extract POST data from request body
     * The body should already contain nonce from the API service
     */
    private fun getPostData(request: okhttp3.Request): String {
        val bodyBuffer = Buffer()
        request.body?.writeTo(bodyBuffer)
        return bodyBuffer.readUtf8()
    }

    /**
     * Extract nonce value from POST data
     * POST data format: "nonce=1234567890&other_field=value"
     */
    private fun extractNonce(postData: String): String {
        val nonceRegex = Regex("nonce=([^&]+)")
        val matchResult = nonceRegex.find(postData)
        return matchResult?.groupValues?.get(1)
            ?: throw IllegalStateException("Nonce not found in POST data: $postData")
    }
}
