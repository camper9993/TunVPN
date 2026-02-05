package ru.tipowk.tunvpn.vpn

import android.util.Log
import java.io.File

/**
 * Manages hev-socks5-tunnel (tun2socks) which bridges TUN interface to SOCKS5 proxy.
 *
 * hev-socks5-tunnel reads IP packets from TUN fd and forwards them to a SOCKS5 server.
 * This allows routing all traffic through Xray's SOCKS5 inbound.
 */
object Tun2SocksManager {

    private const val TAG = "Tun2SocksManager"

    private var isRunning = false

    // JNI methods from libhev-socks5-tunnel.so
    private external fun nativeStart(configPath: String): Int
    private external fun nativeStop()

    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
            Log.d(TAG, "hev-socks5-tunnel library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load hev-socks5-tunnel library", e)
        }
    }

    /**
     * Start tun2socks with the given parameters.
     *
     * @param tunFd File descriptor of the TUN interface
     * @param socksAddress SOCKS5 server address (e.g., "127.0.0.1")
     * @param socksPort SOCKS5 server port (e.g., 10808)
     * @param mtu MTU of TUN interface
     * @param configDir Directory to write config file
     * @return true if started successfully
     */
    fun start(
        tunFd: Int,
        socksAddress: String,
        socksPort: Int,
        mtu: Int,
        configDir: File
    ): Boolean {
        if (isRunning) {
            Log.w(TAG, "tun2socks already running")
            return true
        }

        try {
            // Write config file for hev-socks5-tunnel
            val configFile = File(configDir, "tun2socks.yml")
            val config = """
                tunnel:
                  name: tun0
                  mtu: $mtu
                  multi-queue: false

                socks5:
                  address: $socksAddress
                  port: $socksPort
                  udp: udp

                misc:
                  task-stack-size: 20480
                  connect-timeout: 5000
                  read-write-timeout: 60000
                  log-file: /dev/null
                  log-level: warn
                  pid-file: /dev/null
                  limit-nofile: 65535
            """.trimIndent()

            configFile.writeText(config)
            Log.d(TAG, "Config written to ${configFile.absolutePath}")

            // Start in a new thread
            Thread({
                try {
                    Log.d(TAG, "Starting tun2socks with fd=$tunFd")
                    val result = nativeStart(configFile.absolutePath)
                    Log.d(TAG, "tun2socks exited with code $result")
                } catch (e: Exception) {
                    Log.e(TAG, "tun2socks error", e)
                } finally {
                    isRunning = false
                }
            }, "tun2socks").start()

            Thread.sleep(100) // Give it time to start
            isRunning = true
            Log.d(TAG, "tun2socks started")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tun2socks", e)
            return false
        }
    }

    /**
     * Stop tun2socks.
     */
    fun stop() {
        if (!isRunning) return

        try {
            Log.d(TAG, "Stopping tun2socks...")
            nativeStop()
            isRunning = false
            Log.d(TAG, "tun2socks stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop tun2socks", e)
        }
    }

    fun isRunning(): Boolean = isRunning
}
