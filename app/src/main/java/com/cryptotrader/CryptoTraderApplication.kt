package com.cryptotrader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cryptotrader.workers.TradingWorker
import com.cryptotrader.workers.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class CryptoTraderApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workScheduler: WorkScheduler

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // SECURITY: Use ProductionTree that filters sensitive data in production
            Timber.plant(com.cryptotrader.utils.ProductionTree())
        }

        Timber.d("CryptoTrader Application started")

        // SECURITY: Perform security checks on startup
        performSecurityChecks()

        // Start trading worker if API keys are configured
        startTradingWorkerIfConfigured()

        // Start AI market analysis scheduling
        startMarketAnalysisIfConfigured()
    }

    /**
     * Perform comprehensive security checks on app startup
     */
    private fun performSecurityChecks() {
        try {
            val securityResult = com.cryptotrader.utils.RootDetection.performSecurityCheck(this)
            val recommendation = com.cryptotrader.utils.RootDetection.getSecurityRecommendation(securityResult)

            if (!securityResult.isSafe) {
                Timber.w("⚠️ SECURITY WARNING: ${recommendation}")
                securityResult.threats.forEach { threat ->
                    Timber.w("   - $threat")
                }

                // In production, you might want to:
                // 1. Show a warning dialog to the user
                // 2. Disable trading features on rooted devices
                // 3. Require additional authentication
                // 4. Log to crash reporting service

                if (securityResult.isRooted && !BuildConfig.DEBUG) {
                    Timber.e("⛔ CRITICAL: App is running on rooted device in production mode!")
                    // Consider preventing app launch or disabling sensitive features
                }
            } else {
                Timber.i("✅ Security check passed: Device is secure")
            }

            // Log certificate pinning status
            if (com.cryptotrader.utils.CertificatePinnerConfig.isEnabled()) {
                Timber.i("✅ Certificate pinning enabled for production")
            } else {
                Timber.w("⚠️ Certificate pinning disabled (DEBUG mode)")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error performing security checks")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Start trading worker if API credentials are configured
     */
    private fun startTradingWorkerIfConfigured() {
        try {
            // Check if API keys are set
            val hasApiKeys = com.cryptotrader.utils.CryptoUtils.hasApiCredentials(this)

            if (hasApiKeys) {
                scheduleTradingWorker()
                Timber.i("✅ Trading worker scheduled (runs every 1 minute)")
            } else {
                Timber.d("⏸️ Trading worker not started - API keys not configured")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking API configuration")
        }
    }

    /**
     * Schedule periodic trading worker
     * Called when API keys are configured or when user starts trading
     */
    fun scheduleTradingWorker() {
        try {
            val tradingWorkRequest = PeriodicWorkRequestBuilder<TradingWorker>(
                repeatInterval = 1, // Run every 1 minute
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "trading_worker",
                ExistingPeriodicWorkPolicy.UPDATE, // Update if already exists
                tradingWorkRequest
            )

            Timber.i("🚀 Trading worker scheduled successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule trading worker")
        }
    }

    /**
     * Stop trading worker
     */
    fun stopTradingWorker() {
        try {
            WorkManager.getInstance(this).cancelUniqueWork("trading_worker")
            Timber.i("⏹️ Trading worker stopped")
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop trading worker")
        }
    }

    /**
     * Start market analysis scheduling if Claude API key is configured
     */
    private fun startMarketAnalysisIfConfigured() {
        try {
            // Check if Claude API key is set
            val hasClaudeKey = com.cryptotrader.utils.CryptoUtils.getClaudeApiKey(this)?.isNotBlank() == true

            if (hasClaudeKey) {
                workScheduler.scheduleMarketAnalysis(enabled = true)
                Timber.i("✅ AI Market Analysis scheduled (runs every 1 hour)")
            } else {
                Timber.d("⏸️ AI Market Analysis not started - Claude API key not configured")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting market analysis scheduling")
        }
    }

    /**
     * Enable/disable market analysis scheduling
     */
    fun setMarketAnalysisEnabled(enabled: Boolean) {
        try {
            workScheduler.scheduleMarketAnalysis(enabled)
            if (enabled) {
                Timber.i("🤖 AI Market Analysis enabled")
            } else {
                Timber.i("⏸️ AI Market Analysis disabled")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update market analysis scheduling")
        }
    }
}
