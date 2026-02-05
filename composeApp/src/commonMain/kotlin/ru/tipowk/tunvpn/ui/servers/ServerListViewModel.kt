package ru.tipowk.tunvpn.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.tipowk.tunvpn.data.ClipboardProvider
import ru.tipowk.tunvpn.data.ServerRepository
import ru.tipowk.tunvpn.data.SettingsRepository
import ru.tipowk.tunvpn.data.VlessUriParser
import ru.tipowk.tunvpn.model.ServerConfig

data class ServerListUiState(
    val servers: List<ServerConfig> = emptyList(),
    val activeServerId: String? = null,
    val importError: String? = null,
    val importSuccess: String? = null,
)

class ServerListViewModel(
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val clipboardProvider: ClipboardProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerListUiState())
    val uiState: StateFlow<ServerListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                serverRepository.getServers(),
                settingsRepository.getSettings(),
            ) { servers, settings ->
                ServerListUiState(
                    servers = servers,
                    activeServerId = settings.activeServerId,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectServer(serverId: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings(
                ru.tipowk.tunvpn.model.VpnSettings(
                    activeServerId = serverId,
                )
            )
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            serverRepository.deleteServer(serverId)
        }
    }

    /**
     * Import servers from clipboard.
     * Reads clipboard text and parses all vless:// URIs found.
     */
    fun importFromClipboard() {
        val clipboardText = clipboardProvider.getText()
        if (clipboardText.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                importError = "Clipboard is empty",
                importSuccess = null,
            )
            return
        }

        viewModelScope.launch {
            val lines = clipboardText.trim().lines()
            var imported = 0
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("vless://")) {
                    val config = VlessUriParser.parse(trimmed)
                    if (config != null) {
                        serverRepository.addServer(config)
                        imported++
                    }
                }
            }
            if (imported == 0) {
                _uiState.value = _uiState.value.copy(
                    importError = "No valid vless:// links found",
                    importSuccess = null,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    importError = null,
                    importSuccess = "Imported $imported server(s)",
                )
            }
        }
    }

    fun clearImportError() {
        _uiState.value = _uiState.value.copy(importError = null)
    }

    fun clearImportSuccess() {
        _uiState.value = _uiState.value.copy(importSuccess = null)
    }
}
