package com.cryptotrader.presentation.screens.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

/**
 * Book Detail Screen
 * Shows comprehensive book information with tabs for Overview, Study Plan, Chapters, and Progress
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChapter: (String) -> Unit,
    onNavigateToStudyPlan: (String) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleBookmark() }) {
                        Icon(
                            imageVector = if (uiState.book?.isFavorite == true)
                                Icons.Default.Bookmark
                            else
                                Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (uiState.book?.isFavorite == true)
                                LearningColors.Tertiary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { /* Share */ }) {
                        Icon(Icons.Default.Share, "Share")
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
        } else if (uiState.book != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                // Book Header
                item {
                    BookHeaderSection(
                        book = uiState.book!!,
                        evaluation = uiState.evaluation
                    )
                }

                // AI Evaluation Card
                if (uiState.evaluation != null) {
                    item {
                        AIEvaluationCard(
                            evaluation = uiState.evaluation!!,
                            modifier = Modifier.padding(horizontal = Spacing.m)
                        )
                    }
                }

                // Tab Row
                item {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = LearningColors.Primary
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Overview") },
                            icon = { Icon(Icons.Default.Info, null) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Study Plan") },
                            icon = { Icon(Icons.Default.CalendarMonth, null) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Chapters") },
                            icon = { Icon(Icons.Default.List, null) }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("Progress") },
                            icon = { Icon(Icons.Default.TrendingUp, null) }
                        )
                    }
                }

                // Tab Content
                when (selectedTab) {
                    0 -> {
                        item {
                            OverviewTab(
                                book = uiState.book!!,
                                analysis = uiState.analysis,
                                modifier = Modifier.padding(horizontal = Spacing.m)
                            )
                        }
                    }
                    1 -> {
                        if (uiState.studyPlan != null) {
                            item {
                                StudyPlanPreview(
                                    studyPlan = uiState.studyPlan!!,
                                    onViewFullPlan = { onNavigateToStudyPlan(uiState.studyPlan!!.id) },
                                    modifier = Modifier.padding(horizontal = Spacing.m)
                                )
                            }
                        } else {
                            item {
                                EmptyState(
                                    icon = Icons.Default.CalendarMonth,
                                    title = "No Study Plan Yet",
                                    message = "Create a personalized study plan for this book",
                                    actionButton = {
                                        Button(
                                            onClick = { viewModel.createStudyPlan() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = LearningColors.Primary
                                            )
                                        ) {
                                            Icon(Icons.Default.Add, null)
                                            Spacer(modifier = Modifier.width(Spacing.s))
                                            Text("Create Study Plan")
                                        }
                                    },
                                    modifier = Modifier.padding(Spacing.xl)
                                )
                            }
                        }
                    }
                    2 -> {
                        if (uiState.chapters.isNotEmpty()) {
                            items(uiState.chapters) { chapter ->
                                ChapterListItem(
                                    chapter = chapter,
                                    isCompleted = uiState.completedChapters.contains(chapter.id),
                                    onClick = { onNavigateToChapter(chapter.id) },
                                    modifier = Modifier.padding(horizontal = Spacing.m)
                                )
                            }
                        } else {
                            item {
                                EmptyState(
                                    icon = Icons.Default.MenuBook,
                                    title = "No Chapters Available",
                                    message = "Chapters will appear after AI analysis",
                                    modifier = Modifier.padding(Spacing.xl)
                                )
                            }
                        }
                    }
                    3 -> {
                        item {
                            ProgressTab(
                                progress = uiState.progress,
                                modifier = Modifier.padding(horizontal = Spacing.m)
                            )
                        }
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
                Text("Book not found")
            }
        }
    }
}

