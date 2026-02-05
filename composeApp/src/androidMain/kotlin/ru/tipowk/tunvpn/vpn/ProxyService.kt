package ru.tipowk.tunvpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
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
 * Simple foreground service that runs Xray-core in proxy mode.
 * No VPN/TUN - just SOCKS5 proxy on 127.0.0.1:10808 and HTTP proxy on 127.0.0.1:10809.
 *
 * Apps need to be configured manually to use the proxy.
 */
class ProxyService : Service() {

    companion object {
        const val ACTION_START = "ru.tipowk.tunvpn.START_PROXY"
        const val ACTION_STOP = "ru.tipowk.tunvpn.STOP_PROXY"
        const val EXTRA_CONFIG_JSON = "config_json"

        private const val TAG = "ProxyService"
        private const val NOTIFICATION_ID = 2
        private const val NOTIFICATION_CHANNEL_ID = "proxy_channel"

        fun start(context: Context, configJson: String) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG_JSON, configJson)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var coreController: CoreController? = null
    private var isRunning = false
    private var statsJob: Job? = null

    private val callbackHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(status: Long, msg: String?): Long {
            Log.d(TAG, "onEmitStatus: status=$status, msg=$msg")
            return 0
        }

        override fun startup(): Long {
            Log.d(TAG, "startup callback (no TUN in proxy mode)")
            return 0 // No TUN fd needed
        }

        override fun shutdown(): Long {
            Log.d(TAG, "shutdown callback")
            return 0
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON)
                if (configJson != null) {
                    startForeground(NOTIFICATION_ID, createNotification("Starting proxy..."))
                    serviceScope.launch {
                        startProxy(configJson)
                    }
                } else {
                    Log.e(TAG, "No config provided")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received")
                stopProxy()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startProxy(configJson: String) {
        try {
            Log.d(TAG, "Initializing Xray-core...")
            Log.d(TAG, "Config JSON (first 500 chars): ${configJson.take(500)}")

            // Create controller
            coreController = Libv2ray.newCoreController(callbackHandler)
            Log.d(TAG, "CoreController created: $coreController")

            // Write config for debugging
            val configFile = File(filesDir, "proxy_config.json")
            configFile.writeText(configJson)
            Log.d(TAG, "Config written to: ${configFile.absolutePath}")

            // Start Xray in proxy mode (tunFd = 0)
            Log.d(TAG, "Calling startLoop with tunFd=0...")
            coreController?.startLoop(configJson, 0)
            Log.d(TAG, "startLoop returned")

            // Check if running
            val running = coreController?.isRunning ?: false
            Log.d(TAG, "CoreController.isRunning = $running")

            isRunning = true
            Log.d(TAG, "Xray-core started! SOCKS5: 127.0.0.1:${XrayConfigBuilder.SOCKS_PORT}, HTTP: 127.0.0.1:${XrayConfigBuilder.HTTP_PORT}")

            // Try to verify port is open
            serviceScope.launch {
                delay(1000) // Wait a bit for Xray to fully start
                try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", XrayConfigBuilder.SOCKS_PORT), 1000)
                    socket.close()
                    Log.d(TAG, "SOCKS5 port ${XrayConfigBuilder.SOCKS_PORT} is OPEN and accepting connections!")
                } catch (e: Exception) {
                    Log.e(TAG, "SOCKS5 port ${XrayConfigBuilder.SOCKS_PORT} is NOT open: ${e.message}")
                }
            }

            // Notify connection manager
            VpnConnectionManager.onServiceConnected()
            updateNotification("Proxy running\nSOCKS5: 127.0.0.1:${XrayConfigBuilder.SOCKS_PORT}")

            // Start stats polling
            startStatsPolling()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start proxy", e)
            e.printStackTrace()
            VpnConnectionManager.onServiceError()
            stopSelf()
        }
    }

    private fun stopProxy() {
        Log.d(TAG, "Stopping proxy...")

        statsJob?.cancel()
        statsJob = null

        if (isRunning) {
            try {
                coreController?.stopLoop()
                Log.d(TAG, "Xray-core stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping Xray", e)
            }
            isRunning = false
        }

        coreController = null
        VpnConnectionManager.onServiceDisconnected()
    }

    private fun startStatsPolling() {
        var prevUplink = 0L
        var prevDownlink = 0L

        statsJob = serviceScope.launch {
            while (isActive) {
                delay(1000)

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

    override fun onDestroy() {
        stopProxy()
        serviceScope.cancel()
        super.onDestroy()
    }

    // --- Notifications ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Proxy Status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows proxy connection status"
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

        val stopIntent = Intent(this, ProxyService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TunVPN Proxy")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openAppPending)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Stop",
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
