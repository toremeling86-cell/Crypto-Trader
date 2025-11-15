package com.cryptotrader.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.StrategyRepository
import com.cryptotrader.domain.ai.ClaudeChatService
import com.cryptotrader.domain.model.ChatMessage
import com.cryptotrader.domain.model.Strategy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val claudeChatService: ClaudeChatService,
    private val strategyRepository: StrategyRepository,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_MESSAGES = "chat_messages"
        private const val KEY_HAS_WELCOMED = "has_welcomed"
    }

    private val _uiState = MutableStateFlow(ChatState())
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    init {
        // Restore saved messages if they exist
        val savedMessages = savedStateHandle.get<List<ChatMessage>>(KEY_MESSAGES)
        val hasWelcomed = savedStateHandle.get<Boolean>(KEY_HAS_WELCOMED) ?: false

        if (savedMessages != null && savedMessages.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(messages = savedMessages)
        } else if (!hasWelcomed) {
            addWelcomeMessage()
            savedStateHandle[KEY_HAS_WELCOMED] = true
        }
    }

    private fun addWelcomeMessage() {
        // Add welcome message
        val welcomeMessage = ChatMessage(
            content = """
                Hei! Jeg er din AI trading assistent.

                Jeg kan hjelpe deg med:
                • Lage trading strategier
                • Analysere markedet
                • Forklare strategier
                • Gi råd om risikostyring

                Hva kan jeg hjelpe deg med?
            """.trimIndent(),
            isFromUser = false
        )
        _uiState.value = _uiState.value.copy(messages = listOf(welcomeMessage))
    }

    /**
     * Send a message to Claude
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        viewModelScope.launch {
            try {
                // Add user message to UI
                val userMessage = ChatMessage(content = message, isFromUser = true)
                addMessage(userMessage)

                // Show typing indicator
                val typingMessage = ChatMessage(
                    content = "Tenker...",
                    isFromUser = false,
                    isTyping = true
                )
                addMessage(typingMessage)

                // Send to Claude
                val result = claudeChatService.sendMessage(
                    userMessage = message,
                    includePortfolioContext = true
                )

                // Remove typing indicator
                removeTypingIndicator()

                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    addMessage(response)

                    Timber.i("Claude response received: ${response.content.take(100)}")
                } else {
                    val errorMessage = ChatMessage(
                        content = "Beklager, jeg fikk ikke svar fra AI: ${result.exceptionOrNull()?.message}",
                        isFromUser = false
                    )
                    addMessage(errorMessage)
                }

            } catch (e: Exception) {
                Timber.e(e, "Error sending message")
                removeTypingIndicator()
                val errorMessage = ChatMessage(
                    content = "En feil oppstod: ${e.message}",
                    isFromUser = false
                )
                addMessage(errorMessage)
            }
        }
    }

    /**
     * Save strategy suggested by Claude as PENDING (for user approval)
     */
    fun implementStrategy(strategy: Strategy) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isImplementingStrategy = true)

                // Save strategy to database with PENDING status
                // User must approve it in Strategies tab before it becomes active
                strategyRepository.insertStrategy(strategy)

                Timber.i("Strategy saved as PENDING: ${strategy.name}")

                // Add success message
                val successMessage = ChatMessage(
                    content = """
                        ✅ Strategien "${strategy.name}" er lagret!

                        Gå til **Strategies**-fanen for å se analysen og godkjenne strategien.

                        Den vil ikke handle automatisk før du godkjenner den.
                    """.trimIndent(),
                    isFromUser = false
                )
                addMessage(successMessage)

                _uiState.value = _uiState.value.copy(
                    isImplementingStrategy = false,
                    successMessage = "Strategi lagret! Gå til Strategies for å godkjenne."
                )

            } catch (e: Exception) {
                Timber.e(e, "Error saving strategy")
                _uiState.value = _uiState.value.copy(
                    isImplementingStrategy = false,
                    errorMessage = "Kunne ikke lagre strategi: ${e.message}"
                )
            }
        }
    }

    /**
     * Clear chat history
     */
    fun clearChat() {
        claudeChatService.clearHistory()
        _uiState.value = ChatState()
        addWelcomeMessage() // Re-add welcome message
    }

    /**
     * Clear error/success messages
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    private fun addMessage(message: ChatMessage) {
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.add(message)
        _uiState.value = _uiState.value.copy(messages = currentMessages)

        // Save to SavedStateHandle for persistence
        savedStateHandle[KEY_MESSAGES] = currentMessages
    }

    private fun removeTypingIndicator() {
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.removeAll { it.isTyping }
        _uiState.value = _uiState.value.copy(messages = currentMessages)

        // Save to SavedStateHandle for persistence
        savedStateHandle[KEY_MESSAGES] = currentMessages
    }
}

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isImplementingStrategy: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
