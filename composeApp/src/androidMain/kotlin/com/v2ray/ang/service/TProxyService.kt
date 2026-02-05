package com.v2ray.ang.service

import android.util.Log

/**
 * JNI interface for hev-socks5-tunnel library from v2rayNG.
 *
 * This class MUST be in package "com.v2ray.ang.service" and named "TProxyService"
 * because the native library from v2rayNG expects these exact JNI method names:
 * - Java_com_v2ray_ang_service_TProxyService_TProxyStartService
 * - Java_com_v2ray_ang_service_TProxyService_TProxyStopService
 * - Java_com_v2ray_ang_service_TProxyService_TProxyGetStats
 */
object TProxyService {

    private const val TAG = "TProxyService"

    @Volatile
    private var isRunning = false

    init {
        try {
            Log.d(TAG, "Loading libhev-socks5-tunnel.so...")
            System.loadLibrary("hev-socks5-tunnel")
            Log.d(TAG, "libhev-socks5-tunnel.so loaded successfully!")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libhev-socks5-tunnel.so", e)
        }
    }

    /**
     * Start the tun2socks service.
     *
     * @param configPath Path to YAML config file
     * @param fd TUN file descriptor
     *
     * Note: This method blocks until TProxyStopService is called!
     */
    fun startService(configPath: String, fd: Int) {
        Log.d(TAG, "startService: configPath=$configPath, fd=$fd, isRunning=$isRunning")

        if (fd <= 0) {
            Log.e(TAG, "Invalid fd: $fd")
            return
        }

        if (isRunning) {
            Log.w(TAG, "Service already running!")
            return
        }

        isRunning = true
        try {
            Log.d(TAG, "Calling native TProxyStartService...")
            TProxyStartService(configPath, fd)
            Log.d(TAG, "TProxyStartService returned")
        } catch (e: Exception) {
            Log.e(TAG, "Error in TProxyStartService", e)
        } finally {
            isRunning = false
            Log.d(TAG, "isRunning set to false")
        }
    }

    /**
     * Stop the tun2socks service.
     */
    fun stopService() {
        Log.d(TAG, "stopService: isRunning=$isRunning")

        if (!isRunning) {
            Log.d(TAG, "Service not running")
            return
        }

        try {
            Log.d(TAG, "Calling native TProxyStopService...")
            TProxyStopService()
            Log.d(TAG, "TProxyStopService returned")
        } catch (e: Exception) {
            Log.e(TAG, "Error in TProxyStopService", e)
        }
    }

    /**
     * Get traffic statistics.
     * @return LongArray [txPackets, txBytes, rxPackets, rxBytes]
     */
    fun getStats(): LongArray {
        return try {
            TProxyGetStats()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stats", e)
            longArrayOf(0, 0, 0, 0)
        }
    }

    fun isRunning(): Boolean = isRunning

    /**
     * Force reset running state. Call this if the native library is stuck.
     */
    fun forceReset() {
        Log.w(TAG, "forceReset: setting isRunning to false")
        isRunning = false
    }

    // Native methods
    @JvmStatic
    private external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    private external fun TProxyStopService()

    @JvmStatic
    private external fun TProxyGetStats(): LongArray
}
