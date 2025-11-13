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

    fun getAllStrategies(): Flow<List<Strategy>> {
        return strategyDao.getAllStrategies().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getActiveStrategies(): Flow<List<Strategy>> {
        return strategyDao.getActiveStrategies().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getStrategyById(id: String): Strategy? {
        return strategyDao.getStrategyById(id)?.toDomain()
    }

    suspend fun insertStrategy(strategy: Strategy) {
        try {
            val entity = strategy.toEntity()
            strategyDao.insertStrategy(entity)
            Timber.d("Strategy saved: ${strategy.name}")
        } catch (e: Exception) {
            Timber.e(e, "Error saving strategy")
            throw e
        }
    }

    suspend fun updateStrategy(strategy: Strategy) {
        try {
            val entity = strategy.toEntity()
            strategyDao.updateStrategy(entity)
            Timber.d("Strategy updated: ${strategy.name}")
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
        source = source.toString()
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
        source = com.cryptotrader.domain.model.StrategySource.valueOf(source)
    )
}
