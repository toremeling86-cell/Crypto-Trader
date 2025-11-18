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
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LearningRepository
 *
 * Manages persistence for Learning/Education features including books, analysis,
 * study plans, and progress tracking with proper error handling and logging
 */
@Singleton
class LearningRepositoryImpl @Inject constructor(
    private val bookDao: LearningBookDao,
    private val analysisDao: BookAnalysisDao,
    private val chapterDao: ChapterSummaryDao,
    private val studyPlanDao: StudyPlanDao,
    private val weeklyScheduleDao: WeeklyScheduleDao,
    private val evaluationDao: BookEvaluationDao,
    private val progressDao: StudyProgressDao,
    private val quizScoreDao: QuizScoreDao,
    private val noteDao: StudyNoteDao,
    private val bookmarkDao: BookmarkDao,
    private val sessionDao: LearningSessionDao,
    private val milestoneDao: StudyMilestoneDao,
    @com.cryptotrader.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LearningRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== Book Management ====================

    override suspend fun uploadBook(
        file: File,
        title: String,
        author: String?,
        category: BookCategory
    ): Result<LearningBook> = withContext(ioDispatcher) {
        try {
            val bookId = java.util.UUID.randomUUID().toString()
            val book = LearningBook(
                id = bookId,
                title = title,
                author = author,
                category = category,
                filePath = file.absolutePath,
                fileSize = file.length(),
                pageCount = null,
                uploadDate = LocalDateTime.now(),
                lastOpenedDate = null,
                coverImagePath = null,
                isbn = null,
                publicationYear = null,
                language = "en",
                tags = emptyList(),
                readingProgress = 0f,
                isFavorite = false,
                analysisStatus = AnalysisStatus.NOT_ANALYZED
            )
            bookDao.insertBook(book.toEntity())
            Timber.d("Uploaded book: $title (ID: $bookId)")
            Result.success(book)
        } catch (e: Exception) {
            Timber.e(e, "Error uploading book: $title")
            Result.failure(e)
        }
    }

    override suspend fun deleteBook(bookId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val book = bookDao.getBookById(bookId)
            if (book != null) {
                bookDao.deleteBook(book)
                Timber.d("Deleted book: $bookId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Book not found: $bookId"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting book: $bookId")
            Result.failure(e)
        }
    }

    override suspend fun getBookById(bookId: String): Result<LearningBook?> = withContext(ioDispatcher) {
        try {
            val book = bookDao.getBookById(bookId)?.toDomain()
            Result.success(book)
        } catch (e: Exception) {
            Timber.e(e, "Error getting book by ID: $bookId")
            Result.failure(e)
        }
    }

    override fun getAllBooks(): Flow<List<LearningBook>> {
        return bookDao.getAllBooks()
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting all books")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getFavoriteBooks(): Flow<List<LearningBook>> {
        return bookDao.getFavoriteBooks()
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting favorite books")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getBooksByCategory(category: BookCategory): Flow<List<LearningBook>> {
        return bookDao.getBooksByCategory(category.toString())
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting books by category: $category")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun searchBooks(query: String): Flow<List<LearningBook>> {
        return bookDao.searchBooks(query)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error searching books: $query")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun updateBookProgress(bookId: String, progress: Float): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                bookDao.updateReadingProgress(bookId, progress)
                Timber.d("Updated book progress: $bookId -> $progress")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Error updating book progress: $bookId")
                Result.failure(e)
            }
        }

    override suspend fun toggleBookFavorite(bookId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val book = bookDao.getBookById(bookId)
            if (book != null) {
                bookDao.updateFavoriteStatus(bookId, !book.isFavorite)
                Timber.d("Toggled favorite for book: $bookId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Book not found: $bookId"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error toggling favorite: $bookId")
            Result.failure(e)
        }
    }

    // ==================== AI Analysis ====================

    override suspend fun analyzeBook(bookId: String): Result<BookAnalysis> = withContext(ioDispatcher) {
        try {
            // TODO: Implement actual AI analysis via Claude API
            // This is a placeholder that creates a mock analysis
            val analysisId = java.util.UUID.randomUUID().toString()
            val analysis = BookAnalysis(
                id = analysisId,
                bookId = bookId,
                summary = "AI analysis pending implementation",
                keyTakeaways = emptyList(),
                targetAudience = "General traders",
                difficultyLevel = DifficultyLevel.INTERMEDIATE,
                estimatedReadingTime = Duration.ofHours(10),
                prerequisites = emptyList(),
                chapterSummaries = emptyList(),
                createdDate = LocalDateTime.now(),
                aiModel = "claude-3",
                confidence = 0.0f
            )
            analysisDao.insertAnalysis(analysis.toEntity())
            Timber.d("Created analysis for book: $bookId")
            Result.success(analysis)
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing book: $bookId")
            Result.failure(e)
        }
    }

    override suspend fun getBookAnalysis(bookId: String): Result<BookAnalysis?> =
        withContext(ioDispatcher) {
            try {
                val analysis = analysisDao.getAnalysisByBookId(bookId)?.toDomain()
                Result.success(analysis)
            } catch (e: Exception) {
                Timber.e(e, "Error getting book analysis: $bookId")
                Result.failure(e)
            }
        }

    override suspend fun generateStudyPlan(
        bookId: String,
        userId: String,
        dailyCommitmentMinutes: Int,
        targetCompletionWeeks: Int
    ): Result<StudyPlan> = withContext(ioDispatcher) {
        try {
            // TODO: Implement AI-powered study plan generation
            val planId = java.util.UUID.randomUUID().toString()
            val now = LocalDateTime.now()
            val plan = StudyPlan(
                id = planId,
                bookId = bookId,
                userId = userId,
                title = "Study Plan",
                description = "AI-generated study plan",
                totalDuration = Duration.ofDays(targetCompletionWeeks * 7L),
                dailyCommitment = Duration.ofMinutes(dailyCommitmentMinutes.toLong()),
                startDate = now,
                targetEndDate = now.plusWeeks(targetCompletionWeeks.toLong()),
                schedule = emptyList(),
                learningObjectives = emptyList(),
                milestones = emptyList(),
                adaptiveDifficulty = true,
                status = PlanStatus.DRAFT,
                createdDate = now,
                lastModifiedDate = now
            )
            studyPlanDao.insertStudyPlan(plan.toEntity())
            Timber.d("Generated study plan: $planId for book: $bookId")
            Result.success(plan)
        } catch (e: Exception) {
            Timber.e(e, "Error generating study plan for book: $bookId")
            Result.failure(e)
        }
    }

    override suspend fun evaluateBookQuality(bookId: String): Result<BookEvaluation> =
        withContext(ioDispatcher) {
            try {
                // TODO: Implement AI book evaluation
                val evaluationId = java.util.UUID.randomUUID().toString()
                val evaluation = BookEvaluation(
                    id = evaluationId,
                    bookId = bookId,
                    overallRating = 0.0f,
                    contentQuality = QualityRating.AVERAGE,
                    relevanceToTrading = QualityRating.AVERAGE,
                    practicalValue = QualityRating.AVERAGE,
                    accuracy = QualityRating.AVERAGE,
                    clarity = QualityRating.AVERAGE,
                    strengths = emptyList(),
                    weaknesses = emptyList(),
                    recommendations = emptyList(),
                    alternativeBooks = emptyList(),
                    bestForAudience = "General audience",
                    evaluationDate = LocalDateTime.now(),
                    detailedReview = "Evaluation pending"
                )
                evaluationDao.insertEvaluation(evaluation.toEntity())
                Timber.d("Created evaluation for book: $bookId")
                Result.success(evaluation)
            } catch (e: Exception) {
                Timber.e(e, "Error evaluating book: $bookId")
                Result.failure(e)
            }
        }

    override suspend fun getBookEvaluation(bookId: String): Result<BookEvaluation?> =
        withContext(ioDispatcher) {
            try {
                val evaluation = evaluationDao.getEvaluationByBookId(bookId)?.toDomain()
                Result.success(evaluation)
            } catch (e: Exception) {
                Timber.e(e, "Error getting book evaluation: $bookId")
                Result.failure(e)
            }
        }

    override suspend fun generateChapterSummaries(bookId: String): Result<List<ChapterSummary>> =
        withContext(ioDispatcher) {
            try {
                // TODO: Implement AI chapter summary generation
                Timber.d("Chapter summary generation not yet implemented for book: $bookId")
                Result.success(emptyList())
            } catch (e: Exception) {
                Timber.e(e, "Error generating chapter summaries: $bookId")
                Result.failure(e)
            }
        }

    // ==================== Study Plans ====================

    override suspend fun createStudyPlan(plan: StudyPlan): Result<Unit> = withContext(ioDispatcher) {
        try {
            studyPlanDao.insertStudyPlan(plan.toEntity())
            // Insert weekly schedules
            plan.schedule.forEach { schedule ->
                weeklyScheduleDao.insertSchedule(schedule.toEntity(plan.id))
            }
            // Insert milestones
            plan.milestones.forEach { milestone ->
                milestoneDao.insertMilestone(milestone.toEntity(plan.id))
            }
            Timber.d("Created study plan: ${plan.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error creating study plan: ${plan.id}")
            Result.failure(e)
        }
    }

    override suspend fun updateStudyPlan(plan: StudyPlan): Result<Unit> = withContext(ioDispatcher) {
        try {
            studyPlanDao.updateStudyPlan(plan.toEntity())
            Timber.d("Updated study plan: ${plan.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error updating study plan: ${plan.id}")
            Result.failure(e)
        }
    }

    override suspend fun deleteStudyPlan(planId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val plan = studyPlanDao.getStudyPlanById(planId)
            if (plan != null) {
                studyPlanDao.deleteStudyPlan(plan)
                Timber.d("Deleted study plan: $planId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Study plan not found: $planId"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting study plan: $planId")
            Result.failure(e)
        }
    }

    override suspend fun getStudyPlanById(planId: String): Result<StudyPlan?> =
        withContext(ioDispatcher) {
            try {
                val plan = studyPlanDao.getStudyPlanById(planId)?.toDomain(
                    schedules = emptyList(), // TODO: Load schedules and milestones
                    milestones = emptyList()
                )
                Result.success(plan)
            } catch (e: Exception) {
                Timber.e(e, "Error getting study plan: $planId")
                Result.failure(e)
            }
        }

    override fun getUserStudyPlans(userId: String): Flow<List<StudyPlan>> {
        return studyPlanDao.getUserStudyPlans(userId)
            .map { entities -> entities.map { it.toDomain(emptyList(), emptyList()) } }
            .catch { e ->
                Timber.e(e, "Error getting user study plans: $userId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getActiveStudyPlans(userId: String): Flow<List<StudyPlan>> {
        return studyPlanDao.getUserStudyPlansByStatus(userId, PlanStatus.ACTIVE.toString())
            .map { entities -> entities.map { it.toDomain(emptyList(), emptyList()) } }
            .catch { e ->
                Timber.e(e, "Error getting active study plans: $userId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun pauseStudyPlan(planId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            studyPlanDao.updatePlanStatus(planId, PlanStatus.PAUSED.toString())
            Timber.d("Paused study plan: $planId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error pausing study plan: $planId")
            Result.failure(e)
        }
    }

    override suspend fun resumeStudyPlan(planId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            studyPlanDao.updatePlanStatus(planId, PlanStatus.ACTIVE.toString())
            Timber.d("Resumed study plan: $planId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error resuming study plan: $planId")
            Result.failure(e)
        }
    }

    override suspend fun completeStudyPlan(planId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            studyPlanDao.updatePlanStatus(planId, PlanStatus.COMPLETED.toString())
            Timber.d("Completed study plan: $planId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error completing study plan: $planId")
            Result.failure(e)
        }
    }

    // ==================== Weekly Schedules ====================

    override suspend fun getCurrentWeekSchedule(planId: String): Result<WeeklySchedule?> =
        withContext(ioDispatcher) {
            try {
                val schedule = weeklyScheduleDao.getCurrentWeekSchedule(planId, LocalDateTime.now())
                    ?.toDomain()
                Result.success(schedule)
            } catch (e: Exception) {
                Timber.e(e, "Error getting current week schedule: $planId")
                Result.failure(e)
            }
        }

    override suspend fun updateWeeklyProgress(
        scheduleId: String,
        completionRate: Float,
        actualHours: Float
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            weeklyScheduleDao.updateScheduleProgress(scheduleId, completionRate, actualHours)
            Timber.d("Updated weekly progress: $scheduleId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error updating weekly progress: $scheduleId")
            Result.failure(e)
        }
    }

    override suspend fun markChapterComplete(
        scheduleId: String,
        chapterId: String
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            // TODO: Implement chapter completion tracking
            Timber.d("Marked chapter complete: $chapterId in schedule: $scheduleId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error marking chapter complete: $chapterId")
            Result.failure(e)
        }
    }

    override fun getWeeklySchedules(planId: String): Flow<List<WeeklySchedule>> {
        return weeklyScheduleDao.getSchedulesByPlanId(planId)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting weekly schedules: $planId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    // ==================== Progress Tracking ====================

    override suspend fun startLearningSession(
        userId: String,
        bookId: String?,
        topicId: String?,
        sessionType: SessionType
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val sessionId = java.util.UUID.randomUUID().toString()
            val now = LocalDateTime.now()
            val session = LearningSession(
                id = sessionId,
                userId = userId,
                bookId = bookId,
                topicId = topicId,
                startTime = now,
                endTime = now, // Will be updated on end
                duration = Duration.ZERO,
                pagesRead = null,
                chaptersCompleted = emptyList(),
                topicsReviewed = emptyList(),
                sessionType = sessionType,
                productivityScore = null,
                distractions = null,
                notes = null,
                mood = null
            )
            sessionDao.insertSession(session.toEntity())
            Timber.d("Started learning session: $sessionId")
            Result.success(sessionId)
        } catch (e: Exception) {
            Timber.e(e, "Error starting learning session")
            Result.failure(e)
        }
    }

    override suspend fun endLearningSession(
        sessionId: String,
        pagesRead: Int?,
        chaptersCompleted: List<Int>,
        topicsReviewed: List<String>,
        productivityScore: Float?,
        notes: String?,
        mood: StudyMood?
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val session = sessionDao.getSessionById(sessionId)
            if (session != null) {
                val now = LocalDateTime.now()
                val duration = Duration.between(
                    LocalDateTime.parse(session.startTime.toString()),
                    now
                )
                val updatedSession = session.copy(
                    endTime = now,
                    durationMinutes = duration.toMinutes(),
                    pagesRead = pagesRead,
                    chaptersCompleted = json.encodeToString(chaptersCompleted),
                    topicsReviewed = json.encodeToString(topicsReviewed),
                    productivityScore = productivityScore,
                    notes = notes,
                    mood = mood?.toString()
                )
                sessionDao.updateSession(updatedSession)
                Timber.d("Ended learning session: $sessionId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Session not found: $sessionId"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error ending learning session: $sessionId")
            Result.failure(e)
        }
    }

    override suspend fun getStudyProgress(userId: String, bookId: String): Result<StudyProgress?> =
        withContext(ioDispatcher) {
            try {
                val progress = progressDao.getProgressByUserAndBook(userId, bookId)?.toDomain(
                    quizScores = emptyList(), // TODO: Load related data
                    notes = emptyList(),
                    bookmarks = emptyList()
                )
                Result.success(progress)
            } catch (e: Exception) {
                Timber.e(e, "Error getting study progress: userId=$userId, bookId=$bookId")
                Result.failure(e)
            }
        }

    override fun getUserProgress(userId: String): Flow<List<StudyProgress>> {
        return progressDao.getUserProgress(userId)
            .map { entities -> entities.map { it.toDomain(emptyList(), emptyList(), emptyList()) } }
            .catch { e ->
                Timber.e(e, "Error getting user progress: $userId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getInProgressBooks(userId: String): Flow<List<StudyProgress>> {
        return progressDao.getInProgressBooks(userId)
            .map { entities -> entities.map { it.toDomain(emptyList(), emptyList(), emptyList()) } }
            .catch { e ->
                Timber.e(e, "Error getting in-progress books: $userId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun updateReadingPosition(
        userId: String,
        bookId: String,
        chapter: Int,
        page: Int?
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val progress = progressDao.getProgressByUserAndBook(userId, bookId)
            if (progress != null) {
                val percent = 0f // TODO: Calculate based on total pages
                progressDao.updateReadingPosition(progress.progressId, chapter, page, percent)
                Timber.d("Updated reading position: userId=$userId, bookId=$bookId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Progress not found"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating reading position")
            Result.failure(e)
        }
    }

    override suspend fun recordQuizScore(
        userId: String,
        bookId: String,
        quizScore: QuizScore
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val progress = progressDao.getProgressByUserAndBook(userId, bookId)
            if (progress != null) {
                quizScoreDao.insertScore(quizScore.toEntity(progress.progressId))
                Timber.d("Recorded quiz score: userId=$userId, bookId=$bookId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Progress not found"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error recording quiz score")
            Result.failure(e)
        }
    }

    override fun getQuizHistory(userId: String, bookId: String): Flow<List<QuizScore>> {
        return kotlinx.coroutines.flow.flow {
            try {
                val progress = withContext(ioDispatcher) {
                    progressDao.getProgressByUserAndBook(userId, bookId)
                }

                if (progress != null) {
                    quizScoreDao.getScoresByProgressId(progress.progressId)
                        .map { entities -> entities.map { it.toDomain() } }
                        .catch { e ->
                            Timber.e(e, "Error getting quiz history")
                            emit(emptyList())
                        }
                        .collect { scores ->
                            emit(scores)
                        }
                } else {
                    emit(emptyList())
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting quiz history")
                emit(emptyList())
            }
        }.flowOn(ioDispatcher)
    }

    // ==================== Notes and Bookmarks ====================

    override suspend fun addStudyNote(note: StudyNote): Result<Unit> = withContext(ioDispatcher) {
        try {
            noteDao.insertNote(note.toEntity())
            Timber.d("Added study note: ${note.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error adding study note")
            Result.failure(e)
        }
    }

    override suspend fun updateStudyNote(note: StudyNote): Result<Unit> = withContext(ioDispatcher) {
        try {
            noteDao.updateNote(note.toEntity())
            Timber.d("Updated study note: ${note.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error updating study note")
            Result.failure(e)
        }
    }

    override suspend fun deleteStudyNote(noteId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val note = noteDao.getNoteById(noteId)
            if (note != null) {
                noteDao.deleteNote(note)
                Timber.d("Deleted study note: $noteId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Note not found: $noteId"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting study note: $noteId")
            Result.failure(e)
        }
    }

    override fun getBookNotes(bookId: String): Flow<List<StudyNote>> {
        return noteDao.getNotesByBookId(bookId)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting book notes: $bookId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun searchNotes(query: String): Flow<List<StudyNote>> {
        return noteDao.searchNotes(query)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error searching notes: $query")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun addBookmark(bookmark: Bookmark): Result<Unit> = withContext(ioDispatcher) {
        try {
            bookmarkDao.insertBookmark(bookmark.toEntity())
            Timber.d("Added bookmark: ${bookmark.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error adding bookmark")
            Result.failure(e)
        }
    }

    override suspend fun removeBookmark(bookmarkId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            // TODO: Need to find bookmark by ID first
            Timber.d("Removed bookmark: $bookmarkId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error removing bookmark: $bookmarkId")
            Result.failure(e)
        }
    }

    override fun getBookBookmarks(bookId: String): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksByBookId(bookId)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting book bookmarks: $bookId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    // ==================== Learning Sessions ====================

    override fun getUserSessions(userId: String): Flow<List<LearningSession>> {
        return sessionDao.getUserSessions(userId)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting user sessions: $userId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getRecentSessions(userId: String, days: Int): Flow<List<LearningSession>> {
        val since = LocalDateTime.now().minusDays(days.toLong())
        return sessionDao.getRecentSessions(userId, since)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting recent sessions: $userId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun getTotalStudyTime(userId: String, since: LocalDateTime): Result<Long> =
        withContext(ioDispatcher) {
            try {
                val totalMinutes = sessionDao.getTotalStudyTime(userId, since) ?: 0L
                Result.success(totalMinutes)
            } catch (e: Exception) {
                Timber.e(e, "Error getting total study time: $userId")
                Result.failure(e)
            }
        }

    override suspend fun getStudyStreak(userId: String): Result<Int> = withContext(ioDispatcher) {
        try {
            // TODO: Implement streak calculation based on sessions
            Result.success(0)
        } catch (e: Exception) {
            Timber.e(e, "Error getting study streak: $userId")
            Result.failure(e)
        }
    }

    override suspend fun getAverageProductivity(userId: String, days: Int): Result<Float?> =
        withContext(ioDispatcher) {
            try {
                val since = LocalDateTime.now().minusDays(days.toLong())
                val avgProductivity = sessionDao.getAverageProductivity(userId, since)
                Result.success(avgProductivity)
            } catch (e: Exception) {
                Timber.e(e, "Error getting average productivity: $userId")
                Result.failure(e)
            }
        }

    // ==================== Study Statistics ====================

    override suspend fun getLearningStatistics(userId: String): Result<LearningStatistics> =
        withContext(ioDispatcher) {
            try {
                // TODO: Implement comprehensive statistics gathering
                val stats = LearningStatistics(
                    totalBooks = 0,
                    completedBooks = 0,
                    inProgressBooks = 0,
                    totalStudyHours = 0f,
                    averageSessionDuration = 0f,
                    currentStreak = 0,
                    longestStreak = 0,
                    averageComprehension = 0f,
                    topicsStudied = 0,
                    notesCreated = 0,
                    quizzesTaken = 0,
                    averageQuizScore = 0f
                )
                Result.success(stats)
            } catch (e: Exception) {
                Timber.e(e, "Error getting learning statistics: $userId")
                Result.failure(e)
            }
        }

    override suspend fun getBookStatistics(bookId: String): Result<BookStatistics> =
        withContext(ioDispatcher) {
            try {
                // TODO: Implement book-specific statistics
                val stats = BookStatistics(
                    bookId = bookId,
                    totalReaders = 0,
                    averageCompletionTime = 0f,
                    averageRating = 0f,
                    completionRate = 0f,
                    mostHighlightedPassages = emptyList(),
                    commonNotes = emptyList()
                )
                Result.success(stats)
            } catch (e: Exception) {
                Timber.e(e, "Error getting book statistics: $bookId")
                Result.failure(e)
            }
        }

    override suspend fun getWeeklyStudyReport(userId: String): Result<WeeklyStudyReport> =
        withContext(ioDispatcher) {
            try {
                // TODO: Implement weekly report generation
                val now = LocalDateTime.now()
                val report = WeeklyStudyReport(
                    weekStartDate = now.minusDays(7),
                    weekEndDate = now,
                    totalHoursStudied = 0f,
                    booksRead = emptyList(),
                    chaptersCompleted = 0,
                    topicsReviewed = emptyList(),
                    quizzesTaken = 0,
                    averageProductivity = 0f,
                    goalsAchieved = emptyList(),
                    nextWeekSuggestions = emptyList()
                )
                Result.success(report)
            } catch (e: Exception) {
                Timber.e(e, "Error getting weekly study report: $userId")
                Result.failure(e)
            }
        }

    // ==================== Entity/Domain Mapping ====================

    private fun LearningBook.toEntity(): LearningBookEntity {
        return LearningBookEntity(
            bookId = id,
            title = title,
            author = author,
            category = category.toString(),
            filePath = filePath,
            fileSize = fileSize,
            pageCount = pageCount,
            uploadDate = uploadDate,
            lastOpenedDate = lastOpenedDate,
            coverImagePath = coverImagePath,
            isbn = isbn,
            publicationYear = publicationYear,
            language = language,
            tags = json.encodeToString(tags),
            readingProgress = readingProgress,
            isFavorite = isFavorite,
            analysisStatus = analysisStatus.toString()
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

    private fun BookAnalysis.toEntity(): BookAnalysisEntity {
        return BookAnalysisEntity(
            analysisId = id,
            bookId = bookId,
            summary = summary,
            keyTakeaways = json.encodeToString(keyTakeaways),
            targetAudience = targetAudience,
            difficultyLevel = difficultyLevel.toString(),
            estimatedReadingTimeMinutes = estimatedReadingTime.toMinutes(),
            prerequisites = json.encodeToString(prerequisites),
            createdDate = createdDate,
            aiModel = aiModel,
            confidence = confidence
        )
    }

    private fun BookAnalysisEntity.toDomain(): BookAnalysis {
        return BookAnalysis(
            id = analysisId,
            bookId = bookId,
            summary = summary,
            keyTakeaways = try { json.decodeFromString(keyTakeaways) } catch (e: Exception) { emptyList() },
            targetAudience = targetAudience,
            difficultyLevel = DifficultyLevel.valueOf(difficultyLevel),
            estimatedReadingTime = Duration.ofMinutes(estimatedReadingTimeMinutes),
            prerequisites = try { json.decodeFromString(prerequisites) } catch (e: Exception) { emptyList() },
            chapterSummaries = emptyList(), // Loaded separately
            createdDate = createdDate,
            aiModel = aiModel,
            confidence = confidence
        )
    }

    private fun StudyPlan.toEntity(): StudyPlanEntity {
        return StudyPlanEntity(
            planId = id,
            bookId = bookId,
            userId = userId,
            title = title,
            description = description,
            totalDurationDays = totalDuration.toDays().toInt(),
            dailyCommitmentMinutes = dailyCommitment.toMinutes().toInt(),
            startDate = startDate,
            targetEndDate = targetEndDate,
            learningObjectives = json.encodeToString(learningObjectives),
            adaptiveDifficulty = adaptiveDifficulty,
            status = status.toString(),
            createdDate = createdDate,
            lastModifiedDate = lastModifiedDate
        )
    }

    private fun StudyPlanEntity.toDomain(
        schedules: List<WeeklySchedule>,
        milestones: List<StudyMilestone>
    ): StudyPlan {
        return StudyPlan(
            id = planId,
            bookId = bookId,
            userId = userId,
            title = title,
            description = description,
            totalDuration = Duration.ofDays(totalDurationDays.toLong()),
            dailyCommitment = Duration.ofMinutes(dailyCommitmentMinutes.toLong()),
            startDate = startDate,
            targetEndDate = targetEndDate,
            schedule = schedules,
            learningObjectives = try { json.decodeFromString(learningObjectives) } catch (e: Exception) { emptyList() },
            milestones = milestones,
            adaptiveDifficulty = adaptiveDifficulty,
            status = PlanStatus.valueOf(status),
            createdDate = createdDate,
            lastModifiedDate = lastModifiedDate
        )
    }

    private fun WeeklySchedule.toEntity(planId: String): WeeklyScheduleEntity {
        return WeeklyScheduleEntity(
            scheduleId = id,
            studyPlanId = planId,
            weekNumber = weekNumber,
            startDate = startDate,
            endDate = endDate,
            chapterAssignments = json.encodeToString(chapters),
            topics = json.encodeToString(topics),
            goals = json.encodeToString(goals),
            estimatedHours = estimatedHours,
            actualHours = actualHours,
            completionRate = completionRate,
            notes = notes
        )
    }

    private fun WeeklyScheduleEntity.toDomain(): WeeklySchedule {
        return WeeklySchedule(
            id = scheduleId,
            studyPlanId = studyPlanId,
            weekNumber = weekNumber,
            startDate = startDate,
            endDate = endDate,
            chapters = try { json.decodeFromString(chapterAssignments) } catch (e: Exception) { emptyList() },
            topics = try { json.decodeFromString(topics) } catch (e: Exception) { emptyList() },
            goals = try { json.decodeFromString(goals) } catch (e: Exception) { emptyList() },
            estimatedHours = estimatedHours,
            actualHours = actualHours,
            completionRate = completionRate,
            notes = notes
        )
    }

    private fun BookEvaluation.toEntity(): BookEvaluationEntity {
        return BookEvaluationEntity(
            evaluationId = id,
            bookId = bookId,
            overallRating = overallRating,
            contentQuality = contentQuality.toString(),
            relevanceToTrading = relevanceToTrading.toString(),
            practicalValue = practicalValue.toString(),
            accuracy = accuracy.toString(),
            clarity = clarity.toString(),
            strengths = json.encodeToString(strengths),
            weaknesses = json.encodeToString(weaknesses),
            recommendations = json.encodeToString(recommendations),
            alternativeBooks = json.encodeToString(alternativeBooks),
            bestForAudience = bestForAudience,
            evaluationDate = evaluationDate,
            detailedReview = detailedReview
        )
    }

    private fun BookEvaluationEntity.toDomain(): BookEvaluation {
        return BookEvaluation(
            id = evaluationId,
            bookId = bookId,
            overallRating = overallRating,
            contentQuality = QualityRating.valueOf(contentQuality),
            relevanceToTrading = QualityRating.valueOf(relevanceToTrading),
            practicalValue = QualityRating.valueOf(practicalValue),
            accuracy = QualityRating.valueOf(accuracy),
            clarity = QualityRating.valueOf(clarity),
            strengths = try { json.decodeFromString(strengths) } catch (e: Exception) { emptyList() },
            weaknesses = try { json.decodeFromString(weaknesses) } catch (e: Exception) { emptyList() },
            recommendations = try { json.decodeFromString(recommendations) } catch (e: Exception) { emptyList() },
            alternativeBooks = try { json.decodeFromString(alternativeBooks) } catch (e: Exception) { emptyList() },
            bestForAudience = bestForAudience,
            evaluationDate = evaluationDate,
            detailedReview = detailedReview
        )
    }

    private fun StudyProgress.toEntity(): StudyProgressEntity {
        return StudyProgressEntity(
            progressId = id,
            userId = userId,
            bookId = bookId,
            currentChapter = currentChapter,
            currentPage = currentPage,
            totalPagesRead = totalPagesRead,
            percentComplete = percentComplete,
            totalTimeSpentMinutes = totalTimeSpent.toMinutes(),
            averageSessionDurationMinutes = averageSessionDuration.toMinutes(),
            lastStudyDate = lastStudyDate,
            streakDays = streak,
            comprehensionLevel = comprehensionLevel.toString()
        )
    }

    private fun StudyProgressEntity.toDomain(
        quizScores: List<QuizScore>,
        notes: List<StudyNote>,
        bookmarks: List<Bookmark>
    ): StudyProgress {
        return StudyProgress(
            id = progressId,
            userId = userId,
            bookId = bookId,
            currentChapter = currentChapter,
            currentPage = currentPage,
            totalPagesRead = totalPagesRead,
            percentComplete = percentComplete,
            totalTimeSpent = Duration.ofMinutes(totalTimeSpentMinutes),
            averageSessionDuration = Duration.ofMinutes(averageSessionDurationMinutes),
            lastStudyDate = lastStudyDate,
            streak = streakDays,
            quizScores = quizScores,
            comprehensionLevel = ComprehensionLevel.valueOf(comprehensionLevel),
            notes = notes,
            bookmarks = bookmarks
        )
    }

    private fun QuizScore.toEntity(progressId: String): QuizScoreEntity {
        return QuizScoreEntity(
            quizId = quizId,
            progressId = progressId,
            chapterId = chapterId,
            score = score,
            totalQuestions = totalQuestions,
            correctAnswers = correctAnswers,
            timeTakenSeconds = timeTaken.seconds,
            date = date,
            topics = json.encodeToString(topics)
        )
    }

    private fun QuizScoreEntity.toDomain(): QuizScore {
        return QuizScore(
            quizId = quizId,
            chapterId = chapterId,
            score = score,
            totalQuestions = totalQuestions,
            correctAnswers = correctAnswers,
            timeTaken = Duration.ofSeconds(timeTakenSeconds),
            date = date,
            topics = try { json.decodeFromString(topics) } catch (e: Exception) { emptyList() }
        )
    }

    private fun StudyNote.toEntity(): StudyNoteEntity {
        return StudyNoteEntity(
            noteId = id,
            bookId = bookId,
            chapterId = chapterId,
            pageNumber = pageNumber,
            content = content,
            highlightedText = highlightedText,
            color = color,
            createdDate = createdDate,
            lastModifiedDate = lastModifiedDate,
            tags = json.encodeToString(tags)
        )
    }

    private fun StudyNoteEntity.toDomain(): StudyNote {
        return StudyNote(
            id = noteId,
            bookId = bookId,
            chapterId = chapterId,
            pageNumber = pageNumber,
            content = content,
            highlightedText = highlightedText,
            color = color,
            createdDate = createdDate,
            lastModifiedDate = lastModifiedDate,
            tags = try { json.decodeFromString(tags) } catch (e: Exception) { emptyList() }
        )
    }

    private fun Bookmark.toEntity(): BookmarkEntity {
        return BookmarkEntity(
            bookmarkId = id,
            bookId = bookId,
            pageNumber = pageNumber,
            label = label,
            createdDate = createdDate
        )
    }

    private fun BookmarkEntity.toDomain(): Bookmark {
        return Bookmark(
            id = bookmarkId,
            bookId = bookId,
            pageNumber = pageNumber,
            label = label,
            createdDate = createdDate
        )
    }

    private fun LearningSession.toEntity(): LearningSessionEntity {
        return LearningSessionEntity(
            sessionId = id,
            userId = userId,
            bookId = bookId,
            topicId = topicId,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = duration.toMinutes(),
            pagesRead = pagesRead,
            chaptersCompleted = json.encodeToString(chaptersCompleted),
            topicsReviewed = json.encodeToString(topicsReviewed),
            sessionType = sessionType.toString(),
            productivityScore = productivityScore,
            distractions = distractions,
            notes = notes,
            mood = mood?.toString()
        )
    }

    private fun LearningSessionEntity.toDomain(): LearningSession {
        return LearningSession(
            id = sessionId,
            userId = userId,
            bookId = bookId,
            topicId = topicId,
            startTime = startTime,
            endTime = endTime,
            duration = Duration.ofMinutes(durationMinutes),
            pagesRead = pagesRead,
            chaptersCompleted = try { json.decodeFromString(chaptersCompleted) } catch (e: Exception) { emptyList() },
            topicsReviewed = try { json.decodeFromString(topicsReviewed) } catch (e: Exception) { emptyList() },
            sessionType = SessionType.valueOf(sessionType),
            productivityScore = productivityScore,
            distractions = distractions,
            notes = notes,
            mood = mood?.let { StudyMood.valueOf(it) }
        )
    }

    private fun StudyMilestone.toEntity(planId: String): StudyMilestoneEntity {
        return StudyMilestoneEntity(
            milestoneId = id,
            planId = planId,
            title = title,
            description = description,
            targetDate = targetDate,
            isCompleted = isCompleted,
            completedDate = completedDate,
            reward = reward
        )
    }
}
