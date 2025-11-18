package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.StrategyDao
import com.cryptotrader.data.local.entities.StrategyEntity
import com.cryptotrader.domain.model.RiskLevel
import com.cryptotrader.domain.model.Strategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StrategyRepository @Inject constructor(
    private val strategyDao: StrategyDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Validates a strategy to ensure it doesn't contain hardcoded price levels.
     * Strategies must use indicator-based conditions instead of absolute price values.
     *
     * @param strategy The strategy to validate
     * @return Result.success if valid, Result.failure with IllegalArgumentException if invalid
     */
    private fun validateStrategy(strategy: Strategy): Result<Unit> {
        // Regex patterns to detect hardcoded price levels
        val pricePatterns = listOf(
            // Dollar amounts: $42,500 or $2,100
            Regex("\\$\\s*\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?"),

            // Large numbers that look like crypto prices (4-5+ digits)
            Regex("\\b\\d{4,}(?:\\.\\d+)?\\b"),

            // Price comparisons with large numbers: "price > 42500"
            Regex("price\\s*[><=]+\\s*\\d{4,}"),

            // Trading pair with dollar amounts: "BTC: $42,500-43,500"
            Regex("[A-Z]{3,}:\\s*\\$\\s*\\d{1,3}(?:,\\d{3})*"),

            // Price ranges with large numbers: "42500-43500"
            Regex("\\d{4,}\\s*-\\s*\\d{4,}")
        )

        // Valid indicator-based patterns (these are allowed)
        val validIndicatorPatterns = listOf(
            Regex("RSI", RegexOption.IGNORE_CASE),
            Regex("MACD", RegexOption.IGNORE_CASE),
            Regex("SMA", RegexOption.IGNORE_CASE),
            Regex("EMA", RegexOption.IGNORE_CASE),
            Regex("Bollinger", RegexOption.IGNORE_CASE),
            Regex("ATR", RegexOption.IGNORE_CASE),
            Regex("Volume", RegexOption.IGNORE_CASE),
            Regex("\\d+%"), // Percentage-based conditions
            Regex("crossover", RegexOption.IGNORE_CASE),
            Regex("above|below", RegexOption.IGNORE_CASE)
        )

        // Combine all conditions for checking
        val allConditions = strategy.entryConditions + strategy.exitConditions

        for (condition in allConditions) {
            val trimmedCondition = condition.trim()

            // Skip empty conditions
            if (trimmedCondition.isEmpty()) continue

            // Check if condition contains hardcoded price patterns
            for (pattern in pricePatterns) {
                if (pattern.containsMatchIn(trimmedCondition)) {
                    // Check if this is a false positive by looking for valid indicators
                    val hasValidIndicator = validIndicatorPatterns.any {
                        it.containsMatchIn(trimmedCondition)
                    }

                    // Special case: Allow small numbers (likely percentages, timeframes, etc.)
                    val matchedValue = pattern.find(trimmedCondition)?.value
                    val isSmallNumber = matchedValue?.replace(Regex("[^0-9]"), "")?.toIntOrNull()?.let {
                        it < 1000
                    } ?: false

                    if (!hasValidIndicator && !isSmallNumber) {
                        Timber.w("Strategy validation failed: Hardcoded price found in condition '$trimmedCondition'")
                        return Result.failure(
                            IllegalArgumentException(
                                "Strategy contains hardcoded price levels in condition: '$trimmedCondition'. " +
                                "Please use indicator-based conditions instead (RSI, MACD, SMA, EMA, " +
                                "Bollinger Bands, ATR, Volume) or percentage-based conditions (e.g., 'price gain > 5%')."
                            )
                        )
                    }
                }
            }
        }

        Timber.d("Strategy validation passed: ${strategy.name}")
        return Result.success(Unit)
    }

    fun getAllStrategies(): Flow<List<Strategy>> {
        return strategyDao.getAllStrategies().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getActiveStrategies(): Flow<List<Strategy>> {
        // UPDATED: Now uses getActiveValidStrategies() to exclude soft-deleted strategies
        return strategyDao.getActiveValidStrategies().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get only valid strategies (excluding soft-deleted ones)
     * This should be used for most strategy queries
     */
    fun getValidStrategies(): Flow<List<Strategy>> {
        return strategyDao.getValidStrategies().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get invalid strategies (soft-deleted, for debugging)
     */
    fun getInvalidStrategies(): Flow<List<Strategy>> {
        return strategyDao.getInvalidStrategies().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Manually mark a strategy as invalid
     * Used for admin actions or external validation
     */
    suspend fun markStrategyAsInvalid(strategyId: String, reason: String) {
        try {
            strategyDao.markAsInvalid(strategyId, reason)
            Timber.w("Strategy marked as invalid: $strategyId - $reason")
        } catch (e: Exception) {
            Timber.e(e, "Error marking strategy as invalid")
            throw e
        }
    }

    /**
     * Restore a previously invalidated strategy
     */
    suspend fun restoreStrategy(strategyId: String) {
        try {
            strategyDao.restoreStrategy(strategyId)
            Timber.i("Strategy restored: $strategyId")
        } catch (e: Exception) {
            Timber.e(e, "Error restoring strategy")
            throw e
        }
    }

    suspend fun getStrategyById(id: String): Strategy? {
        return strategyDao.getStrategyById(id)?.toDomain()
    }

    suspend fun insertStrategy(strategy: Strategy) {
        try {
            val entity = strategy.toEntity()
            strategyDao.insertStrategy(entity)

            // Validate strategy AFTER insertion (soft-delete pattern)
            val validationResult = validateStrategy(strategy)
            if (validationResult.isFailure) {
                val reason = validationResult.exceptionOrNull()?.message ?: "Validation failed"
                strategyDao.markAsInvalid(strategy.id, reason)
                Timber.w("Strategy saved but marked as INVALID: ${strategy.name} - $reason")
            } else {
                Timber.d("Strategy saved and validated: ${strategy.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving strategy")
            throw e
        }
    }

    suspend fun updateStrategy(strategy: Strategy) {
        try {
            val entity = strategy.toEntity()
            strategyDao.updateStrategy(entity)

            // Re-validate strategy AFTER update (soft-delete pattern)
            val validationResult = validateStrategy(strategy)
            if (validationResult.isFailure) {
                val reason = validationResult.exceptionOrNull()?.message ?: "Validation failed"
                strategyDao.markAsInvalid(strategy.id, reason)
                Timber.w("Strategy updated but marked as INVALID: ${strategy.name} - $reason")
            } else {
                // If strategy was previously invalid but now passes validation, restore it
                strategyDao.restoreStrategy(strategy.id)
                Timber.d("Strategy updated and validated: ${strategy.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating strategy")
            throw e
        }
    }

    suspend fun deleteStrategy(strategy: Strategy) {
        try {
            strategyDao.deleteStrategyById(strategy.id)
            Timber.d("Strategy deleted: ${strategy.name}")
        } catch (e: Exception) {
            Timber.e(e, "Error deleting strategy")
            throw e
        }
    }

    suspend fun setStrategyActive(id: String, isActive: Boolean) {
        try {
            strategyDao.setStrategyActive(id, isActive)
            Timber.d("Strategy ${if (isActive) "activated" else "deactivated"}: $id")
        } catch (e: Exception) {
            Timber.e(e, "Error changing strategy status")
            throw e
        }
    }

    suspend fun approveStrategy(strategyId: String) {
        try {
            val strategy = getStrategyById(strategyId)
            if (strategy != null) {
                val approved = strategy.copy(
                    approvalStatus = com.cryptotrader.domain.model.ApprovalStatus.APPROVED
                )
                updateStrategy(approved)
                Timber.i("Strategy approved: ${strategy.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error approving strategy")
            throw e
        }
    }

    suspend fun rejectStrategy(strategyId: String) {
        try {
            val strategy = getStrategyById(strategyId)
            if (strategy != null) {
                val rejected = strategy.copy(
                    approvalStatus = com.cryptotrader.domain.model.ApprovalStatus.REJECTED,
                    isActive = false // Ensure rejected strategies are not active
                )
                updateStrategy(rejected)
                Timber.i("Strategy rejected: ${strategy.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error rejecting strategy")
            throw e
        }
    }

    fun getPendingStrategies(): Flow<List<Strategy>> {
        return strategyDao.getAllStrategies().map { entities ->
            entities.map { it.toDomain() }
                .filter { it.approvalStatus == com.cryptotrader.domain.model.ApprovalStatus.PENDING }
        }
    }

    suspend fun updateStrategyStats(
        strategyId: String,
        totalTrades: Int,
        successfulTrades: Int,
        failedTrades: Int,
        winRate: Double,
        totalProfit: Double
    ) {
        try {
            strategyDao.updateStrategyStats(
                strategyId = strategyId,
                totalTrades = totalTrades,
                successfulTrades = successfulTrades,
                failedTrades = failedTrades,
                winRate = winRate,
                totalProfit = totalProfit,
                lastExecuted = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error updating strategy stats")
            throw e
        }
    }

    private fun Strategy.toEntity() = StrategyEntity(
        id = id,
        name = name,
        description = description,
        entryConditions = json.encodeToString(entryConditions),
        exitConditions = json.encodeToString(exitConditions),
        positionSizePercent = positionSizePercent,
        stopLossPercent = stopLossPercent,
        takeProfitPercent = takeProfitPercent,
        tradingPairs = json.encodeToString(tradingPairs),
        isActive = isActive,
        tradingMode = tradingMode.toString(),
        createdAt = createdAt,
        lastExecuted = lastExecuted,
        totalTrades = totalTrades,
        successfulTrades = successfulTrades,
        failedTrades = failedTrades,
        winRate = winRate,
        totalProfit = totalProfit,
        riskLevel = riskLevel.toString(),
        analysisReport = analysisReport,
        approvalStatus = approvalStatus.toString(),
        source = source.toString(),
        // Phase 3C: Performance Tracking & Strategy Lineage
        metaAnalysisId = metaAnalysisId,
        sourceReportCount = sourceReportCount,
        maxDrawdown = maxDrawdown,
        avgWinAmount = avgWinAmount,
        avgLossAmount = avgLossAmount,
        profitFactor = profitFactor,
        sharpeRatio = sharpeRatio,
        largestWin = largestWin,
        largestLoss = largestLoss,
        currentStreak = currentStreak,
        longestWinStreak = longestWinStreak,
        longestLossStreak = longestLossStreak,
        performanceScore = performanceScore,
        isTopPerformer = if (isTopPerformer) 1 else 0,
        totalProfitPercent = totalProfitPercent
    )

    private fun StrategyEntity.toDomain() = Strategy(
        id = id,
        name = name,
        description = description,
        entryConditions = try {
            json.decodeFromString(entryConditions)
        } catch (e: Exception) {
            listOf(entryConditions)
        },
        exitConditions = try {
            json.decodeFromString(exitConditions)
        } catch (e: Exception) {
            listOf(exitConditions)
        },
        positionSizePercent = positionSizePercent,
        stopLossPercent = stopLossPercent,
        takeProfitPercent = takeProfitPercent,
        tradingPairs = try {
            json.decodeFromString(tradingPairs)
        } catch (e: Exception) {
            listOf(tradingPairs)
        },
        isActive = isActive,
        tradingMode = com.cryptotrader.domain.model.TradingMode.fromString(tradingMode),
        createdAt = createdAt,
        lastExecuted = lastExecuted,
        totalTrades = totalTrades,
        successfulTrades = successfulTrades,
        failedTrades = failedTrades,
        winRate = winRate,
        totalProfit = totalProfit,
        riskLevel = RiskLevel.fromString(riskLevel),
        analysisReport = analysisReport,
        approvalStatus = com.cryptotrader.domain.model.ApprovalStatus.valueOf(approvalStatus),
        source = com.cryptotrader.domain.model.StrategySource.valueOf(source),
        // Phase 3C: Performance Tracking & Strategy Lineage
        metaAnalysisId = metaAnalysisId,
        sourceReportCount = sourceReportCount,
        maxDrawdown = maxDrawdown,
        avgWinAmount = avgWinAmount,
        avgLossAmount = avgLossAmount,
        profitFactor = profitFactor,
        sharpeRatio = sharpeRatio,
        largestWin = largestWin,
        largestLoss = largestLoss,
        currentStreak = currentStreak,
        longestWinStreak = longestWinStreak,
        longestLossStreak = longestLossStreak,
        performanceScore = performanceScore,
        isTopPerformer = isTopPerformer == 1,
        totalProfitPercent = totalProfitPercent
    )
}
