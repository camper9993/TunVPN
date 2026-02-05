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
import ru.tipowk.tunvpn.MainActivity
import ru.tipowk.tunvpn.model.TrafficStats
import java.io.File

/**
 * Android VpnService that creates a TUN interface and routes traffic
 * through Xray-core using hev-socks5-tunnel (tun2socks).
 *
 * Architecture:
 *   Selected Apps -> TUN interface -> tun2socks -> SOCKS5 (Xray) -> VLESS Server
 *
 * Lifecycle:
 * 1. Receives ACTION_START with config JSON and selected apps
 * 2. Creates TUN interface with per-app routing
 * 3. Starts Xray-core (creates SOCKS5 proxy on 127.0.0.1:10808)
 * 4. Starts tun2socks to bridge TUN <-> SOCKS5
 * 5. Polls traffic stats every second
 * 6. On ACTION_STOP, stops tun2socks, Xray, closes TUN
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
            Log.d(TAG, "start() called")
            Log.d(TAG, "  selectedApps: $selectedApps")
            val intent = Intent(context, TunVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG_JSON, configJson)
                putExtra(EXTRA_SELECTED_APPS, selectedApps.toTypedArray())
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            Log.d(TAG, "stop() called")
            val intent = Intent(context, TunVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Xray-core controller
    private var coreController: CoreController? = null

    // TUN interface
    private var tunFd: ParcelFileDescriptor? = null

    // tun2socks thread
    private var tun2socksJob: Job? = null

    // Stats polling
    private var statsJob: Job? = null

    private var isRunning = false

    // Xray callback handler
    private val callbackHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(status: Long, msg: String?): Long {
            Log.d(TAG, "[Xray] onEmitStatus: status=$status, msg=$msg")
            return 0
        }

        override fun startup(): Long {
            Log.d(TAG, "[Xray] startup callback")
            return 0 // We manage TUN ourselves
        }

        override fun shutdown(): Long {
            Log.d(TAG, "[Xray] shutdown callback")
            return 0
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== onCreate ===")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "=== onStartCommand ===")
        Log.d(TAG, "  action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON)
                val selectedApps = intent.getStringArrayExtra(EXTRA_SELECTED_APPS)?.toSet() ?: emptySet()

                Log.d(TAG, "ACTION_START received")
                Log.d(TAG, "  configJson length: ${configJson?.length}")
                Log.d(TAG, "  selectedApps: $selectedApps")

                if (configJson != null) {
                    startForeground(NOTIFICATION_ID, createNotification("Connecting..."))
                    serviceScope.launch {
                        startVpn(configJson, selectedApps)
                    }
                } else {
                    Log.e(TAG, "No config provided!")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received")
                stopVpn()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private suspend fun startVpn(configJson: String, selectedApps: Set<String>) {
        Log.d(TAG, "=== startVpn ===")

        try {
            // Step 1: Create TUN interface
            Log.d(TAG, "Step 1: Creating TUN interface...")
            val tun = createTunInterface(selectedApps)
            if (tun == null) {
                Log.e(TAG, "Failed to create TUN interface!")
                VpnConnectionManager.onServiceError()
                stopSelf()
                return
            }
            tunFd = tun
            val fd = tun.fd
            Log.d(TAG, "TUN interface created, fd=$fd")

            // Step 2: Start Xray-core (SOCKS5 proxy)
            Log.d(TAG, "Step 2: Starting Xray-core...")
            if (!startXray(configJson)) {
                Log.e(TAG, "Failed to start Xray-core!")
                closeTun()
                VpnConnectionManager.onServiceError()
                stopSelf()
                return
            }
            Log.d(TAG, "Xray-core started, SOCKS5 on 127.0.0.1:${XrayConfigBuilder.SOCKS_PORT}")

            // Step 3: Start tun2socks
            Log.d(TAG, "Step 3: Starting tun2socks...")
            startTun2Socks(fd)

            // Step 4: Mark as running and notify UI
            isRunning = true
            VpnConnectionManager.onServiceConnected()
            updateNotification("Connected")

            // Step 5: Start stats polling
            startStatsPolling()

            Log.d(TAG, "=== VPN started successfully! ===")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            e.printStackTrace()
            stopVpn()
            VpnConnectionManager.onServiceError()
            stopSelf()
        }
    }

    private fun createTunInterface(selectedApps: Set<String>): ParcelFileDescriptor? {
        Log.d(TAG, "createTunInterface: selectedApps=$selectedApps")

        return try {
            val builder = Builder()
                .setSession("TunVPN")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)        // Route all IPv4
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(1500)
                .setBlocking(false)

            // Per-app VPN routing
            if (selectedApps.isNotEmpty()) {
                Log.d(TAG, "Per-app mode: routing only selected apps")
                for (pkg in selectedApps) {
                    try {
                        builder.addAllowedApplication(pkg)
                        Log.d(TAG, "  Added allowed app: $pkg")
                    } catch (e: Exception) {
                        Log.w(TAG, "  Cannot add app $pkg: ${e.message}")
                    }
                }
            } else {
                Log.d(TAG, "Global mode: routing all apps except ourselves")
                try {
                    builder.addDisallowedApplication(packageName)
                    Log.d(TAG, "  Excluded self: $packageName")
                } catch (e: Exception) {
                    Log.w(TAG, "  Cannot exclude self: ${e.message}")
                }
            }

            val fd = builder.establish()
            Log.d(TAG, "TUN established: fd=${fd?.fd}")
            fd
        } catch (e: Exception) {
            Log.e(TAG, "Error creating TUN interface", e)
            null
        }
    }

    private fun startXray(configJson: String): Boolean {
        Log.d(TAG, "startXray: config length=${configJson.length}")

        return try {
            // Write config for debugging
            val configFile = File(cacheDir, "xray_config.json")
            configFile.writeText(configJson)
            Log.d(TAG, "Xray config written to: ${configFile.absolutePath}")

            // Create controller
            coreController = Libv2ray.newCoreController(callbackHandler)
            Log.d(TAG, "CoreController created")

            // Start Xray in proxy mode (fd=0 means no TUN from Xray side)
            coreController?.startLoop(configJson, 0)
            Log.d(TAG, "Xray startLoop called")

            // Check if running
            val running = coreController?.isRunning ?: false
            Log.d(TAG, "Xray isRunning: $running")

            running
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Xray", e)
            false
        }
    }

    private fun startTun2Socks(tunFd: Int) {
        Log.d(TAG, "startTun2Socks: tunFd=$tunFd")

        // tun2socks blocks, so run in a coroutine
        tun2socksJob = serviceScope.launch {
            Log.d(TAG, "[tun2socks] Starting in background thread...")
            Tun2Socks.startTunnel(cacheDir, tunFd, XrayConfigBuilder.SOCKS_PORT)
            Log.d(TAG, "[tun2socks] Tunnel stopped")
        }
    }

    private fun stopVpn() {
        Log.d(TAG, "=== stopVpn ===")

        // Stop stats polling
        statsJob?.cancel()
        statsJob = null
        Log.d(TAG, "Stats polling stopped")

        // Stop tun2socks
        Log.d(TAG, "Stopping tun2socks...")
        Tun2Socks.stopTunnel()
        tun2socksJob?.cancel()
        tun2socksJob = null
        Log.d(TAG, "tun2socks stopped")

        // Stop Xray
        Log.d(TAG, "Stopping Xray...")
        try {
            coreController?.stopLoop()
            Log.d(TAG, "Xray stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Xray", e)
        }
        coreController = null

        // Close TUN
        closeTun()

        isRunning = false
        VpnConnectionManager.onServiceDisconnected()
        Log.d(TAG, "=== VPN stopped ===")
    }

    private fun closeTun() {
        Log.d(TAG, "Closing TUN interface...")
        try {
            tunFd?.close()
            Log.d(TAG, "TUN closed")
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

                // Get Xray stats
                val uplink = try {
                    coreController?.queryStats("proxy", "uplink") ?: 0L
                } catch (_: Exception) { 0L }

                val downlink = try {
                    coreController?.queryStats("proxy", "downlink") ?: 0L
                } catch (_: Exception) { 0L }

                val uploadSpeed = if (prevUplink > 0) (uplink - prevUplink) else 0L
                val downloadSpeed = if (prevDownlink > 0) (downlink - prevDownlink) else 0L

                prevUplink = uplink
                prevDownlink = downlink

                VpnConnectionManager.updateTrafficStats(
                    TrafficStats(
                        uploadSpeed = uploadSpeed.coerceAtLeast(0),
                        downloadSpeed = downloadSpeed.coerceAtLeast(0),
                        totalUpload = uplink,
                        totalDownload = downlink,
                    )
                )
            }
        }
    }

    override fun onRevoke() {
        Log.d(TAG, "=== onRevoke === (user revoked VPN permission)")
        stopVpn()
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "=== onDestroy ===")
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    // --- Notifications ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "VPN Status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows VPN connection status"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(status: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = Intent(this, TunVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TunVPN")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openAppPending)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Disconnect",
                    stopPending,
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(status))
    }
}
