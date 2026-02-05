package ru.tipowk.tunvpn.vpn

import android.util.Log
import com.v2ray.ang.service.TProxyService
import java.io.File

/**
 * High-level wrapper for hev-socks5-tunnel (tun2socks).
 *
 * This class manages the tun2socks tunnel lifecycle and provides
 * a simpler API for the VPN service.
 *
 * Architecture:
 *   App traffic -> VpnService TUN -> tun2socks -> SOCKS5 (Xray) -> VLESS Server
 */
object Tun2Socks {

    private const val TAG = "Tun2Socks"

    /**
     * Generate YAML config for hev-socks5-tunnel.
     *
     * @param socksAddress SOCKS5 server address (e.g., "127.0.0.1")
     * @param socksPort SOCKS5 server port (e.g., 10808)
     * @param tunMtu TUN interface MTU
     * @param tunIpv4 TUN IPv4 address (e.g., "10.0.0.2")
     * @param logLevel Log level: debug, info, warn, error
     */
    fun generateConfig(
        socksAddress: String = "127.0.0.1",
        socksPort: Int = XrayConfigBuilder.SOCKS_PORT,
        tunMtu: Int = 1500,
        tunIpv4: String = "10.0.0.2",
        dnsServer: String = "1.1.1.1",
        logLevel: String = "debug"
    ): String {
        // DNS fakе address - tun2socks will intercept DNS queries to this address
        // and forward them through SOCKS5 to the real DNS server
        val fakeDns = "198.18.0.1"

        val config = """
tunnel:
  mtu: $tunMtu
  ipv4: $tunIpv4

socks5:
  address: $socksAddress
  port: $socksPort
  udp: udp

misc:
  log-level: $logLevel
  task-stack-size: 81920
""".trimIndent()

        Log.d(TAG, "Generated tun2socks config:\n$config")
        return config
    }

    /**
     * Write config to file and start the tunnel.
     *
     * @param cacheDir Directory to write config file
     * @param tunFd TUN file descriptor
     * @param socksPort SOCKS5 proxy port (default: 10808)
     *
     * Note: This method BLOCKS until stopTunnel() is called!
     * Run it in a background thread.
     */
    fun startTunnel(cacheDir: File, tunFd: Int, socksPort: Int = XrayConfigBuilder.SOCKS_PORT) {
        Log.d(TAG, "=== startTunnel ===")
        Log.d(TAG, "  cacheDir: ${cacheDir.absolutePath}")
        Log.d(TAG, "  tunFd: $tunFd")
        Log.d(TAG, "  socksPort: $socksPort")

        // Generate config
        val config = generateConfig(socksPort = socksPort)

        // Write config to file
        val configFile = File(cacheDir, "tun2socks.yml")
        configFile.writeText(config)
        Log.d(TAG, "Config written to: ${configFile.absolutePath}")

        // Verify file was written
        if (!configFile.exists()) {
            Log.e(TAG, "Config file was not created!")
            return
        }
        Log.d(TAG, "Config file size: ${configFile.length()} bytes")

        // Start the tunnel (blocks until stopped)
        Log.d(TAG, "Starting TProxyService...")
        TProxyService.startService(configFile.absolutePath, tunFd)
        Log.d(TAG, "TProxyService.startService returned (tunnel stopped)")
    }

    /**
     * Stop the running tunnel.
     */
    fun stopTunnel() {
        Log.d(TAG, "=== stopTunnel ===")
        TProxyService.stopService()
    }

    /**
     * Get traffic statistics.
     *
     * @return Pair of (txBytes, rxBytes) or null if not available
     */
    fun getStats(): Pair<Long, Long>? {
        return try {
            val stats = TProxyService.getStats()
            if (stats.size >= 4) {
                Pair(stats[1], stats[3]) // txBytes, rxBytes
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stats", e)
            null
        }
    }

    /**
     * Check if tunnel is running.
     */
    fun isRunning(): Boolean = TProxyService.isRunning()
}
