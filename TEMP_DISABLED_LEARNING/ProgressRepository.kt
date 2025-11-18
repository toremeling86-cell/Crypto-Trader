package com.cryptotrader.data.repository

import com.cryptotrader.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Repository interface for Progress & Analytics features
 */
interface ProgressRepository {

    // Milestones
    suspend fun createMilestone(milestone: StudyMilestone): Result<Unit>

    suspend fun completeMilestone(milestoneId: String): Result<Unit>

    fun getMilestonesByPlan(planId: String): Flow<List<StudyMilestone>>

    fun getUpcomingMilestones(userId: String): Flow<List<StudyMilestone>>

    // Analytics
    suspend fun getReadingSpeed(userId: String): Result<Float>

    suspend fun getComprehensionScore(userId: String): Result<Float>

    suspend fun getLearningVelocity(userId: String): Result<LearningVelocity>

    suspend fun getPredictedCompletionDate(userId: String, bookId: String): Result<LocalDateTime?>

    // Recommendations
    suspend fun getNextBookRecommendations(userId: String): Result<List<BookRecommendation>>

    suspend fun getStudyTimeRecommendation(userId: String): Result<StudyTimeRecommendation>

    suspend fun getTopicRecommendations(userId: String): Result<List<KnowledgeTopic>>

    // Achievements & Gamification
    suspend fun checkAchievements(userId: String): Result<List<Achievement>>

    suspend fun getUserLevel(userId: String): Result<UserLevel>

    suspend fun getLeaderboard(timeframe: Timeframe): Result<List<LeaderboardEntry>>
}
