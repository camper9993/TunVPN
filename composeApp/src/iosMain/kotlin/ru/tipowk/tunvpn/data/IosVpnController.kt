package ru.tipowk.tunvpn.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.tipowk.tunvpn.model.ConnectionState
import ru.tipowk.tunvpn.model.ServerConfig
import ru.tipowk.tunvpn.model.TrafficStats

class IosVpnController : VpnController {
    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.DISCONNECTED)

    override val trafficStats: StateFlow<TrafficStats> =
        MutableStateFlow(TrafficStats())

    override fun requestConnect(config: ServerConfig, selectedApps: Set<String>) {
        // TODO: iOS VPN not yet implemented
    }

    override fun requestDisconnect() {
        // TODO: iOS VPN not yet implemented
    }
}
