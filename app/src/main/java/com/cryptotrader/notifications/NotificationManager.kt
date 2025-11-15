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
import com.cryptotrader.presentation.MainActivity
import com.cryptotrader.R
import com.cryptotrader.domain.model.Trade
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages local push notifications for trading events
 */
@Singleton
class NotificationManager @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val CHANNEL_TRADES = "trades"
        private const val CHANNEL_ALERTS = "alerts"
        private const val CHANNEL_PERFORMANCE = "performance"

        private const val NOTIFICATION_ID_TRADE = 1000
        private const val NOTIFICATION_ID_ALERT = 2000
        private const val NOTIFICATION_ID_PERFORMANCE = 3000
    }

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels for Android O+
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val tradeChannel = NotificationChannel(
                CHANNEL_TRADES,
                "Trade Executions",
                AndroidNotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for executed trades"
                enableVibration(true)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Trading Alerts",
                AndroidNotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for stop-loss, take-profit, and price alerts"
                enableVibration(true)
            }

            val performanceChannel = NotificationChannel(
                CHANNEL_PERFORMANCE,
                "Performance Updates",
                AndroidNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Strategy performance and P&L updates"
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
            notificationManager.createNotificationChannel(tradeChannel)
            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(performanceChannel)
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
     * Send notification for trade execution
     */
    fun notifyTradeExecuted(trade: Trade) {
        if (!hasNotificationPermission()) {
            Timber.w("Notification permission not granted")
            return
        }

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val title = "${trade.type} ${trade.pair}"
            val message = "Executed at ${String.format("$%.2f", trade.price)} • Vol: ${String.format("%.4f", trade.volume)}"

            // Choose icon based on trade type
            val iconRes = when (trade.type.uppercase()) {
                "BUY" -> R.drawable.ic_notification_trade_buy
                "SELL" -> R.drawable.ic_notification_trade_sell
                else -> R.drawable.ic_notification_trade_buy
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_TRADES)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_TRADE + trade.id.toInt(),
                notification
            )

            Timber.d("Sent trade notification: $title")
        } catch (e: Exception) {
            Timber.e(e, "Error sending trade notification")
        }
    }

    /**
     * Send notification for stop-loss execution
     */
    fun notifyStopLossHit(pair: String, entryPrice: Double, exitPrice: Double, pnl: Double) {
        if (!hasNotificationPermission()) return

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val title = "🛑 Stop-Loss Hit: $pair"
            val message = "Entry: $${String.format("%.2f", entryPrice)} → Exit: $${String.format("%.2f", exitPrice)} • P&L: ${String.format("$%.2f", pnl)}"

            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification_stop_loss)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ALERT, notification)

            Timber.d("Sent stop-loss notification: $title")
        } catch (e: Exception) {
            Timber.e(e, "Error sending stop-loss notification")
        }
    }

    /**
     * Send notification for take-profit execution
     */
    fun notifyTakeProfitHit(pair: String, entryPrice: Double, exitPrice: Double, pnl: Double) {
        if (!hasNotificationPermission()) return

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val title = "✅ Take-Profit Hit: $pair"
            val message = "Entry: $${String.format("%.2f", entryPrice)} → Exit: $${String.format("%.2f", exitPrice)} • Profit: ${String.format("$%.2f", pnl)}"

            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification_take_profit)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ALERT + 1, notification)

            Timber.d("Sent take-profit notification: $title")
        } catch (e: Exception) {
            Timber.e(e, "Error sending take-profit notification")
        }
    }

    /**
     * Send notification for emergency stop activation
     */
    fun notifyEmergencyStop() {
        if (!hasNotificationPermission()) return

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification_emergency_stop)
                .setContentTitle("🚨 EMERGENCY STOP ACTIVATED")
                .setContentText("All automated trading has been halted")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ALERT + 2, notification)

            Timber.w("Sent emergency stop notification")
        } catch (e: Exception) {
            Timber.e(e, "Error sending emergency stop notification")
        }
    }

    /**
     * Clear emergency stop notification
     */
    fun clearEmergencyStopNotification() {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ALERT + 2)
        } catch (e: Exception) {
            Timber.e(e, "Error clearing emergency stop notification")
        }
    }

    /**
     * Send daily performance summary
     */
    fun notifyDailyPerformance(totalPnL: Double, dayPnL: Double, winRate: Double) {
        if (!hasNotificationPermission()) return

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val title = "📊 Daily Performance Summary"
            val message = "Today: ${String.format("$%.2f", dayPnL)} • Total: ${String.format("$%.2f", totalPnL)} • Win Rate: ${String.format("%.1f%%", winRate)}"

            val notification = NotificationCompat.Builder(context, CHANNEL_PERFORMANCE)
                .setSmallIcon(R.drawable.ic_notification_trade_buy)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PERFORMANCE, notification)

            Timber.d("Sent daily performance notification")
        } catch (e: Exception) {
            Timber.e(e, "Error sending performance notification")
        }
    }

    /**
     * Send notification for strategy activation
     */
    fun notifyStrategyActivated(strategyName: String) {
        if (!hasNotificationPermission()) return

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification_opportunity)
                .setContentTitle("Strategy Activated")
                .setContentText("$strategyName is now trading automatically")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ALERT + 3, notification)

            Timber.d("Sent strategy activation notification: $strategyName")
        } catch (e: Exception) {
            Timber.e(e, "Error sending strategy activation notification")
        }
    }

    /**
     * Send notification for large loss alert
     */
    fun notifyLargeLoss(pnl: Double, threshold: Double) {
        if (!hasNotificationPermission()) return

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification_stop_loss)
                .setContentTitle("⚠️ Large Loss Alert")
                .setContentText("Loss of ${String.format("$%.2f", kotlin.math.abs(pnl))} exceeds threshold of ${String.format("$%.2f", threshold)}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ALERT + 4, notification)

            Timber.w("Sent large loss alert notification")
        } catch (e: Exception) {
            Timber.e(e, "Error sending large loss notification")
        }
    }
}
