package com.cryptotrader.presentation.screens.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.CryptoTraderApplication
import com.cryptotrader.data.repository.KrakenRepository
import com.cryptotrader.utils.CryptoUtils
import com.cryptotrader.utils.FocusModeManager
import com.cryptotrader.utils.HapticFeedbackManager
import com.cryptotrader.utils.ThemeManager
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
    private val application: Application,
    private val focusModeManager: FocusModeManager,
    private val hapticFeedbackManager: HapticFeedbackManager,
    private val themeManager: ThemeManager
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

                // Load manager states
                val focusModeEnabled = focusModeManager.isEnabled()
                val hapticEnabled = hapticFeedbackManager.isEnabled()
                val hapticIntensity = hapticFeedbackManager.getIntensity()
                val currentTheme = themeManager.getCurrentTheme()
                val (marketStart, marketEnd) = themeManager.getMarketHours()

                _uiState.value = _uiState.value.copy(
                    isPaperTradingMode = isPaperMode,
                    hasApiKeys = hasApiKeys,
                    maskedApiKey = apiKey,
                    hasClaudeApiKey = hasClaudeKey,
                    maskedClaudeApiKey = maskedClaudeKey,
                    focusModeEnabled = focusModeEnabled,
                    hapticFeedbackEnabled = hapticEnabled,
                    hapticIntensity = hapticIntensity,
                    currentTheme = currentTheme,
                    marketHoursStart = marketStart,
                    marketHoursEnd = marketEnd,
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

                // Trim and remove ALL whitespace (spaces, tabs, newlines)
                val publicKey = _uiState.value.editPublicKey
                    .replace("\\s".toRegex(), "")  // Remove ALL whitespace
                val privateKey = _uiState.value.editPrivateKey
                    .replace("\\s".toRegex(), "")  // Remove ALL whitespace

                Timber.d("🔑 Cleaned public key length: ${publicKey.length}")
                Timber.d("🔑 Cleaned private key length: ${privateKey.length}")

                if (publicKey.isBlank() || privateKey.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Both keys are required"
                    )
                    return@launch
                }

                // Save temporarily and test
                CryptoUtils.saveApiCredentials(context, publicKey, privateKey)

                Timber.d("🔑 Testing Kraken API keys...")
                Timber.d("🔑 Public key length: ${publicKey.length}")
                Timber.d("🔑 Private key length: ${privateKey.length}")
                Timber.d("🔑 Public key starts with: ${publicKey.take(10)}")

                val balanceResult = krakenRepository.getBalance()

                if (balanceResult.isFailure) {
                    val error = balanceResult.exceptionOrNull()
                    Timber.e(error, "❌ API key test failed")

                    CryptoUtils.clearApiCredentials(context)

                    val detailedError = when {
                        error?.message?.contains("EAPI:Invalid key") == true ->
                            "Invalid API Key. Please check:\n" +
                            "1. API Key is copied correctly from Kraken\n" +
                            "2. No extra spaces before/after the key\n" +
                            "3. Key has 'Query Funds' permission enabled"
                        error?.message?.contains("EAPI:Invalid signature") == true ->
                            "Invalid API Secret. Please check:\n" +
                            "1. API Secret is copied correctly from Kraken\n" +
                            "2. No extra spaces before/after the secret\n" +
                            "3. Secret is the full base64 string"
                        error?.message?.contains("EAPI:Invalid nonce") == true ->
                            "Nonce error. Your device clock may be incorrect."
                        else ->
                            "API test failed: ${error?.message}\n\n" +
                            "Common issues:\n" +
                            "• API Key/Secret copied incorrectly\n" +
                            "• Missing 'Query Funds' permission\n" +
                            "• Extra spaces in keys"
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = detailedError
                    )
                    return@launch
                }

                Timber.i("✅ API keys validated successfully")

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

    // Focus Mode Management
    fun onFocusModeToggled(enabled: Boolean) {
        focusModeManager.setFocusMode(enabled)
        _uiState.value = _uiState.value.copy(
            focusModeEnabled = enabled,
            successMessage = if (enabled) {
                "Focus Mode enabled - Dollar amounts hidden"
            } else {
                "Focus Mode disabled - Dollar amounts visible"
            }
        )
        // Trigger haptic feedback for user confirmation
        if (enabled) hapticFeedbackManager.success() else hapticFeedbackManager.buttonPress()
    }

    // Haptic Feedback Management
    fun onHapticFeedbackToggled(enabled: Boolean) {
        hapticFeedbackManager.setEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            hapticFeedbackEnabled = enabled,
            successMessage = if (enabled) {
                "Haptic feedback enabled"
            } else {
                "Haptic feedback disabled"
            }
        )
        // Test haptic if enabling
        if (enabled) hapticFeedbackManager.success()
    }

    fun onHapticIntensityChanged(intensity: HapticFeedbackManager.HapticIntensity) {
        hapticFeedbackManager.setIntensity(intensity)
        _uiState.value = _uiState.value.copy(
            hapticIntensity = intensity
        )
        // Test the new intensity
        hapticFeedbackManager.buttonPress()
    }

    // Theme Management
    fun onThemeChanged(theme: ThemeManager.Theme) {
        themeManager.setTheme(theme)
        _uiState.value = _uiState.value.copy(
            currentTheme = theme,
            successMessage = "Theme changed to ${theme.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}"
        )
    }

    fun onMarketHoursChanged(startHour: Int, endHour: Int) {
        themeManager.setMarketHours(startHour, endHour)
        _uiState.value = _uiState.value.copy(
            marketHoursStart = startHour,
            marketHoursEnd = endHour,
            successMessage = "Market hours updated: ${startHour}:00 - ${endHour}:00"
        )
    }

    fun onShowMarketHoursDialogChanged(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            showMarketHoursDialog = show
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
    val logoutComplete: Boolean = false,

    // Focus Mode
    val focusModeEnabled: Boolean = false,

    // Haptic Feedback
    val hapticFeedbackEnabled: Boolean = true,
    val hapticIntensity: HapticFeedbackManager.HapticIntensity = HapticFeedbackManager.HapticIntensity.MEDIUM,

    // Theme
    val currentTheme: ThemeManager.Theme = ThemeManager.Theme.AUTO,
    val marketHoursStart: Int = 8,
    val marketHoursEnd: Int = 22,
    val showMarketHoursDialog: Boolean = false
)
