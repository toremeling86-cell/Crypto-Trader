package com.cryptotrader.presentation.screens.learning

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cryptotrader.domain.model.*

/**
 * Study Plan Screen
 * Displays personalized study plan with timeline visualization and weekly breakdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPlanScreen(
    planId: String,
    onNavigateBack: () -> Unit,
    viewModel: StudyPlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(planId) {
        viewModel.loadStudyPlan(planId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Plan") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Edit plan */ }) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LearningColors.Primary)
            }
        } else if (uiState.studyPlan != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(LearningColors.Surface),
                contentPadding = PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                // Header Card
                item {
                    StudyPlanHeader(
                        studyPlan = uiState.studyPlan!!,
                        overallProgress = uiState.overallProgress
                    )
                }

                // Weekly Timeline
                itemsIndexed(uiState.studyPlan!!.schedule) { index, weeklySchedule ->
                    WeekTimelineItem(
                        week = weeklySchedule,
                        weekNumber = index + 1,
                        isCurrentWeek = index == uiState.currentWeekIndex,
                        isCompleted = weeklySchedule.completionRate >= 1.0f,
                        isLastWeek = index == uiState.studyPlan!!.schedule.size - 1,
                        onToggleExpand = { viewModel.toggleWeekExpansion(index) },
                        onToggleTopicComplete = { topicIndex ->
                            viewModel.toggleTopicCompletion(index, topicIndex)
                        },
                        isExpanded = uiState.expandedWeeks.contains(index)
                    )
                }

                // Milestones Section
                if (uiState.studyPlan!!.milestones.isNotEmpty()) {
                    item {
                        MilestonesSection(
                            milestones = uiState.studyPlan!!.milestones
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Study plan not found")
            }
        }
    }
}

