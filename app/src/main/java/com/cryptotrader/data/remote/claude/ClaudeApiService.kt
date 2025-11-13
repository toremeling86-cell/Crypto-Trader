package com.cryptotrader.data.remote.claude

import com.cryptotrader.data.remote.claude.dto.ClaudeRequest
import com.cryptotrader.data.remote.claude.dto.ClaudeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Claude API Service for AI-powered strategy generation
 */
interface ClaudeApiService {

    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Body request: ClaudeRequest
    ): Response<ClaudeResponse>
}
