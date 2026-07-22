package com.sivpn.cepat.vpn

import android.util.Log

object NativeSshTunnel {
    var isLibraryLoaded = false
        internal set

    init {
        try {
            System.loadLibrary("ssh")
            isLibraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NativeSshTunnel", "Gagal memuat libssh.so", e)
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
            Log.e("NativeSshTunnel", "Gagal memuat libssh dari path: $path", e)
            false
        }
    }

    /**
     * Memulai koneksi SSH secara native dan membuka port SOCKS5 lokal.
     * Proses ini bersifat blocking, jalankan di IO thread (Dispatchers.IO).
     * @return status code (0 jika berhasil tapi sudah berhenti, atau error code). 
     */
    @JvmStatic
    external fun startSshTunnel(
        host: String,
        port: Int,
        username: String,
        password: String,
        socksPort: Int
    ): Int

    /**
     * Menghentikan SSH tunnel.
     */
    @JvmStatic
    external fun stopSshTunnel()
}
