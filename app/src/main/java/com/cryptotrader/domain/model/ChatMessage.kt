package com.cryptotrader.domain.model

import java.time.Instant

/**
 * Chat message between user and Claude AI
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val suggestedStrategy: Strategy? = null, // If Claude suggests a strategy
    val isTyping: Boolean = false // For typing indicator
)