@Composable
private fun StudyPlanHeader(
    studyPlan: StudyPlan,
    overallProgress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LearningColors.PrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = studyPlan.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = studyPlan.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = when {
                        overallProgress >= 0.8f -> LearningColors.Success
                        overallProgress >= 0.4f -> LearningColors.Warning
                        else -> LearningColors.Primary
                    }
                ) {
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${(overallProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.m))

            // Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetadataItem(
                    icon = Icons.Default.CalendarMonth,
                    label = "Duration",
                    value = "${studyPlan.totalDuration.toDays()} days"
                )
                MetadataItem(
                    icon = Icons.Default.Schedule,
                    label = "Daily",
                    value = "${studyPlan.dailyCommitment.toMinutes()} min"
                )
                MetadataItem(
                    icon = Icons.Default.Flag,
                    label = "Objectives",
                    value = "${studyPlan.learningObjectives.size}"
                )
            }

            Spacer(modifier = Modifier.height(Spacing.m))

            // Overall Progress Bar
            LinearProgressIndicator(
                progress = overallProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = when {
                    overallProgress >= 0.8f -> LearningColors.Success
                    overallProgress >= 0.4f -> LearningColors.Warning
                    else -> LearningColors.Primary
                }
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Text(
                text = "Overall Progress: ${(overallProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun MetadataItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LearningColors.Primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekTimelineItem(
    week: WeeklySchedule,
    weekNumber: Int,
    isCurrentWeek: Boolean,
    isCompleted: Boolean,
    isLastWeek: Boolean,
    onToggleExpand: () -> Unit,
    onToggleTopicComplete: (Int) -> Unit,
    isExpanded: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Timeline Indicator Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(56.dp)
        ) {
            // Week Number Circle
            Surface(
                shape = CircleShape,
                color = when {
                    isCompleted -> LearningColors.Success
                    isCurrentWeek -> LearningColors.Primary
                    else -> LearningColors.SurfaceVariant
                },
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = "$weekNumber",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isCurrentWeek) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Connecting Line
            if (!isLastWeek) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(if (isExpanded) 200.dp else 80.dp)
                        .background(
                            if (isCompleted) LearningColors.Success
                            else LearningColors.SurfaceVariant
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(Spacing.m))

        // Week Content Card
        Card(
            onClick = onToggleExpand,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrentWeek)
                    LearningColors.PrimaryContainer
                else
                    LearningColors.SurfaceElevated
            ),
            border = if (isCurrentWeek)
                BorderStroke(2.dp, LearningColors.Primary)
            else null,
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isCurrentWeek) 4.dp else 2.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.m)
            ) {
                // Week Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Week $weekNumber",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            if (isCurrentWeek) {
                                Spacer(modifier = Modifier.width(Spacing.s))
                                AssistChip(
                                    onClick = { },
                                    label = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocalFireDepartment,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(Spacing.xs))
                                            Text("Current")
                                        }
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = LearningColors.Tertiary
                                    )
                                )
                            }
                        }

                        if (week.topics.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = week.topics.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.s))

                // Time Estimate
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = LearningColors.Primary
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = "${week.estimatedHours} hours estimated",
                        style = MaterialTheme.typography.labelMedium
                    )

                    if (week.actualHours != null) {
                        Spacer(modifier = Modifier.width(Spacing.m))
                        Text(
                            text = "• ${week.actualHours} hours actual",
                            style = MaterialTheme.typography.labelMedium,
                            color = LearningColors.Secondary
                        )
                    }
                }

                // Progress Bar
                Spacer(modifier = Modifier.height(Spacing.m))
                LinearProgressIndicator(
                    progress = week.completionRate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = when {
                        week.completionRate >= 0.8f -> LearningColors.Success
                        week.completionRate >= 0.4f -> LearningColors.Warning
                        else -> LearningColors.Primary
                    }
                )

                // Expanded Content
                if (isExpanded) {
                    Spacer(modifier = Modifier.height(Spacing.m))
                    Divider()
                    Spacer(modifier = Modifier.height(Spacing.m))

                    // Goals
                    if (week.goals.isNotEmpty()) {
                        Text(
                            text = "Weekly Goals",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Spacing.s))
                        week.goals.forEachIndexed { index, goal ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xs),
                                verticalAlignment = Alignment.Top
                            ) {
                                Checkbox(
                                    checked = false, // This would come from ViewModel state
                                    onCheckedChange = { onToggleTopicComplete(index) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = LearningColors.Success
                                    )
                                )
                                Spacer(modifier = Modifier.width(Spacing.s))
                                Text(
                                    text = goal,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }
                    }

                    // Chapters
                    if (week.chapters.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.m))
                        Text(
                            text = "Chapters to Complete",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Spacing.s))

                        week.chapters.forEach { chapter ->
                            ChapterAssignmentItem(chapter = chapter)
                        }
                    }

                    // Notes
                    if (week.notes != null) {
                        Spacer(modifier = Modifier.height(Spacing.m))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = LearningColors.Info.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.m)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Note,
                                    contentDescription = null,
                                    tint = LearningColors.Info
                                )
                                Spacer(modifier = Modifier.width(Spacing.s))
                                Text(
                                    text = week.notes!!,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterAssignmentItem(
    chapter: ChapterAssignment
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        colors = CardDefaults.cardColors(
            containerColor = if (chapter.isCompleted)
                LearningColors.Success.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (chapter.isCompleted)
            BorderStroke(1.dp, LearningColors.Success)
        else
            BorderStroke(1.dp, LearningColors.SurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chapter Number
            Surface(
                shape = CircleShape,
                color = if (chapter.isCompleted)
                    LearningColors.Success
                else
                    LearningColors.Primary,
                modifier = Modifier.size(32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    if (chapter.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = "${chapter.chapterNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.m))

            // Chapter Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (chapter.isCompleted)
                        TextDecoration.LineThrough
                    else
                        TextDecoration.None
                )

                if (chapter.timeSpent != null) {
                    Text(
                        text = "Time spent: ${chapter.timeSpent!!.toMinutes()} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            if (chapter.isCompleted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    tint = LearningColors.Success
                )
            }
        }
    }
}

@Composable
private fun MilestonesSection(
    milestones: List<StudyMilestone>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LearningColors.TertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = LearningColors.Tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(
                    text = "Milestones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(Spacing.m))

            milestones.forEach { milestone ->
                MilestoneItem(milestone = milestone)
                Spacer(modifier = Modifier.height(Spacing.s))
            }
        }
    }
}

@Composable
private fun MilestoneItem(
    milestone: StudyMilestone
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (milestone.isCompleted)
                Icons.Default.CheckCircle
            else
                Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (milestone.isCompleted)
                LearningColors.Success
            else
                LearningColors.NotStarted
        )

        Spacer(modifier = Modifier.width(Spacing.m))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = milestone.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (milestone.isCompleted)
                    TextDecoration.LineThrough
                else
                    TextDecoration.None
            )
            Text(
                text = milestone.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        if (milestone.reward != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = LearningColors.Tertiary.copy(alpha = 0.2f)
            ) {
                Text(
                    text = milestone.reward!!,
                    modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
