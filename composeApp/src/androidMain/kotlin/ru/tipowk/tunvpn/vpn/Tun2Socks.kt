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
     * Generate YAML config for hev-socks5-tunnel (same format as v2rayNG).
     */
    fun generateConfig(
        socksPort: Int = XrayConfigBuilder.SOCKS_PORT,
        tunMtu: Int = 1500,
        tunIpv4: String = "10.0.0.2",
    ): String {
        // Format matching v2rayNG exactly
        return """
tunnel:
  mtu: $tunMtu
  ipv4: $tunIpv4
socks5:
  port: $socksPort
  address: 127.0.0.1
  udp: 'udp'
misc:
  tcp-read-write-timeout: 300000
  udp-read-write-timeout: 60000
  log-level: debug
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

        // Reset state if stuck from previous crash
        if (TProxyService.isRunning()) {
            Log.w(TAG, "TProxyService still thinks it's running, forcing reset")
            TProxyService.forceReset()
        }

        val config = generateConfig(socksPort = socksPort)
        // Use filesDir and same filename as v2rayNG
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
