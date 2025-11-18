package com.cryptotrader.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cryptotrader.utils.formatCurrency

/**
 * Trade confirmation dialog for live trading
 *
 * Shows:
 * - Clear warning that this is LIVE trading
 * - Trade details (pair, type, amount, price, estimated fees)
 * - Confirm/Cancel buttons
 */
@Composable
fun TradeConfirmationDialog(
    pair: String,
    tradeType: String, // "BUY" or "SELL"
    volume: Double,
    price: Double?,
    estimatedFee: Double,
    isLiveMode: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning banner for live trading
                if (isLiveMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ LIVE TRADING MODE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This will execute a REAL trade with REAL money",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "📄 PAPER TRADING MODE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Title
                Text(
                    text = "Confirm Trade",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Trade details
                TradeDetailRow(label = "Pair", value = pair.replace("XBT", "BTC"))
                Spacer(modifier = Modifier.height(12.dp))

                TradeDetailRow(
                    label = "Type",
                    value = tradeType,
                    valueColor = if (tradeType == "BUY")
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))

                TradeDetailRow(label = "Volume", value = String.format("%.8f", volume))
                Spacer(modifier = Modifier.height(12.dp))

                if (price != null) {
                    TradeDetailRow(label = "Price", value = price.formatCurrency())
                    Spacer(modifier = Modifier.height(12.dp))

                    val estimatedCost = volume * price
                    TradeDetailRow(
                        label = "Est. Cost",
                        value = estimatedCost.formatCurrency(),
                        isBold = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    TradeDetailRow(label = "Order Type", value = "Market")
                    Spacer(modifier = Modifier.height(12.dp))
                }

                TradeDetailRow(
                    label = "Est. Fee",
                    value = estimatedFee.formatCurrency(),
                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Confirm button
                    Button(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = if (isLiveMode) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(
                            text = if (isLiveMode) "Confirm LIVE Trade" else "Confirm",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeDetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
