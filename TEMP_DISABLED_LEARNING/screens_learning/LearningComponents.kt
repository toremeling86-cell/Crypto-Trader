package com.cryptotrader.presentation.screens.learning

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cryptotrader.domain.model.*
import java.time.format.DateTimeFormatter

/**
 * Reusable Components for Learning Section
 * Material Design 3 Implementation
 */

// Color Palette
object LearningColors {
    val Primary = Color(0xFF6750A4)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFE9DDFF)
    val OnPrimaryContainer = Color(0xFF22005D)

    val Secondary = Color(0xFF00BFA5)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFA7F3E3)
    val OnSecondaryContainer = Color(0xFF003730)

    val Tertiary = Color(0xFFFFB300)
    val TertiaryContainer = Color(0xFFFFE082)

    val Surface = Color(0xFFFEF7FF)
    val SurfaceVariant = Color(0xFFE7E0EC)
    val SurfaceElevated = Color(0xFFFFFFFF)

    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)
    val Error = Color(0xFFE91E63)
    val Info = Color(0xFF2196F3)

    val NotStarted = Color(0xFF9E9E9E)
    val InProgress = Color(0xFFFFB300)
    val Completed = Color(0xFF4CAF50)
}

// Spacing System
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

/**
 * Book Card Component
 * Displays book with cover, title, author, progress, and AI rating
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookCard(
    book: LearningBook,
    evaluation: BookEvaluation?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 8.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = LearningColors.SurfaceElevated
        )
    ) {
        Column {
            // Book Cover Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                LearningColors.Primary.copy(alpha = 0.1f),
                                LearningColors.Primary.copy(alpha = 0.3f)
                            )
                        )
                    )
            ) {
                // AI Rating Badge
                if (evaluation != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.s),
                        shape = RoundedCornerShape(Spacing.s),
                        color = when {
                            evaluation.overallRating >= 4.0f -> LearningColors.Success
                            evaluation.overallRating >= 3.0f -> LearningColors.Warning
                            else -> LearningColors.Error
                        }
                    ) {
                        Text(
                            text = "${String.format("%.1f", evaluation.overallRating)}/5",
                            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Progress Indicator
                if (book.readingProgress > 0) {
                    LinearProgressIndicator(
                        progress = book.readingProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        color = LearningColors.Secondary,
                        trackColor = Color.Black.copy(alpha = 0.2f)
                    )
                }
            }

            // Book Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.m)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (book.author != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.s))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                ) {
                    // Status Chip
                    val statusColor = when (book.analysisStatus) {
                        AnalysisStatus.ANALYZED -> LearningColors.Success
                        AnalysisStatus.ANALYZING -> LearningColors.InProgress
                        else -> LearningColors.NotStarted
                    }

                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = book.category.name.replace("_", " "),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = statusColor.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    }
}

/**
 * AI Evaluation Card
 * Displays Claude's honest assessment of a book
 */
@Composable
fun AIEvaluationCard(
    evaluation: BookEvaluation,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                evaluation.overallRating >= 4.0f -> LearningColors.Success.copy(alpha = 0.1f)
                evaluation.overallRating >= 3.0f -> LearningColors.Warning.copy(alpha = 0.1f)
                else -> LearningColors.Error.copy(alpha = 0.1f)
            }
        ),
        border = BorderStroke(
            width = 2.dp,
            color = when {
                evaluation.overallRating >= 4.0f -> LearningColors.Success
                evaluation.overallRating >= 3.0f -> LearningColors.Warning
                else -> LearningColors.Error
            }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "AI",
                        tint = LearningColors.Primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.s))
                    Text(
                        text = "Claude's Honest Assessment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Rating Badge
                Surface(
                    shape = CircleShape,
                    color = when {
                        evaluation.overallRating >= 4.0f -> LearningColors.Success
                        evaluation.overallRating >= 3.0f -> LearningColors.Warning
                        else -> LearningColors.Error
                    }
                ) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%.1f", evaluation.overallRating),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.m))

            // Evaluation Text
            Text(
                text = evaluation.detailedReview,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp
            )

            if (evaluation.strengths.isNotEmpty() || evaluation.weaknesses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.m))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m)
                ) {
                    // Strengths
                    if (evaluation.strengths.isNotEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Strengths",
                                style = MaterialTheme.typography.titleSmall,
                                color = LearningColors.Success,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(Spacing.s))
                            evaluation.strengths.take(3).forEach { strength ->
                                Row(
                                    modifier = Modifier.padding(vertical = Spacing.xs)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = LearningColors.Success,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = strength,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    // Weaknesses
                    if (evaluation.weaknesses.isNotEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Weaknesses",
                                style = MaterialTheme.typography.titleSmall,
                                color = LearningColors.Warning,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(Spacing.s))
                            evaluation.weaknesses.take(3).forEach { weakness ->
                                Row(
                                    modifier = Modifier.padding(vertical = Spacing.xs)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = LearningColors.Warning,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = weakness,
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
}

/**
 * Stats Card Component
 * Displays learning statistics
 */
@Composable
fun StatsCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    color: Color = LearningColors.Primary
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.s))
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
}

/**
 * Chapter List Item
 * Displays chapter with summary and completion status
 */
@Composable
fun ChapterListItem(
    chapter: ChapterSummary,
    isCompleted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                LearningColors.Success.copy(alpha = 0.1f)
            else
                LearningColors.SurfaceElevated
        ),
        border = if (isCompleted)
            BorderStroke(1.dp, LearningColors.Success)
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chapter Number Circle
            Surface(
                shape = CircleShape,
                color = if (isCompleted)
                    LearningColors.Success
                else
                    LearningColors.Primary,
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
                            tint = Color.White
                        )
                    } else {
                        Text(
                            text = "${chapter.chapterNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.m))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isCompleted)
                        TextDecoration.LineThrough
                    else
                        TextDecoration.None
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = chapter.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
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
                        text = "${chapter.estimatedReadingTime.toMinutes()} min",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Topic Card Component
 * Displays knowledge base topic with status
 */
@Composable
fun TopicCard(
    topic: KnowledgeTopic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LearningColors.SurfaceElevated
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
                    else -> Icons.Default.RadioButtonUnchecked
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = when (topic.masteryLevel) {
                    MasteryLevel.MASTER, MasteryLevel.EXPERT -> LearningColors.Success
                    MasteryLevel.PROFICIENT, MasteryLevel.FAMILIAR -> LearningColors.Warning
                    else -> LearningColors.NotStarted
                }
            )

            Spacer(modifier = Modifier.width(Spacing.m))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = topic.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = topic.category.name.replace("_", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Importance Badge
            if (topic.importance == ImportanceLevel.CRITICAL || topic.importance == ImportanceLevel.HIGH) {
                Surface(
                    shape = RoundedCornerShape(Spacing.xs),
                    color = if (topic.importance == ImportanceLevel.CRITICAL)
                        LearningColors.Error
                    else
                        LearningColors.Warning
                ) {
                    Text(
                        text = topic.importance.name,
                        modifier = Modifier.padding(
                            horizontal = Spacing.s,
                            vertical = Spacing.xs
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Loading State with Shimmer Effect
 */
@Composable
fun LoadingCard(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LearningColors.SurfaceVariant.copy(alpha = alpha)
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.height(Spacing.s))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }
    }
}

/**
 * Empty State Component
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionButton: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(Spacing.m))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(Spacing.s))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        if (actionButton != null) {
            Spacer(modifier = Modifier.height(Spacing.l))
            actionButton()
        }
    }
}
