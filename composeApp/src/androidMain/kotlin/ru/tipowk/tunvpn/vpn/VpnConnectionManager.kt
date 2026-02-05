package ru.tipowk.tunvpn.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.tipowk.tunvpn.model.ConnectionState
import ru.tipowk.tunvpn.model.ServerConfig
import ru.tipowk.tunvpn.model.TrafficStats

/**
 * Singleton that manages VPN connection state and bridges between
 * the TunVpnService (Android VpnService) and the UI layer (ViewModels).
 *
 * Supports real VPN mode with per-app routing via hev-socks5-tunnel (tun2socks).
 *
 * Flow:
 * 1. UI calls connect(config, selectedApps)
 * 2. Manager checks VPN permission
 * 3. If permission granted, starts TunVpnService
 * 4. TunVpnService creates TUN -> Xray SOCKS5 -> tun2socks
 * 5. UI observes connectionState and trafficStats flows
 */
object VpnConnectionManager {

    private const val TAG = "VpnConnectionManager"

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _trafficStats = MutableStateFlow(TrafficStats())
    val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()

    private var pendingConfig: ServerConfig? = null
    private var pendingSelectedApps: Set<String> = emptySet()

    /**
     * Request VPN connection.
     *
     * @param activity Activity for requesting VPN permission
     * @param config Server configuration
     * @param selectedApps Apps to route through VPN (empty = all apps)
     * @param vpnPermissionLauncher Launcher for VPN permission dialog
     */
    fun connect(
        activity: Activity,
        config: ServerConfig,
        selectedApps: Set<String>,
        vpnPermissionLauncher: (Intent) -> Unit,
    ) {
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            Log.w(TAG, "Already connecting or connected")
            return
        }

        Log.d(TAG, "=== connect ===")
        Log.d(TAG, "  server: ${config.address}:${config.port}")
        Log.d(TAG, "  selectedApps: $selectedApps")

        pendingConfig = config
        pendingSelectedApps = selectedApps
        _connectionState.value = ConnectionState.CONNECTING

        // Check VPN permission
        val prepareIntent = VpnService.prepare(activity)
        if (prepareIntent != null) {
            Log.d(TAG, "VPN permission needed, launching dialog...")
            vpnPermissionLauncher(prepareIntent)
        } else {
            Log.d(TAG, "VPN permission already granted")
            startVpnService(activity)
        }
    }

    /**
     * Called when user grants VPN permission from the system dialog.
     */
    fun onVpnPermissionGranted(context: Context) {
        Log.d(TAG, "onVpnPermissionGranted")
        startVpnService(context)
    }

    /**
     * Called when user denies VPN permission.
     */
    fun onVpnPermissionDenied() {
        Log.d(TAG, "onVpnPermissionDenied")
        _connectionState.value = ConnectionState.DISCONNECTED
        pendingConfig = null
        pendingSelectedApps = emptySet()
    }

    /**
     * Request disconnection.
     */
    fun disconnect(context: Context) {
        val currentState = _connectionState.value
        Log.d(TAG, "disconnect() called, currentState=$currentState")

        if (currentState == ConnectionState.DISCONNECTED || currentState == ConnectionState.DISCONNECTING) {
            Log.d(TAG, "Already disconnected or disconnecting")
            return
        }

        _connectionState.value = ConnectionState.DISCONNECTING
        TunVpnService.stop(context)
    }

    private fun startVpnService(context: Context) {
        val config = pendingConfig ?: run {
            Log.e(TAG, "No pending config!")
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        Log.d(TAG, "Starting TunVpnService...")
        Log.d(TAG, "  server: ${config.address}:${config.port}")
        Log.d(TAG, "  selectedApps: $pendingSelectedApps")

        val configJson = XrayConfigBuilder.build(config)
        Log.d(TAG, "  configJson length: ${configJson.length}")

        TunVpnService.start(context, configJson, pendingSelectedApps)
    }

    // --- Called by TunVpnService to update state ---

    internal fun onServiceConnected() {
        Log.d(TAG, "onServiceConnected")
        _connectionState.value = ConnectionState.CONNECTED
        pendingConfig = null
        pendingSelectedApps = emptySet()
    }

    internal fun onServiceDisconnected() {
        Log.d(TAG, "onServiceDisconnected")
        _connectionState.value = ConnectionState.DISCONNECTED
        _trafficStats.value = TrafficStats()
    }

    internal fun onServiceError() {
        Log.e(TAG, "onServiceError")
        _connectionState.value = ConnectionState.DISCONNECTED
        _trafficStats.value = TrafficStats()
        pendingConfig = null
        pendingSelectedApps = emptySet()
    }

    internal fun updateTrafficStats(stats: TrafficStats) {
        _trafficStats.value = stats
    }
}
