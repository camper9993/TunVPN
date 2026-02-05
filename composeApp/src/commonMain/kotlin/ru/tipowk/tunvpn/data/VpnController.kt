package ru.tipowk.tunvpn.data

import kotlinx.coroutines.flow.StateFlow
import ru.tipowk.tunvpn.model.ConnectionState
import ru.tipowk.tunvpn.model.ServerConfig
import ru.tipowk.tunvpn.model.TrafficStats

/**
 * Platform-agnostic interface for VPN control.
 * Android implementation delegates to VpnConnectionManager.
 * iOS implementation is a stub (VPN not yet supported on iOS).
 */
interface VpnController {
    val connectionState: StateFlow<ConnectionState>
    val trafficStats: StateFlow<TrafficStats>

    /**
     * Request VPN connection. Platform will handle permission flow if needed.
     */
    fun requestConnect(config: ServerConfig, selectedApps: Set<String>)

    /**
     * Request VPN disconnection.
     */
    fun requestDisconnect()
}
