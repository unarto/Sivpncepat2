package com.sivpn.cepat.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.widget.Toast
import com.sivpn.cepat.vpn.service.HevTunnelConfigurator
import com.sivpn.cepat.vpn.service.VpnInterfaceConfigurator
import com.sivpn.cepat.vpn.service.VpnMonitors
import com.sivpn.cepat.vpn.service.VpnNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SiVpnService : VpnService() {

    companion object {
        const val ACTION_STOP = "com.sivpn.cepat.vpn.STOP"
        var isRunning = false
        var connectionState = "DISCONNECTED" // DISCONNECTED, CONNECTING, CONNECTED
        var connectionStartTime = 0L
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private var logCleanupJob: Job? = null
    private var sshJob: Job? = null
    private var hevJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    @Volatile private var isSshRunning = false
    @Volatile private var isHevRunning = false

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        if (isRunning && vpnJob?.isActive == true) {
            LogManager.addLog("VPN service sudah berjalan, mengabaikan start request ganda.")
            VpnNotificationManager.updateForegroundNotification(this, "Koneksi aman sedang berjalan")
            return START_STICKY
        }

        acquireWakeLock()

        VpnNotificationManager.showForegroundNotification(this)
        isRunning = true
        connectionState = "CONNECTING"
        connectionStartTime = 0L
        startVpnScope()
        startPeriodicLogCleanup()
        return START_STICKY
    }

    private fun startPeriodicLogCleanup() {
        if (logCleanupJob?.isActive == true) return
        logCleanupJob = serviceScope.launch {
            VpnMonitors.runLogCleanupMonitor(this@SiVpnService)
        }
    }

    private fun startVpnScope() {
        if (vpnJob?.isActive == true) return
        vpnJob = serviceScope.launch {
            val model = android.os.Build.MODEL
            val sdkInt = android.os.Build.VERSION.SDK_INT
            val arch = if (android.os.Build.SUPPORTED_ABIS.isNotEmpty()) android.os.Build.SUPPORTED_ABIS[0] else "unknown"

            LogManager.addLog("--- Informasi Sistem ---")
            LogManager.addLog("Model Perangkat: $model")
            LogManager.addLog("Versi API Android: $sdkInt")
            LogManager.addLog("Arsitektur CPU: $arch")
            LogManager.addLog("-------------------------")

            LogManager.addLog("Memvalidasi ketersediaan library native JNI...")
            JniLibHelper.loadDownloadedLibs(this@SiVpnService)
            if (!NativeSshTunnel.isLibraryLoaded || !com.sivpn.cepat.TProxyService.isLibraryLoaded) {
                val errMsg = "Koneksi Dibatalkan: File library JNI (.so) tidak lengkap atau gagal dimuat!\n" +
                        "libssh.so: " + (if (NativeSshTunnel.isLibraryLoaded) "Ditemukan" else "TIDAK DITEMUKAN") + ", " +
                        "libhev-socks5-tunnel.so: " + (if (com.sivpn.cepat.TProxyService.isLibraryLoaded) "Ditemukan" else "TIDAK DITEMUKAN") + ".\n" +
                        "Pastikan file-file tersebut terpasang di folder jniLibs."
                LogManager.addLog(errMsg)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SiVpnService, "Gagal memulai VPN: Library native (.so) tidak ditemukan!", Toast.LENGTH_LONG).show()
                }
                connectionState = "DISCONNECTED"
                stopSelf()
                return@launch
            }

            LogManager.addLog("Extracting assets...")
            ConnectionManager.extractAssets(this@SiVpnService)

            LogManager.addLog("Setting up VPN Interface...")
            if (prepare(this@SiVpnService) != null) {
                LogManager.addLog("[Warning] VpnService.prepare returned non-null within service, proceeding to establish anyway...")
            }
            setupVpnInterface()

            if (vpnInterface == null) {
                LogManager.addLog("Error: VPN Interface is null")
                connectionState = "DISCONNECTED"
                stopSelf()
                return@launch
            }

            launch(Dispatchers.IO) {
                VpnMonitors.runDurationMonitor(this@SiVpnService) { stopSelf() }
            }

            launch(Dispatchers.IO) {
                VpnMonitors.runKeepAliveMonitor(this@SiVpnService)
            }

            launch(Dispatchers.IO) {
                VpnMonitors.runSpeedometerMonitor(this@SiVpnService)
            }

            var reconnectDelayMs = 2000L
            val minDelayMs = 2000L
            val maxDelayMs = 32000L

            while (isRunning) {
                connectionState = "CONNECTING"
                cleanupProcesses()

                val proxyHost = VpnSettingsManager.getProxyHost(this@SiVpnService)
                val proxyPort = VpnSettingsManager.getProxyPort(this@SiVpnService)
                val payload = VpnSettingsManager.getPayload(this@SiVpnService)
                val sni = VpnSettingsManager.getSni(this@SiVpnService)
                val tlsVersion = VpnSettingsManager.getForcingTls(this@SiVpnService)
                
                var actualSshHost = VpnSettingsManager.getSshHost(this@SiVpnService)
                var actualSshPort = VpnSettingsManager.getSshPort(this@SiVpnService)

                if (payload.isNotEmpty() || sni.isNotEmpty() || (proxyHost.isNotEmpty() && proxyPort > 0)) {
                    LogManager.addLog("Starting Payload/SNI Injector Mode...")
                    PayloadInjector.start(proxyHost, proxyPort, actualSshHost, actualSshPort, payload, sni, tlsVersion)
                    
                    if (PayloadInjector.isRunning) {
                        actualSshHost = "127.0.0.1"
                        actualSshPort = PayloadInjector.localPort
                        LogManager.addLog("Routing SSH connection through local proxy: $actualSshHost:$actualSshPort")
                    } else {
                        LogManager.addLog("Gagal memulai Payload Injector. Melanjutkan dengan Direct connect.")
                    }
                }

                LogManager.addLog("Starting SSH natively...")
                isSshRunning = true
                sshJob = launch(Dispatchers.IO) {
                    try {
                        val username = VpnSettingsManager.getSshUsername(this@SiVpnService)
                        val password = VpnSettingsManager.getSshPassword(this@SiVpnService)
                        
                        val result = NativeSshTunnel.startSshTunnel(actualSshHost, actualSshPort, username, password, 1080)
                        
                        when (result) {
                            0 -> LogManager.addLog("SSH Tunnel berhenti secara normal.")
                            -1 -> LogManager.addLog("SSH Error (-1): Gagal menginisialisasi libssh2.")
                            -3 -> LogManager.addLog("SSH Error (-3): Gagal terhubung ke remote SSH server ($actualSshHost:$actualSshPort).")
                            -4 -> LogManager.addLog("SSH Error (-4): Gagal membuat sesi SSH.")
                            -5 -> LogManager.addLog("SSH Error (-5): Handshake SSH gagal.")
                            -6 -> LogManager.addLog("SSH Error (-6): Autentikasi SSH gagal. Periksa Username/Password.")
                            -7 -> LogManager.addLog("SSH Error (-7): Gagal membuat local server socket SOCKS5.")
                            -8 -> LogManager.addLog("SSH Error (-8): Gagal melakukan bind pada local SOCKS5 port (1080).")
                            -9 -> LogManager.addLog("SSH Error (-9): Gagal listen pada local SOCKS5 port.")
                            -10 -> LogManager.addLog("SSH Error (-10): Argumen JNI tidak valid.")
                            -11 -> LogManager.addLog("SSH Error (-11): Gagal konversi string JNI.")
                            else -> LogManager.addLog("SSH Tunnel berhenti dengan kode: $result")
                        }
                    } catch (t: Throwable) {
                        LogManager.addLog("Error SSH Native: ${t.javaClass.simpleName} - ${t.message}")
                    } finally {
                        isSshRunning = false
                        PayloadInjector.stop()
                    }
                }

                delay(3000)

                if (!isRunning) break

                if (!isSshRunning) {
                    if (!VpnSettingsManager.getAutoReconnectEnabled(this@SiVpnService)) {
                        LogManager.addLog("SSH native thread stopped. Auto-Reconnect dimatikan. Menghentikan VPN.")
                        connectionState = "DISCONNECTED"
                        stopSelf()
                        break
                    }
                    LogManager.addLog("SSH native thread stopped unexpectedly. Retrying in ${reconnectDelayMs / 1000}s...")
                    delay(reconnectDelayMs)
                    reconnectDelayMs = minOf(reconnectDelayMs * 2, maxDelayMs)
                    continue
                }

                LogManager.addLog("Starting hev-socks5-tunnel natively...")
                isHevRunning = true
                hevJob = launch(Dispatchers.IO) {
                    try {
                        val fd = vpnInterface?.fd
                        if (fd != null) {
                            HevTunnelConfigurator.startHevTunnel(this@SiVpnService, fd)
                        }
                    } catch (t: Throwable) {
                        LogManager.addLog("Error HEV Native: ${t.javaClass.simpleName} - ${t.message}")
                    } finally {
                        isHevRunning = false
                    }
                }

                delay(1500)

                if (!isRunning) break

                if (isSshRunning && isHevRunning) {
                    LogManager.addLog("Koneksi berhasil terjalin!")
                    connectionState = "CONNECTED"
                    if (connectionStartTime == 0L) {
                        connectionStartTime = System.currentTimeMillis()
                    }
                    reconnectDelayMs = minDelayMs
                } else {
                    if (!VpnSettingsManager.getAutoReconnectEnabled(this@SiVpnService)) {
                        LogManager.addLog("Failed to establish tunnel. Auto-Reconnect dimatikan. Menghentikan VPN.")
                        connectionState = "DISCONNECTED"
                        stopSelf()
                        break
                    }
                    LogManager.addLog("Failed to establish tunnel. Retrying in ${reconnectDelayMs / 1000}s...")
                    delay(reconnectDelayMs)
                    reconnectDelayMs = minOf(reconnectDelayMs * 2, maxDelayMs)
                    continue
                }

                while (isRunning && isSshRunning && isHevRunning) {
                    delay(1000)
                }

                if (isRunning) {
                    val reason = when {
                        !isSshRunning -> "Library SSH (libssh) terputus"
                        !isHevRunning -> "Library HEV Tunnel terputus"
                        else -> "Koneksi tidak stabil"
                    }
                    
                    if (!VpnSettingsManager.getAutoReconnectEnabled(this@SiVpnService)) {
                        LogManager.addLog("Koneksi terputus: $reason. Auto-Reconnect dimatikan. Menghentikan VPN.")
                        connectionState = "DISCONNECTED"
                        stopSelf()
                        break
                    }
                    
                    LogManager.addLog("Auto-Reconnect: $reason! Memulai rekoneksi otomatis...")
                    connectionState = "CONNECTING"
                    cleanupProcesses()
                    
                    delay(reconnectDelayMs)
                    reconnectDelayMs = minOf(reconnectDelayMs * 2, maxDelayMs)
                }
            }
        }
    }

    private fun cleanupProcesses() {
        try {
            PayloadInjector.stop()
        } catch (e: Exception) {}

        try {
            NativeSshTunnel.stopSshTunnel()
        } catch (e: Exception) {
            LogManager.addLog("Failed to interrupt SSH JNI: ${e.message}")
        }
        sshJob?.cancel()
        sshJob = null
        isSshRunning = false

        try {
            com.sivpn.cepat.TProxyService.TProxyStopService()
        } catch (e: Throwable) {
            LogManager.addLog("Failed to quit HevSocks5Tunnel JNI: ${e.message}")
        }
        hevJob?.cancel()
        hevJob = null
        isHevRunning = false

        try {
            LocalPortForwarder.stop()
        } catch (e: Exception) {}
        try {
            HttpProxyServer.stop()
        } catch (e: Exception) {}
    }

    private fun setupVpnInterface() {
        try {
            val builder = Builder()
            VpnInterfaceConfigurator.configure(builder, this)
            vpnInterface = builder.establish()
            LogManager.addLog("VPN Interface created successfully with MTU 1500")
        } catch (se: SecurityException) {
            LogManager.addLog("ERROR KEAMANAN VPN (SecurityException): Gagal melakukan establish VPN.")
            LogManager.addLog("- Kemungkinan 1: Izin VPN ditolak, dicabut, atau diblokir oleh sistem.")
            LogManager.addLog("- Kemungkinan 2: Ada aplikasi VPN lain yang sedang aktif dengan mode 'Always-on VPN' (Block connections without VPN) di pengaturan Android. Matikan VPN lain tersebut terlebih dahulu!")
            LogManager.addLog("- Detail error: ${se.message}")
            vpnInterface = null
        } catch (e: Exception) {
            LogManager.addLog("VPN setup failed (Exception): ${e.message}")
            vpnInterface = null
        }
    }

    private fun acquireWakeLock() {
        if (!VpnSettingsManager.getVpnWakeLockEnabled(this)) {
            LogManager.addLog("VPN WakeLock is disabled in settings. Skipping acquire to save battery (CPU can sleep).")
            return
        }
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SiVPN::WakeLock")
            wakeLock?.acquire()
            LogManager.addLog("WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            LogManager.addLog("WakeLock released")
        }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        connectionState = "DISCONNECTED"
        connectionStartTime = 0L
        vpnJob?.cancel()
        vpnJob = null
        logCleanupJob?.cancel()
        logCleanupJob = null
        cleanupProcesses()
        serviceJob.cancel()

        releaseWakeLock()
        
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            LogManager.addLog("Error closing VPN Interface: ${e.message}")
        }
        vpnInterface = null
        LogManager.addLog("SiVPN Service Stopped")
    }
}