@Composable
private fun BookHeaderSection(
    book: LearningBook,
    evaluation: BookEvaluation?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m),
        colors = CardDefaults.cardColors(
            containerColor = LearningColors.PrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (book.author != null) {
                        Spacer(modifier = Modifier.height(Spacing.s))
                        Text(
                            text = "by ${book.author}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                if (evaluation != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = when {
                            evaluation.overallRating >= 4.0f -> LearningColors.Success
                            evaluation.overallRating >= 3.0f -> LearningColors.Warning
                            else -> LearningColors.Error
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.m),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = String.format("%.1f", evaluation.overallRating),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "/ 5.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.m))

            // Book metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                MetadataChip(
                    icon = Icons.Default.Category,
                    label = book.category.name.replace("_", " ")
                )

                if (book.pageCount != null) {
                    MetadataChip(
                        icon = Icons.Default.Pages,
                        label = "${book.pageCount} pages"
                    )
                }

                if (book.publicationYear != null) {
                    MetadataChip(
                        icon = Icons.Default.CalendarMonth,
                        label = "${book.publicationYear}"
                    )
                }
            }

            // Reading Progress
            if (book.readingProgress > 0) {
                Spacer(modifier = Modifier.height(Spacing.m))
                Column {
                    Text(
                        text = "Reading Progress",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(Spacing.s))
                    LinearProgressIndicator(
                        progress = book.readingProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = LearningColors.Secondary
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "${(book.readingProgress * 100).toInt()}% Complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    AssistChip(
        onClick = { },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

@Composable
private fun OverviewTab(
    book: LearningBook,
    analysis: BookAnalysis?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        if (analysis != null) {
            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = LearningColors.SurfaceElevated
                )
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.m)
                ) {
                    Text(
                        text = "Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(Spacing.s))
                    Text(
                        text = analysis.summary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Key Takeaways
            if (analysis.keyTakeaways.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = LearningColors.SecondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.m)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = LearningColors.Secondary
                            )
                            Spacer(modifier = Modifier.width(Spacing.s))
                            Text(
                                text = "Key Takeaways",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.m))
                        analysis.keyTakeaways.forEach { takeaway ->
                            Row(
                                modifier = Modifier.padding(vertical = Spacing.xs)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Circle,
                                    contentDescription = null,
                                    modifier = Modifier.size(8.dp),
                                    tint = LearningColors.Secondary
                                )
                                Spacer(modifier = Modifier.width(Spacing.s))
                                Text(
                                    text = takeaway,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // Metadata Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = LearningColors.SurfaceElevated
                )
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m)
                ) {
                    Text(
                        text = "Book Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    InfoRow("Difficulty", analysis.difficultyLevel.name)
                    InfoRow("Target Audience", analysis.targetAudience)
                    InfoRow(
                        "Estimated Reading Time",
                        "${analysis.estimatedReadingTime.toHours()}h ${analysis.estimatedReadingTime.toMinutes() % 60}m"
                    )

                    if (analysis.prerequisites.isNotEmpty()) {
                        Column {
                            Text(
                                text = "Prerequisites",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            analysis.prerequisites.forEach { prereq ->
                                Text(
                                    text = "• $prereq",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = Spacing.s)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            EmptyState(
                icon = Icons.Default.Psychology,
                title = "Analysis in Progress",
                message = "AI is analyzing this book. Check back soon for insights!",
                modifier = Modifier.padding(Spacing.xl)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StudyPlanPreview(
    studyPlan: StudyPlan,
    onViewFullPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LearningColors.PrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m)
        ) {
            Text(
                text = studyPlan.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.s))
            Text(
                text = studyPlan.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(Spacing.m))

            Button(
                onClick = onViewFullPlan,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LearningColors.Primary
                )
            ) {
                Text("View Full Study Plan")
                Spacer(modifier = Modifier.width(Spacing.s))
                Icon(Icons.Default.ArrowForward, null)
            }
        }
    }
}

@Composable
private fun ProgressTab(
    progress: StudyProgress?,
    modifier: Modifier = Modifier
) {
    if (progress != null) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            // Overall Progress Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = LearningColors.SecondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.m)
                ) {
                    Text(
                        text = "Overall Progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(Spacing.m))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProgressStat(
                            label = "Completion",
                            value = "${(progress.percentComplete * 100).toInt()}%"
                        )
                        ProgressStat(
                            label = "Current Chapter",
                            value = "${progress.currentChapter}"
                        )
                        ProgressStat(
                            label = "Time Spent",
                            value = "${progress.totalTimeSpent.toHours()}h"
                        )
                    }
                }
            }

            // Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                StatsCard(
                    label = "Streak",
                    value = "${progress.streak}",
                    icon = Icons.Default.LocalFireDepartment,
                    modifier = Modifier.weight(1f),
                    color = LearningColors.Tertiary
                )

                StatsCard(
                    label = "Quiz Avg",
                    value = "${progress.quizScores.map { it.score }.average().toInt()}%",
                    icon = Icons.Default.Quiz,
                    modifier = Modifier.weight(1f),
                    color = LearningColors.Primary
                )
            }
        }
    } else {
        EmptyState(
            icon = Icons.Default.TrendingUp,
            title = "No Progress Yet",
            message = "Start reading to track your progress",
            modifier = Modifier.padding(Spacing.xl)
        )
    }
}

@Composable
private fun ProgressStat(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = LearningColors.Secondary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
