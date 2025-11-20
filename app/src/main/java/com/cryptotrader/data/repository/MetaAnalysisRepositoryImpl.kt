package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.MetaAnalysisDao
import com.cryptotrader.data.local.entities.MetaAnalysisEntity
import com.cryptotrader.domain.model.AnalysisStatus
import com.cryptotrader.domain.model.MarketOutlook
import com.cryptotrader.domain.model.MetaAnalysis
import com.cryptotrader.domain.model.RecommendedStrategy
import com.cryptotrader.domain.model.RiskLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MetaAnalysisRepository
 *
 * Manages persistence of Opus 4.1 meta-analysis results with proper error handling
 */
@Singleton
class MetaAnalysisRepositoryImpl @Inject constructor(
    private val metaAnalysisDao: MetaAnalysisDao,
    @com.cryptotrader.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MetaAnalysisRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== Analysis Operations ====================

    override suspend fun insertAnalysis(analysis: MetaAnalysis): Long = withContext(ioDispatcher) {
        try {
            val entity = analysis.toEntity()
            val id = metaAnalysisDao.insertAnalysis(entity)
            Timber.d("Inserted meta-analysis: ${analysis.strategyName} (ID: $id)")
            id
        } catch (e: Exception) {
            Timber.e(e, "Error inserting meta-analysis")
            throw e
        }
    }

    override suspend fun updateAnalysis(analysis: MetaAnalysis) = withContext(ioDispatcher) {
        try {
            val entity = analysis.toEntity()
            metaAnalysisDao.updateAnalysis(entity)
            Timber.d("Updated meta-analysis: ${analysis.strategyName}")
        } catch (e: Exception) {
            Timber.e(e, "Error updating meta-analysis")
            throw e
        }
    }

    override suspend fun getAnalysisById(id: Long): MetaAnalysis? = withContext(ioDispatcher) {
        try {
            metaAnalysisDao.getAnalysisById(id)?.toDomain()
        } catch (e: Exception) {
            Timber.e(e, "Error getting analysis by ID: $id")
            null
        }
    }

    override fun getAllAnalyses(): Flow<List<MetaAnalysis>> {
        return metaAnalysisDao.getAllAnalyses()
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting all analyses")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getRecentAnalyses(limit: Int): Flow<List<MetaAnalysis>> {
        // Calculate timestamp for last 30 days
        val sinceTimestamp = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        return metaAnalysisDao.getRecentAnalyses(sinceTimestamp)
            .map { entities -> entities.take(limit).map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting recent analyses (limit=$limit)")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getAnalysesByStatus(status: AnalysisStatus): Flow<List<MetaAnalysis>> {
        return metaAnalysisDao.getAnalysesByStatus(status.toString())
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting analyses by status: $status")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getPendingAnalyses(): Flow<List<MetaAnalysis>> {
        return getAnalysesByStatus(AnalysisStatus.PENDING)
    }

    override fun getApprovedAnalyses(): Flow<List<MetaAnalysis>> {
        return getAnalysesByStatus(AnalysisStatus.APPROVED)
    }

    override fun getActiveAnalyses(): Flow<List<MetaAnalysis>> {
        return getAnalysesByStatus(AnalysisStatus.ACTIVE)
    }

    // ==================== Approval Operations ====================

    override suspend fun approveAnalysis(analysisId: Long, strategyId: Long?) = withContext(ioDispatcher) {
        try {
            val timestamp = System.currentTimeMillis()
            metaAnalysisDao.markAsApproved(analysisId, timestamp)
            if (strategyId != null) {
                metaAnalysisDao.linkToStrategy(analysisId, strategyId)
            }
            Timber.i("Approved meta-analysis: $analysisId (strategyId: $strategyId)")
        } catch (e: Exception) {
            Timber.e(e, "Error approving analysis: $analysisId")
            throw e
        }
    }

    override suspend fun rejectAnalysis(analysisId: Long, reason: String) = withContext(ioDispatcher) {
        try {
            val timestamp = System.currentTimeMillis()
            metaAnalysisDao.markAsRejected(analysisId, reason, timestamp)
            Timber.i("Rejected meta-analysis: $analysisId (reason: $reason)")
        } catch (e: Exception) {
            Timber.e(e, "Error rejecting analysis: $analysisId")
            throw e
        }
    }

    override suspend fun markAnalysisAsActive(analysisId: Long, strategyId: Long) = withContext(ioDispatcher) {
        try {
            metaAnalysisDao.linkToStrategy(analysisId, strategyId)
            metaAnalysisDao.markAsActive(analysisId)
            Timber.i("Marked meta-analysis as active: $analysisId (strategyId: $strategyId)")
        } catch (e: Exception) {
            Timber.e(e, "Error marking analysis as active: $analysisId")
            throw e
        }
    }

    // ==================== Analytics Operations ====================

    override suspend fun getAverageConfidence(): Double? = withContext(ioDispatcher) {
        try {
            metaAnalysisDao.getAverageConfidence()
        } catch (e: Exception) {
            Timber.e(e, "Error getting average confidence")
            null
        }
    }

    override suspend fun getAnalysisCountByStatus(status: AnalysisStatus): Int = withContext(ioDispatcher) {
        try {
            metaAnalysisDao.getCountByStatus(status.toString())
        } catch (e: Exception) {
            Timber.e(e, "Error getting analysis count by status: $status")
            0
        }
    }

    override suspend fun deleteAnalysis(analysisId: Long) = withContext(ioDispatcher) {
        try {
            metaAnalysisDao.deleteAnalysisById(analysisId)
            Timber.d("Deleted meta-analysis: $analysisId")
        } catch (e: Exception) {
            Timber.e(e, "Error deleting analysis: $analysisId")
            throw e
        }
    }

    override suspend fun deleteAnalysesOlderThan(before: Long): Int = withContext(ioDispatcher) {
        try {
            // Delete old rejected analyses only
            metaAnalysisDao.deleteOldRejectedAnalyses(before)
            Timber.d("Deleted old rejected meta-analyses before $before")
            // Return 0 since DAO doesn't return count
            0
        } catch (e: Exception) {
            Timber.e(e, "Error deleting old analyses")
            0
        }
    }

    // ==================== Entity/Domain Mapping ====================

    private fun MetaAnalysis.toEntity(): MetaAnalysisEntity {
        return MetaAnalysisEntity(
            id = id,
            timestamp = timestamp,
            reportIds = json.encodeToString(reportIds),
            reportCount = reportCount,
            // Phase 3B: Temporal fields
            timeframe = timeframe.toString(),
            reportWeights = null, // Not persisting weights, calculated on-the-fly
            oldestReportDate = oldestReportDate,
            newestReportDate = newestReportDate,
            temporalWeightingApplied = if (temporalWeightingApplied) 1 else 0,
            findings = findings,
            consensus = consensus,
            contradictions = contradictions,
            marketOutlook = marketOutlook?.toString(),
            recommendedStrategyJson = json.encodeToString(recommendedStrategy),
            strategyName = strategyName,
            tradingPairs = json.encodeToString(tradingPairs),
            confidence = confidence,
            riskLevel = riskLevel.toString(),
            expectedReturn = expectedReturn,
            status = status.toString(),
            strategyId = strategyId,
            approvedAt = approvedAt,
            rejectedAt = rejectedAt,
            rejectionReason = rejectionReason,
            opusModel = opusModel,
            tokensUsed = tokensUsed,
            analysisTimeMs = analysisTimeMs
        )
    }

    private fun MetaAnalysisEntity.toDomain(): MetaAnalysis {
        return MetaAnalysis(
            id = id,
            timestamp = timestamp,
            reportIds = try {
                json.decodeFromString(reportIds)
            } catch (e: Exception) {
                emptyList()
            },
            reportCount = reportCount,
            // Phase 3B: Temporal fields
            timeframe = com.cryptotrader.domain.model.AnalysisTimeframe.fromString(timeframe ?: "WEEKLY"),
            oldestReportDate = oldestReportDate,
            newestReportDate = newestReportDate,
            temporalWeightingApplied = temporalWeightingApplied == 1,
            findings = findings,
            consensus = consensus,
            contradictions = contradictions,
            marketOutlook = marketOutlook?.let { MarketOutlook.fromString(it) },
            recommendedStrategy = try {
                json.decodeFromString(recommendedStrategyJson)
            } catch (e: Exception) {
                // Fallback to empty strategy
                RecommendedStrategy(
                    name = strategyName,
                    description = "Strategy details unavailable",
                    rationale = "",
                    tradingPairs = emptyList(),
                    entryConditions = emptyList(),
                    exitConditions = emptyList(),
                    positionSizePercent = 0.0,
                    stopLossPercent = 0.0,
                    takeProfitPercent = 0.0,
                    riskLevel = RiskLevel.MEDIUM,
                    confidenceScore = 0.0
                )
            },
            strategyName = strategyName,
            tradingPairs = try {
                json.decodeFromString(tradingPairs)
            } catch (e: Exception) {
                emptyList()
            },
            confidence = confidence,
            riskLevel = RiskLevel.fromString(riskLevel),
            expectedReturn = expectedReturn,
            status = AnalysisStatus.fromString(status),
            strategyId = strategyId,
            approvedAt = approvedAt,
            rejectedAt = rejectedAt,
            rejectionReason = rejectionReason,
            opusModel = opusModel,
            tokensUsed = tokensUsed,
            analysisTimeMs = analysisTimeMs
        )
    }
}
