package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.*
import com.cryptotrader.data.local.entity.*
import com.cryptotrader.domain.model.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of KnowledgeBaseRepository
 *
 * Manages persistence for knowledge topics, learning resources, and topic relationships
 * with proper error handling and logging
 */
@Singleton
class KnowledgeBaseRepositoryImpl @Inject constructor(
    private val topicDao: KnowledgeTopicDao,
    private val bookTopicDao: BookTopicDao,
    private val bookDao: LearningBookDao,
    @com.cryptotrader.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : KnowledgeBaseRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== Topic Management ====================

    override suspend fun createTopic(topic: KnowledgeTopic): Result<Unit> = withContext(ioDispatcher) {
        try {
            topicDao.insertTopic(topic.toEntity())
            Timber.d("Created knowledge topic: ${topic.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error creating topic: ${topic.name}")
            Result.failure(e)
        }
    }

    override suspend fun updateTopic(topic: KnowledgeTopic): Result<Unit> = withContext(ioDispatcher) {
        try {
            topicDao.updateTopic(topic.toEntity())
            Timber.d("Updated knowledge topic: ${topic.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error updating topic: ${topic.id}")
            Result.failure(e)
        }
    }

    override suspend fun deleteTopic(topicId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val topic = topicDao.getTopicById(topicId)
            if (topic != null) {
                topicDao.deleteTopic(topic)
                Timber.d("Deleted knowledge topic: $topicId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Topic not found: $topicId"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting topic: $topicId")
            Result.failure(e)
        }
    }

    override suspend fun getTopicById(topicId: String): Result<KnowledgeTopic?> =
        withContext(ioDispatcher) {
            try {
                val topic = topicDao.getTopicById(topicId)?.toDomain()
                Result.success(topic)
            } catch (e: Exception) {
                Timber.e(e, "Error getting topic by ID: $topicId")
                Result.failure(e)
            }
        }

    override fun getAllTopics(): Flow<List<KnowledgeTopic>> {
        return topicDao.getAllTopics()
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting all topics")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getStudiedTopics(): Flow<List<KnowledgeTopic>> {
        return topicDao.getStudiedTopics()
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting studied topics")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getUnstudiedTopics(): Flow<List<KnowledgeTopic>> {
        return topicDao.getUnstudiedTopics()
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting unstudied topics")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getTopicsByCategory(category: TopicCategory): Flow<List<KnowledgeTopic>> {
        return topicDao.getTopicsByCategory(category.toString())
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting topics by category: $category")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun searchTopics(query: String): Flow<List<KnowledgeTopic>> {
        return topicDao.searchTopics(query)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error searching topics: $query")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    // ==================== Topic Progress ====================

    override suspend fun markTopicStudied(topicId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val topic = topicDao.getTopicById(topicId)
            if (topic != null) {
                val updatedTopic = topic.copy(isStudied = true)
                topicDao.updateTopic(updatedTopic)
                Timber.d("Marked topic as studied: $topicId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Topic not found: $topicId"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error marking topic as studied: $topicId")
            Result.failure(e)
        }
    }

    override suspend fun updateMasteryLevel(topicId: String, level: MasteryLevel): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                topicDao.updateMasteryLevel(topicId, level.toString(), LocalDateTime.now())
                Timber.d("Updated mastery level for topic $topicId to $level")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Error updating mastery level: $topicId")
                Result.failure(e)
            }
        }

    override suspend fun scheduleTopicReview(topicId: String, reviewDate: LocalDateTime): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                val topic = topicDao.getTopicById(topicId)
                if (topic != null) {
                    val updatedTopic = topic.copy(nextReviewDate = reviewDate)
                    topicDao.updateTopic(updatedTopic)
                    Timber.d("Scheduled review for topic $topicId at $reviewDate")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Topic not found: $topicId"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error scheduling topic review: $topicId")
                Result.failure(e)
            }
        }

    override fun getTopicsForReview(): Flow<List<KnowledgeTopic>> {
        return topicDao.getTopicsForReview(LocalDateTime.now())
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting topics for review")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun recordTopicStudyTime(topicId: String, minutes: Long): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                topicDao.addStudyTime(topicId, minutes)
                Timber.d("Recorded $minutes minutes study time for topic: $topicId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Error recording study time for topic: $topicId")
                Result.failure(e)
            }
        }

    // ==================== Topic Relationships ====================

    override suspend fun linkBookToTopic(
        bookId: String,
        topicId: String,
        relevanceScore: Float
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val crossRef = BookTopicCrossRef(
                bookId = bookId,
                topicId = topicId,
                relevanceScore = relevanceScore
            )
            bookTopicDao.insertBookTopicRelation(crossRef)
            Timber.d("Linked book $bookId to topic $topicId with relevance $relevanceScore")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error linking book to topic: bookId=$bookId, topicId=$topicId")
            Result.failure(e)
        }
    }

    override suspend fun unlinkBookFromTopic(bookId: String, topicId: String): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                val crossRef = BookTopicCrossRef(
                    bookId = bookId,
                    topicId = topicId,
                    relevanceScore = 0f
                )
                bookTopicDao.deleteBookTopicRelation(crossRef)
                Timber.d("Unlinked book $bookId from topic $topicId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Error unlinking book from topic: bookId=$bookId, topicId=$topicId")
                Result.failure(e)
            }
        }

    override fun getTopicsForBook(bookId: String): Flow<List<KnowledgeTopic>> {
        return bookTopicDao.getTopicsByBookId(bookId)
            .map { crossRefs ->
                crossRefs.mapNotNull { crossRef ->
                    topicDao.getTopicById(crossRef.topicId)?.toDomain()
                }
            }
            .catch { e ->
                Timber.e(e, "Error getting topics for book: $bookId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getBooksForTopic(topicId: String): Flow<List<LearningBook>> {
        return bookTopicDao.getBooksByTopicId(topicId)
            .map { crossRefs ->
                crossRefs.mapNotNull { crossRef ->
                    bookDao.getBookById(crossRef.bookId)?.toDomain()
                }
            }
            .catch { e ->
                Timber.e(e, "Error getting books for topic: $topicId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun addTopicPrerequisite(topicId: String, prerequisiteId: String): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                val topic = topicDao.getTopicById(topicId)
                if (topic != null) {
                    val prerequisites: List<String> = try {
                        json.decodeFromString(topic.prerequisites)
                    } catch (e: Exception) {
                        emptyList()
                    }

                    if (!prerequisites.contains(prerequisiteId)) {
                        val updatedPrerequisites = prerequisites + prerequisiteId
                        val updatedTopic = topic.copy(
                            prerequisites = json.encodeToString(updatedPrerequisites)
                        )
                        topicDao.updateTopic(updatedTopic)
                        Timber.d("Added prerequisite $prerequisiteId to topic $topicId")
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Topic not found: $topicId"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error adding prerequisite: topicId=$topicId, prerequisiteId=$prerequisiteId")
                Result.failure(e)
            }
        }

    override suspend fun removeTopicPrerequisite(topicId: String, prerequisiteId: String): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                val topic = topicDao.getTopicById(topicId)
                if (topic != null) {
                    val prerequisites: List<String> = try {
                        json.decodeFromString(topic.prerequisites)
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val updatedPrerequisites = prerequisites.filter { it != prerequisiteId }
                    val updatedTopic = topic.copy(
                        prerequisites = json.encodeToString(updatedPrerequisites)
                    )
                    topicDao.updateTopic(updatedTopic)
                    Timber.d("Removed prerequisite $prerequisiteId from topic $topicId")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Topic not found: $topicId"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error removing prerequisite: topicId=$topicId, prerequisiteId=$prerequisiteId")
                Result.failure(e)
            }
        }

    // ==================== Learning Resources ====================

    override suspend fun addResourceToTopic(topicId: String, resource: LearningResource): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                val topic = topicDao.getTopicById(topicId)
                if (topic != null) {
                    val resources: List<LearningResource> = try {
                        json.decodeFromString(topic.resources)
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val updatedResources = resources + resource
                    val updatedTopic = topic.copy(
                        resources = json.encodeToString(updatedResources)
                    )
                    topicDao.updateTopic(updatedTopic)
                    Timber.d("Added resource ${resource.id} to topic $topicId")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Topic not found: $topicId"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error adding resource to topic: $topicId")
                Result.failure(e)
            }
        }

    override suspend fun removeResourceFromTopic(topicId: String, resourceId: String): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                val topic = topicDao.getTopicById(topicId)
                if (topic != null) {
                    val resources: List<LearningResource> = try {
                        json.decodeFromString(topic.resources)
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val updatedResources = resources.filter { it.id != resourceId }
                    val updatedTopic = topic.copy(
                        resources = json.encodeToString(updatedResources)
                    )
                    topicDao.updateTopic(updatedTopic)
                    Timber.d("Removed resource $resourceId from topic $topicId")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Topic not found: $topicId"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error removing resource from topic: $topicId")
                Result.failure(e)
            }
        }

    // ==================== Knowledge Base Overview ====================

    override suspend fun getKnowledgeBaseOverview(userId: String): Result<KnowledgeBaseOverview> =
        withContext(ioDispatcher) {
            try {
                // Get all topics synchronously for overview calculation
                val allTopics = getAllTopicsSync()

                val totalTopics = allTopics.size
                val studiedTopics = allTopics.count { it.isStudied }

                val masteryDistribution = allTopics
                    .groupBy { it.masteryLevel }
                    .mapValues { it.value.size }

                val topicsToReview = allTopics
                    .filter { topic ->
                        topic.nextReviewDate?.let { it.isBefore(LocalDateTime.now().plusDays(7)) } ?: false
                    }
                    .take(10)

                val recentlyStudied = allTopics
                    .filter { it.isStudied }
                    .sortedByDescending { it.lastReviewDate }
                    .take(10)

                val overview = KnowledgeBaseOverview(
                    totalTopics = totalTopics,
                    studiedTopics = studiedTopics,
                    masteryDistribution = masteryDistribution,
                    topicsToReview = topicsToReview,
                    recentlyStudied = recentlyStudied
                )

                Timber.d("Generated knowledge base overview for user: $userId")
                Result.success(overview)
            } catch (e: Exception) {
                Timber.e(e, "Error generating knowledge base overview")
                Result.failure(e)
            }
        }

    override suspend fun generateTopicRoadmap(
        userId: String,
        targetTopics: List<String>
    ): Result<TopicRoadmap> = withContext(ioDispatcher) {
        try {
            // TODO: Implement AI-powered topic roadmap generation
            val roadmap = TopicRoadmap(
                startTopics = emptyList(),
                targetTopics = emptyList(),
                learningPath = emptyList(),
                estimatedDuration = 0,
                prerequisites = emptyList()
            )
            Timber.d("Generated topic roadmap for user: $userId")
            Result.success(roadmap)
        } catch (e: Exception) {
            Timber.e(e, "Error generating topic roadmap")
            Result.failure(e)
        }
    }

    // ==================== Helper Methods ====================

    private suspend fun getAllTopicsSync(): List<KnowledgeTopic> {
        return try {
            topicDao.getAllTopics()
                .map { entities -> entities.map { it.toDomain() } }
                .catch { emit(emptyList()) }
                .flowOn(ioDispatcher)
                .first()
        } catch (e: Exception) {
            Timber.e(e, "Error getting all topics synchronously")
            emptyList()
        }
    }

    // ==================== Entity/Domain Mapping ====================

    private fun KnowledgeTopic.toEntity(): KnowledgeTopicEntity {
        return KnowledgeTopicEntity(
            topicId = id,
            name = name,
            category = category.toString(),
            description = description,
            importance = importance.toString(),
            masteryLevel = masteryLevel.toString(),
            lastReviewDate = lastReviewDate,
            nextReviewDate = nextReviewDate,
            totalStudyTimeMinutes = totalStudyTime.toMinutes(),
            relatedBooks = json.encodeToString(relatedBooks),
            relatedTopics = json.encodeToString(relatedTopics),
            prerequisites = json.encodeToString(prerequisites),
            resources = json.encodeToString(resources),
            notes = notes,
            isStudied = isStudied
        )
    }

    private fun KnowledgeTopicEntity.toDomain(): KnowledgeTopic {
        return KnowledgeTopic(
            id = topicId,
            name = name,
            category = TopicCategory.valueOf(category),
            description = description,
            importance = ImportanceLevel.valueOf(importance),
            masteryLevel = MasteryLevel.valueOf(masteryLevel),
            lastReviewDate = lastReviewDate,
            nextReviewDate = nextReviewDate,
            totalStudyTime = Duration.ofMinutes(totalStudyTimeMinutes),
            relatedBooks = try { json.decodeFromString(relatedBooks) } catch (e: Exception) { emptyList() },
            relatedTopics = try { json.decodeFromString(relatedTopics) } catch (e: Exception) { emptyList() },
            prerequisites = try { json.decodeFromString(prerequisites) } catch (e: Exception) { emptyList() },
            resources = try { json.decodeFromString(resources) } catch (e: Exception) { emptyList() },
            notes = notes,
            isStudied = isStudied
        )
    }

    private fun LearningBookEntity.toDomain(): LearningBook {
        return LearningBook(
            id = bookId,
            title = title,
            author = author,
            category = BookCategory.valueOf(category),
            filePath = filePath,
            fileSize = fileSize,
            pageCount = pageCount,
            uploadDate = uploadDate,
            lastOpenedDate = lastOpenedDate,
            coverImagePath = coverImagePath,
            isbn = isbn,
            publicationYear = publicationYear,
            language = language,
            tags = try { json.decodeFromString(tags) } catch (e: Exception) { emptyList() },
            readingProgress = readingProgress,
            isFavorite = isFavorite,
            analysisStatus = AnalysisStatus.valueOf(analysisStatus)
        )
    }
}
