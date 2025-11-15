package com.cryptotrader.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cryptotrader.presentation.screens.ai.AIScreen
import com.cryptotrader.presentation.screens.dashboard.DashboardScreen
import com.cryptotrader.presentation.screens.disclaimer.DisclaimerScreen
import com.cryptotrader.presentation.screens.market.MarketScreen
import com.cryptotrader.presentation.screens.portfolio.PortfolioScreen
import com.cryptotrader.presentation.screens.settings.SettingsScreen
import com.cryptotrader.presentation.screens.setup.ApiKeySetupScreen

sealed class Screen(val route: String) {
    object Disclaimer : Screen("disclaimer")
    object ApiSetup : Screen("api_setup")
    object Dashboard : Screen("dashboard")
    object Market : Screen("market")
    object AI : Screen("ai")
    object Portfolio : Screen("portfolio")
    object Settings : Screen("settings")
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

        composable(Screen.Settings.route) {
            SettingsScreen(
                onLogoutComplete = {
                    navController.navigate(Screen.ApiSetup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
