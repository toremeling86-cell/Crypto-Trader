package com.cryptotrader.presentation.screens.learning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.LearningRepository
import com.cryptotrader.domain.model.PlanStatus
import com.cryptotrader.domain.model.StudyPlan
import com.cryptotrader.domain.model.StudyPlanWithProgress
import com.cryptotrader.domain.model.WeeklySchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * ViewModel for Study Plan Screen
 * Manages personalized study plans and weekly schedules
 */
@HiltViewModel
class StudyPlanViewModel @Inject constructor(
    private val learningRepository: LearningRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyPlanUiState())
    val uiState: StateFlow<StudyPlanUiState> = _uiState.asStateFlow()

    private val userId = "current_user" // TODO: Get from auth service

    init {
        loadStudyPlans()
    }

    /**
     * Load all study plans for the user
     */
    fun loadStudyPlans() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // Observe user's study plans
                combine(
                    learningRepository.getUserStudyPlans(userId),
                    learningRepository.getRecentSessions(userId, days = 7)
                ) { plans, sessions ->
                    // Create StudyPlanWithProgress for each plan
                    plans.map { plan ->
                        val currentWeek = learningRepository.getCurrentWeekSchedule(plan.id).getOrNull()
                        val weekSessions = sessions.filter { session ->
                            session.bookId == plan.bookId &&
                                    session.startTime.isAfter(currentWeek?.startDate ?: LocalDateTime.now())
                        }

                        // Calculate overall progress
                        val completedWeeks = plan.schedule.count { it.completionRate >= 1.0f }
                        val totalWeeks = plan.schedule.size
                        val overallProgress = if (totalWeeks > 0) {
                            completedWeeks.toFloat() / totalWeeks.toFloat()
                        } else 0f

                        StudyPlanWithProgress(
                            studyPlan = plan,
                            currentWeek = currentWeek,
                            overallProgress = overallProgress,
                            sessionsThisWeek = weekSessions
                        )
                    }
                }.collect { plansWithProgress ->
                    // Separate active and completed plans
                    val activePlans = plansWithProgress.filter {
                        it.studyPlan.status == PlanStatus.ACTIVE || it.studyPlan.status == PlanStatus.PAUSED
                    }
                    val completedPlans = plansWithProgress.filter {
                        it.studyPlan.status == PlanStatus.COMPLETED
                    }

                    _uiState.value = _uiState.value.copy(
                        activePlans = activePlans,
                        completedPlans = completedPlans,
                        selectedPlan = _uiState.value.selectedPlan ?: activePlans.firstOrNull(),
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Error loading study plans")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load study plans: ${e.message}"
                )
            }
        }
    }

    /**
     * Select a study plan to view details
     */
    fun selectPlan(plan: StudyPlanWithProgress) {
        _uiState.value = _uiState.value.copy(selectedPlan = plan)
        loadWeeklySchedules(plan.studyPlan.id)
        Timber.d("Selected study plan: ${plan.studyPlan.title}")
    }

    /**
     * Load weekly schedules for a plan
     */
    private fun loadWeeklySchedules(planId: String) {
        viewModelScope.launch {
            try {
                learningRepository.getWeeklySchedules(planId).collect { schedules ->
                    _uiState.value = _uiState.value.copy(weeklySchedules = schedules)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading weekly schedules")
            }
        }
    }

    /**
     * Pause a study plan
     */
    fun pausePlan(planId: String) {
        viewModelScope.launch {
            try {
                val result = learningRepository.pauseStudyPlan(planId)
                if (result.isSuccess) {
                    Timber.i("Paused study plan: $planId")
                    // Plans will auto-update through Flow
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to pause plan: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error pausing plan")
                _uiState.value = _uiState.value.copy(
                    error = "Error pausing plan: ${e.message}"
                )
            }
        }
    }

    /**
     * Resume a study plan
     */
    fun resumePlan(planId: String) {
        viewModelScope.launch {
            try {
                val result = learningRepository.resumeStudyPlan(planId)
                if (result.isSuccess) {
                    Timber.i("Resumed study plan: $planId")
                    // Plans will auto-update through Flow
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to resume plan: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error resuming plan")
                _uiState.value = _uiState.value.copy(
                    error = "Error resuming plan: ${e.message}"
                )
            }
        }
    }

    /**
     * Mark a chapter as complete within a weekly schedule
     */
    fun markChapterComplete(scheduleId: String, chapterId: String) {
        viewModelScope.launch {
            try {
                val result = learningRepository.markChapterComplete(scheduleId, chapterId)
                if (result.isSuccess) {
                    Timber.i("Marked chapter complete: $chapterId in schedule: $scheduleId")
                    // Schedules will auto-update through Flow
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to mark chapter complete: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error marking chapter complete")
                _uiState.value = _uiState.value.copy(
                    error = "Error marking chapter complete: ${e.message}"
                )
            }
        }
    }

    /**
     * Update weekly progress
     */
    fun updateWeeklyProgress(scheduleId: String, completionRate: Float, actualHours: Float) {
        viewModelScope.launch {
            try {
                val result = learningRepository.updateWeeklyProgress(
                    scheduleId = scheduleId,
                    completionRate = completionRate,
                    actualHours = actualHours
                )
                if (result.isSuccess) {
                    Timber.d("Updated weekly progress for schedule: $scheduleId")
                    // Schedules will auto-update through Flow
                } else {
                    Timber.e("Failed to update weekly progress: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating weekly progress")
            }
        }
    }

    /**
     * Complete a study plan
     */
    fun completePlan(planId: String) {
        viewModelScope.launch {
            try {
                val result = learningRepository.completeStudyPlan(planId)
                if (result.isSuccess) {
                    Timber.i("Completed study plan: $planId")
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Congratulations! Study plan completed!"
                    )
                    // Plans will auto-update through Flow
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to complete plan: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error completing plan")
                _uiState.value = _uiState.value.copy(
                    error = "Error completing plan: ${e.message}"
                )
            }
        }
    }

    /**
     * Delete a study plan
     */
    fun deletePlan(planId: String) {
        viewModelScope.launch {
            try {
                val result = learningRepository.deleteStudyPlan(planId)
                if (result.isSuccess) {
                    Timber.i("Deleted study plan: $planId")
                    // If this was the selected plan, clear selection
                    if (_uiState.value.selectedPlan?.studyPlan?.id == planId) {
                        _uiState.value = _uiState.value.copy(selectedPlan = null)
                    }
                    // Plans will auto-update through Flow
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to delete plan: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting plan")
                _uiState.value = _uiState.value.copy(
                    error = "Error deleting plan: ${e.message}"
                )
            }
        }
    }

    /**
     * Navigate to a specific week
     */
    fun navigateToWeek(weekNumber: Int) {
        _uiState.value = _uiState.value.copy(selectedWeek = weekNumber)
        Timber.d("Navigated to week: $weekNumber")
    }

    /**
     * Clear messages
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}

/**
 * UI State for Study Plan Screen
 */
data class StudyPlanUiState(
    val activePlans: List<StudyPlanWithProgress> = emptyList(),
    val completedPlans: List<StudyPlanWithProgress> = emptyList(),
    val selectedPlan: StudyPlanWithProgress? = null,
    val weeklySchedules: List<WeeklySchedule> = emptyList(),
    val selectedWeek: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
