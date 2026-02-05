package ru.tipowk.tunvpn.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ru.tipowk.tunvpn.ui.apps.AppSelectionScreen
import ru.tipowk.tunvpn.ui.home.HomeScreen
import ru.tipowk.tunvpn.ui.servers.ServerEditScreen
import ru.tipowk.tunvpn.ui.servers.ServerListScreen
import ru.tipowk.tunvpn.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home,
    ) {
        composable<Screen.Home> {
            HomeScreen(
                onNavigateToServers = { navController.navigate(Screen.ServerList) },
                onNavigateToSettings = { navController.navigate(Screen.Settings) },
            )
        }

        composable<Screen.ServerList> {
            ServerListScreen(
                onAddServer = { navController.navigate(Screen.ServerEdit()) },
                onEditServer = { id -> navController.navigate(Screen.ServerEdit(serverId = id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Screen.ServerEdit> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.ServerEdit>()
            ServerEditScreen(
                serverId = route.serverId,
                onBack = { navController.popBackStack() },
            )
        }

        composable<Screen.AppSelection> {
            AppSelectionScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateToApps = { navController.navigate(Screen.AppSelection) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
