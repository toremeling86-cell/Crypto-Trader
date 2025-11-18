package com.cryptotrader.presentation.screens.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cryptotrader.domain.model.*

/**
 * Library Screen
 * Displays all books with search, filter, and sort capabilities
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBook: (String) -> Unit,
    onUploadPdf: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // View toggle (Grid/List)
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector = if (uiState.isGridView)
                                Icons.Default.ViewList
                            else
                                Icons.Default.GridView,
                            contentDescription = "Toggle View"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onUploadPdf,
                containerColor = LearningColors.Primary,
                contentColor = LearningColors.OnPrimary
            ) {
                Icon(Icons.Default.Add, "Add")
                Spacer(modifier = Modifier.width(Spacing.s))
                Text("Upload PDF")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.m)
            )

            // Filter Chips
            FilterChipsRow(
                selectedCategory = uiState.selectedCategory,
                selectedStatus = uiState.selectedStatus,
                onCategorySelected = { viewModel.selectCategory(it) },
                onStatusSelected = { viewModel.selectStatus(it) }
            )

            // Sort Options
            SortOptionsRow(
                selectedSort = uiState.sortOption,
                onSortSelected = { viewModel.selectSort(it) }
            )

            // Books Grid/List
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.m),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = LearningColors.Primary
                    )
                }
            } else if (uiState.filteredBooks.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "No books found",
                    message = if (uiState.searchQuery.isNotBlank())
                        "Try adjusting your search or filters"
                    else
                        "Upload your first book to get started",
                    actionButton = if (uiState.searchQuery.isBlank()) {
                        {
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
                        }
                    } else null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                if (uiState.isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.m),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m)
                    ) {
                        items(uiState.filteredBooks) { bookWithAnalysis ->
                            BookCard(
                                book = bookWithAnalysis.book,
                                evaluation = bookWithAnalysis.evaluation,
                                onClick = { onNavigateToBook(bookWithAnalysis.book.id) }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m)
                    ) {
                        items(uiState.filteredBooks) { bookWithAnalysis ->
                            BookListItem(
                                book = bookWithAnalysis.book,
                                evaluation = bookWithAnalysis.evaluation,
                                onClick = { onNavigateToBook(bookWithAnalysis.book.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search books...") },
        leadingIcon = {
            Icon(Icons.Default.Search, "Search")
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, "Clear")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
private fun FilterChipsRow(
    selectedCategory: BookCategory?,
    selectedStatus: AnalysisStatus?,
    onCategorySelected: (BookCategory?) -> Unit,
    onStatusSelected: (AnalysisStatus?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.s)
    ) {
        Text(
            text = "Category",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text("All") }
                )
            }

            items(BookCategory.values()) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.name.replace("_", " ")) }
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.s))

        Text(
            text = "Status",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            item {
                FilterChip(
                    selected = selectedStatus == null,
                    onClick = { onStatusSelected(null) },
                    label = { Text("All") }
                )
            }

            item {
                FilterChip(
                    selected = selectedStatus == AnalysisStatus.ANALYZED,
                    onClick = { onStatusSelected(AnalysisStatus.ANALYZED) },
                    label = { Text("Analyzed") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            item {
                FilterChip(
                    selected = selectedStatus == AnalysisStatus.NOT_ANALYZED,
                    onClick = { onStatusSelected(AnalysisStatus.NOT_ANALYZED) },
                    label = { Text("Not Analyzed") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.RadioButtonUnchecked,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SortOptionsRow(
    selectedSort: SortOption,
    onSortSelected: (SortOption) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Sort by:",
            style = MaterialTheme.typography.labelMedium
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            items(SortOption.values()) { option ->
                FilterChip(
                    selected = selectedSort == option,
                    onClick = { onSortSelected(option) },
                    label = { Text(option.displayName) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookListItem(
    book: LearningBook,
    evaluation: BookEvaluation?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
            // Book Icon/Cover
            Surface(
                shape = MaterialTheme.shapes.small,
                color = LearningColors.Primary.copy(alpha = 0.2f),
                modifier = Modifier.size(60.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = LearningColors.Primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.m))

            // Book Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )

                if (book.author != null) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                ) {
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                book.category.name.replace("_", " "),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = LearningColors.PrimaryContainer
                        )
                    )
                }
            }

            // Rating and Progress
            Column(
                horizontalAlignment = Alignment.End
            ) {
                if (evaluation != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = when {
                            evaluation.overallRating >= 4.0f -> LearningColors.Success
                            evaluation.overallRating >= 3.0f -> LearningColors.Warning
                            else -> LearningColors.Error
                        }
                    ) {
                        Text(
                            text = String.format("%.1f", evaluation.overallRating),
                            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                            style = MaterialTheme.typography.labelLarge,
                            color = androidx.compose.ui.graphics.Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s))

                if (book.readingProgress > 0) {
                    Text(
                        text = "${(book.readingProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = LearningColors.Secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

enum class SortOption(val displayName: String) {
    RECENT("Recent"),
    TITLE("Title"),
    AUTHOR("Author"),
    RATING("Rating"),
    PROGRESS("Progress")
}
