package ru.tipowk.tunvpn.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.tipowk.tunvpn.data.ClipboardProvider
import ru.tipowk.tunvpn.data.ServerRepository
import ru.tipowk.tunvpn.data.SettingsRepository
import ru.tipowk.tunvpn.data.VpnController
import ru.tipowk.tunvpn.model.ConnectionState
import ru.tipowk.tunvpn.model.ServerConfig
import ru.tipowk.tunvpn.model.TrafficStats

data class HomeUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val activeServer: ServerConfig? = null,
    val trafficStats: TrafficStats = TrafficStats(),
)

class HomeViewModel(
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val vpnController: VpnController,
    private val clipboardProvider: ClipboardProvider,
) : ViewModel() {

    private val _activeServer = MutableStateFlow<ServerConfig?>(null)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                serverRepository.getServers(),
                settingsRepository.getSettings(),
            ) { servers, settings ->
                settings.activeServerId?.let { id ->
                    servers.find { it.id == id }
                } ?: servers.firstOrNull()
            }.collect { server ->
                _activeServer.value = server
            }
        }

        viewModelScope.launch {
            combine(
                vpnController.connectionState,
                vpnController.trafficStats,
                _activeServer,
            ) { connectionState, trafficStats, activeServer ->
                HomeUiState(
                    connectionState = connectionState,
                    activeServer = activeServer,
                    trafficStats = trafficStats,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun toggleConnection() {
        when (_uiState.value.connectionState) {
            ConnectionState.DISCONNECTED -> connect()
            ConnectionState.CONNECTED -> disconnect()
            else -> {}
        }
    }

    private fun connect() {
        val server = _activeServer.value ?: return
        viewModelScope.launch {
            val settings = settingsRepository.getSettings().first()
            vpnController.requestConnect(server, settings.selectedAppPackages)
        }
    }

    private fun disconnect() {
        vpnController.requestDisconnect()
    }

    fun copyToClipboard(text: String) {
        clipboardProvider.setText(text)
    }
}
