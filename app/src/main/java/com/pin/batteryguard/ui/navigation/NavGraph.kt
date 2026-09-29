package com.pin.batteryguard.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhonelinkErase
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhonelinkErase
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pin.batteryguard.ui.screen.apps.AppListScreen
import com.pin.batteryguard.ui.screen.dashboard.DashboardScreen
import com.pin.batteryguard.ui.screen.exceptions.ExceptionListScreen
import com.pin.batteryguard.ui.screen.history.HistoryScreen
import com.pin.batteryguard.ui.screen.permission.PermissionGranterScreen
import com.pin.batteryguard.ui.screen.settings.SettingsScreen
import com.pin.batteryguard.ui.screen.automation.AutomationScreen
import com.pin.batteryguard.ui.screen.setup.SetupWizardScreen
import com.pin.batteryguard.ui.screen.shield.AppShieldScreen
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Bolt

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Dashboard : Screen("dashboard", "Tổng quan", Icons.Filled.Dashboard, Icons.Outlined.Dashboard)
    data object Apps : Screen("apps", "Ứng dụng", Icons.Filled.PhonelinkErase, Icons.Outlined.PhonelinkErase)
    data object Automation : Screen("automation", "Tự động", Icons.Filled.Bolt, Icons.Outlined.Bolt)
    data object Exceptions : Screen("exceptions", "Ngoại lệ", Icons.Filled.Shield, Icons.Outlined.Shield)
    data object History : Screen("history", "Lịch sử", Icons.Filled.History, Icons.Outlined.History)
    data object Settings : Screen("settings", "Cài đặt", Icons.Filled.Settings, Icons.Outlined.Settings)
    data object Setup : Screen("setup", "Thiết lập", Icons.Filled.Settings, Icons.Outlined.Settings)
    data object PermissionGranter : Screen("permission_granter", "Cấp quyền", Icons.Filled.Shield, Icons.Outlined.Shield)
    data object AppShield : Screen("app_shield", "Bảo vệ", Icons.Filled.Shield, Icons.Outlined.Shield)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Apps,
    Screen.Automation,
    Screen.History,
    Screen.Settings
)

@Composable
fun BatteryGuardNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Ẩn bottom bar ở setup wizard và permission granter
    val showBottomBar = currentDestination?.route != Screen.Setup.route &&
            currentDestination?.route != Screen.PermissionGranter.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start, tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start, tween(300)
                )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End, tween(300)
                )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End, tween(300)
                )
            }
        ) {
            composable(Screen.Setup.route) {
                SetupWizardScreen(
                    onSetupComplete = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Setup.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToApps = {
                        navController.navigate(Screen.Apps.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSetup = {
                        navController.navigate(Screen.Setup.route)
                    }
                )
            }
            composable(Screen.Apps.route) {
                AppListScreen()
            }
            composable(Screen.Automation.route) {
                AutomationScreen(
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }
            composable(Screen.Exceptions.route) {
                ExceptionListScreen()
            }
            composable(Screen.History.route) {
                HistoryScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToSetup = {
                        navController.navigate(Screen.Setup.route)
                    },
                    onNavigateToPermissionGranter = {
                        navController.navigate(Screen.PermissionGranter.route)
                    }
                )
            }
            composable(Screen.PermissionGranter.route) {
                PermissionGranterScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.AppShield.route) {
                AppShieldScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToPermissionGranter = {
                        navController.navigate(Screen.PermissionGranter.route)
                    }
                )
            }
        }
    }
}
