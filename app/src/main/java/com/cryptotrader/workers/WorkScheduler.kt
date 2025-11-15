package com.cryptotrader.workers

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages all WorkManager scheduling
 *
 * Schedules periodic tasks like:
 * - Hourly market analysis
 * - Market data fetching
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Schedule hourly market analysis
     * Runs every hour in background, even when app is closed
     */
    fun scheduleMarketAnalysis(enabled: Boolean = true) {
        if (enabled) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<MarketAnalysisWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 15,
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(MarketAnalysisWorker.TAG)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                MarketAnalysisWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Timber.i("Market analysis scheduled: Every 1 hour")
        } else {
            cancelMarketAnalysis()
        }
    }

    /**
     * Cancel scheduled market analysis
     */
    fun cancelMarketAnalysis() {
        workManager.cancelUniqueWork(MarketAnalysisWorker.WORK_NAME)
        Timber.i("Market analysis scheduling cancelled")
    }

    /**
     * Get status of market analysis work
     */
    fun getMarketAnalysisStatus() =
        workManager.getWorkInfosForUniqueWorkLiveData(MarketAnalysisWorker.WORK_NAME)

    /**
     * Trigger one-time immediate market analysis
     * (For testing or manual triggers)
     */
    fun triggerImmediateAnalysis() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<MarketAnalysisWorker>()
            .setConstraints(constraints)
            .addTag(MarketAnalysisWorker.TAG)
            .build()

        workManager.enqueue(workRequest)
        Timber.i("Immediate market analysis triggered")
    }

    /**
     * Cancel all work
     */
    fun cancelAllWork() {
        workManager.cancelAllWork()
        Timber.i("All scheduled work cancelled")
    }

    companion object {
        // Settings keys for SharedPreferences
        const val PREF_ANALYSIS_ENABLED = "market_analysis_enabled"
        const val PREF_ANALYSIS_INTERVAL_HOURS = "market_analysis_interval_hours"
    }
}
