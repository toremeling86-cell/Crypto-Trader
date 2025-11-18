package com.cryptotrader.data.repository

import com.cryptotrader.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Repository interface for Knowledge Base features
 */
interface KnowledgeBaseRepository {

    // Topic Management
    suspend fun createTopic(topic: KnowledgeTopic): Result<Unit>

    suspend fun updateTopic(topic: KnowledgeTopic): Result<Unit>

    suspend fun deleteTopic(topicId: String): Result<Unit>

    suspend fun getTopicById(topicId: String): Result<KnowledgeTopic?>

    fun getAllTopics(): Flow<List<KnowledgeTopic>>

    fun getStudiedTopics(): Flow<List<KnowledgeTopic>>

    fun getUnstudiedTopics(): Flow<List<KnowledgeTopic>>

    fun getTopicsByCategory(category: TopicCategory): Flow<List<KnowledgeTopic>>

    fun searchTopics(query: String): Flow<List<KnowledgeTopic>>

    // Topic Progress
    suspend fun markTopicStudied(topicId: String): Result<Unit>

    suspend fun updateMasteryLevel(topicId: String, level: MasteryLevel): Result<Unit>

    suspend fun scheduleTopicReview(topicId: String, reviewDate: LocalDateTime): Result<Unit>

    fun getTopicsForReview(): Flow<List<KnowledgeTopic>>

    suspend fun recordTopicStudyTime(topicId: String, minutes: Long): Result<Unit>

    // Topic Relationships
    suspend fun linkBookToTopic(bookId: String, topicId: String, relevanceScore: Float): Result<Unit>

    suspend fun unlinkBookFromTopic(bookId: String, topicId: String): Result<Unit>

    fun getTopicsForBook(bookId: String): Flow<List<KnowledgeTopic>>

    fun getBooksForTopic(topicId: String): Flow<List<LearningBook>>

    suspend fun addTopicPrerequisite(topicId: String, prerequisiteId: String): Result<Unit>

    suspend fun removeTopicPrerequisite(topicId: String, prerequisiteId: String): Result<Unit>

    // Learning Resources
    suspend fun addResourceToTopic(topicId: String, resource: LearningResource): Result<Unit>

    suspend fun removeResourceFromTopic(topicId: String, resourceId: String): Result<Unit>

    // Knowledge Base Overview
    suspend fun getKnowledgeBaseOverview(userId: String): Result<KnowledgeBaseOverview>

    suspend fun generateTopicRoadmap(userId: String, targetTopics: List<String>): Result<TopicRoadmap>
}

/**
 * Data class for topic roadmap
 */
data class TopicRoadmap(
    val startTopics: List<KnowledgeTopic>,
    val targetTopics: List<KnowledgeTopic>,
    val learningPath: List<KnowledgeTopic>,
    val estimatedDuration: Int, // in days
    val prerequisites: List<KnowledgeTopic>
)
