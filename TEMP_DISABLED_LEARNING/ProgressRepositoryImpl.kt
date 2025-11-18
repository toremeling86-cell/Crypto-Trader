package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.*
import com.cryptotrader.data.local.entity.StudyMilestoneEntity
import com.cryptotrader.data.local.entity.StudyProgressEntity
import com.cryptotrader.domain.model.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ProgressRepository
 *
 * Manages persistence for progress analytics, milestones, and recommendations
 * with proper error handling and logging
 */
@Singleton
class ProgressRepositoryImpl @Inject constructor(
    private val milestoneDao: StudyMilestoneDao,
    private val studyPlanDao: StudyPlanDao,
    private val sessionDao: LearningSessionDao,
    private val progressDao: StudyProgressDao,
    private val bookDao: LearningBookDao,
    @com.cryptotrader.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ProgressRepository {

    // ==================== Milestones ====================

    override suspend fun createMilestone(milestone: StudyMilestone): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                // Note: We need a planId to create a milestone entity
                // This implementation assumes the milestone is created in context of a study plan
                // The actual planId should be passed or derived from context
                Timber.w("Creating milestone without planId - this needs plan context")
                Result.failure(Exception("Milestone creation requires study plan context"))
            } catch (e: Exception) {
                Timber.e(e, "Error creating milestone: ${milestone.id}")
                Result.failure(e)
            }
        }

    override suspend fun completeMilestone(milestoneId: String): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                milestoneDao.completeMilestone(milestoneId, LocalDateTime.now())
                Timber.d("Completed milestone: $milestoneId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Error completing milestone: $milestoneId")
                Result.failure(e)
            }
        }

    override fun getMilestonesByPlan(planId: String): Flow<List<StudyMilestone>> {
        return milestoneDao.getMilestonesByPlanId(planId)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting milestones for plan: $planId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getUpcomingMilestones(userId: String): Flow<List<StudyMilestone>> {
        return kotlinx.coroutines.flow.flow {
            try {
                val plans = studyPlanDao.getUserStudyPlans(userId).first()
                val allMilestones = mutableListOf<StudyMilestoneEntity>()

                plans.forEach { plan ->
                    try {
                        val milestones = milestoneDao.getMilestonesByPlanId(plan.planId).first()
                        allMilestones.addAll(milestones)
                    } catch (e: Exception) {
                        Timber.e(e, "Error getting milestones for plan: ${plan.planId}")
                    }
                }

                val upcoming = allMilestones
                    .map { it.toDomain() }
                    .filter { !it.isCompleted }
                    .sortedBy { it.targetDate }
                    .take(10)

                emit(upcoming)
            } catch (e: Exception) {
                Timber.e(e, "Error getting upcoming milestones for user: $userId")
                emit(emptyList())
            }
        }.flowOn(ioDispatcher)
    }

    // ==================== Analytics ====================

    override suspend fun getReadingSpeed(userId: String): Result<Float> = withContext(ioDispatcher) {
        try {
            // Calculate reading speed based on learning sessions
            val sessions = getRecentSessionsSync(userId, 30)
            val totalPages = sessions.mapNotNull { it.pagesRead }.sum()
            val totalHours = sessions.sumOf { it.duration.toMinutes() } / 60.0f

            val pagesPerHour = if (totalHours > 0) totalPages / totalHours else 0f
            Timber.d("Calculated reading speed for user $userId: $pagesPerHour pages/hour")
            Result.success(pagesPerHour)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating reading speed for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun getComprehensionScore(userId: String): Result<Float> =
        withContext(ioDispatcher) {
            try {
                // Calculate comprehension based on quiz scores
                // Note: quizScoreDao is not available in constructor, using alternative approach
                val progressRecords = getProgressRecordsSync(userId)

                // For now, use a placeholder comprehension calculation
                // TODO: Implement quiz score tracking when quizScoreDao is added
                val avgComprehension = if (progressRecords.isNotEmpty()) {
                    progressRecords.map { progress ->
                        when (progress.comprehensionLevel) {
                            "BEGINNER" -> 0.25f
                            "INTERMEDIATE" -> 0.50f
                            "ADVANCED" -> 0.75f
                            "EXPERT" -> 1.0f
                            else -> 0.0f
                        }
                    }.average().toFloat()
                } else {
                    0f
                }

                Timber.d("Calculated comprehension score for user $userId: $avgComprehension")
                Result.success(avgComprehension)
            } catch (e: Exception) {
                Timber.e(e, "Error calculating comprehension score for user: $userId")
                Result.failure(e)
            }
        }

    override suspend fun getLearningVelocity(userId: String): Result<LearningVelocity> =
        withContext(ioDispatcher) {
            try {
                val recentSessions = getRecentSessionsSync(userId, 30)
                val previousSessions = getSessionsInRangeSync(userId, 30, 60)

                // Calculate current metrics
                val currentPagesPerDay = recentSessions.mapNotNull { it.pagesRead }.sum() / 30.0f
                val currentTopicsPerWeek = recentSessions
                    .flatMap { it.topicsReviewed }
                    .distinct()
                    .size / 4.0f

                // Calculate previous metrics for comparison
                val previousPagesPerDay = previousSessions.mapNotNull { it.pagesRead }.sum() / 30.0f

                val improvementRate = if (previousPagesPerDay > 0) {
                    ((currentPagesPerDay - previousPagesPerDay) / previousPagesPerDay) * 100
                } else {
                    0f
                }

                val trend = when {
                    improvementRate > 10 -> Trend.IMPROVING
                    improvementRate < -10 -> Trend.DECLINING
                    else -> Trend.STABLE
                }

                val velocity = LearningVelocity(
                    pagesPerDay = currentPagesPerDay,
                    topicsPerWeek = currentTopicsPerWeek,
                    improvementRate = improvementRate,
                    trend = trend
                )

                Timber.d("Calculated learning velocity for user $userId: $velocity")
                Result.success(velocity)
            } catch (e: Exception) {
                Timber.e(e, "Error calculating learning velocity for user: $userId")
                Result.failure(e)
            }
        }

    override suspend fun getPredictedCompletionDate(
        userId: String,
        bookId: String
    ): Result<LocalDateTime?> = withContext(ioDispatcher) {
        try {
            val progress = progressDao.getProgressByUserAndBook(userId, bookId)
            val book = bookDao.getBookById(bookId)

            if (progress != null && book != null && book.pageCount != null) {
                val pagesRemaining = book.pageCount - progress.totalPagesRead
                val readingSpeed = getReadingSpeed(userId).getOrNull() ?: 0f

                val predictedDate = if (readingSpeed > 0) {
                    val hoursRemaining = pagesRemaining / readingSpeed
                    val avgSessionHours = progress.averageSessionDurationMinutes / 60.0f

                    val sessionsNeeded = if (avgSessionHours > 0) {
                        (hoursRemaining / avgSessionHours).toInt()
                    } else {
                        0
                    }

                    // Assuming sessions every other day on average
                    LocalDateTime.now().plusDays((sessionsNeeded * 2).toLong())
                } else {
                    null
                }

                Timber.d("Predicted completion date for book $bookId: $predictedDate")
                Result.success(predictedDate)
            } else {
                Timber.w("Cannot predict completion - missing data for user=$userId, book=$bookId")
                Result.success(null)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error predicting completion date")
            Result.failure(e)
        }
    }

    // ==================== Recommendations ====================

    override suspend fun getNextBookRecommendations(userId: String): Result<List<BookRecommendation>> =
        withContext(ioDispatcher) {
            try {
                // TODO: Implement AI-powered book recommendations
                // This would analyze completed books, comprehension levels, and learning goals
                Timber.d("Book recommendations not yet implemented for user: $userId")
                Result.success(emptyList())
            } catch (e: Exception) {
                Timber.e(e, "Error getting book recommendations for user: $userId")
                Result.failure(e)
            }
        }

    override suspend fun getStudyTimeRecommendation(userId: String): Result<StudyTimeRecommendation> =
        withContext(ioDispatcher) {
            try {
                val sessions = getRecentSessionsSync(userId, 30)

                // Calculate average productivity by time of day
                val morningProductivity = sessions
                    .filter { it.startTime.hour in 6..11 }
                    .mapNotNull { it.productivityScore }
                    .average()

                val afternoonProductivity = sessions
                    .filter { it.startTime.hour in 12..17 }
                    .mapNotNull { it.productivityScore }
                    .average()

                val eveningProductivity = sessions
                    .filter { it.startTime.hour in 18..22 }
                    .mapNotNull { it.productivityScore }
                    .average()

                val bestTimeOfDay = when {
                    morningProductivity >= afternoonProductivity && morningProductivity >= eveningProductivity -> "Morning (6-11 AM)"
                    afternoonProductivity >= eveningProductivity -> "Afternoon (12-5 PM)"
                    else -> "Evening (6-10 PM)"
                }

                // Calculate optimal daily commitment
                val avgSessionMinutes = sessions
                    .map { it.duration.toMinutes() }
                    .average()
                    .toInt()

                val optimalDailyMinutes = when {
                    avgSessionMinutes < 30 -> 45
                    avgSessionMinutes > 120 -> 90
                    else -> avgSessionMinutes
                }

                val recommendation = StudyTimeRecommendation(
                    optimalDailyMinutes = optimalDailyMinutes,
                    bestTimeOfDay = bestTimeOfDay,
                    recommendedBreakPattern = "25 minutes study, 5 minutes break (Pomodoro technique)",
                    focusTechniques = listOf(
                        "Eliminate distractions (phone on silent)",
                        "Use active reading techniques",
                        "Take notes while reading",
                        "Review previous day's material before starting"
                    )
                )

                Timber.d("Generated study time recommendation for user: $userId")
                Result.success(recommendation)
            } catch (e: Exception) {
                Timber.e(e, "Error generating study time recommendation for user: $userId")
                Result.failure(e)
            }
        }

    override suspend fun getTopicRecommendations(userId: String): Result<List<KnowledgeTopic>> =
        withContext(ioDispatcher) {
            try {
                // TODO: Implement topic recommendations based on learning goals and progress
                Timber.d("Topic recommendations not yet implemented for user: $userId")
                Result.success(emptyList())
            } catch (e: Exception) {
                Timber.e(e, "Error getting topic recommendations for user: $userId")
                Result.failure(e)
            }
        }

    // ==================== Achievements & Gamification ====================

    override suspend fun checkAchievements(userId: String): Result<List<Achievement>> =
        withContext(ioDispatcher) {
            try {
                // TODO: Implement achievement system
                // Check for milestones like:
                // - First book completed
                // - 10 books completed
                // - 30-day streak
                // - Perfect quiz scores
                // - Speed reader (high pages/hour)
                Timber.d("Achievement checking not yet implemented for user: $userId")
                Result.success(emptyList())
            } catch (e: Exception) {
                Timber.e(e, "Error checking achievements for user: $userId")
                Result.failure(e)
            }
        }

    override suspend fun getUserLevel(userId: String): Result<UserLevel> = withContext(ioDispatcher) {
        try {
            val progress = getProgressRecordsSync(userId)
            val completedBooks = progress.count { it.percentComplete >= 1.0f }
            val totalStudyHours = progress.sumOf { it.totalTimeSpentMinutes.toInt() } / 60

            // Simple XP calculation
            val xp = (completedBooks * 1000) + (totalStudyHours * 10).toInt()
            val level = (xp / 1000) + 1
            val xpToNextLevel = ((level * 1000) - xp).coerceAtLeast(0)

            val levelName = when (level) {
                in 1..5 -> "Novice Trader"
                in 6..10 -> "Apprentice Trader"
                in 11..20 -> "Skilled Trader"
                in 21..35 -> "Expert Trader"
                else -> "Master Trader"
            }

            val rank = when {
                completedBooks >= 50 -> "Legendary Scholar"
                completedBooks >= 25 -> "Master Scholar"
                completedBooks >= 10 -> "Advanced Scholar"
                completedBooks >= 5 -> "Scholar"
                else -> "Student"
            }

            val userLevel = UserLevel(
                currentLevel = level,
                levelName = levelName,
                currentXP = xp,
                xpToNextLevel = xpToNextLevel,
                rank = rank,
                badges = emptyList() // TODO: Implement badge system
            )

            Timber.d("Calculated user level for $userId: Level $level ($levelName)")
            Result.success(userLevel)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating user level for: $userId")
            Result.failure(e)
        }
    }

    override suspend fun getLeaderboard(timeframe: Timeframe): Result<List<LeaderboardEntry>> =
        withContext(ioDispatcher) {
            try {
                // TODO: Implement multi-user leaderboard
                // This requires user management system
                Timber.d("Leaderboard not yet implemented for timeframe: $timeframe")
                Result.success(emptyList())
            } catch (e: Exception) {
                Timber.e(e, "Error getting leaderboard for timeframe: $timeframe")
                Result.failure(e)
            }
        }

    // ==================== Helper Methods ====================

    private suspend fun getRecentSessionsSync(userId: String, days: Int): List<LearningSession> {
        return try {
            val since = LocalDateTime.now().minusDays(days.toLong())
            sessionDao.getRecentSessions(userId, since).first().map { it.toDomain() }
        } catch (e: Exception) {
            Timber.e(e, "Error getting recent sessions")
            emptyList()
        }
    }

    private suspend fun getSessionsInRangeSync(
        userId: String,
        startDaysAgo: Int,
        endDaysAgo: Int
    ): List<LearningSession> {
        return try {
            val start = LocalDateTime.now().minusDays(startDaysAgo.toLong())
            val end = LocalDateTime.now().minusDays(endDaysAgo.toLong())

            sessionDao.getRecentSessions(userId, end).first()
                .filter { it.startTime.isAfter(end) && it.startTime.isBefore(start) }
                .map { it.toDomain() }
        } catch (e: Exception) {
            Timber.e(e, "Error getting sessions in range")
            emptyList()
        }
    }

    private suspend fun getProgressRecordsSync(userId: String): List<StudyProgressEntity> {
        return try {
            progressDao.getUserProgress(userId).first()
        } catch (e: Exception) {
            Timber.e(e, "Error getting progress records")
            emptyList()
        }
    }

    // ==================== Entity/Domain Mapping ====================

    private fun StudyMilestoneEntity.toDomain(): StudyMilestone {
        return StudyMilestone(
            id = milestoneId,
            title = title,
            description = description,
            targetDate = targetDate,
            isCompleted = isCompleted,
            completedDate = completedDate,
            reward = reward
        )
    }

    private fun com.cryptotrader.data.local.entity.LearningSessionEntity.toDomain(): LearningSession {
        return LearningSession(
            id = sessionId,
            userId = userId,
            bookId = bookId,
            topicId = topicId,
            startTime = startTime,
            endTime = endTime,
            duration = java.time.Duration.ofMinutes(durationMinutes),
            pagesRead = pagesRead,
            chaptersCompleted = try {
                kotlinx.serialization.json.Json.decodeFromString(chaptersCompleted)
            } catch (e: Exception) {
                emptyList()
            },
            topicsReviewed = try {
                kotlinx.serialization.json.Json.decodeFromString(topicsReviewed)
            } catch (e: Exception) {
                emptyList()
            },
            sessionType = SessionType.valueOf(sessionType),
            productivityScore = productivityScore,
            distractions = distractions,
            notes = notes,
            mood = mood?.let { StudyMood.valueOf(it) }
        )
    }
}
