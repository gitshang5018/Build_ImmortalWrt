package org.immortalwrt.manager.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import org.immortalwrt.manager.ImmortalWrtApp
import org.immortalwrt.manager.ui.screens.clients.ClientsScreen
import org.immortalwrt.manager.ui.screens.clients.ClientsViewModel
import org.immortalwrt.manager.ui.screens.dashboard.DashboardScreen
import org.immortalwrt.manager.ui.screens.dashboard.DashboardViewModel
import org.immortalwrt.manager.ui.screens.login.LoginScreen
import org.immortalwrt.manager.ui.screens.login.LoginViewModel
import org.immortalwrt.manager.ui.screens.settings.SettingsScreen
import org.immortalwrt.manager.ui.screens.settings.SettingsViewModel
import org.immortalwrt.manager.ui.screens.tools.ToolsScreen
import org.immortalwrt.manager.ui.screens.tools.ToolsViewModel
import org.immortalwrt.manager.ui.screens.wireless.WirelessScreen
import org.immortalwrt.manager.ui.screens.wireless.WirelessViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val app = ImmortalWrtApp.instance

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            val loginViewModel = LoginViewModel(app.routerRepository, app.preferencesRepository)
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainContainerScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainerScreen(
    onLogout: () -> Unit = {}
) {
    val bottomNavController = rememberNavController()
    val app = ImmortalWrtApp.instance

    val dashboardViewModel = DashboardViewModel(app.routerRepository, app.preferencesRepository)
    val clientsViewModel = ClientsViewModel(app.routerRepository)
    val wirelessViewModel = WirelessViewModel(app.routerRepository)
    val toolsViewModel = ToolsViewModel(app.routerRepository)
    val settingsViewModel = SettingsViewModel(app.routerRepository, app.preferencesRepository)

    val items = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Clients,
        BottomNavItem.Wireless,
        BottomNavItem.Tools,
        BottomNavItem.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentDestination?.route == item.route,
                        onClick = {
                            bottomNavController.navigate(item.route) {
                                popUpTo(bottomNavController.graph.findStartDestination().id) {
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
    ) { innerPadding ->
        NavHost(
            navController = bottomNavController,
            startDestination = BottomNavItem.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Dashboard.route) {
                DashboardScreen(viewModel = dashboardViewModel)
            }
            composable(BottomNavItem.Clients.route) {
                ClientsScreen(viewModel = clientsViewModel)
            }
            composable(BottomNavItem.Wireless.route) {
                WirelessScreen(viewModel = wirelessViewModel)
            }
            composable(BottomNavItem.Tools.route) {
                ToolsScreen(viewModel = toolsViewModel)
            }
            composable(BottomNavItem.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onLogout = onLogout
                )
            }
        }
    }
}
