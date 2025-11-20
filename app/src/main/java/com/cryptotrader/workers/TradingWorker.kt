package com.cryptotrader.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cryptotrader.data.repository.StrategyRepository
import com.cryptotrader.domain.model.TradingMode
import com.cryptotrader.domain.trading.StrategyExecutor
import com.cryptotrader.notifications.NotificationManager
import com.cryptotrader.utils.CryptoUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Trading Worker - Automated Strategy Execution
 *
 * Evaluates active trading strategies and executes trades based on market conditions.
 * Runs periodically (every 1 minute) via PeriodicWorkRequest.
 *
 * WORKFLOW:
 * 1. Pre-flight checks: Verify trading enabled, API keys, emergency stop
 * 2. Load active strategies from database
 * 3. For each strategy:
 *    - Fetch current market data
 *    - Evaluate entry/exit conditions
 *    - Execute trades if signal generated
 * 4. Monitor existing positions for stop-loss/take-profit
 * 5. Update portfolio and position tracking
 *
 * SAFETY FEATURES:
 * - Emergency stop check (disables all trading)
 * - Paper trading mode support
 * - Minimum balance checks
 * - Position size limits
 * - Retry policy with exponential backoff
 *
 * BigDecimal Migration (Phase 2.9):
 * - All monetary calculations use exact decimal arithmetic
 * - Position tracking uses BigDecimal fields
 * - Risk calculations maintain hedge-fund precision
 */
@HiltWorker
class TradingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val strategyRepository: StrategyRepository,
    private val strategyExecutor: StrategyExecutor,
    private val notificationManager: NotificationManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "trading_worker"
        const val TAG = "TradingWorker"
    }

    /**
     * Main worker execution method
     * Implements automated trading with comprehensive error handling
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.d("TradingWorker started (attempt ${runAttemptCount + 1})")

            // PHASE 1: Pre-flight Checks
            val preFlightResult = performPreFlightChecks()
            if (!preFlightResult.success) {
                Timber.d("Pre-flight checks failed: ${preFlightResult.reason}")
                return@withContext Result.success() // Success to avoid retries for expected conditions
            }

            // PHASE 2: Load Active Strategies
            val strategies = strategyRepository.getActiveStrategies().first()
            if (strategies.isEmpty()) {
                Timber.d("No active strategies. Trading worker idle.")
                return@withContext Result.success()
            }

            Timber.i("📊 Evaluating ${strategies.size} active strategies")

            // PHASE 3: Determine Trading Mode
            val isPaperTrading = CryptoUtils.isPaperTradingMode(context)
            val tradingMode = if (isPaperTrading) TradingMode.PAPER else TradingMode.LIVE

            Timber.i("🎯 Trading Mode: ${tradingMode.name}")

            // PHASE 4: Evaluate and Execute Each Strategy
            var signalsGenerated = 0
            var errors = 0

            strategies.forEach { strategy ->
                try {
                    Timber.d("Evaluating strategy: ${strategy.name}")

                    // Execute strategy (handles both entry and exit signals)
                    strategyExecutor.evaluateAndExecute(strategy, tradingMode)

                    signalsGenerated++

                } catch (e: Exception) {
                    Timber.e(e, "Error executing strategy: ${strategy.name}")
                    errors++
                }
            }

            // PHASE 5: Summary Logging
            Timber.i("✅ Trading worker completed: $signalsGenerated strategies evaluated, $errors errors")

            // Notify user if any trades were executed (notifications handled in StrategyExecutor)

            Result.success()

        } catch (e: Exception) {
            Timber.e(e, "Trading worker failed")

            // Retry with exponential backoff up to 3 attempts
            if (runAttemptCount < 2) {
                Timber.w("Retrying trading worker (attempt ${runAttemptCount + 2}/3)")
                Result.retry()
            } else {
                Timber.e("Trading worker failed after 3 attempts")
                notificationManager.notifyTradingWorkerFailed(e.message ?: "Unknown error")
                Result.failure()
            }
        }
    }

    /**
     * PHASE 1: Pre-flight Checks
     * Verify all conditions are met before executing trades
     */
    private fun performPreFlightChecks(): PreFlightResult {
        // Check 1: Emergency stop not active
        if (CryptoUtils.isEmergencyStopActive(context)) {
            return PreFlightResult(false, "Emergency stop is active")
        }

        // Check 2: API credentials configured (unless in paper trading mode)
        val isPaperTrading = CryptoUtils.isPaperTradingMode(context)
        if (!isPaperTrading) {
            val hasApiKeys = CryptoUtils.hasApiCredentials(context)
            if (!hasApiKeys) {
                return PreFlightResult(false, "Kraken API keys not configured")
            }
        }

        // Check 3: Minimum balance check (paper trading)
        if (isPaperTrading) {
            val paperBalance = CryptoUtils.getPaperTradingBalance(context)
            if (paperBalance < 10.0) {
                return PreFlightResult(false, "Paper trading balance too low: $$paperBalance")
            }
        }

        // All checks passed
        return PreFlightResult(true, "Ready to trade")
    }

    /**
     * Pre-flight check result
     */
    private data class PreFlightResult(
        val success: Boolean,
        val reason: String
    )
}
