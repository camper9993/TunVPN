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
 * Delegates to VpnConnectionManager singleton and holds a weak reference
 * to the current Activity for VPN permission handling.
 */
class AndroidVpnController(private val appContext: Context) : VpnController {

    companion object {
        private const val TAG = "AndroidVpnController"
    }

    override val connectionState: StateFlow<ConnectionState>
        get() = VpnConnectionManager.connectionState

    override val trafficStats: StateFlow<TrafficStats>
        get() = VpnConnectionManager.trafficStats

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
        VpnConnectionManager.connect(activity, config, selectedApps, launcher)
    }

    override fun requestDisconnect() {
        Log.d(TAG, "requestDisconnect called")
        // Use application context for disconnect - doesn't need Activity
        VpnConnectionManager.disconnect(appContext)
    }
}
