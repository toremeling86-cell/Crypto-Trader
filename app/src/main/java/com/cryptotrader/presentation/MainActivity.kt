package com.cryptotrader.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cryptotrader.presentation.navigation.NavGraph
import com.cryptotrader.presentation.navigation.Screen
import com.cryptotrader.presentation.theme.CryptoTraderTheme
import com.cryptotrader.utils.CryptoUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CryptoTraderTheme {
                CryptoTraderApp()
            }
        }
    }
}

@Composable
fun CryptoTraderApp() {
    val context = LocalContext.current
    val navController = rememberNavController()

    // Check disclaimer acceptance first
    val hasAcceptedTerms = remember {
        CryptoUtils.hasAcceptedTerms(context)
    }

    // Then check if API keys are set
    val hasApiKeys = remember {
        CryptoUtils.hasApiCredentials(context)
    }

    val startDestination = when {
        !hasAcceptedTerms -> Screen.Disclaimer.route
        !hasApiKeys -> Screen.ApiSetup.route
        else -> Screen.Dashboard.route
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            bottomBar = {
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                if (currentRoute != Screen.ApiSetup.route && currentRoute != Screen.Disclaimer.route) {
                    BottomNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                NavGraph(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Screen.Dashboard.route,
            onClick = { onNavigate(Screen.Dashboard.route) },
            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
            label = { Text("Dashboard") }
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Strategy.route,
            onClick = { onNavigate(Screen.Strategy.route) },
            icon = { Icon(Icons.Default.TrendingUp, contentDescription = "Strategies") },
            label = { Text("Strategies") }
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Chat.route,
            onClick = { onNavigate(Screen.Chat.route) },
            icon = { Icon(Icons.Default.Chat, contentDescription = "AI Chat") },
            label = { Text("AI Chat") }
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Settings.route,
            onClick = { onNavigate(Screen.Settings.route) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}
