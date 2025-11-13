package com.cryptotrader.presentation.screens.disclaimer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cryptotrader.utils.CryptoUtils

@Composable
fun DisclaimerScreen(
    onAccepted: () -> Unit
) {
    val context = LocalContext.current
    var termsAccepted by remember { mutableStateOf(false) }
    var riskAccepted by remember { mutableStateOf(false) }
    var lossConfirmed by remember { mutableStateOf(false) }

    val termsText = remember {
        try {
            context.assets.open("terms.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Terms and conditions could not be loaded."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with warning
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⚠️ IMPORTANT LEGAL NOTICE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You must read and accept these terms before using CryptoTrader",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Terms scrollable content
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = termsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Risk acknowledgement checkboxes
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF3E0) // Light orange
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "REQUIRED ACKNOWLEDGEMENTS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Checkbox 1: Terms accepted
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = termsAccepted,
                        onCheckedChange = { termsAccepted = it }
                    )
                    Text(
                        text = "I have read and accept the Terms of Service above",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Checkbox 2: Risk understood
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = riskAccepted,
                        onCheckedChange = { riskAccepted = it }
                    )
                    Text(
                        text = "I understand that cryptocurrency trading involves SUBSTANTIAL RISK OF LOSS",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD84315),
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Checkbox 3: Loss confirmed
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = lossConfirmed,
                        onCheckedChange = { lossConfirmed = it }
                    )
                    Text(
                        text = "I acknowledge that I MAY LOSE ALL FUNDS and will only trade with money I can afford to lose",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFBF360C),
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Accept button
        Button(
            onClick = {
                CryptoUtils.acceptTerms(context)
                CryptoUtils.acceptRiskDisclaimer(context)
                onAccepted()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = termsAccepted && riskAccepted && lossConfirmed,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (termsAccepted && riskAccepted && lossConfirmed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Text(
                text = if (termsAccepted && riskAccepted && lossConfirmed) {
                    "I Accept - Enter CryptoTrader"
                } else {
                    "You must check all boxes to continue"
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Exit button
        TextButton(
            onClick = { android.os.Process.killProcess(android.os.Process.myPid()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I Do Not Accept - Exit App")
        }
    }
}
