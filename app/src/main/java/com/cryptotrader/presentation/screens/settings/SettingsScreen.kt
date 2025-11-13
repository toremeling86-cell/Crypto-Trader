package com.cryptotrader.presentation.screens.settings

import android.content.pm.PackageInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cryptotrader.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogoutComplete: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.logoutComplete) {
        if (state.logoutComplete) {
            onLogoutComplete()
        }
    }

    // Show success message
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSuccessMessage()
        }
    }

    // Show error message
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            kotlinx.coroutines.delay(5000)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Success/Error Messages
            state.successMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = Color.White
                    )
                }
            }

            state.errorMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Trading Mode Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Trading Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Paper Trading Mode",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (state.isPaperTradingMode) {
                                    "Trades are simulated (No real money)"
                                } else {
                                    "⚠️ LIVE TRADING - Real money is used!"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.isPaperTradingMode) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    Color.Red
                                }
                            )
                        }
                        Switch(
                            checked = state.isPaperTradingMode,
                            onCheckedChange = viewModel::onPaperTradingToggled,
                            enabled = !state.isLoading
                        )
                    }
                }
            }

            // API Keys Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "API Keys",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    if (state.hasApiKeys) {
                        Text(
                            text = "Public Key:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.maskedApiKey,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = viewModel::onEditApiKeysClicked,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Edit API Keys")
                        }
                    } else {
                        Text(
                            text = "No API keys configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Claude API Key Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFFD97757) // Claude brand color
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Claude AI API Key",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Required for AI-powered strategy generation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (state.hasClaudeApiKey) {
                        Text(
                            text = "API Key:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.maskedClaudeApiKey,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color(0xFF4CAF50) // Green to indicate configured
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = viewModel::onEditClaudeApiKeyClicked,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Edit Claude API Key")
                        }
                    } else {
                        Text(
                            text = "⚠️ No Claude API key configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Button(
                            onClick = viewModel::onEditClaudeApiKeyClicked,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Claude API Key")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Get your API key from console.anthropic.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // App Info Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "CryptoTrader",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Automated cryptocurrency trading bot with AI-powered strategy generation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Logout Button
            OutlinedButton(
                onClick = viewModel::onLogoutClicked,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout")
            }
        }
    }

    // Paper Trading Mode Confirmation Dialog
    if (state.showPaperTradingConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPaperTradingDialog,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (!state.pendingPaperTradingMode) Color.Red else MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = if (state.pendingPaperTradingMode) {
                        "Enable Paper Trading?"
                    } else {
                        "⚠️ Enable LIVE TRADING?"
                    }
                )
            },
            text = {
                Text(
                    text = if (state.pendingPaperTradingMode) {
                        "All trades will be simulated. No real money will be used."
                    } else {
                        "WARNING: Live trading will use REAL MONEY from your Kraken account. " +
                                "Make sure you understand the risks and have tested your strategies thoroughly in paper trading mode first.\n\n" +
                                "Are you absolutely sure you want to enable live trading?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmPaperTradingToggle,
                    colors = if (!state.pendingPaperTradingMode) {
                        ButtonDefaults.buttonColors(containerColor = Color.Red)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(if (state.pendingPaperTradingMode) "Enable Paper Trading" else "Enable Live Trading")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPaperTradingDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit API Keys Dialog
    if (state.showEditApiKeysDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEditApiKeysDialog,
            icon = { Icon(imageVector = Icons.Default.Key, contentDescription = null) },
            title = { Text("Edit API Keys") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.editPublicKey,
                        onValueChange = viewModel::onPublicKeyChanged,
                        label = { Text("Public Key") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.editPrivateKey,
                        onValueChange = viewModel::onPrivateKeyChanged,
                        label = { Text("Private Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Keys will be validated against Kraken API",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::saveNewApiKeys,
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save & Validate")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissEditApiKeysDialog,
                    enabled = !state.isLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Logout Confirmation Dialog
    if (state.showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLogoutDialog,
            icon = { Icon(imageVector = Icons.Default.Logout, contentDescription = null) },
            title = { Text("Logout") },
            text = {
                Text(
                    "Are you sure you want to logout? This will:\n" +
                            "• Stop all active trading\n" +
                            "• Clear your API keys\n" +
                            "• Reset to paper trading mode\n\n" +
                            "You will need to re-enter your API keys to continue."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLogoutDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Claude API Key Dialog
    if (state.showEditClaudeApiKeyDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClaudeApiKeyDialog,
            icon = {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = Color(0xFFD97757)
                )
            },
            title = { Text("Claude API Key") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.editClaudeApiKey,
                        onValueChange = viewModel::onClaudeApiKeyChanged,
                        label = { Text("API Key") },
                        placeholder = { Text("sk-ant-...") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Get your API key from console.anthropic.com\n" +
                                "• Required for AI strategy generation\n" +
                                "• Must start with 'sk-ant-'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::saveClaudeApiKey
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClaudeApiKeyDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}
