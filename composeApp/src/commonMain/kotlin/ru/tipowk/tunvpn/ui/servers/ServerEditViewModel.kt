package ru.tipowk.tunvpn.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.tipowk.tunvpn.data.ServerRepository
import ru.tipowk.tunvpn.model.Flow
import ru.tipowk.tunvpn.model.SecurityType
import ru.tipowk.tunvpn.model.ServerConfig
import ru.tipowk.tunvpn.model.TransportType

data class ServerEditUiState(
    val config: ServerConfig = ServerConfig(),
    val isEditMode: Boolean = false,
    val isSaved: Boolean = false,
)

class ServerEditViewModel(
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerEditUiState())
    val uiState: StateFlow<ServerEditUiState> = _uiState.asStateFlow()

    fun loadServer(serverId: String?) {
        if (serverId == null) return
        viewModelScope.launch {
            val server = serverRepository.getServerById(serverId)
            if (server != null) {
                _uiState.value = _uiState.value.copy(
                    config = server,
                    isEditMode = true,
                )
            }
        }
    }

    fun updateName(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(name = value)
        )
    }

    fun updateAddress(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(address = value)
        )
    }

    fun updatePort(value: String) {
        val port = value.toIntOrNull() ?: return
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(port = port)
        )
    }

    fun updateUuid(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(uuid = value)
        )
    }

    fun updateNetwork(value: TransportType) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(network = value)
        )
    }

    fun updateSecurity(value: SecurityType) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(security = value)
        )
    }

    fun updateFlow(value: Flow) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(flow = value)
        )
    }

    fun updateSni(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(sni = value)
        )
    }

    fun updateFingerprint(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(fingerprint = value)
        )
    }

    fun updateAllowInsecure(value: Boolean) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(allowInsecure = value)
        )
    }

    fun updatePublicKey(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(publicKey = value)
        )
    }

    fun updateShortId(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(shortId = value)
        )
    }

    fun updateSpiderX(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(spiderX = value)
        )
    }

    fun updateWsPath(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(wsPath = value)
        )
    }

    fun updateWsHost(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(wsHost = value)
        )
    }

    fun updateGrpcServiceName(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(grpcServiceName = value)
        )
    }

    fun save() {
        val config = _uiState.value.config
        if (config.address.isBlank() || config.uuid.isBlank()) return

        viewModelScope.launch {
            if (_uiState.value.isEditMode) {
                serverRepository.updateServer(config)
            } else {
                serverRepository.addServer(config)
            }
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
