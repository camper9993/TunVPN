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

    /**
     * Restart VPN with a new app list while connected.
     * Used when user changes selected apps during active VPN session.
     */
    fun restartWithNewApps(config: ServerConfig, selectedApps: Set<String>)

    /**
     * Check if VPN is currently connected.
     */
    fun isConnected(): Boolean
}
