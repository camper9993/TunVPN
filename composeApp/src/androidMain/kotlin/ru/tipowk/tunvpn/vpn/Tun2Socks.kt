package ru.tipowk.tunvpn.vpn

import android.util.Log
import com.v2ray.ang.service.TProxyService
import java.io.File

/**
 * High-level wrapper for hev-socks5-tunnel (tun2socks).
 */
object Tun2Socks {

    private const val TAG = "Tun2Socks"

    /**
     * Generate YAML config for hev-socks5-tunnel.
     * Format based on sockstun (official Android app using this library).
     * NOTE: Do NOT include 'name' field - the TUN fd is passed directly from VpnService.
     */
    fun generateConfig(
        socksPort: Int = XrayConfigBuilder.SOCKS_PORT,
        tunMtu: Int = 1500,
    ): String {
        // Format matching sockstun exactly - misc first, then tunnel, then socks5
        // NO 'name' or 'ipv4' fields - fd is passed directly
        return """
misc:
  task-stack-size: 81920
  log-level: debug
tunnel:
  mtu: $tunMtu
socks5:
  port: $socksPort
  address: '127.0.0.1'
  udp: 'udp'
""".trimIndent()
    }

    /**
     * Start the tunnel. BLOCKS until stopTunnel() is called!
     */
    fun startTunnel(filesDir: File, tunFd: Int, socksPort: Int = XrayConfigBuilder.SOCKS_PORT) {
        Log.d(TAG, "startTunnel: tunFd=$tunFd, socksPort=$socksPort, isRunning=${TProxyService.isRunning()}")

        if (tunFd <= 0) {
            Log.e(TAG, "Invalid tunFd: $tunFd")
            return
        }

        // If still running, something is wrong - previous stop didn't complete
        if (TProxyService.isRunning()) {
            Log.e(TAG, "TProxyService still running! Cannot start new tunnel.")
            return
        }

        val config = generateConfig(socksPort = socksPort)
        val configFile = File(filesDir, "hev-socks5-tunnel.yaml")
        configFile.writeText(config)
        Log.d(TAG, "Config written to ${configFile.absolutePath}:\n$config")

        TProxyService.startService(configFile.absolutePath, tunFd)
        Log.d(TAG, "TProxyService.startService returned")
    }

    fun stopTunnel() {
        Log.d(TAG, "stopTunnel")
        TProxyService.stopService()
    }

    fun getStats(): Pair<Long, Long>? {
        return try {
            val stats = TProxyService.getStats()
            if (stats.size >= 4) Pair(stats[1], stats[3]) else null
        } catch (e: Exception) {
            null
        }
    }

    fun isRunning(): Boolean = TProxyService.isRunning()
}
