package ru.tipowk.tunvpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.koin.android.ext.android.inject
import ru.tipowk.tunvpn.MainActivity
import ru.tipowk.tunvpn.model.TrafficStats
import java.io.File

/**
 * Android VpnService that creates a TUN interface and routes traffic
 * through Xray-core using hev-socks5-tunnel (tun2socks).
 */
class TunVpnService : VpnService() {

    companion object {
        const val ACTION_START = "ru.tipowk.tunvpn.START_VPN"
        const val ACTION_STOP = "ru.tipowk.tunvpn.STOP_VPN"
        const val EXTRA_CONFIG_JSON = "config_json"
        const val EXTRA_SELECTED_APPS = "selected_apps"

        private const val TAG = "TunVpnService"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "vpn_channel"

        fun start(context: Context, configJson: String, selectedApps: Set<String>) {
            Log.d(TAG, "start() selectedApps=$selectedApps")
            val intent = Intent(context, TunVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG_JSON, configJson)
                putExtra(EXTRA_SELECTED_APPS, selectedApps.toTypedArray())
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            Log.d(TAG, "stop()")
            val intent = Intent(context, TunVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val vpnConnectionManager: VpnConnectionManager by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var coreController: CoreController? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var tun2socksJob: Job? = null
    private var statsJob: Job? = null
    private var isRunning = false

    private val callbackHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(status: Long, msg: String?): Long {
            Log.d(TAG, "[Xray] status=$status, msg=$msg")
            return 0
        }
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON)
                val selectedApps = intent.getStringArrayExtra(EXTRA_SELECTED_APPS)?.toSet() ?: emptySet()

                if (configJson != null) {
                    startForeground(NOTIFICATION_ID, createNotification("Connecting..."))
                    serviceScope.launch {
                        // Stop current VPN if running
                        if (isRunning) {
                            Log.d(TAG, "Stopping current VPN...")
                            stopVpnInternal()
                        }
                        // Start new VPN
                        startVpnInternal(configJson, selectedApps)
                    }
                } else {
                    Log.e(TAG, "No config!")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    stopVpnInternal()
                    vpnConnectionManager.onServiceDisconnected()
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun startVpnInternal(configJson: String, selectedApps: Set<String>) {
        Log.d(TAG, "=== startVpnInternal === apps=$selectedApps")

        try {
            // 1. Create TUN
            val tun = createTunInterface(selectedApps)
            if (tun == null) {
                Log.e(TAG, "Failed to create TUN!")
                vpnConnectionManager.onServiceError()
                return
            }
            tunFd = tun
            val fd = tun.fd
            Log.d(TAG, "TUN created, fd=$fd")

            // 2. Start Xray
            if (!startXray(configJson)) {
                Log.e(TAG, "Failed to start Xray!")
                closeTun()
                vpnConnectionManager.onServiceError()
                return
            }
            Log.d(TAG, "Xray started")

            // 3. Start tun2socks (blocks in coroutine)
            tun2socksJob = serviceScope.launch {
                Log.d(TAG, "Starting tun2socks with fd=$fd...")
                Tun2Socks.startTunnel(filesDir, fd, XrayConfigBuilder.SOCKS_PORT)
                Log.d(TAG, "tun2socks stopped")
            }

            // Small delay to let tun2socks initialize
            delay(100)

            // 4. Done
            isRunning = true
            vpnConnectionManager.onServiceConnected()
            updateNotification("Connected")
            startStatsPolling()

            Log.d(TAG, "=== VPN started! ===")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopVpnInternal()
            vpnConnectionManager.onServiceError()
        }
    }

    private suspend fun stopVpnInternal() {
        Log.d(TAG, "=== stopVpnInternal ===")

        // 1. Stop stats
        statsJob?.cancel()
        statsJob = null

        // 2. Stop tun2socks and WAIT for it to fully stop
        Tun2Socks.stopTunnel()
        tun2socksJob?.join()
        tun2socksJob = null

        // Wait for native library to actually stop
        var waitCount = 0
        while (Tun2Socks.isRunning() && waitCount < 30) {
            Log.d(TAG, "Waiting for tun2socks to stop... ($waitCount)")
            delay(100)
            waitCount++
        }

        // 3. Stop Xray
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Xray", e)
        }
        coreController = null

        // 4. Close TUN
        closeTun()

        isRunning = false
        Log.d(TAG, "=== VPN stopped ===")
    }

    private fun createTunInterface(selectedApps: Set<String>): ParcelFileDescriptor? {
        return try {
            val builder = Builder()
                .setSession("TunVPN")
                .addAddress("10.0.0.2", 32)
                .setMtu(1500)
                .setBlocking(false)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            if (selectedApps.isNotEmpty()) {
                for (pkg in selectedApps) {
                    try {
                        builder.addAllowedApplication(pkg)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot add app $pkg: ${e.message}")
                    }
                }
            } else {
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot exclude self: ${e.message}")
                }
            }

            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating TUN", e)
            null
        }
    }

    private fun startXray(configJson: String): Boolean {
        return try {
            val configFile = File(filesDir, "xray_config.json")
            configFile.writeText(configJson)

            coreController = Libv2ray.newCoreController(callbackHandler)
            coreController?.startLoop(configJson, 0)
            coreController?.isRunning ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Xray", e)
            false
        }
    }

    private fun closeTun() {
        try {
            tunFd?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing TUN", e)
        }
        tunFd = null
    }

    private fun startStatsPolling() {
        var prevUplink = 0L
        var prevDownlink = 0L

        statsJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val uplink = try { coreController?.queryStats("proxy", "uplink") ?: 0L } catch (_: Exception) { 0L }
                val downlink = try { coreController?.queryStats("proxy", "downlink") ?: 0L } catch (_: Exception) { 0L }

                val uploadSpeed = if (prevUplink > 0) (uplink - prevUplink).coerceAtLeast(0) else 0L
                val downloadSpeed = if (prevDownlink > 0) (downlink - prevDownlink).coerceAtLeast(0) else 0L

                prevUplink = uplink
                prevDownlink = downlink

                vpnConnectionManager.updateTrafficStats(
                    TrafficStats(uploadSpeed, downloadSpeed, uplink, downlink)
                )
            }
        }
    }

    override fun onRevoke() {
        Log.d(TAG, "onRevoke")
        serviceScope.launch {
            stopVpnInternal()
            vpnConnectionManager.onServiceDisconnected()
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        Tun2Socks.stopTunnel()
        tun2socksJob?.cancel()
        try { coreController?.stopLoop() } catch (_: Exception) {}
        try { tunFd?.close() } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "VPN Status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "VPN connection status"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TunVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TunVPN")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, "Disconnect", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification(status))
    }
}
