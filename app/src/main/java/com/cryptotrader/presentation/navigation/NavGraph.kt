package com.cryptotrader.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.cryptotrader.presentation.screens.ai.AIScreen
import com.cryptotrader.presentation.screens.dashboard.DashboardScreen
import com.cryptotrader.presentation.screens.disclaimer.DisclaimerScreen
import com.cryptotrader.presentation.screens.market.MarketScreen
import com.cryptotrader.presentation.screens.orders.OrderManagementScreen
import com.cryptotrader.presentation.screens.portfolio.PortfolioScreen
import com.cryptotrader.presentation.screens.positions.PositionManagementScreen
import com.cryptotrader.presentation.screens.reports.ReportsScreen
import com.cryptotrader.presentation.screens.history.TradingHistoryScreen
import com.cryptotrader.presentation.screens.analytics.PerformanceScreen
import com.cryptotrader.presentation.screens.chat.ChatScreen
import com.cryptotrader.presentation.screens.strategy.CreateStrategyScreen
import com.cryptotrader.presentation.screens.settings.SettingsScreen
import com.cryptotrader.presentation.screens.setup.ApiKeySetupScreen
// TEMPORARILY DISABLED - Learning section has compilation errors
// import com.cryptotrader.presentation.screens.learning.LearningHomeScreen
// import com.cryptotrader.presentation.screens.learning.LibraryScreen
// import com.cryptotrader.presentation.screens.learning.BookDetailScreen
// import com.cryptotrader.presentation.screens.learning.StudyPlanScreen
// import com.cryptotrader.presentation.screens.learning.KnowledgeBaseScreen

sealed class Screen(val route: String) {
    object Disclaimer : Screen("disclaimer")
    object ApiSetup : Screen("api_setup")
    object Dashboard : Screen("dashboard")
    object Market : Screen("market")
    object AI : Screen("ai")
    object Portfolio : Screen("portfolio")
    object Reports : Screen("reports")
    object Orders : Screen("orders")
    object Positions : Screen("positions")
    object History : Screen("history")
    object Analytics : Screen("analytics")
    object Chat : Screen("chat")
    object CreateStrategy : Screen("create_strategy")
    object Settings : Screen("settings")

    // Learning Section
    object Learning : Screen("learning")
    object Library : Screen("library")
    object BookDetail : Screen("book_detail/{bookId}") {
        fun createRoute(bookId: String) = "book_detail/$bookId"
    }
    object StudyPlan : Screen("study_plan/{planId}") {
        fun createRoute(planId: String) = "study_plan/$planId"
    }
    object KnowledgeBase : Screen("knowledge_base")
    object Chapter : Screen("chapter/{chapterId}") {
        fun createRoute(chapterId: String) = "chapter/$chapterId"
    }
    object Topic : Screen("topic/{topicId}") {
        fun createRoute(topicId: String) = "topic/$topicId"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Disclaimer.route) {
            DisclaimerScreen(
                onAccepted = {
                    navController.navigate(Screen.ApiSetup.route) {
                        popUpTo(Screen.Disclaimer.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ApiSetup.route) {
            ApiKeySetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.ApiSetup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen()
        }

        composable(Screen.Market.route) {
            MarketScreen()
        }

        composable(Screen.AI.route) {
            AIScreen()
        }

        composable(Screen.Portfolio.route) {
            PortfolioScreen()
        }

        composable(Screen.Reports.route) {
            ReportsScreen()
        }

        composable(Screen.Orders.route) {
            OrderManagementScreen()
        }

        composable(Screen.Positions.route) {
            PositionManagementScreen()
        }

        composable(Screen.History.route) {
            TradingHistoryScreen()
        }

        composable(Screen.Analytics.route) {
            PerformanceScreen()
        }

        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.CreateStrategy.route) {
            CreateStrategyScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onLogoutComplete = {
                    navController.navigate(Screen.ApiSetup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // TEMPORARILY DISABLED - Learning Section has compilation errors
        // Will be re-enabled after fixing
        /*
        composable(Screen.Learning.route) {
            LearningHomeScreen(
                onNavigateToLibrary = {
                    navController.navigate(Screen.Library.route)
                },
                onNavigateToKnowledgeBase = {
                    navController.navigate(Screen.KnowledgeBase.route)
                },
                onNavigateToBook = { bookId ->
                    navController.navigate(Screen.BookDetail.createRoute(bookId))
                },
                onUploadPdf = {
                    // TODO: Implement PDF upload functionality
                    // This will likely open a file picker and handle PDF import
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToBook = { bookId ->
                    navController.navigate(Screen.BookDetail.createRoute(bookId))
                },
                onUploadPdf = {
                    // TODO: Implement PDF upload functionality
                }
            )
        }

        composable(
            route = Screen.BookDetail.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            BookDetailScreen(
                bookId = bookId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToChapter = { chapterId ->
                    navController.navigate(Screen.Chapter.createRoute(chapterId))
                },
                onNavigateToStudyPlan = { planId ->
                    navController.navigate(Screen.StudyPlan.createRoute(planId))
                }
            )
        }

        composable(
            route = Screen.StudyPlan.route,
            arguments = listOf(
                navArgument("planId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val planId = backStackEntry.arguments?.getString("planId") ?: return@composable
            StudyPlanScreen(
                planId = planId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.KnowledgeBase.route) {
            KnowledgeBaseScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToTopic = { topicId ->
                    navController.navigate(Screen.Topic.createRoute(topicId))
                }
            )
        }
        */

        // TEMPORARILY DISABLED - Chapter and Topic screens for Learning section
        /*
        composable(
            route = Screen.Chapter.route,
            arguments = listOf(
                navArgument("chapterId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: return@composable
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Text("Chapter View - Coming Soon")
            }
        }

        composable(
            route = Screen.Topic.route,
            arguments = listOf(
                navArgument("topicId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val topicId = backStackEntry.arguments?.getString("topicId") ?: return@composable
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Text("Topic Detail - Coming Soon")
            }
        }
        */
    }
}
