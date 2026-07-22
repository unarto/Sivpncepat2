package com.sivpn.cepat.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.sivpn.cepat.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
            updateForegroundNotification("Koneksi aman sedang berjalan")
            return START_STICKY
        }

        acquireWakeLock()

        showForegroundNotification()
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
            LogManager.maxLogLines = VpnSettingsManager.getMaxLogLines(this@SiVpnService)
            while (isRunning) {
                val autoCleanLogsEnabled = VpnSettingsManager.getAutoCleanLogsEnabled(this@SiVpnService)
                val intervalMins = VpnSettingsManager.getAutoCleanInterval(this@SiVpnService)
                LogManager.maxLogLines = VpnSettingsManager.getMaxLogLines(this@SiVpnService)
                
                if (autoCleanLogsEnabled && intervalMins > 0) {
                    val delayMs = intervalMins * 60 * 1000L
                    delay(delayMs)
                    if (isRunning && VpnSettingsManager.getAutoCleanLogsEnabled(this@SiVpnService)) {
                        LogManager.addLog("--- Menjalankan Pembersihan Log Berkala Otomatis ---")
                        LogManager.clearLogs()
                        LogManager.clearPhysicalLogFile(this@SiVpnService)
                        LogManager.addLog("--- Pembersihan Log Selesai ---")
                    }
                } else {
                    delay(30000L) // check settings status again in 30 seconds
                }
            }
        }
    }

    private fun showForegroundNotification() {
        val channelId = "vpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(this, MainActivity::class.java)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent, flags
        )

        val stopIntent = Intent(this, SiVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, flags
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SiVPN Aktif")
            .setContentText("Koneksi aman sedang berjalan")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "MATIKAN", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun updateForegroundNotification(text: String) {
        val channelId = "vpn_channel"
        val activityIntent = Intent(this, MainActivity::class.java)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent, flags
        )

        val stopIntent = Intent(this, SiVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, flags
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SiVPN Aktif")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "MATIKAN", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, notification)
    }

    private fun startVpnScope() {
        if (vpnJob?.isActive == true) return
        vpnJob = serviceScope.launch {
            // Log device specifications prior to connection
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

            // Jalankan monitor durasi koneksi satu kali saja selama VPN berjalan
            launch(Dispatchers.IO) {
                while (isRunning) {
                    val limitMinutes = VpnSettingsManager.getConnectionLimitMinutes(this@SiVpnService)
                    val limitEnabled = VpnSettingsManager.getConnectionLimitEnabled(this@SiVpnService)
                    if (limitEnabled && limitMinutes > 0 && connectionStartTime > 0L) {
                        val elapsedMs = System.currentTimeMillis() - connectionStartTime
                        if (elapsedMs >= limitMinutes * 60 * 1000L) {
                            LogManager.addLog("Batas durasi koneksi ($limitMinutes menit) tercapai! Memutuskan koneksi otomatis.")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@SiVpnService,
                                    "Batas waktu koneksi $limitMinutes menit tercapai!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            stopSelf()
                            break
                        }
                    }
                    delay(2500)
                }
            }

            // Jalankan keep-alive ping loop di background thread (Dispatchers.IO) agar tidak ANR
            launch(Dispatchers.IO) {
                while (isRunning) {
                    val autoPingEnabled = VpnSettingsManager.getAutoPing(this@SiVpnService)
                    if (autoPingEnabled && connectionState == "CONNECTED") {
                        val customPingAddress = VpnSettingsManager.getPingAddress(this@SiVpnService).trim()
                        val sshHost = VpnSettingsManager.getSshHost(this@SiVpnService)
                        val sshPort = VpnSettingsManager.getSshPort(this@SiVpnService)
                        
                        val (host, port) = if (customPingAddress.isNotEmpty()) {
                            parseHostAndPort(customPingAddress, 80)
                        } else {
                            Pair(sshHost, sshPort.coerceIn(1, 65535))
                        }

                        if (host.isNotEmpty()) {
                            try {
                                LogManager.addLog("Keep-alive: mem-ping $host:$port...")
                                val latency = PingUtility.measureLatency(host, port, timeoutMs = 2500)
                                if (latency >= 0) {
                                    LogManager.addLog("Keep-alive ping berhasil: $latency ms")
                                } else {
                                    LogManager.addLog("Keep-alive ping gagal atau timeout.")
                                }
                            } catch (e: Exception) {
                                LogManager.addLog("Keep-alive ping error: ${e.message}")
                            }
                        }
                        
                        val intervalSec = VpnSettingsManager.getKeepAliveInterval(this@SiVpnService).coerceAtLeast(5)
                        delay(intervalSec * 1000L)
                    } else {
                        delay(30000L) // Slower check when keepalive is disabled or disconnected to conserve battery
                    }
                }
            }

            // Jalankan speedometer task di background thread
            launch(Dispatchers.IO) {
                var lastRxBytes = android.net.TrafficStats.getTotalRxBytes()
                var lastTxBytes = android.net.TrafficStats.getTotalTxBytes()
                
                while (isRunning) {
                    val speedometerEnabled = VpnSettingsManager.getSpeedometerEnabled(this@SiVpnService)
                    if (speedometerEnabled && connectionState == "CONNECTED") {
                        val currentRxBytes = android.net.TrafficStats.getTotalRxBytes()
                        val currentTxBytes = android.net.TrafficStats.getTotalTxBytes()
                        
                        val rxDiff = currentRxBytes - lastRxBytes
                        val txDiff = currentTxBytes - lastTxBytes
                        
                        lastRxBytes = currentRxBytes
                        lastTxBytes = currentTxBytes
                        
                        fun formatSpeed(bytesPerSec: Long): String {
                            if (bytesPerSec <= 0) return "0 B/s"
                            val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s", "TB/s")
                            val digitGroups = (Math.log10(bytesPerSec.toDouble()) / Math.log10(1024.0)).toInt()
                                .coerceIn(0, units.lastIndex)
                            return String.format("%.1f %s", bytesPerSec / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
                        }
                        
                        val rxSpeedStr = formatSpeed(rxDiff)
                        val txSpeedStr = formatSpeed(txDiff)
                        
                        updateForegroundNotification("↓ $rxSpeedStr | ↑ $txSpeedStr")
                        delay(1000)
                    } else {
                        if (connectionState == "CONNECTED") {
                            // Restore original notification text when not enabled
                            updateForegroundNotification("Koneksi aman sedang berjalan")
                        }
                        delay(10000) // Delay significantly longer (10s) when speedometer is inactive to preserve battery
                    }
                }
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

                // Jika Payload, SNI, atau Proxy (selain null/kosong) digunakan, kita rutekan koneksi SSH ke Local Proxy
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
                        
                        // JNI Method ini bersifat blocking -> menghubungkan via libssh.so
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

                // Beri jeda secukupnya agar tunneling library libssh binding port lokal 1080
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
                        startHevTunnel()
                    } catch (t: Throwable) {
                        LogManager.addLog("Error HEV Native: ${t.javaClass.simpleName} - ${t.message}")
                    } finally {
                        isHevRunning = false
                    }
                }

                // Beri jeda agar hev tunnel inisialisasi
                delay(1500)

                if (!isRunning) break

                if (isSshRunning && isHevRunning) {
                    LogManager.addLog("Koneksi berhasil terjalin!")
                    connectionState = "CONNECTED"
                    if (connectionStartTime == 0L) {
                        connectionStartTime = System.currentTimeMillis()
                    }
                    // Reset backoff delay as the connection was successful
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

                // Monitor loop - cek status kedua process setiap detik
                while (isRunning && isSshRunning && isHevRunning) {
                    delay(1000)
                }

                // Jika terdeteksi putus dan masih running, lakukan reconnect
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

    private fun parseHostAndPort(value: String, defaultPort: Int): Pair<String, Int> {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return "" to defaultPort

        if (trimmed.startsWith("[")) {
            val endBracket = trimmed.indexOf(']')
            if (endBracket > 0) {
                val host = trimmed.substring(1, endBracket)
                val port = trimmed.substring(endBracket + 1)
                    .removePrefix(":")
                    .toIntOrNull()
                    ?: defaultPort
                return host to port.coerceIn(1, 65535)
            }
        }

        val colonIndex = trimmed.lastIndexOf(':')
        val hasSingleColon = colonIndex > 0 && trimmed.indexOf(':') == colonIndex
        if (hasSingleColon) {
            val host = trimmed.substring(0, colonIndex).trim()
            val port = trimmed.substring(colonIndex + 1).toIntOrNull() ?: defaultPort
            return host to port.coerceIn(1, 65535)
        }

        return trimmed to defaultPort.coerceIn(1, 65535)
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

        // Stop Hotshare if VPN is disconnected
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
            builder.setBlocking(false)
            builder.addAddress("10.0.0.2", 24)
            // Mengalihkan seluruh trafik perangkat ke TUN Interface
            builder.addRoute("0.0.0.0", 0)
            
            // Konfigurasi DNS dinamis
            val dnsString = VpnSettingsManager.getDns(this@SiVpnService)
            dnsString.split(Regex("[:;,\\s]+")).forEach { dnsIp ->
                if (dnsIp.isNotBlank()) {
                    try {
                        builder.addDnsServer(dnsIp.trim())
                    } catch (e: Exception) {
                        LogManager.addLog("Failed to add DNS $dnsIp: ${e.message}")
                    }
                }
            }
            
            builder.setMtu(1500)
            builder.setSession("SiVPN")

            // Set configure intent agar user bisa mengetuk notifikasi VPN untuk masuk ke aplikasi
            try {
                val configIntent = android.app.PendingIntent.getActivity(
                    this@SiVpnService,
                    0,
                    Intent(this@SiVpnService, Class.forName("com.sivpn.cepat.MainActivity")),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                builder.setConfigureIntent(configIntent)
            } catch (e: Exception) {
                LogManager.addLog("Gagal menetapkan Configure Intent: ${e.message}")
            }

            // Log status Kill Switch
            if (VpnSettingsManager.getKillSwitchEnabled(this@SiVpnService)) {
                LogManager.addLog("Kill Switch aktif: Mengunci lalu lintas internet selama rekoneksi.")
            } else {
                LogManager.addLog("Kill Switch nonaktif.")
            }

            // Split Tunneling (Bypass / Apps Filter)
            if (VpnSettingsManager.getSplitTunnelingEnabled(this@SiVpnService)) {
                val bypassApps = VpnSettingsManager.getBypassApps(this@SiVpnService)
                val filterMode = VpnSettingsManager.getAppsFilterMode(this@SiVpnService)
                if (filterMode == "filter") {
                    LogManager.addLog("Apps Filter (Only Tunnel) aktif. Memproses ${bypassApps.size} aplikasi...")
                    for (packageName in bypassApps) {
                        if (packageName.isNotBlank()) {
                            try {
                                builder.addAllowedApplication(packageName)
                                LogManager.addLog("Hanya rute VPN untuk: $packageName")
                            } catch (e: Exception) {
                                LogManager.addLog("Gagal menambahkan rute $packageName: ${e.message}")
                            }
                        }
                    }
                } else {
                    LogManager.addLog("Bypass Aplikasi aktif. Memproses ${bypassApps.size} aplikasi bypass...")
                    for (packageName in bypassApps) {
                        if (packageName.isNotBlank()) {
                            try {
                                builder.addDisallowedApplication(packageName)
                                LogManager.addLog("Aplikasi bypass: $packageName")
                            } catch (e: Exception) {
                                LogManager.addLog("Gagal menambahkan bypass $packageName: ${e.message}")
                            }
                        }
                    }
                }
            }

            try {
                // Selalu bypass aplikasi ini sendiri agar tidak routing loop, kecuali jika menggunakan mode filter
                val filterMode = VpnSettingsManager.getAppsFilterMode(this@SiVpnService)
                val splitTunneling = VpnSettingsManager.getSplitTunnelingEnabled(this@SiVpnService)
                if (!splitTunneling || filterMode != "filter") {
                    builder.addDisallowedApplication(applicationContext.packageName)
                    LogManager.addLog("Aplikasi sendiri (SIVPN) dibypass untuk menghindari routing loop.")
                }
            } catch (e: Exception) {
                LogManager.addLog("Gagal menambahkan self-bypass: ${e.message}")
            }

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

    private suspend fun startHevTunnel() = withContext(Dispatchers.IO) {
        try {
            val fd = vpnInterface?.fd ?: return@withContext
            
            val hevMtu = VpnSettingsManager.getHevMtu(this@SiVpnService)
            val hevMultiQueue = VpnSettingsManager.getHevMultiQueue(this@SiVpnService)
            val hevIpv4 = VpnSettingsManager.getHevIpv4(this@SiVpnService)
            val hevIpv6 = VpnSettingsManager.getHevIpv6(this@SiVpnService)

            val hevDnsPort = VpnSettingsManager.getHevDnsPort(this@SiVpnService)
            val hevDnsAddress = VpnSettingsManager.getHevDnsAddress(this@SiVpnService)

            val hevSocks5Port = VpnSettingsManager.getHevSocks5Port(this@SiVpnService)
            val hevSocks5Address = VpnSettingsManager.getHevSocks5Address(this@SiVpnService)
            val hevSocks5Udp = VpnSettingsManager.getHevSocks5Udp(this@SiVpnService)

            val hevTaskStackSize = VpnSettingsManager.getHevTaskStackSize(this@SiVpnService)
            val hevTcpBufferSize = VpnSettingsManager.getHevTcpBufferSize(this@SiVpnService)
            val hevUdpRecvBufferSize = VpnSettingsManager.getHevUdpRecvBufferSize(this@SiVpnService)
            val hevUdpCopyBufferNums = VpnSettingsManager.getHevUdpCopyBufferNums(this@SiVpnService)
            val hevMaxSessionCount = VpnSettingsManager.getHevMaxSessionCount(this@SiVpnService)
            val hevConnectTimeout = VpnSettingsManager.getHevConnectTimeout(this@SiVpnService)
            val hevTcpReadWriteTimeout = VpnSettingsManager.getHevTcpReadWriteTimeout(this@SiVpnService)
            val hevUdpReadWriteTimeout = VpnSettingsManager.getHevUdpReadWriteTimeout(this@SiVpnService)
            val hevLogFile = VpnSettingsManager.getHevLogFile(this@SiVpnService)
            val hevLogLevel = VpnSettingsManager.getHevLogLevel(this@SiVpnService)

            val udpgwServer = VpnSettingsManager.getUdpgw(this@SiVpnService)
            val socks5UdpLineStyle = if (hevSocks5Udp == "udpgw" && udpgwServer.isNotBlank()) {
                val (host, port) = parseHostAndPort(udpgwServer, 7300)
                "  udp: 'udpgw'\n  udpgw-address: $host\n  udpgw-port: $port"
            } else {
                "  udp: '$hevSocks5Udp'"
            }

            // Konfigurasi sesuai instruksi untuk hev-socks5-tunnel
            val configContent = """
tunnel:
  mtu: $hevMtu
  icmp: 'reply'
  multi-queue: $hevMultiQueue
  ipv4: $hevIpv4
  ipv6: '$hevIpv6'

dns:
  port: $hevDnsPort
  address: $hevDnsAddress

socks5:
  port: $hevSocks5Port
  address: $hevSocks5Address
$socks5UdpLineStyle

misc:
  task-stack-size: $hevTaskStackSize
  tcp-buffer-size: $hevTcpBufferSize
  udp-recv-buffer-size: $hevUdpRecvBufferSize
  udp-copy-buffer-nums: $hevUdpCopyBufferNums
  max-session-count: $hevMaxSessionCount
  connect-timeout: $hevConnectTimeout
  tcp-read-write-timeout: $hevTcpReadWriteTimeout
  udp-read-write-timeout: $hevUdpReadWriteTimeout
  log-file: $hevLogFile
  log-level: $hevLogLevel
""".trimIndent()
            
            val configFile = File(filesDir, "hev_config.yml")
            configFile.writeText(configContent)

            // Instead of executing an extracted binary, we call JNI bridge natively
            LogManager.addLog("Starting HevSocks5Tunnel natively via JNI...")
            
            // Native call blocks until tunnel dies or quit() is called
            try {
                com.sivpn.cepat.TProxyService.TProxyStartService(configFile.absolutePath, fd)
            } catch (e: UnsatisfiedLinkError) {
                LogManager.addLog("Native library hev-socks5-tunnel not found! Make sure .so files are in jniLibs.")
                throw e
            }
        } catch (e: Exception) {
            LogManager.addLog("Execution Hev tunnel failed: ${e.message}")
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
