package com.cryptotrader.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cryptotrader.R
import com.cryptotrader.data.local.entities.TradingOpportunityEntity
import com.cryptotrader.presentation.MainActivity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages notifications for the AI Trading Advisor
 * Sends notifications for trading opportunities, market analysis updates, and risk alerts
 */
@Singleton
class AdvisorNotificationManager @Inject constructor(
    private val context: Context
) {

    companion object {
        // Notification channels
        private const val CHANNEL_ADVISOR = "advisor_high"
        private const val CHANNEL_ADVISOR_LOW = "advisor_low"

        // Notification IDs
        private const val NOTIFICATION_ID_OPPORTUNITY_BASE = 5000
        private const val NOTIFICATION_ID_ANALYSIS = 5500
        private const val NOTIFICATION_ID_RISK_ALERT = 5600

        // Deep linking keys
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_OPPORTUNITY_ASSET = "opportunity_asset"
        const val EXTRA_OPPORTUNITY_TIMESTAMP = "opportunity_timestamp"
        const val EXTRA_OPPORTUNITY_ID = "opportunity_id"

        // Screen destinations
        const val SCREEN_OPPORTUNITY_DETAILS = "opportunity_details"
        const val SCREEN_ADVISOR_DASHBOARD = "advisor_dashboard"
        const val SCREEN_ANALYSIS_DETAILS = "analysis_details"

        // Action IDs for broadcast receivers
        const val ACTION_VIEW_DETAILS = "com.cryptotrader.ACTION_VIEW_DETAILS"
        const val ACTION_DISMISS = "com.cryptotrader.ACTION_DISMISS"
    }

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels for Android O+
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val advisorChannel = NotificationChannel(
                CHANNEL_ADVISOR,
                "AI Trading Opportunities",
                AndroidNotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notifications for trading opportunities identified by AI"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            val advisorLowChannel = NotificationChannel(
                CHANNEL_ADVISOR_LOW,
                "AI Market Analysis",
                AndroidNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General market analysis and insights from AI advisor"
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
            notificationManager.createNotificationChannel(advisorChannel)
            notificationManager.createNotificationChannel(advisorLowChannel)

            Timber.d("Created AI Advisor notification channels")
        }
    }

    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No permission needed before Android 13
        }
    }

    /**
     * Send notification for a new trading opportunity
     * Uses BigTextStyle for expanded content and includes action buttons
     */
    fun notifyTradingOpportunity(opportunity: TradingOpportunityEntity) {
        if (!hasNotificationPermission()) {
            Timber.w("Notification permission not granted")
            return
        }

        try {
            // Create intent with deep link to opportunity details
            val viewIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_SCREEN, SCREEN_OPPORTUNITY_DETAILS)
                putExtra(EXTRA_OPPORTUNITY_ASSET, opportunity.asset)
                putExtra(EXTRA_OPPORTUNITY_TIMESTAMP, opportunity.timestamp)
                putExtra(EXTRA_OPPORTUNITY_ID, opportunity.id)
            }
            val viewPendingIntent = PendingIntent.getActivity(
                context,
                opportunity.id.toInt(),
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Dismiss action intent
            val dismissIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_OPPORTUNITY_ID, opportunity.id)
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                opportunity.id.toInt() + 10000,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Format notification content
            val title = formatOpportunityTitle(opportunity)
            val shortMessage = formatOpportunityShortMessage(opportunity)
            val expandedMessage = formatOpportunityExpandedMessage(opportunity)

            // Determine priority emoji
            val priorityEmoji = when (opportunity.priority) {
                "URGENT" -> "🔴"
                "HIGH" -> "🟠"
                "MEDIUM" -> "🟡"
                "LOW" -> "🟢"
                else -> "ℹ️"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ADVISOR)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO: Add proper advisor icon
                .setContentTitle("$priorityEmoji $title")
                .setContentText(shortMessage)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(expandedMessage)
                        .setBigContentTitle("$priorityEmoji $title")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setContentIntent(viewPendingIntent)
                .setAutoCancel(true)
                .addAction(
                    R.drawable.ic_launcher_foreground, // TODO: Add proper icon
                    "View Details",
                    viewPendingIntent
                )
                .addAction(
                    R.drawable.ic_launcher_foreground, // TODO: Add proper dismiss icon
                    "Dismiss",
                    dismissPendingIntent
                )
                .build()

            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_OPPORTUNITY_BASE + opportunity.id.toInt(),
                notification
            )

            Timber.d("Sent trading opportunity notification: ${opportunity.asset} ${opportunity.direction}")
        } catch (e: Exception) {
            Timber.e(e, "Error sending trading opportunity notification")
        }
    }

    /**
     * Send notification for completed market analysis
     */
    fun notifyAnalysisComplete(
        sentiment: String,
        opportunitiesCount: Int,
        analysisId: Long
    ) {
        if (!hasNotificationPermission()) return

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_SCREEN, SCREEN_ADVISOR_DASHBOARD)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                analysisId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val sentimentEmoji = when (sentiment) {
                "BULLISH" -> "📈"
                "BEARISH" -> "📉"
                "NEUTRAL" -> "➡️"
                "MIXED" -> "🔀"
                else -> "📊"
            }

            val title = "Market Analysis Complete"
            val message = if (opportunitiesCount > 0) {
                "$sentimentEmoji $sentiment market • $opportunitiesCount trading opportunities identified"
            } else {
                "$sentimentEmoji $sentiment market • No immediate opportunities"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ADVISOR_LOW)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_ANALYSIS,
                notification
            )

            Timber.d("Sent analysis complete notification: $sentiment")
        } catch (e: Exception) {
            Timber.e(e, "Error sending analysis notification")
        }
    }

    /**
     * Send notification for risk alert
     */
    fun notifyRiskAlert(
        asset: String,
        riskLevel: String,
        message: String
    ) {
        if (!hasNotificationPermission()) return

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_SCREEN, SCREEN_ADVISOR_DASHBOARD)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val title = "⚠️ Risk Alert: $asset"

            val notification = NotificationCompat.Builder(context, CHANNEL_ADVISOR)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_RISK_ALERT,
                notification
            )

            Timber.w("Sent risk alert notification for $asset")
        } catch (e: Exception) {
            Timber.e(e, "Error sending risk alert notification")
        }
    }

    /**
     * Cancel a specific opportunity notification
     */
    fun cancelOpportunityNotification(opportunityId: Long) {
        try {
            NotificationManagerCompat.from(context).cancel(
                NOTIFICATION_ID_OPPORTUNITY_BASE + opportunityId.toInt()
            )
            Timber.d("Cancelled opportunity notification: $opportunityId")
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling notification")
        }
    }

    /**
     * Cancel all advisor notifications
     */
    fun cancelAllAdvisorNotifications() {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            // Cancel common notification IDs
            for (i in 0..100) {
                notificationManager.cancel(NOTIFICATION_ID_OPPORTUNITY_BASE + i)
            }
            notificationManager.cancel(NOTIFICATION_ID_ANALYSIS)
            notificationManager.cancel(NOTIFICATION_ID_RISK_ALERT)
            Timber.d("Cancelled all advisor notifications")
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling all notifications")
        }
    }

    // ===== Formatting Helpers =====

    private fun formatOpportunityTitle(opportunity: TradingOpportunityEntity): String {
        return "${opportunity.direction} ${opportunity.asset}"
    }

    private fun formatOpportunityShortMessage(opportunity: TradingOpportunityEntity): String {
        val gainPercent = String.format("%.1f%%", opportunity.potentialGainPercent)
        val rr = String.format("%.1f", opportunity.riskRewardRatio)
        return "Entry: ${formatPrice(opportunity.entryPrice)} • Target: ${formatPrice(opportunity.targetPrice)} • Potential: +$gainPercent (R/R: $rr)"
    }

    private fun formatOpportunityExpandedMessage(opportunity: TradingOpportunityEntity): String {
        return buildString {
            append("Entry: ${formatPrice(opportunity.entryPrice)}\n")
            append("Target: ${formatPrice(opportunity.targetPrice)}\n")
            append("Stop Loss: ${formatPrice(opportunity.stopLoss)}\n")
            append("Potential Gain: ${String.format("%.1f%%", opportunity.potentialGainPercent)}\n")
            append("Risk/Reward: ${String.format("%.1f", opportunity.riskRewardRatio)}\n")
            append("Confidence: ${String.format("%.0f%%", opportunity.confidence * 100)}\n")
            append("Timeframe: ${formatTimeframe(opportunity.timeframe)}\n\n")
            append(opportunity.rationale)
        }
    }

    private fun formatPrice(price: Double): String {
        return when {
            price >= 1000 -> String.format("$%.2f", price)
            price >= 1 -> String.format("$%.4f", price)
            else -> String.format("$%.6f", price)
        }
    }

    private fun formatTimeframe(timeframe: String): String {
        return when (timeframe) {
            "SHORT_TERM" -> "Short-term (1-7 days)"
            "MEDIUM_TERM" -> "Medium-term (1-4 weeks)"
            "LONG_TERM" -> "Long-term (1-3 months)"
            else -> timeframe
        }
    }
}
