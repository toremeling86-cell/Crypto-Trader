package com.cryptotrader.presentation.screens.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.CryptoTraderApplication
import com.cryptotrader.data.repository.KrakenRepository
import com.cryptotrader.utils.CryptoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val krakenRepository: KrakenRepository,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val isPaperMode = CryptoUtils.isPaperTradingMode(context)
                val hasApiKeys = CryptoUtils.hasApiCredentials(context)
                val apiKey = if (hasApiKeys) {
                    val key = CryptoUtils.getPublicKey(context) ?: ""
                    // Mask the key: show first 8 and last 4 characters
                    if (key.length > 12) {
                        "${key.take(8)}...${key.takeLast(4)}"
                    } else key
                } else ""

                // Load Claude API key status
                val claudeKey = CryptoUtils.getClaudeApiKey(context)
                val hasClaudeKey = !claudeKey.isNullOrBlank()
                val maskedClaudeKey = if (hasClaudeKey && claudeKey != null) {
                    maskClaudeApiKey(claudeKey)
                } else ""

                _uiState.value = _uiState.value.copy(
                    isPaperTradingMode = isPaperMode,
                    hasApiKeys = hasApiKeys,
                    maskedApiKey = apiKey,
                    hasClaudeApiKey = hasClaudeKey,
                    maskedClaudeApiKey = maskedClaudeKey,
                    isLoading = false
                )
            } catch (e: Exception) {
                Timber.e(e, "Error loading settings")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading settings: ${e.message}"
                )
            }
        }
    }

    fun onPaperTradingToggled(enabled: Boolean) {
        if (!enabled && !_uiState.value.hasApiKeys) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Cannot enable live trading without API keys"
            )
            return
        }

        // Show confirmation dialog instead of directly toggling
        _uiState.value = _uiState.value.copy(
            showPaperTradingConfirmDialog = true,
            pendingPaperTradingMode = enabled
        )
    }

    fun confirmPaperTradingToggle() {
        viewModelScope.launch {
            try {
                val enabled = _uiState.value.pendingPaperTradingMode
                CryptoUtils.setPaperTradingMode(context, enabled)

                _uiState.value = _uiState.value.copy(
                    isPaperTradingMode = enabled,
                    showPaperTradingConfirmDialog = false,
                    successMessage = if (enabled) {
                        "Paper trading mode ENABLED - All trades will be simulated"
                    } else {
                        "Live trading mode ENABLED - Real money will be used!"
                    }
                )

                Timber.i("Paper trading mode changed to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Error toggling paper trading")
                _uiState.value = _uiState.value.copy(
                    showPaperTradingConfirmDialog = false,
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    fun dismissPaperTradingDialog() {
        _uiState.value = _uiState.value.copy(
            showPaperTradingConfirmDialog = false
        )
    }

    fun onEditApiKeysClicked() {
        _uiState.value = _uiState.value.copy(
            showEditApiKeysDialog = true,
            editPublicKey = "",
            editPrivateKey = ""
        )
    }

    fun onPublicKeyChanged(key: String) {
        _uiState.value = _uiState.value.copy(editPublicKey = key)
    }

    fun onPrivateKeyChanged(key: String) {
        _uiState.value = _uiState.value.copy(editPrivateKey = key)
    }

    fun saveNewApiKeys() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val publicKey = _uiState.value.editPublicKey.trim()
                val privateKey = _uiState.value.editPrivateKey.trim()

                if (publicKey.isBlank() || privateKey.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Both keys are required"
                    )
                    return@launch
                }

                // Save temporarily and test
                CryptoUtils.saveApiCredentials(context, publicKey, privateKey)
                val balanceResult = krakenRepository.getBalance()

                if (balanceResult.isFailure) {
                    CryptoUtils.clearApiCredentials(context)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Invalid API keys: ${balanceResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showEditApiKeysDialog = false,
                    successMessage = "API keys updated successfully",
                    hasApiKeys = true
                )

                loadSettings() // Reload to update masked key
            } catch (e: Exception) {
                Timber.e(e, "Error saving API keys")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    fun dismissEditApiKeysDialog() {
        _uiState.value = _uiState.value.copy(
            showEditApiKeysDialog = false,
            editPublicKey = "",
            editPrivateKey = ""
        )
    }

    fun onLogoutClicked() {
        _uiState.value = _uiState.value.copy(showLogoutConfirmDialog = true)
    }

    fun confirmLogout() {
        viewModelScope.launch {
            try {
                // Stop trading worker
                val app = application as CryptoTraderApplication
                app.stopTradingWorker()

                // Clear all credentials
                CryptoUtils.clearApiCredentials(context)
                CryptoUtils.setPaperTradingMode(context, true) // Reset to safe default

                _uiState.value = _uiState.value.copy(
                    showLogoutConfirmDialog = false,
                    logoutComplete = true
                )

                Timber.i("User logged out successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error during logout")
                _uiState.value = _uiState.value.copy(
                    showLogoutConfirmDialog = false,
                    errorMessage = "Logout error: ${e.message}"
                )
            }
        }
    }

    fun dismissLogoutDialog() {
        _uiState.value = _uiState.value.copy(showLogoutConfirmDialog = false)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // Claude API Key Management
    private fun maskClaudeApiKey(key: String): String {
        return if (key.length > 20) {
            "sk-ant-***...${key.takeLast(8)}"
        } else "***"
    }

    fun onEditClaudeApiKeyClicked() {
        _uiState.value = _uiState.value.copy(
            showEditClaudeApiKeyDialog = true,
            editClaudeApiKey = ""
        )
    }

    fun onClaudeApiKeyChanged(key: String) {
        _uiState.value = _uiState.value.copy(editClaudeApiKey = key)
    }

    fun saveClaudeApiKey() {
        viewModelScope.launch {
            try {
                val key = _uiState.value.editClaudeApiKey.trim()

                if (key.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Claude API key cannot be empty"
                    )
                    return@launch
                }

                if (!key.startsWith("sk-ant-")) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Invalid Claude API key format (must start with sk-ant-)"
                    )
                    return@launch
                }

                CryptoUtils.saveClaudeApiKey(context, key)

                _uiState.value = _uiState.value.copy(
                    showEditClaudeApiKeyDialog = false,
                    hasClaudeApiKey = true,
                    maskedClaudeApiKey = maskClaudeApiKey(key),
                    successMessage = "✅ Claude API key saved successfully",
                    editClaudeApiKey = ""
                )

                Timber.i("Claude API key saved successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error saving Claude API key")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error saving Claude API key: ${e.message}"
                )
            }
        }
    }

    fun dismissClaudeApiKeyDialog() {
        _uiState.value = _uiState.value.copy(
            showEditClaudeApiKeyDialog = false,
            editClaudeApiKey = ""
        )
    }
}

data class SettingsState(
    val isLoading: Boolean = false,
    val isPaperTradingMode: Boolean = true,
    val hasApiKeys: Boolean = false,
    val maskedApiKey: String = "",
    val successMessage: String? = null,
    val errorMessage: String? = null,

    // Paper trading dialog
    val showPaperTradingConfirmDialog: Boolean = false,
    val pendingPaperTradingMode: Boolean = false,

    // Edit API keys dialog
    val showEditApiKeysDialog: Boolean = false,
    val editPublicKey: String = "",
    val editPrivateKey: String = "",

    // Claude API key
    val hasClaudeApiKey: Boolean = false,
    val maskedClaudeApiKey: String = "",
    val showEditClaudeApiKeyDialog: Boolean = false,
    val editClaudeApiKey: String = "",

    // Logout dialog
    val showLogoutConfirmDialog: Boolean = false,
    val logoutComplete: Boolean = false
)
