package com.cryptotrader.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cryptotrader.domain.ai.ClaudeMarketAnalyzer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * WorkManager Worker for scheduled market analysis
 *
 * Runs hourly to analyze market conditions using Claude AI
 * Triggered automatically in background, even when app is closed
 */
@HiltWorker
class MarketAnalysisWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val claudeMarketAnalyzer: ClaudeMarketAnalyzer
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Timber.i("Starting scheduled market analysis")

            // Perform market analysis
            val result = claudeMarketAnalyzer.analyzeMarket(
                triggerType = "SCHEDULED",
                includeExpertReports = false
            )

            if (result.isSuccess) {
                val analysisId = result.getOrNull()
                Timber.i("Scheduled market analysis completed successfully (ID: $analysisId)")
                Result.success()
            } else {
                val error = result.exceptionOrNull()
                Timber.e(error, "Scheduled market analysis failed")

                // Retry with exponential backoff
                Result.retry()
            }

        } catch (e: Exception) {
            Timber.e(e, "Error in MarketAnalysisWorker")

            // Retry on exceptions
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "market_analysis_periodic"
        const val TAG = "MarketAnalysis"
    }
}
