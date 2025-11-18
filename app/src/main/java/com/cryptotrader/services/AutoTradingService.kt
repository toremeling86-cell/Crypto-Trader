package com.cryptotrader.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cryptotrader.R
import com.cryptotrader.data.repository.StrategyRepository
import com.cryptotrader.domain.model.TradingMode
import com.cryptotrader.domain.trading.StrategyExecutor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service for auto-trading
 *
 * Continuously monitors and executes active trading strategies
 * Runs as foreground service to avoid being killed by system
 */
@AndroidEntryPoint
class AutoTradingService : Service() {

    @Inject
    lateinit var strategyRepository: StrategyRepository

    @Inject
    lateinit var strategyExecutor: StrategyExecutor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitoringJob: Job? = null

    private var activePaperStrategies = 0
    private var activeLiveStrategies = 0

    companion object {
        private const val CHANNEL_ID = "auto_trading_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MONITORING_INTERVAL_MS = 60_000L // 1 minute

        fun start(context: Context) {
            val intent = Intent(context, AutoTradingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoTradingService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("🤖 AutoTradingService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("🤖 AutoTradingService started")

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        // Start monitoring strategies
        startMonitoring()

        return START_STICKY // Restart service if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("🤖 AutoTradingService destroyed")
        monitoringJob?.cancel()
        serviceScope.cancel()
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Get active strategies
                    val strategies = strategyRepository.getActiveStrategies().first()

                    // Filter by trading mode
                    val paperStrategies = strategies.filter { it.tradingMode == TradingMode.PAPER }
                    val liveStrategies = strategies.filter { it.tradingMode == TradingMode.LIVE }

                    activePaperStrategies = paperStrategies.size
                    activeLiveStrategies = liveStrategies.size

                    Timber.d("🤖 Monitoring ${paperStrategies.size} Paper + ${liveStrategies.size} Live strategies")

                    // Update notification
                    updateNotification()

                    // If no active strategies, stop service
                    if (strategies.isEmpty()) {
                        Timber.i("🤖 No active strategies. Stopping service.")
                        stopSelf()
                        return@launch
                    }

                    // Execute Paper Trading strategies
                    paperStrategies.forEach { strategy ->
                        try {
                            strategyExecutor.evaluateAndExecute(strategy, TradingMode.PAPER)
                        } catch (e: Exception) {
                            Timber.e(e, "Error executing Paper strategy: ${strategy.name}")
                        }
                    }

                    // Execute Live Trading strategies
                    liveStrategies.forEach { strategy ->
                        try {
                            strategyExecutor.evaluateAndExecute(strategy, TradingMode.LIVE)
                        } catch (e: Exception) {
                            Timber.e(e, "Error executing Live strategy: ${strategy.name}")
                        }
                    }

                    // Wait for next iteration
                    delay(MONITORING_INTERVAL_MS)

                } catch (e: CancellationException) {
                    throw e // Re-throw cancellation
                } catch (e: Exception) {
                    Timber.e(e, "Error in monitoring loop")
                    delay(MONITORING_INTERVAL_MS) // Wait before retry
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Trading",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows auto-trading status"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto-Trading Active")
            .setContentText("Monitoring strategies...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentText = buildString {
            if (activePaperStrategies > 0) {
                append("📄 $activePaperStrategies Paper")
            }
            if (activeLiveStrategies > 0) {
                if (activePaperStrategies > 0) append(" • ")
                append("💰 $activeLiveStrategies Live")
            }
            if (activePaperStrategies == 0 && activeLiveStrategies == 0) {
                append("Monitoring...")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto-Trading Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
