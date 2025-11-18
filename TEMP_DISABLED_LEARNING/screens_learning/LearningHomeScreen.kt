package com.cryptotrader.presentation.screens.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cryptotrader.domain.model.*
import com.cryptotrader.presentation.screens.learning.LearningColors
import com.cryptotrader.presentation.screens.learning.Spacing

/**
 * Learning Home Screen
 * Main dashboard showing overview, recent books, stats, and quick actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningHomeScreen(
    onNavigateToLibrary: () -> Unit,
    onNavigateToKnowledgeBase: () -> Unit,
    onNavigateToBook: (String) -> Unit,
    onUploadPdf: () -> Unit,
    viewModel: LearningHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learning") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LearningColors.PrimaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onUploadPdf,
                containerColor = LearningColors.Primary,
                contentColor = LearningColors.OnPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Upload PDF"
                )
                Spacer(modifier = Modifier.width(Spacing.s))
                Text("Upload PDF")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            // Welcome Section
            item {
                WelcomeSection(
                    userName = uiState.userName,
                    streak = uiState.streak
                )
            }

            // Progress Overview Card
            item {
                ProgressOverviewCard(
                    weeklyProgress = uiState.weeklyProgress,
                    sessionsCompleted = uiState.sessionsThisWeek,
                    totalSessions = uiState.totalWeeklySessions
                )
            }

            // Learning Stats
            item {
                LearningStatsRow(
                    booksRead = uiState.totalBooksCompleted,
                    hoursStudied = uiState.totalHoursStudied,
                    currentStreak = uiState.streak
                )
            }

            // Quick Actions
            item {
                QuickActionsRow(
                    onLibraryClick = onNavigateToLibrary,
                    onKnowledgeBaseClick = onNavigateToKnowledgeBase,
                    onContinueReading = {
                        uiState.currentBook?.let { onNavigateToBook(it.id) }
                    },
                    hasCurrentBook = uiState.currentBook != null
                )
            }

            // Current Study Plan Card
            if (uiState.currentStudyPlan != null) {
                item {
                    CurrentStudyPlanCard(
                        studyPlan = uiState.currentStudyPlan
                    )
                }
            }

            // Recent Books Section
            if (uiState.recentBooks.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Books",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = Spacing.m)
                    )
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.m),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
                    ) {
                        items(uiState.recentBooks) { bookWithAnalysis ->
                            BookCard(
                                book = bookWithAnalysis.book,
                                evaluation = bookWithAnalysis.evaluation,
                                onClick = { onNavigateToBook(bookWithAnalysis.book.id) },
                                modifier = Modifier.width(200.dp)
                            )
                        }
                    }
                }
            }

            // Empty State
            if (uiState.recentBooks.isEmpty() && !uiState.isLoading) {
                item {
                    EmptyState(
                        icon = Icons.Default.MenuBook,
                        title = "Start Your Learning Journey",
                        message = "Upload your first trading book to begin building your knowledge",
                        actionButton = {
                            Button(
                                onClick = onUploadPdf,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LearningColors.Primary
                                )
                            ) {
                                Icon(Icons.Default.CloudUpload, null)
                                Spacer(modifier = Modifier.width(Spacing.s))
                                Text("Upload PDF")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xxl)
                    )
                }
            }

            // Loading State
            if (uiState.isLoading) {
                items(3) {
                    LoadingCard(
                        modifier = Modifier.padding(horizontal = Spacing.m)
                    )
                }
            }

            // Error State
            uiState.errorMessage?.let { error ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.m),
                        colors = CardDefaults.cardColors(
                            containerColor = LearningColors.Error.copy(alpha = 0.1f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LearningColors.Error)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.m),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = LearningColors.Error
                            )
                            Spacer(modifier = Modifier.width(Spacing.m))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = LearningColors.Error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeSection(
    userName: String,
    streak: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m)
    ) {
        Text(
            text = "Welcome back, $userName!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = LearningColors.OnPrimaryContainer
        )
        Spacer(modifier = Modifier.height(Spacing.s))
        if (streak > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = LearningColors.Tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "You're on a $streak-day learning streak!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LearningColors.Tertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ProgressOverviewCard(
    weeklyProgress: Float,
    sessionsCompleted: Int,
    totalSessions: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m),
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
                Column {
                    Text(
                        text = "This Week's Progress",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "$sessionsCompleted of $totalSessions sessions completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LearningColors.OnPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                CircularProgressIndicator(
                    progress = weeklyProgress,
                    modifier = Modifier.size(64.dp),
                    color = LearningColors.Secondary,
                    strokeWidth = 6.dp,
                    trackColor = LearningColors.SurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Spacing.m))

            LinearProgressIndicator(
                progress = weeklyProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = LearningColors.Secondary,
                trackColor = LearningColors.SurfaceVariant
            )
        }
    }
}

@Composable
private fun LearningStatsRow(
    booksRead: Int,
    hoursStudied: Int,
    currentStreak: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        StatsCard(
            label = "Books Read",
            value = "$booksRead",
            icon = Icons.Default.MenuBook,
            modifier = Modifier.weight(1f),
            color = LearningColors.Primary
        )

        StatsCard(
            label = "Hours Studied",
            value = "$hoursStudied",
            icon = Icons.Default.Schedule,
            modifier = Modifier.weight(1f),
            color = LearningColors.Secondary
        )

        StatsCard(
            label = "Day Streak",
            value = "$currentStreak",
            icon = Icons.Default.LocalFireDepartment,
            modifier = Modifier.weight(1f),
            color = LearningColors.Tertiary
        )
    }
}

@Composable
private fun QuickActionsRow(
    onLibraryClick: () -> Unit,
    onKnowledgeBaseClick: () -> Unit,
    onContinueReading: () -> Unit,
    hasCurrentBook: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        // Primary Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            FilledTonalButton(
                onClick = onLibraryClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = LearningColors.SecondaryContainer
                )
            ) {
                Icon(Icons.Default.LibraryBooks, null)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Library")
            }

            FilledTonalButton(
                onClick = onKnowledgeBaseClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = LearningColors.PrimaryContainer
                )
            ) {
                Icon(Icons.Default.School, null)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Knowledge")
            }
        }

        // Continue Reading Button
        if (hasCurrentBook) {
            Button(
                onClick = onContinueReading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LearningColors.Primary
                )
            ) {
                Icon(Icons.Default.AutoStories, null)
                Spacer(modifier = Modifier.width(Spacing.s))
                Text("Continue Reading")
            }
        }
    }
}

@Composable
private fun CurrentStudyPlanCard(
    studyPlan: StudyPlanWithProgress
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m),
        colors = CardDefaults.cardColors(
            containerColor = LearningColors.SecondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Study Plan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = LearningColors.Secondary
                )
            }

            Spacer(modifier = Modifier.height(Spacing.m))

            Text(
                text = studyPlan.studyPlan.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.s))

            Text(
                text = studyPlan.studyPlan.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(Spacing.m))

            LinearProgressIndicator(
                progress = studyPlan.overallProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = LearningColors.Success
            )

            Spacer(modifier = Modifier.height(Spacing.s))

            Text(
                text = "${(studyPlan.overallProgress * 100).toInt()}% Complete",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
