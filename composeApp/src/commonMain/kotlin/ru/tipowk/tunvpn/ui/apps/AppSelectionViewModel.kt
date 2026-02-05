package ru.tipowk.tunvpn.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.tipowk.tunvpn.data.ServerRepository
import ru.tipowk.tunvpn.data.SettingsRepository
import ru.tipowk.tunvpn.data.VpnController
import ru.tipowk.tunvpn.model.AppInfo

data class AppSelectionUiState(
    val apps: List<AppInfo> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val showSystemApps: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val vpnNeedsRestart: Boolean = false,
)

/**
 * Interface for platform-specific installed app listing.
 * Android implementation uses PackageManager.
 */
interface InstalledAppsProvider {
    suspend fun getInstalledApps(): List<AppInfo>
}

/**
 * ViewModel for app selection screen.
 * Allows user to select which apps should be routed through VPN.
 */
class AppSelectionViewModel(
    private val settingsRepository: SettingsRepository,
    private val serverRepository: ServerRepository,
    private val vpnController: VpnController,
    private val installedAppsProvider: InstalledAppsProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSelectionUiState())
    val uiState: StateFlow<AppSelectionUiState> = _uiState.asStateFlow()

    private var allApps: List<AppInfo> = emptyList()

    init {
        viewModelScope.launch {
            // Load saved selection
            val settings = settingsRepository.getSettings().first()
            _uiState.value = _uiState.value.copy(
                selectedPackages = settings.selectedAppPackages,
            )

            // Load installed apps
            allApps = installedAppsProvider.getInstalledApps()
            _uiState.value = _uiState.value.copy(
                apps = filterApps(),
                isLoading = false,
            )
        }
    }

    fun toggleApp(packageName: String) {
        val current = _uiState.value.selectedPackages
        val updated = if (packageName in current) {
            current - packageName
        } else {
            current + packageName
        }
        _uiState.value = _uiState.value.copy(
            selectedPackages = updated,
            vpnNeedsRestart = vpnController.isConnected(),
        )

        // Save to settings and restart VPN if connected
        viewModelScope.launch {
            val settings = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(
                settings.copy(selectedAppPackages = updated)
            )

            // If VPN is connected, restart it with new app list
            if (vpnController.isConnected()) {
                val activeServerId = settings.activeServerId
                if (activeServerId != null) {
                    val servers = serverRepository.getServers().first()
                    val activeServer = servers.find { it.id == activeServerId }
                    if (activeServer != null) {
                        vpnController.restartWithNewApps(activeServer, updated)
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            apps = filterApps(query = query),
        )
    }

    fun toggleShowSystemApps() {
        val newValue = !_uiState.value.showSystemApps
        _uiState.value = _uiState.value.copy(
            showSystemApps = newValue,
            apps = filterApps(showSystem = newValue),
        )
    }

    private fun filterApps(
        query: String = _uiState.value.searchQuery,
        showSystem: Boolean = _uiState.value.showSystemApps,
    ): List<AppInfo> {
        return allApps
            .filter { app ->
                (showSystem || !app.isSystem) &&
                        (query.isBlank() || app.label.contains(query, ignoreCase = true) ||
                                app.packageName.contains(query, ignoreCase = true))
            }
    }
}
