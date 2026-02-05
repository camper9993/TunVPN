package ru.tipowk.tunvpn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import org.koin.compose.koinInject
import ru.tipowk.tunvpn.data.SettingsRepository
import ru.tipowk.tunvpn.navigation.AppNavGraph
import ru.tipowk.tunvpn.theme.TunVpnTheme

@Composable
fun App() {
    val settingsRepository = koinInject<SettingsRepository>()
    val settings by settingsRepository.getSettings().collectAsState(initial = null)

    TunVpnTheme(darkTheme = true) {
        val navController = rememberNavController()
        AppNavGraph(navController = navController)
    }
}
