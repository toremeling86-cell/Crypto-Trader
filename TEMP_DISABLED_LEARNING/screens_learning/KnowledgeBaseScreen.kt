package com.cryptotrader.presentation.screens.learning

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cryptotrader.domain.model.*

/**
 * Knowledge Base Screen
 * Displays hierarchical topic tree with progress tracking and mastery levels
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTopic: (String) -> Unit,
    viewModel: KnowledgeBaseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Knowledge Base") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                    IconButton(onClick = { /* Filter */ }) {
                        Icon(Icons.Default.FilterList, "Filter")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            // Overall Progress Card
            item {
                KnowledgeOverviewCard(
                    overview = uiState.overview
                )
            }

            // Mastery Distribution
            item {
                MasteryDistributionCard(
                    distribution = uiState.overview.masteryDistribution
                )
            }

            // Topics to Review
            if (uiState.overview.topicsToReview.isNotEmpty()) {
                item {
                    Text(
                        text = "Topics to Review",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(uiState.overview.topicsToReview.take(3)) { topic ->
                    TopicCard(
                        topic = topic,
                        onClick = { onNavigateToTopic(topic.id) }
                    )
                }
            }

            // Category Breakdown
            item {
                Text(
                    text = "All Topics by Category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(uiState.categories) { category ->
                CategoryCard(
                    category = category,
                    isExpanded = uiState.expandedCategories.contains(category.category),
                    onToggleExpand = { viewModel.toggleCategoryExpansion(category.category) },
                    onTopicClick = onNavigateToTopic
                )
            }

            // Recently Studied
            if (uiState.overview.recentlyStudied.isNotEmpty()) {
                item {
                    Text(
                        text = "Recently Studied",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(uiState.overview.recentlyStudied) { topic ->
                    TopicCard(
                        topic = topic,
                        onClick = { onNavigateToTopic(topic.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KnowledgeOverviewCard(
    overview: KnowledgeBaseOverview
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = LearningColors.Primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.m))
                Text(
                    text = "Knowledge Overview",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(Spacing.m))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OverviewStat(
                    label = "Total Topics",
                    value = "${overview.totalTopics}",
                    icon = Icons.Default.Topic
                )

                OverviewStat(
                    label = "Studied",
                    value = "${overview.studiedTopics}",
                    icon = Icons.Default.CheckCircle,
                    color = LearningColors.Success
                )

                OverviewStat(
                    label = "Remaining",
                    value = "${overview.totalTopics - overview.studiedTopics}",
                    icon = Icons.Default.RadioButtonUnchecked,
                    color = LearningColors.NotStarted
                )
            }

            Spacer(modifier = Modifier.height(Spacing.m))

            // Overall Progress
            val progress = if (overview.totalTopics > 0)
                overview.studiedTopics.toFloat() / overview.totalTopics
            else
                0f

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = LearningColors.Success
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Text(
                text = "${(progress * 100).toInt()}% Complete",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun OverviewStat(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color = LearningColors.Primary
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun MasteryDistributionCard(
    distribution: Map<MasteryLevel, Int>
) {
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
                text = "Mastery Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.m))

            MasteryLevel.values().forEach { level ->
                val count = distribution[level] ?: 0
                if (count > 0) {
                    MasteryLevelRow(
                        level = level,
                        count = count,
                        totalCount = distribution.values.sum()
                    )
                    Spacer(modifier = Modifier.height(Spacing.s))
                }
            }
        }
    }
}

@Composable
private fun MasteryLevelRow(
    level: MasteryLevel,
    count: Int,
    totalCount: Int
) {
    val progress = if (totalCount > 0) count.toFloat() / totalCount else 0f
    val color = when (level) {
        MasteryLevel.MASTER, MasteryLevel.EXPERT -> LearningColors.Success
        MasteryLevel.PROFICIENT, MasteryLevel.FAMILIAR -> LearningColors.Warning
        MasteryLevel.BEGINNER -> LearningColors.Info
        MasteryLevel.NOT_STARTED -> LearningColors.NotStarted
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = level.name.replace("_", " "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$count (${(progress * 100).toInt()}%)",
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xs))

        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryCard(
    category: CategoryWithTopics,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onTopicClick: (String) -> Unit
) {
    Card(
        onClick = onToggleExpand,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = LearningColors.SurfaceElevated
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Category Icon with Progress Ring
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = category.progress,
                            modifier = Modifier.size(56.dp),
                            strokeWidth = 4.dp,
                            color = when {
                                category.progress >= 0.8f -> LearningColors.Success
                                category.progress >= 0.4f -> LearningColors.Warning
                                else -> LearningColors.NotStarted
                            },
                            trackColor = LearningColors.SurfaceVariant
                        )

                        Icon(
                            imageVector = getCategoryIcon(category.category),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = LearningColors.Primary
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.m))

                    Column {
                        Text(
                            text = category.category.name.replace("_", " "),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${category.completedTopics}/${category.totalTopics} topics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = "Expand"
                    )
                }
            }

            // Progress Bar
            Spacer(modifier = Modifier.height(Spacing.m))
            LinearProgressIndicator(
                progress = category.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = when {
                    category.progress >= 0.8f -> LearningColors.Success
                    category.progress >= 0.4f -> LearningColors.Warning
                    else -> LearningColors.Primary
                }
            )

            // Expanded Topics
            if (isExpanded) {
                Spacer(modifier = Modifier.height(Spacing.m))
                Divider()
                Spacer(modifier = Modifier.height(Spacing.m))

                category.topics.forEach { topic ->
                    TopicItem(
                        topic = topic,
                        onClick = { onTopicClick(topic.id) }
                    )
                    Spacer(modifier = Modifier.height(Spacing.s))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicItem(
    topic: KnowledgeTopic,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = when (topic.masteryLevel) {
                MasteryLevel.MASTER, MasteryLevel.EXPERT -> LearningColors.Success
                MasteryLevel.PROFICIENT, MasteryLevel.FAMILIAR -> LearningColors.Warning
                else -> LearningColors.SurfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon
            Icon(
                imageVector = when (topic.masteryLevel) {
                    MasteryLevel.MASTER, MasteryLevel.EXPERT -> Icons.Default.CheckCircle
                    MasteryLevel.PROFICIENT, MasteryLevel.FAMILIAR -> Icons.Default.RadioButtonChecked
                    MasteryLevel.BEGINNER -> Icons.Default.Circle
                    MasteryLevel.NOT_STARTED -> Icons.Default.RadioButtonUnchecked
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = when (topic.masteryLevel) {
                    MasteryLevel.MASTER, MasteryLevel.EXPERT -> LearningColors.Success
                    MasteryLevel.PROFICIENT, MasteryLevel.FAMILIAR -> LearningColors.Warning
                    MasteryLevel.BEGINNER -> LearningColors.Info
                    MasteryLevel.NOT_STARTED -> LearningColors.NotStarted
                }
            )

            Spacer(modifier = Modifier.width(Spacing.m))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = topic.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (topic.masteryLevel == MasteryLevel.MASTER)
                        TextDecoration.None
                    else
                        TextDecoration.None
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = topic.masteryLevel.name.replace("_", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    if (topic.importance == ImportanceLevel.CRITICAL || topic.importance == ImportanceLevel.HIGH) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (topic.importance == ImportanceLevel.CRITICAL)
                                LearningColors.Error
                            else
                                LearningColors.Warning
                        ) {
                            Text(
                                text = topic.importance.name,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.xs,
                                    vertical = 2.dp
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // Time spent badge
            if (topic.totalStudyTime.toMinutes() > 0) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = LearningColors.SecondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = LearningColors.Secondary
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "${topic.totalStudyTime.toMinutes()}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = LearningColors.Secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// Helper function to get category icons
private fun getCategoryIcon(category: TopicCategory): ImageVector {
    return when (category) {
        TopicCategory.FUNDAMENTALS -> Icons.Default.Foundation
        TopicCategory.TECHNICAL -> Icons.Default.BarChart
        TopicCategory.STRATEGIC -> Icons.Default.Strategy
        TopicCategory.PSYCHOLOGICAL -> Icons.Default.Psychology
        TopicCategory.MATHEMATICAL -> Icons.Default.Calculate
        TopicCategory.REGULATORY -> Icons.Default.Gavel
        TopicCategory.PRACTICAL -> Icons.Default.BuildCircle
    }
}

// Data classes for UI
data class CategoryWithTopics(
    val category: TopicCategory,
    val topics: List<KnowledgeTopic>,
    val totalTopics: Int,
    val completedTopics: Int,
    val progress: Float
)
