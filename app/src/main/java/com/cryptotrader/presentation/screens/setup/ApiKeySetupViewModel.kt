package com.cryptotrader.presentation.screens.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.KrakenRepository
import com.cryptotrader.utils.CryptoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Base64
import javax.inject.Inject

@HiltViewModel
class ApiKeySetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val krakenRepository: KrakenRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiKeySetupState())
    val uiState: StateFlow<ApiKeySetupState> = _uiState.asStateFlow()

    init {
        checkExistingKeys()
    }

    private fun checkExistingKeys() {
        viewModelScope.launch {
            val hasKeys = CryptoUtils.hasApiCredentials(context)
            _uiState.value = _uiState.value.copy(hasExistingKeys = hasKeys)
        }
    }

    fun onPublicKeyChanged(key: String) {
        _uiState.value = _uiState.value.copy(
            publicKey = key,
            errorMessage = null
        )
    }

    fun onPrivateKeyChanged(key: String) {
        _uiState.value = _uiState.value.copy(
            privateKey = key,
            errorMessage = null
        )
    }

    fun saveApiKeys() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                val publicKey = _uiState.value.publicKey.trim()
                val privateKey = _uiState.value.privateKey.trim()

                // Validate empty keys
                if (publicKey.isBlank() || privateKey.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Both keys are required"
                    )
                    return@launch
                }

                // Validate format
                val formatError = validateKeyFormat(publicKey, privateKey)
                if (formatError != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = formatError
                    )
                    return@launch
                }

                // Test API keys with actual Kraken API call
                Timber.d("Testing API keys with Kraken...")
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = "Testing API keys with Kraken..."
                )

                // Temporarily save keys for testing
                CryptoUtils.saveApiCredentials(context, publicKey, privateKey)

                // Test with getBalance call
                val balanceResult = krakenRepository.getBalance()

                if (balanceResult.isFailure) {
                    // Remove invalid keys
                    CryptoUtils.clearApiCredentials(context)

                    val error = balanceResult.exceptionOrNull()
                    val errorMessage = when {
                        error?.message?.contains("Invalid key") == true ->
                            "Invalid API keys. Please check your Kraken API credentials."
                        error?.message?.contains("Permission denied") == true ->
                            "API keys don't have sufficient permissions. Please enable 'Query Funds' and 'Create & Modify Orders' in Kraken."
                        error?.message?.contains("timeout") == true ->
                            "Connection timeout. Please check your internet connection and try again."
                        else ->
                            "Failed to validate API keys: ${error?.message ?: "Unknown error"}"
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = errorMessage
                    )
                    return@launch
                }

                // SAFETY: Keep paper trading mode enabled by default
                // User must explicitly disable it in Settings to enable live trading
                Timber.i("✅ API keys validated - Paper trading mode remains enabled for safety")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    setupComplete = true,
                    errorMessage = null
                )

                Timber.d("API keys validated and saved successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error saving API keys")

                // Clean up any partially saved keys
                CryptoUtils.clearApiCredentials(context)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to save keys: ${e.message}"
                )
            }
        }
    }

    /**
     * Validate Kraken API key format
     * Public key: typically 56 characters, alphanumeric + special chars
     * Private key: base64 encoded, typically 88 characters
     */
    private fun validateKeyFormat(publicKey: String, privateKey: String): String? {
        // Validate public key format
        if (publicKey.length < 50 || publicKey.length > 64) {
            return "Invalid public key format. Kraken public keys are typically 56 characters long."
        }

        // Validate private key is valid base64
        if (privateKey.length < 80 || privateKey.length > 100) {
            return "Invalid private key format. Kraken private keys are typically 88 characters long."
        }

        // Test if private key is valid base64
        try {
            Base64.getDecoder().decode(privateKey)
        } catch (e: IllegalArgumentException) {
            return "Invalid private key format. Private key must be valid base64 encoding."
        }

        return null
    }
}

data class ApiKeySetupState(
    val publicKey: String = "",
    val privateKey: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val setupComplete: Boolean = false,
    val hasExistingKeys: Boolean = false
)
