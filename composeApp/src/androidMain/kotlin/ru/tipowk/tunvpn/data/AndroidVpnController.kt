package ru.tipowk.tunvpn.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import ru.tipowk.tunvpn.model.ConnectionState
import ru.tipowk.tunvpn.model.ServerConfig
import ru.tipowk.tunvpn.model.TrafficStats
import ru.tipowk.tunvpn.vpn.VpnConnectionManager
import java.lang.ref.WeakReference

/**
 * Android implementation of VpnController.
 * Delegates to VpnConnectionManager and holds a weak reference
 * to the current Activity for VPN permission handling.
 */
class AndroidVpnController(
    private val appContext: Context,
    private val vpnConnectionManager: VpnConnectionManager,
) : VpnController {

    companion object {
        private const val TAG = "AndroidVpnController"
    }

    override val connectionState: StateFlow<ConnectionState>
        get() = vpnConnectionManager.connectionState

    override val trafficStats: StateFlow<TrafficStats>
        get() = vpnConnectionManager.trafficStats

    private var activityRef: WeakReference<Activity>? = null
    private var vpnPermissionLauncher: ((Intent) -> Unit)? = null

    /**
     * Bind the current Activity and VPN permission launcher.
     * Must be called from Activity.onCreate().
     */
    fun bind(activity: Activity, launcher: (Intent) -> Unit) {
        activityRef = WeakReference(activity)
        vpnPermissionLauncher = launcher
    }

    /**
     * Unbind the Activity reference.
     * Should be called from Activity.onDestroy().
     */
    fun unbind() {
        activityRef = null
        vpnPermissionLauncher = null
    }

    /**
     * Called when user grants VPN permission.
     */
    fun onVpnPermissionGranted() {
        vpnConnectionManager.onVpnPermissionGranted()
    }

    /**
     * Called when user denies VPN permission.
     */
    fun onVpnPermissionDenied() {
        vpnConnectionManager.onVpnPermissionDenied()
    }

    override fun requestConnect(config: ServerConfig, selectedApps: Set<String>) {
        val activity = activityRef?.get()
        if (activity == null) {
            Log.e(TAG, "requestConnect: Activity not bound")
            return
        }
        val launcher = vpnPermissionLauncher
        if (launcher == null) {
            Log.e(TAG, "requestConnect: vpnPermissionLauncher not set")
            return
        }
        Log.d(TAG, "requestConnect: connecting...")
        vpnConnectionManager.connect(activity, config, selectedApps, launcher)
    }

    override fun requestDisconnect() {
        Log.d(TAG, "requestDisconnect called")
        vpnConnectionManager.disconnect()
    }

    override fun restartWithNewApps(config: ServerConfig, selectedApps: Set<String>) {
        Log.d(TAG, "restartWithNewApps called with ${selectedApps.size} apps")
        vpnConnectionManager.restartWithNewApps(config, selectedApps)
    }

    override fun isConnected(): Boolean = vpnConnectionManager.isConnected()
}
