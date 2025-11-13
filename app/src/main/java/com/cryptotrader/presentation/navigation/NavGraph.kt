package com.cryptotrader.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cryptotrader.presentation.screens.chat.ChatScreen
import com.cryptotrader.presentation.screens.dashboard.DashboardScreen
import com.cryptotrader.presentation.screens.disclaimer.DisclaimerScreen
import com.cryptotrader.presentation.screens.setup.ApiKeySetupScreen
import com.cryptotrader.presentation.screens.settings.SettingsScreen
import com.cryptotrader.presentation.screens.strategy.StrategyConfigScreen

sealed class Screen(val route: String) {
    object Disclaimer : Screen("disclaimer")
    object ApiSetup : Screen("api_setup")
    object Dashboard : Screen("dashboard")
    object Strategy : Screen("strategy")
    object Settings : Screen("settings")
    object Chat : Screen("chat")
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

        composable(Screen.Strategy.route) {
            StrategyConfigScreen()
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

        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
