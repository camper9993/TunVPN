package ru.tipowk.tunvpn.vpn

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

/**
 * Controls the Xray-core engine lifecycle using AndroidLibXrayLite.
 *
 * The library has built-in tun2socks support. We need to:
 * 1. Create TUN interface BEFORE calling startLoop()
 * 2. Pass the TUN fd to startLoop(config, tunFd)
 * 3. Library will use the fd internally for tun2socks
 */
class XrayController(private val vpnService: VpnService) {

    companion object {
        private const val TAG = "XrayController"
    }

    private var coreController: CoreController? = null
    private var isRunning = false

    interface TunBuilder {
        fun createTun(parameters: String?, selectedApps: Set<String>): ParcelFileDescriptor?
        fun closeTun()
    }

    private var tunBuilder: TunBuilder? = null
    private var currentTunFd: ParcelFileDescriptor? = null
    private var selectedApps: Set<String> = emptySet()

    fun setTunBuilder(builder: TunBuilder) {
        tunBuilder = builder
    }

    fun setSelectedApps(apps: Set<String>) {
        selectedApps = apps
    }

    private val callbackHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(status: Long, msg: String?): Long {
            Log.d(TAG, "onEmitStatus: status=$status, msg=$msg")
            return 0
        }

        override fun startup(): Long {
            Log.d(TAG, "startup callback - TUN already created, fd=${currentTunFd?.fd}")
            // TUN is already created before startLoop(), just return the fd
            return currentTunFd?.fd?.toLong() ?: -1
        }

        override fun shutdown(): Long {
            Log.d(TAG, "shutdown callback")
            tunBuilder?.closeTun()
            try {
                currentTunFd?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing TUN", e)
            }
            currentTunFd = null
            return 0
        }
    }

    fun init(context: Context) {
        try {
            Log.d(TAG, "Initializing Xray...")
            val dataDir = context.filesDir.absolutePath
            Log.d(TAG, "Data dir: $dataDir")

            // Log available methods
            Log.d(TAG, "Libv2ray methods:")
            Libv2ray::class.java.methods.forEach { m ->
                Log.d(TAG, "  ${m.name}(${m.parameterTypes.map { it.simpleName }.joinToString(",")}): ${m.returnType.simpleName}")
            }

            Log.d(TAG, "CoreController methods:")
            CoreController::class.java.methods.forEach { m ->
                Log.d(TAG, "  ${m.name}(${m.parameterTypes.map { it.simpleName }.joinToString(",")}): ${m.returnType.simpleName}")
            }

            // Create controller
            coreController = Libv2ray.newCoreController(callbackHandler)
            Log.d(TAG, "CoreController created")

            Log.d(TAG, "Xray initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Xray", e)
            e.printStackTrace()
        }
    }

    /**
     * Start Xray-core with TUN mode.
     *
     * @param context Android context
     * @param configJson Xray JSON configuration
     * @param useTun If true, creates TUN interface and routes traffic through it.
     *               If false, only starts SOCKS5/HTTP proxy on localhost (proxy mode).
     */
    fun start(context: Context, configJson: String, useTun: Boolean = true): Boolean {
        if (isRunning) {
            Log.w(TAG, "Already running")
            return true
        }

        if (coreController == null) {
            init(context)
        }

        val controller = coreController ?: return false

        try {
            // Write config for debugging
            val configFile = File(context.filesDir, "config.json")
            configFile.writeText(configJson)
            Log.d(TAG, "Config written to: ${configFile.absolutePath}")

            var tunFd = 0 // 0 = no TUN, proxy mode only

            if (useTun) {
                // Create TUN interface BEFORE starting Xray
                Log.d(TAG, "Creating TUN interface...")
                val tun = tunBuilder?.createTun(null, selectedApps)
                if (tun == null) {
                    Log.e(TAG, "Failed to create TUN interface")
                    return false
                }
                currentTunFd = tun
                tunFd = tun.fd
                Log.d(TAG, "TUN interface created, fd=$tunFd")
            } else {
                Log.d(TAG, "Starting in proxy mode (no TUN)")
            }

            // Start Xray - startLoop(config: String, tunFd: Int)
            // tunFd=0 means proxy mode only, tunFd>0 means TUN mode with internal tun2socks
            Log.d(TAG, "Starting Xray with config length=${configJson.length}, tunFd=$tunFd")
            controller.startLoop(configJson, tunFd)

            isRunning = true
            Log.d(TAG, "Xray started successfully in ${if (useTun) "TUN" else "proxy"} mode")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Xray", e)
            e.printStackTrace()
            // Clean up TUN if created
            try {
                currentTunFd?.close()
            } catch (_: Exception) {}
            currentTunFd = null
            return false
        }
    }

    fun stop() {
        if (!isRunning) return
        try {
            Log.d(TAG, "Stopping Xray...")
            coreController?.stopLoop()
            isRunning = false
            Log.d(TAG, "Xray stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Stop error", e)
        }
    }

    fun queryStats(tag: String, direct: String): Long {
        return try {
            coreController?.queryStats(tag, direct) ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun isAvailable(): Boolean = coreController != null
    fun isRunning(): Boolean = isRunning
}
