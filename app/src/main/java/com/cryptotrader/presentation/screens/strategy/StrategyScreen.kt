package com.cryptotrader.presentation.screens.strategy

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptotrader.presentation.screens.ai.StrategyTestCenterScreen
import com.cryptotrader.presentation.screens.chat.ChatScreen
import com.cryptotrader.presentation.screens.reports.ReportsScreen

/**
 * Strategy Hub Screen - Central location for all strategy-related functionality
 *
 * Contains 6 tabs:
 * 1. AI Generator - ChatScreen for AI-powered strategy creation
 * 2. Create Manual - Manual strategy builder with full parameters (TODO: Expand in Phase 3)
 * 3. My Strategies - List, activate, edit, delete strategies
 * 4. Test Center - Backtest engine with dataset management
 * 5. Reports Library - Expert markdown reports for inspiration
 * 6. AI Insights - Market analysis by Claude AI
 */
@Composable
fun StrategyScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        CenterAlignedTopAppBar(
            title = { Text("Strategy Hub") },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Tab Row with 6 tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 0.dp
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("AI Generator") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Create Manual") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("My Strategies") }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Test Center") }
            )
            Tab(
                selected = selectedTab == 4,
                onClick = { selectedTab = 4 },
                text = { Text("Reports Library") }
            )
            Tab(
                selected = selectedTab == 5,
                onClick = { selectedTab = 5 },
                text = { Text("AI Insights") }
            )
        }

        // Tab Content
        when (selectedTab) {
            0 -> AIGeneratorTab()
            1 -> CreateManualTab()
            2 -> MyStrategiesTab()
            3 -> TestCenterTab()
            4 -> ReportsLibraryTab()
            5 -> AIInsightsTab()
        }
    }
}

/**
 * Tab 1: AI Generator
 * Uses ChatScreen for natural language strategy creation
 */
@Composable
fun AIGeneratorTab() {
    ChatScreen(onNavigateBack = {})
}

/**
 * Tab 2: Create Manual
 * Manual strategy builder - will be expanded in Phase 3 with full parameters
 */
@Composable
fun CreateManualTab() {
    CreateStrategyScreen(onNavigateBack = {})
}

/**
 * Tab 3: My Strategies
 * List of user strategies with activation, editing, deletion
 */
@Composable
fun MyStrategiesTab() {
    StrategyConfigScreen()
}

/**
 * Tab 4: Test Center
 * Backtest engine with dataset management
 */
@Composable
fun TestCenterTab() {
    StrategyTestCenterScreen()
}

/**
 * Tab 5: Reports Library
 * Expert markdown reports from file system
 */
@Composable
fun ReportsLibraryTab() {
    ReportsScreen()
}

/**
 * Tab 6: AI Insights
 * Market analysis from Claude AI
 */
@Composable
fun AIInsightsTab() {
    com.cryptotrader.presentation.screens.ai.AnalysisTab()
}
