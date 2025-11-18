package com.cryptotrader.data.repository

import kotlinx.coroutines.flow.Flow
import com.cryptotrader.domain.model.*
import java.io.File
import java.time.LocalDateTime

/**
 * Main repository interface for Learning/Education features
 */
interface LearningRepository {

    // Book Management
    suspend fun uploadBook(
        file: File,
        title: String,
        author: String?,
        category: BookCategory
    ): Result<LearningBook>

    suspend fun deleteBook(bookId: String): Result<Unit>

    suspend fun getBookById(bookId: String): Result<LearningBook?>

    fun getAllBooks(): Flow<List<LearningBook>>

    fun getFavoriteBooks(): Flow<List<LearningBook>>

    fun getBooksByCategory(category: BookCategory): Flow<List<LearningBook>>

    fun searchBooks(query: String): Flow<List<LearningBook>>

    suspend fun updateBookProgress(bookId: String, progress: Float): Result<Unit>

    suspend fun toggleBookFavorite(bookId: String): Result<Unit>

    // AI Analysis
    suspend fun analyzeBook(bookId: String): Result<BookAnalysis>

    suspend fun getBookAnalysis(bookId: String): Result<BookAnalysis?>

    suspend fun generateStudyPlan(
        bookId: String,
        userId: String,
        dailyCommitmentMinutes: Int,
        targetCompletionWeeks: Int
    ): Result<StudyPlan>

    suspend fun evaluateBookQuality(bookId: String): Result<BookEvaluation>

    suspend fun getBookEvaluation(bookId: String): Result<BookEvaluation?>

    suspend fun generateChapterSummaries(bookId: String): Result<List<ChapterSummary>>

    // Study Plans
    suspend fun createStudyPlan(plan: StudyPlan): Result<Unit>

    suspend fun updateStudyPlan(plan: StudyPlan): Result<Unit>

    suspend fun deleteStudyPlan(planId: String): Result<Unit>

    suspend fun getStudyPlanById(planId: String): Result<StudyPlan?>

    fun getUserStudyPlans(userId: String): Flow<List<StudyPlan>>

    fun getActiveStudyPlans(userId: String): Flow<List<StudyPlan>>

    suspend fun pauseStudyPlan(planId: String): Result<Unit>

    suspend fun resumeStudyPlan(planId: String): Result<Unit>

    suspend fun completeStudyPlan(planId: String): Result<Unit>

    // Weekly Schedules
    suspend fun getCurrentWeekSchedule(planId: String): Result<WeeklySchedule?>

    suspend fun updateWeeklyProgress(
        scheduleId: String,
        completionRate: Float,
        actualHours: Float
    ): Result<Unit>

    suspend fun markChapterComplete(
        scheduleId: String,
        chapterId: String
    ): Result<Unit>

    fun getWeeklySchedules(planId: String): Flow<List<WeeklySchedule>>

    // Progress Tracking
    suspend fun startLearningSession(
        userId: String,
        bookId: String?,
        topicId: String?,
        sessionType: SessionType
    ): Result<String> // Returns sessionId

    suspend fun endLearningSession(
        sessionId: String,
        pagesRead: Int?,
        chaptersCompleted: List<Int>,
        topicsReviewed: List<String>,
        productivityScore: Float?,
        notes: String?,
        mood: StudyMood?
    ): Result<Unit>

    suspend fun getStudyProgress(userId: String, bookId: String): Result<StudyProgress?>

    fun getUserProgress(userId: String): Flow<List<StudyProgress>>

    fun getInProgressBooks(userId: String): Flow<List<StudyProgress>>

    suspend fun updateReadingPosition(
        userId: String,
        bookId: String,
        chapter: Int,
        page: Int?
    ): Result<Unit>

    suspend fun recordQuizScore(
        userId: String,
        bookId: String,
        quizScore: QuizScore
    ): Result<Unit>

    fun getQuizHistory(userId: String, bookId: String): Flow<List<QuizScore>>

    // Notes and Bookmarks
    suspend fun addStudyNote(note: StudyNote): Result<Unit>

    suspend fun updateStudyNote(note: StudyNote): Result<Unit>

    suspend fun deleteStudyNote(noteId: String): Result<Unit>

    fun getBookNotes(bookId: String): Flow<List<StudyNote>>

    fun searchNotes(query: String): Flow<List<StudyNote>>

    suspend fun addBookmark(bookmark: Bookmark): Result<Unit>

    suspend fun removeBookmark(bookmarkId: String): Result<Unit>

    fun getBookBookmarks(bookId: String): Flow<List<Bookmark>>

    // Learning Sessions
    fun getUserSessions(userId: String): Flow<List<LearningSession>>

    fun getRecentSessions(userId: String, days: Int): Flow<List<LearningSession>>

    suspend fun getTotalStudyTime(userId: String, since: LocalDateTime): Result<Long> // minutes

    suspend fun getStudyStreak(userId: String): Result<Int> // days

    suspend fun getAverageProductivity(userId: String, days: Int): Result<Float?>

    // Study Statistics
    suspend fun getLearningStatistics(userId: String): Result<LearningStatistics>

    suspend fun getBookStatistics(bookId: String): Result<BookStatistics>

    suspend fun getWeeklyStudyReport(userId: String): Result<WeeklyStudyReport>
}