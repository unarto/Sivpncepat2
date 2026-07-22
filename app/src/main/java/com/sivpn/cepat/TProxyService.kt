package com.sivpn.cepat

import android.util.Log

object TProxyService {
    private const val TAG = "TProxyService"

    var isLibraryLoaded = false
        internal set

    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
            isLibraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Gagal memuat libhev-socks5-tunnel.so", e)
            isLibraryLoaded = false
        }
    }

    @JvmStatic
    fun loadFromPath(path: String): Boolean {
        if (path.isBlank()) return false
        return try {
            System.load(path)
            isLibraryLoaded = true
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Gagal memuat libhev-socks5-tunnel dari path: $path", e)
            false
        }
    }

    @JvmStatic
    @Suppress("FunctionName")
    external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    @Suppress("FunctionName")
    external fun TProxyStopService()

    @JvmStatic
    @Suppress("FunctionName")
    external fun TProxyGetStats(): LongArray?
}
