package com.sivpn.cepat

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.TextStyle
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.sivpn.cepat.ui.theme.MyApplicationTheme
import com.sivpn.cepat.vpn.LogManager
import com.sivpn.cepat.vpn.SiVpnService
import com.sivpn.cepat.vpn.VpnSettingsManager
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.sivpn.cepat.vpn.JniLibHelper.loadDownloadedLibs(this)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableStateOf(VpnSettingsManager.getThemeMode(this)) }
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            MyApplicationTheme(darkTheme = darkTheme) {
                MainScreen(
                    onConnect = {
                        prepareAndStartVpn()
                    },
                    onDisconnect = {
                        stopService(Intent(this, SiVpnService::class.java))
                        LogManager.addLog("Disconnecting...")
                    },
                    themeMode = themeMode,
                    onThemeChange = { newMode ->
                        themeMode = newMode
                        VpnSettingsManager.setThemeMode(this, newMode)
                    }
                )
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            LogManager.addLog("[Notifikasi] Izin notifikasi diberikan")
        } else {
            LogManager.addLog("[Notifikasi] Izin notifikasi ditolak")
            Toast.makeText(this, "Izin notifikasi ditolak. Anda tidak akan melihat notifikasi status VPN.", Toast.LENGTH_SHORT).show()
        }
        continuePrepareAndStartVpn()
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            LogManager.addLog("VPN Permission denied")
            Toast.makeText(this, "Permisi VPN ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    private fun prepareAndStartVpn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                continuePrepareAndStartVpn()
            }
        } else {
            continuePrepareAndStartVpn()
        }
    }

    private fun continuePrepareAndStartVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, SiVpnService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    themeMode: Int,
    onThemeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val logs by LogManager.logs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Configuration local states
    var currentProfile by remember { mutableStateOf(VpnSettingsManager.getCurrentProfile(context)) }
    var sshHost by remember { mutableStateOf(VpnSettingsManager.getSshHost(context)) }
    var sshPort by remember { mutableStateOf(VpnSettingsManager.getSshPort(context).toString()) }
    var sshUsername by remember { mutableStateOf(VpnSettingsManager.getSshUsername(context)) }
    var sshPassword by remember { mutableStateOf(VpnSettingsManager.getSshPassword(context)) }
    var sshFullInput by remember { mutableStateOf("$sshHost:$sshPort@$sshUsername:$sshPassword") }
    var payload by remember { mutableStateOf(VpnSettingsManager.getPayload(context)) }
    var proxyHost by remember { mutableStateOf(VpnSettingsManager.getProxyHost(context)) }
    var proxyPort by remember { mutableStateOf(VpnSettingsManager.getProxyPort(context).toString()) }
    var proxyFullInput by remember { mutableStateOf(if (proxyHost.isEmpty()) "" else "$proxyHost:$proxyPort") }
    var sni by remember { mutableStateOf(VpnSettingsManager.getSni(context)) }
    var dns by remember { mutableStateOf(VpnSettingsManager.getDns(context)) }
    var udpgw by remember { mutableStateOf(VpnSettingsManager.getUdpgw(context)) }
    var autoPing by remember { mutableStateOf(VpnSettingsManager.getAutoPing(context)) }
    
    // HevSocks configuration States
    var hevMtu by remember { mutableStateOf(VpnSettingsManager.getHevMtu(context)) }
    var hevMultiQueue by remember { mutableStateOf(VpnSettingsManager.getHevMultiQueue(context)) }
    var hevIpv4 by remember { mutableStateOf(VpnSettingsManager.getHevIpv4(context)) }
    var hevIpv6 by remember { mutableStateOf(VpnSettingsManager.getHevIpv6(context)) }
    var hevDnsPort by remember { mutableStateOf(VpnSettingsManager.getHevDnsPort(context)) }
    var hevDnsAddress by remember { mutableStateOf(VpnSettingsManager.getHevDnsAddress(context)) }
    var hevSocks5Port by remember { mutableStateOf(VpnSettingsManager.getHevSocks5Port(context)) }
    var hevSocks5Address by remember { mutableStateOf(VpnSettingsManager.getHevSocks5Address(context)) }
    var hevSocks5Udp by remember { mutableStateOf(VpnSettingsManager.getHevSocks5Udp(context)) }
    var hevTaskStackSize by remember { mutableStateOf(VpnSettingsManager.getHevTaskStackSize(context)) }
    var hevTcpBufferSize by remember { mutableStateOf(VpnSettingsManager.getHevTcpBufferSize(context)) }
    var hevUdpRecvBufferSize by remember { mutableStateOf(VpnSettingsManager.getHevUdpRecvBufferSize(context)) }
    var hevUdpCopyBufferNums by remember { mutableStateOf(VpnSettingsManager.getHevUdpCopyBufferNums(context)) }
    var hevMaxSessionCount by remember { mutableStateOf(VpnSettingsManager.getHevMaxSessionCount(context)) }
    var hevConnectTimeout by remember { mutableStateOf(VpnSettingsManager.getHevConnectTimeout(context)) }
    var hevTcpReadWriteTimeout by remember { mutableStateOf(VpnSettingsManager.getHevTcpReadWriteTimeout(context)) }
    var hevUdpReadWriteTimeout by remember { mutableStateOf(VpnSettingsManager.getHevUdpReadWriteTimeout(context)) }
    var hevLogFile by remember { mutableStateOf(VpnSettingsManager.getHevLogFile(context)) }
    var hevLogLevel by remember { mutableStateOf(VpnSettingsManager.getHevLogLevel(context)) }
    var isHevSocksExpanded by remember { mutableStateOf(false) }

    var pingAddress by remember { mutableStateOf(VpnSettingsManager.getPingAddress(context)) }
    var splitTunnelingEnabled by remember { mutableStateOf(VpnSettingsManager.getSplitTunnelingEnabled(context)) }
    var appsFilterMode by remember { mutableStateOf(VpnSettingsManager.getAppsFilterMode(context)) }
    var bypassApps by remember { mutableStateOf(VpnSettingsManager.getBypassApps(context)) }
    var killSwitchEnabled by remember { mutableStateOf(VpnSettingsManager.getKillSwitchEnabled(context)) }
    var forcingTls by remember { mutableStateOf(VpnSettingsManager.getForcingTls(context)) }
    var speedometerEnabled by remember { mutableStateOf(VpnSettingsManager.getSpeedometerEnabled(context)) }
    var autoReconnectEnabled by remember { mutableStateOf(VpnSettingsManager.getAutoReconnectEnabled(context)) }
    var ipAutoRefreshEnabled by remember { mutableStateOf(VpnSettingsManager.getIpAutoRefreshEnabled(context)) }
    var ipAutoRefreshInterval by remember { mutableStateOf(VpnSettingsManager.getIpAutoRefreshInterval(context)) }
    var hotshareWakeLockEnabled by remember { mutableStateOf(VpnSettingsManager.getHotshareWakeLockEnabled(context)) }
    var vpnWakeLockEnabled by remember { mutableStateOf(VpnSettingsManager.getVpnWakeLockEnabled(context)) }
    var keepAliveInterval by remember { mutableStateOf(VpnSettingsManager.getKeepAliveInterval(context)) }
    
    // Log auto cleanup states
    var autoCleanLogsEnabled by remember { mutableStateOf(VpnSettingsManager.getAutoCleanLogsEnabled(context)) }
    var autoCleanInterval by remember { mutableStateOf(VpnSettingsManager.getAutoCleanInterval(context)) }
    var maxLogLines by remember { mutableStateOf(VpnSettingsManager.getMaxLogLines(context)) }
    var showKeepAliveDialog by remember { mutableStateOf(false) }

    var manualRefreshTrigger by remember { mutableStateOf(0) }
    var profileList by remember { mutableStateOf(VpnSettingsManager.getProfiles(context).toList()) }
    var isNativeSshLoadedState by remember { mutableStateOf(com.sivpn.cepat.vpn.NativeSshTunnel.isLibraryLoaded) }
    var isHevLoadedState by remember { mutableStateOf(com.sivpn.cepat.TProxyService.isLibraryLoaded) }
    var showJniDownloader by remember { mutableStateOf(false) }

    // Synchronize inputs dynamically when switching profile
    LaunchedEffect(currentProfile, manualRefreshTrigger) {
        sshHost = VpnSettingsManager.getSshHost(context)
        sshPort = VpnSettingsManager.getSshPort(context).toString()
        sshUsername = VpnSettingsManager.getSshUsername(context)
        sshPassword = VpnSettingsManager.getSshPassword(context)
        sshFullInput = "$sshHost:$sshPort@$sshUsername:$sshPassword"
        payload = VpnSettingsManager.getPayload(context)
        proxyHost = VpnSettingsManager.getProxyHost(context)
        proxyPort = VpnSettingsManager.getProxyPort(context).toString()
        proxyFullInput = if (proxyHost.isEmpty()) "" else "$proxyHost:$proxyPort"
        sni = VpnSettingsManager.getSni(context)
        dns = VpnSettingsManager.getDns(context)
        udpgw = VpnSettingsManager.getUdpgw(context)
        autoPing = VpnSettingsManager.getAutoPing(context)
        pingAddress = VpnSettingsManager.getPingAddress(context)
        splitTunnelingEnabled = VpnSettingsManager.getSplitTunnelingEnabled(context)
        appsFilterMode = VpnSettingsManager.getAppsFilterMode(context)
        bypassApps = VpnSettingsManager.getBypassApps(context)
        killSwitchEnabled = VpnSettingsManager.getKillSwitchEnabled(context)
        forcingTls = VpnSettingsManager.getForcingTls(context)
        speedometerEnabled = VpnSettingsManager.getSpeedometerEnabled(context)
        autoReconnectEnabled = VpnSettingsManager.getAutoReconnectEnabled(context)
        ipAutoRefreshEnabled = VpnSettingsManager.getIpAutoRefreshEnabled(context)
        ipAutoRefreshInterval = VpnSettingsManager.getIpAutoRefreshInterval(context)
        hotshareWakeLockEnabled = VpnSettingsManager.getHotshareWakeLockEnabled(context)
        vpnWakeLockEnabled = VpnSettingsManager.getVpnWakeLockEnabled(context)
        keepAliveInterval = VpnSettingsManager.getKeepAliveInterval(context)
        autoCleanLogsEnabled = VpnSettingsManager.getAutoCleanLogsEnabled(context)
        autoCleanInterval = VpnSettingsManager.getAutoCleanInterval(context)
        maxLogLines = VpnSettingsManager.getMaxLogLines(context)

        // HevSocks configuration state sync
        hevMtu = VpnSettingsManager.getHevMtu(context)
        hevMultiQueue = VpnSettingsManager.getHevMultiQueue(context)
        hevIpv4 = VpnSettingsManager.getHevIpv4(context)
        hevIpv6 = VpnSettingsManager.getHevIpv6(context)
        hevDnsPort = VpnSettingsManager.getHevDnsPort(context)
        hevDnsAddress = VpnSettingsManager.getHevDnsAddress(context)
        hevSocks5Port = VpnSettingsManager.getHevSocks5Port(context)
        hevSocks5Address = VpnSettingsManager.getHevSocks5Address(context)
        hevSocks5Udp = VpnSettingsManager.getHevSocks5Udp(context)
        hevTaskStackSize = VpnSettingsManager.getHevTaskStackSize(context)
        hevTcpBufferSize = VpnSettingsManager.getHevTcpBufferSize(context)
        hevUdpRecvBufferSize = VpnSettingsManager.getHevUdpRecvBufferSize(context)
        hevUdpCopyBufferNums = VpnSettingsManager.getHevUdpCopyBufferNums(context)
        hevMaxSessionCount = VpnSettingsManager.getHevMaxSessionCount(context)
        hevConnectTimeout = VpnSettingsManager.getHevConnectTimeout(context)
        hevTcpReadWriteTimeout = VpnSettingsManager.getHevTcpReadWriteTimeout(context)
        hevUdpReadWriteTimeout = VpnSettingsManager.getHevUdpReadWriteTimeout(context)
        hevLogFile = VpnSettingsManager.getHevLogFile(context)
        hevLogLevel = VpnSettingsManager.getHevLogLevel(context)
    }

    LaunchedEffect(Unit) {
        com.sivpn.cepat.vpn.SystemInfoHelper.logSystemInfo(context)
        LogManager.maxLogLines = VpnSettingsManager.getMaxLogLines(context)
    }

    // Dialog state controllers
    var showProfileDialog by remember { mutableStateOf(false) }
    var showPayloadDialog by remember { mutableStateOf(false) }
    var showTlsDialog by remember { mutableStateOf(false) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showLimitDialog by remember { mutableStateOf(false) }
    var showSplitTunnelingDialog by remember { mutableStateOf(false) }
    var showKillSwitchDialog by remember { mutableStateOf(false) }
    var showTetherDialog by remember { mutableStateOf(false) }

    // Dropdown menu state
    var showMenu by remember { mutableStateOf(false) }
    var showDnsDropdown by remember { mutableStateOf(false) }

    // VPN Active state polling
    var isVpnActive by remember { mutableStateOf(SiVpnService.isRunning) }
    var connectionState by remember { mutableStateOf(SiVpnService.connectionState) }
    var connectionStartTime by remember { mutableStateOf(SiVpnService.connectionStartTime) }
    var connectionLimitMinutes by remember { mutableStateOf(VpnSettingsManager.getConnectionLimitMinutes(context)) }
    var connectionLimitEnabled by remember { mutableStateOf(VpnSettingsManager.getConnectionLimitEnabled(context)) }
    var statusCardVisible by remember { mutableStateOf(VpnSettingsManager.getStatusCardVisible(context)) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var rxSpeedBytesSec by remember { mutableStateOf(0L) }
    var txSpeedBytesSec by remember { mutableStateOf(0L) }
    var isBatteryOptimized by remember { mutableStateOf(false) }
    var currentPublicIp by remember { mutableStateOf("") }

    LaunchedEffect(connectionState, sshHost, ipAutoRefreshEnabled, ipAutoRefreshInterval, manualRefreshTrigger) {
        if (connectionState == "CONNECTED") {
            currentPublicIp = "Memeriksa IP..."
            withContext(Dispatchers.IO) {
                // Wait briefly for VPN connection and routing tables to fully establish
                delay(1500)
                
                val fetchPublicIp = suspend {
                    var resolvedIp = ""
                    // Attempt to fetch outer VPN IP up to 3 times
                    for (retry in 1..3) {
                        if (connectionState != "CONNECTED") break
                        try {
                            val url = java.net.URL("https://api.ipify.org")
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.connectTimeout = 6000
                            connection.readTimeout = 6000
                            connection.useCaches = false
                            val ip = connection.inputStream.bufferedReader().use { it.readText().trim() }
                            if (ip.isNotEmpty() && ip.contains(".")) {
                                resolvedIp = ip
                                break
                            }
                        } catch (e: Exception) {
                            try {
                                val fallbackUrl = java.net.URL("https://ifconfig.me/ip")
                                val connFallback = fallbackUrl.openConnection() as java.net.HttpURLConnection
                                connFallback.connectTimeout = 6000
                                connFallback.readTimeout = 6000
                                connFallback.useCaches = false
                                val fallbackIp = connFallback.inputStream.bufferedReader().use { it.readText().trim() }
                                if (fallbackIp.isNotEmpty()) {
                                    resolvedIp = fallbackIp
                                    break
                                }
                            } catch (ex: Exception) {
                                try {
                                    val url2 = java.net.URL("https://ipv4.icanhazip.com")
                                    val conn2 = url2.openConnection() as java.net.HttpURLConnection
                                    conn2.connectTimeout = 6000
                                    conn2.readTimeout = 6000
                                    conn2.useCaches = false
                                    val ip2 = conn2.inputStream.bufferedReader().use { it.readText().trim() }
                                    if (ip2.isNotEmpty()) {
                                        resolvedIp = ip2
                                        break
                                    }
                                } catch (ey: Exception) {
                                    delay(2000)
                                }
                            }
                        }
                    }

                    if (resolvedIp.isEmpty()) {
                        try {
                            resolvedIp = java.net.InetAddress.getByName(sshHost).hostAddress
                        } catch (e: Exception) {
                            resolvedIp = sshHost
                        }
                    }
                    resolvedIp
                }

                // Initial fetch
                val firstIp = fetchPublicIp()
                withContext(Dispatchers.Main) {
                    currentPublicIp = firstIp
                }

                // Periodic loop while connected and auto refresh is enabled
                while (connectionState == "CONNECTED" && ipAutoRefreshEnabled) {
                    delay(ipAutoRefreshInterval * 1000L)
                    if (connectionState != "CONNECTED" || !ipAutoRefreshEnabled) break
                    val refreshedIp = fetchPublicIp()
                    withContext(Dispatchers.Main) {
                        currentPublicIp = refreshedIp
                    }
                }
            }
        } else {
            // Disconnected: resolve the domain of the server
            withContext(Dispatchers.IO) {
                var initialIp = ""
                try {
                    initialIp = java.net.InetAddress.getByName(sshHost).hostAddress
                } catch (e: Exception) {
                    initialIp = sshHost
                }
                withContext(Dispatchers.Main) {
                    currentPublicIp = initialIp
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri != null) {
                val configJson = VpnSettingsManager.exportConfigAsJson(context)
                scope.launch(Dispatchers.IO) {
                    var success = false
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(configJson.toByteArray(Charsets.UTF_8))
                            success = true
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Export failed", e)
                    }
                    withContext(Dispatchers.Main) {
                        if (success) {
                            LogManager.addLog("Berhasil mengekspor konfigurasi.")
                            Toast.makeText(context, "Konfigurasi berhasil diekspor!", Toast.LENGTH_SHORT).show()
                        } else {
                            LogManager.addLog("Gagal mengekspor konfigurasi.")
                            Toast.makeText(context, "Gagal mengekspor konfigurasi", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    var importedContent: String? = null
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val bytes = inputStream.readBytes()
                            importedContent = String(bytes, Charsets.UTF_8)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Import failed", e)
                    }
                    withContext(Dispatchers.Main) {
                        if (importedContent != null) {
                            val success = VpnSettingsManager.importConfigFromJson(context, importedContent!!)
                            if (success) {
                                val oldProfile = currentProfile
                                currentProfile = ""
                                currentProfile = oldProfile
                                profileList = VpnSettingsManager.getProfiles(context).toList()
                                manualRefreshTrigger++
                                LogManager.addLog("Berhasil mengimpor konfigurasi dari berkas.")
                                Toast.makeText(context, "Konfigurasi SIVPN berhasil diimpor!", Toast.LENGTH_SHORT).show()
                            } else {
                                LogManager.addLog("Gagal mengimpor: Berkas tidak valid.")
                                Toast.makeText(context, "Gagal mengimpor berkas: Format tidak valid", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Gagal membaca berkas konfigurasi", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        var lastRxBytes = 0L
        var lastTxBytes = 0L
        var lastTime = System.currentTimeMillis()

        while (true) {
            isVpnActive = SiVpnService.isRunning
            connectionState = SiVpnService.connectionState
            connectionStartTime = SiVpnService.connectionStartTime
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                isBatteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                isBatteryOptimized = false
            }
            
            if (isVpnActive && connectionState == "CONNECTED" && connectionStartTime > 0L) {
                elapsedSeconds = (System.currentTimeMillis() - connectionStartTime) / 1000

                if (speedometerEnabled) {
                    val uid = context.applicationInfo.uid
                    val currentRx = android.net.TrafficStats.getUidRxBytes(uid)
                    val currentTx = android.net.TrafficStats.getUidTxBytes(uid)
                    val currentTime = System.currentTimeMillis()

                    val timeDeltaSec = (currentTime - lastTime) / 1000.0
                    if (timeDeltaSec > 0.1) {
                        if (lastRxBytes > 0L && currentRx >= lastRxBytes) {
                            rxSpeedBytesSec = ((currentRx - lastRxBytes) / timeDeltaSec).toLong()
                        } else {
                            rxSpeedBytesSec = 0L
                        }

                        if (lastTxBytes > 0L && currentTx >= lastTxBytes) {
                            txSpeedBytesSec = ((currentTx - lastTxBytes) / timeDeltaSec).toLong()
                        } else {
                            txSpeedBytesSec = 0L
                        }
                    }

                    lastRxBytes = if (currentRx != android.net.TrafficStats.UNSUPPORTED.toLong()) currentRx else 0L
                    lastTxBytes = if (currentTx != android.net.TrafficStats.UNSUPPORTED.toLong()) currentTx else 0L
                    lastTime = currentTime
                } else {
                    rxSpeedBytesSec = 0L
                    txSpeedBytesSec = 0L
                    lastRxBytes = 0L
                    lastTxBytes = 0L
                }
            } else {
                elapsedSeconds = 0L
                rxSpeedBytesSec = 0L
                txSpeedBytesSec = 0L
                lastRxBytes = 0L
                lastTxBytes = 0L
                lastTime = System.currentTimeMillis()
            }
            delay(500)
        }
    }

    var currentPingMs by remember { mutableStateOf(-1L) }
    var isPinging by remember { mutableStateOf(false) }

    LaunchedEffect(sshHost, sshPort, pingAddress, connectionState) {
        if (connectionState == "CONNECTED") {
            while (true) {
                isPinging = true
                val target = pingAddress.trim()
                val (host, port) = if (target.isNotEmpty()) {
                    if (target.contains(":")) {
                        val parts = target.split(":")
                        val h = parts[0].trim()
                        val p = parts.getOrNull(1)?.toIntOrNull() ?: 80
                        Pair(h, p)
                    } else {
                        Pair(target, 80)
                    }
                } else {
                    Pair(sshHost, sshPort.toIntOrNull() ?: 80)
                }
                val latency = com.sivpn.cepat.vpn.PingUtility.measureLatency(host, port)
                currentPingMs = latency
                isPinging = false
                delay(3000)
            }
        } else {
            currentPingMs = -1L
            isPinging = false
        }
    }

    // Gradient background: White -> SkyBlue (matching gradient in shape XML)
    val vpnGradientBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFFFFFFFF),
            0.5f to Color(0xFFFFFFFF),
            1.0f to Color(0xFFE0F2FE), // very subtle light sky blue center
            1.0f to Color(0xFF87CEEB)  // vibrant sky blue bottom
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vpnGradientBrush)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "SIVPN Cepat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF1E293B)
                            )
                            val ipLabel = if (currentPublicIp.isNotEmpty()) currentPublicIp else sshHost
                            Row(
                                modifier = Modifier.clickable {
                                    manualRefreshTrigger++
                                    Toast.makeText(context, "Memperbarui IP Publik...", Toast.LENGTH_SHORT).show()
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isVpnActive) {
                                        if (connectionState == "CONNECTED") "Connected (IP: $ipLabel)" else "Connecting... (IP: $ipLabel)"
                                    } else {
                                        ipLabel
                                    },
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh IP Publik",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showSplitTunnelingDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "Bypass Aplikasi",
                                tint = if (splitTunnelingEnabled) Color(0xFFF59E0B) else Color(0xFF334155)
                            )
                        }
                        IconButton(onClick = {
                            showKillSwitchDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Kill Switch",
                                tint = if (killSwitchEnabled) Color(0xFFEF4444) else Color(0xFF334155)
                            )
                        }
                        IconButton(onClick = { showTetherDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = "Hotshare / Tethering",
                                tint = Color(0xFF334155)
                            )
                        }
                        IconButton(onClick = { showLogDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Terminal Logs",
                                tint = Color(0xFF334155)
                            )
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = Color(0xFF334155)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Profile") },
                                onClick = {
                                    showMenu = false
                                    showAddProfileDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Current Profile") },
                                onClick = {
                                    showMenu = false
                                    if (profileList.size > 1) {
                                        VpnSettingsManager.removeProfile(context, currentProfile)
                                        profileList = VpnSettingsManager.getProfiles(context).toList()
                                        currentProfile = VpnSettingsManager.getCurrentProfile(context)
                                        LogManager.addLog("Profile $currentProfile deleted.")
                                    } else {
                                        Toast.makeText(context, "Cannot delete last remaining profile!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hotspot Root") },
                                onClick = {
                                    showMenu = false
                                    showTetherDialog = true
                                }
                            )
                            Divider(color = Color(0x3394A3B8), thickness = 1.dp)
                            DropdownMenuItem(
                                text = {
                                    val themeText = when (themeMode) {
                                        1 -> "Mode Tema: Terang"
                                        2 -> "Mode Tema: Gelap"
                                        else -> "Mode Tema: Sistem"
                                    }
                                    Text(themeText)
                                },
                                onClick = {
                                    val nextMode = (themeMode + 1) % 3
                                    onThemeChange(nextMode)
                                }
                            )
                            Divider(color = Color(0x3394A3B8), thickness = 1.dp)

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Speedometer", fontSize = 14.sp)
                                        Switch(
                                            checked = speedometerEnabled,
                                            onCheckedChange = { active ->
                                                showMenu = false
                                                speedometerEnabled = active
                                                VpnSettingsManager.setSpeedometerEnabled(context, active)
                                                LogManager.addLog("Speedometer Real-time diaktifkan: $active")
                                                Toast.makeText(context, "Speedometer: ${if (active) "Aktif" else "Nonaktif"}", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color(0xFF3B82F6),
                                                checkedTrackColor = Color(0xFF93C5FD)
                                            ),
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    }
                                },
                                onClick = {
                                    val nextState = !speedometerEnabled
                                    speedometerEnabled = nextState
                                    VpnSettingsManager.setSpeedometerEnabled(context, nextState)
                                    LogManager.addLog("Speedometer Real-time diaktifkan: $nextState")
                                    Toast.makeText(context, "Speedometer: ${if (nextState) "Aktif" else "Nonaktif"}", Toast.LENGTH_SHORT).show()
                                    showMenu = false
                                }
                            )
                            Divider(color = Color(0x3394A3B8), thickness = 1.dp)
                            DropdownMenuItem(
                                text = { Text("Import Config (.sivpn)") },
                                onClick = {
                                    showMenu = false
                                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import dari Clipboard") },
                                onClick = {
                                    showMenu = false
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val currentData = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    if (currentData.isNotEmpty()) {
                                        val success = VpnSettingsManager.importConfigFromJson(context, currentData)
                                        if (success) {
                                            val oldProfile = currentProfile
                                            currentProfile = ""
                                            currentProfile = oldProfile
                                            
                                            // sync state via manualRefreshTrigger and explicit definitions
                                            profileList = VpnSettingsManager.getProfiles(context).toList()
                                            manualRefreshTrigger++
                                            
                                            sshHost = VpnSettingsManager.getSshHost(context)
                                            sshPort = VpnSettingsManager.getSshPort(context).toString()
                                            sshUsername = VpnSettingsManager.getSshUsername(context)
                                            sshPassword = VpnSettingsManager.getSshPassword(context)
                                            payload = VpnSettingsManager.getPayload(context)
                                            proxyHost = VpnSettingsManager.getProxyHost(context)
                                            proxyPort = VpnSettingsManager.getProxyPort(context).toString()
                                            proxyFullInput = if (proxyHost.isEmpty()) "" else "$proxyHost:$proxyPort"
                                            sni = VpnSettingsManager.getSni(context)
                                            dns = VpnSettingsManager.getDns(context)
                                            udpgw = VpnSettingsManager.getUdpgw(context)
                                            autoPing = VpnSettingsManager.getAutoPing(context)
                                            pingAddress = VpnSettingsManager.getPingAddress(context)
                                            
                                            LogManager.addLog("Berhasil mengimpor dari clipboard.")
                                            Toast.makeText(context, "Konfigurasi berhasil diimpor!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            LogManager.addLog("Gagal mengimpor dari clipboard. Format tidak valid.")
                                            Toast.makeText(context, "Gagal mengimpor, format tidak valid", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Config (.sivpn)") },
                                onClick = {
                                    showMenu = false
                                    exportLauncher.launch("${currentProfile}.sivpn")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export ke Clipboard") },
                                onClick = {
                                    showMenu = false
                                    val configContent = VpnSettingsManager.exportConfigAsJson(context)
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("SIVPN Config", configContent)
                                    clipboardManager.setPrimaryClip(clip)
                                    LogManager.addLog("Konfigurasi disalin ke clipboard.")
                                    Toast.makeText(context, "Konfigurasi berhasil disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color(0xFF1E293B)
                    )
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                // Config sections vertical list
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    // JNI Native Library Presence Warning Card
                    if (!isNativeSshLoadedState || !isHevLoadedState) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFEE2E2),
                                contentColor = Color(0xFF991B1B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "JNI Missed Warning",
                                    tint = Color(0xFFEF4444)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "LIBRARY JNI NATIVE TIDAK DITEMUKAN",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF991B1B)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val missingList = mutableListOf<String>()
                                    if (!isNativeSshLoadedState) missingList.add("libssh.so")
                                    if (!isHevLoadedState) missingList.add("libhev-socks5-tunnel.so")
                                    Text(
                                        text = "Library native berikut gagal dimuat: ${missingList.joinToString(", ")}. Pasang secara online di bawah ini agar koneksi VPN & transparan tunnel dapat berjalan stabil tanpa ANR.",
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = Color(0xFF7F1D1D)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { showJniDownloader = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFDC2626),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Unduh",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "UNDUH JNI ONLINE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 1: CONTROLLER
                    SectionHeader("CONTROLLER")

                    // Start/Stop Card
                    VpnItemCard(
                        icon = Icons.Default.PowerSettingsNew,
                        iconColor = if (isVpnActive) Color(0xFF10B981) else Color(0xFF3B82F6),
                        title = "Mulai Koneksi",
                        subtitle = if (isVpnActive) "VPN terhubung" else "VPN terputus",
                        onClick = {
                            if (isVpnActive) onDisconnect() else onConnect()
                        }
                    ) {
                        Switch(
                            checked = isVpnActive,
                            onCheckedChange = { active ->
                                if (active) onConnect() else onDisconnect()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF3B82F6),
                                checkedTrackColor = Color(0xFF93C5FD)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status & Timer Dashboard Card
                    if (statusCardVisible) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when (connectionState) {
                                    "CONNECTED" -> Color(0xFFECFDF5)
                                    "CONNECTING" -> Color(0xFFFFFBEB)
                                    else -> Color(0xE6FFFFFF)
                                }
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = when (connectionState) {
                                    "CONNECTED" -> Color(0xFF10B981)
                                    "CONNECTING" -> Color(0xFFF59E0B)
                                    else -> Color(0xFFE2E8F0)
                                }
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (connectionState != "DISCONNECTED") 3.dp else 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(
                                                    color = when (connectionState) {
                                                        "CONNECTED" -> Color(0xFF10B981)
                                                        "CONNECTING" -> Color(0xFFF59E0B)
                                                        else -> Color(0xFF94A3B8)
                                                    },
                                                    shape = CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "STATUS KONEKSI",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    color = when (connectionState) {
                                                        "CONNECTED" -> Color(0xFF10B981).copy(alpha = 0.15f)
                                                        "CONNECTING" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                                        else -> Color(0xFFF1F5F9)
                                                    }
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = when (connectionState) {
                                                    "CONNECTED" -> "TERHUBUNG"
                                                    "CONNECTING" -> "MENYAMBUNGKAN"
                                                    else -> "TERPUTUS"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (connectionState) {
                                                    "CONNECTED" -> Color(0xFF047857)
                                                    "CONNECTING" -> Color(0xFFB45309)
                                                    else -> Color(0xFF64748B)
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                statusCardVisible = false
                                                VpnSettingsManager.setStatusCardVisible(context, false)
                                                LogManager.addLog("Dashboard status ditutup.")
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Tutup status",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Column 1: Durasi Aktif
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Durasi Aktif",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            text = formatDuration(elapsedSeconds),
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = when (connectionState) {
                                                "CONNECTED" -> Color(0xFF065F46)
                                                else -> Color(0xFF1E293B)
                                            }
                                        )
                                    }

                                    // Column 2: Centered Latency / Ping Indicator
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Latency Tunnel",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            if (isPinging && connectionState == "CONNECTED") {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(11.dp),
                                                    strokeWidth = 1.5.dp,
                                                    color = Color(0xFF3B82F6)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = when {
                                                    connectionState != "CONNECTED" -> "--"
                                                    currentPingMs < 0 -> "..."
                                                    else -> "$currentPingMs ms"
                                                },
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = when {
                                                    connectionState != "CONNECTED" -> Color(0xFF94A3B8)
                                                    currentPingMs in 1..150 -> Color(0xFF10B981)
                                                    currentPingMs in 151..300 -> Color(0xFFF59E0B)
                                                    currentPingMs > 300 -> Color(0xFFEF4444)
                                                    else -> Color(0xFF64748B)
                                                }
                                            )
                                        }
                                    }

                                    // Column 3: Sisa Waktu (visible only if connected & limit enabled)
                                    if (connectionState == "CONNECTED" && connectionLimitEnabled && connectionLimitMinutes > 0) {
                                        val totalLimitSeconds = connectionLimitMinutes * 60L
                                        val remainingSeconds = maxOf(0L, totalLimitSeconds - elapsedSeconds)
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = "Sisa Waktu",
                                                fontSize = 11.sp,
                                                color = Color(0xFFB45309)
                                            )
                                            Text(
                                                text = formatDuration(remainingSeconds),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF9a3412)
                                            )
                                        }
                                    } else {
                                        // Blank weight placeholder to guarantee Column 2 is perfectly centered at all times
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }

                                if (speedometerEnabled) {
                                    Divider(
                                        color = Color(0x1A94A3B8),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Download Speed Column
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(Color(0x1110B981), shape = RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "Download",
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "DOWNLOAD",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF64748B),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = formatSpeed(rxSpeedBytesSec),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFF1E293B)
                                                )
                                            }
                                        }

                                        // Upload Speed Column
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "UPLOAD",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF64748B),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = formatSpeed(txSpeedBytesSec),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFF1E293B)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(Color(0x113B82F6), shape = RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowUpward,
                                                    contentDescription = "Upload",
                                                    tint = Color(0xFF3B82F6),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Profile Card
                    VpnItemCard(
                        icon = Icons.Default.Person,
                        iconColor = Color(0xFF1E3A8A),
                        title = "Profile",
                        subtitle = currentProfile,
                        onClick = { showProfileDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Profile",
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Connection Limit Card (Pengaturan Waktu Koneksi)
                    VpnItemCard(
                        icon = Icons.Default.Timer,
                        iconColor = Color(0xFFD97706),
                        title = "Pengaturan Waktu",
                        subtitle = if (connectionLimitEnabled) "Batas durasi: ${getLimitLabel(connectionLimitMinutes)}" else "Batas durasi: Dinonaktifkan",
                        onClick = { showLimitDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Time Limit Menu",
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SECTION 2: CONNECTION
                    SectionHeader("CONNECTION")

                    // 1. SSH Settings Card (Direct input)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xE6FFFFFF)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFFEF3C7), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VpnKey,
                                        contentDescription = "SSH Settings",
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "SSH (host:port@username:password)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            OutlinedTextField(
                                value = sshFullInput,
                                onValueChange = { newValue ->
                                    sshFullInput = newValue
                                    // Parse newValue
                                    val atParts = newValue.split('@')
                                    if (atParts.size >= 2) {
                                        val firstPart = atParts[0]
                                        val secondPart = atParts.subList(1, atParts.size).joinToString("@")
                                        
                                        // host:port
                                        val colonIdx = firstPart.lastIndexOf(':')
                                        if (colonIdx != -1) {
                                            val host = firstPart.substring(0, colonIdx).trim()
                                            val portStr = firstPart.substring(colonIdx + 1).trim()
                                            if (host.isNotEmpty()) {
                                                sshHost = host
                                                VpnSettingsManager.setSshHost(context, host)
                                            }
                                            val portInt = portStr.toIntOrNull()
                                            if (portInt != null) {
                                                sshPort = portStr
                                                VpnSettingsManager.setSshPort(context, portInt)
                                            }
                                        } else {
                                            val host = firstPart.trim()
                                            if (host.isNotEmpty()) {
                                                sshHost = host
                                                VpnSettingsManager.setSshHost(context, host)
                                            }
                                        }
                                        
                                        // username:password
                                        val userColonIdx = secondPart.indexOf(':')
                                        if (userColonIdx != -1) {
                                            val user = secondPart.substring(0, userColonIdx).trim()
                                            val pass = secondPart.substring(userColonIdx + 1)
                                            if (user.isNotEmpty()) {
                                                sshUsername = user
                                                VpnSettingsManager.setSshUsername(context, user)
                                            }
                                            sshPassword = pass
                                            VpnSettingsManager.setSshPassword(context, pass)
                                        } else {
                                            val user = secondPart.trim()
                                            if (user.isNotEmpty()) {
                                                sshUsername = user
                                                VpnSettingsManager.setSshUsername(context, user)
                                            }
                                        }
                                    } else {
                                        // No '@', treat as host or host:port
                                        val colonIdx = newValue.lastIndexOf(':')
                                        if (colonIdx != -1) {
                                            val host = newValue.substring(0, colonIdx).trim()
                                            val portStr = newValue.substring(colonIdx + 1).trim()
                                            if (host.isNotEmpty()) {
                                                sshHost = host
                                                VpnSettingsManager.setSshHost(context, host)
                                            }
                                            val portInt = portStr.toIntOrNull()
                                            if (portInt != null) {
                                                sshPort = portStr
                                                VpnSettingsManager.setSshPort(context, portInt)
                                            }
                                        } else {
                                            val host = newValue.trim()
                                            if (host.isNotEmpty()) {
                                                sshHost = host
                                                VpnSettingsManager.setSshHost(context, host)
                                            }
                                        }
                                    }
                                },
                                placeholder = { Text("yu.xhmt.web.id:80@Trial37934:1", fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedTextColor = Color(0xFF1E293B),
                                    unfocusedTextColor = Color(0xFF1E293B),
                                    focusedLabelColor = Color(0xFF3B82F6),
                                    unfocusedLabelColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Payload Configuration
                    VpnItemCard(
                        icon = Icons.Default.Edit,
                        iconColor = Color(0xFF3B82F6),
                        title = "Payload",
                        subtitle = if (payload.length > 50) payload.take(45) + "..." else payload,
                        onClick = { showPayloadDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Edit Payload",
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Proxy Connection Config Card (Direct input)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xE6FFFFFF)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFE0E7FF), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Domain,
                                        contentDescription = "Proxy Server",
                                        tint = Color(0xFF4F46E5),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Proxy Settings (host:port)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = proxyFullInput,
                                onValueChange = { newValue ->
                                    proxyFullInput = newValue
                                    val colonIdx = newValue.lastIndexOf(':')
                                    if (colonIdx != -1) {
                                        val host = newValue.substring(0, colonIdx).trim()
                                        val portStr = newValue.substring(colonIdx + 1).trim()
                                        if (host.isNotEmpty()) {
                                            proxyHost = host
                                            VpnSettingsManager.setProxyHost(context, host)
                                        }
                                        val portInt = portStr.toIntOrNull()
                                        if (portInt != null) {
                                            proxyPort = portStr
                                            VpnSettingsManager.setProxyPort(context, portInt)
                                        }
                                    } else {
                                        val host = newValue.trim()
                                        if (host.isNotEmpty()) {
                                            proxyHost = host
                                            VpnSettingsManager.setProxyHost(context, host)
                                        }
                                    }
                                },
                                placeholder = { Text("proxy.com:80", fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedTextColor = Color(0xFF1E293B),
                                    unfocusedTextColor = Color(0xFF1E293B),
                                    focusedLabelColor = Color(0xFF3B82F6),
                                    unfocusedLabelColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. Server name indication (SNI) Card (Direct input)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xE6FFFFFF)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFFCE7F3), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "SNI Server",
                                        tint = Color(0xFFDB2777),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Server Name Indication (SNI)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = sni,
                                onValueChange = {
                                    sni = it
                                    VpnSettingsManager.setSni(context, it)
                                },
                                label = { Text("SNI Hostname / Address", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedTextColor = Color(0xFF1E293B),
                                    unfocusedTextColor = Color(0xFF1E293B),
                                    focusedLabelColor = Color(0xFF3B82F6),
                                    unfocusedLabelColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SECTION 3: SETTINGS
                    SectionHeader("SETTINGS")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        VpnItemCard(
                            icon = Icons.Default.BatteryStd,
                            iconColor = if (isBatteryOptimized) Color(0xFFEF4444) else Color(0xFF10B981),
                            title = "Optimasi Baterai",
                            subtitle = if (isBatteryOptimized) "Terbatas (Beresiko Terputus)" else "Aman (Latar Belakang Lancar)",
                            onClick = {
                                if (isBatteryOptimized) {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        intent.data = Uri.parse("package:${context.packageName}")
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        ) {
                            if (isBatteryOptimized) {
                                Text(
                                    text = "Bypass",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color(0xFFEF4444), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Aman",
                                    tint = Color(0xFF10B981)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        VpnItemCard(
                            icon = Icons.Default.FlashOn,
                            iconColor = if (vpnWakeLockEnabled) Color(0xFFF59E0B) else Color(0xFF64748B),
                            title = "SIVPN CPU WakeLock",
                            subtitle = if (vpnWakeLockEnabled) "Aktif (Koneksi Lebih Stabil)" else "Nonaktif (Sangat Hemat Baterai)",
                            onClick = {
                                val newVal = !vpnWakeLockEnabled
                                vpnWakeLockEnabled = newVal
                                VpnSettingsManager.setVpnWakeLockEnabled(context, newVal)
                                LogManager.addLog("VPN WakeLock diubah ke: $newVal")
                            }
                        ) {
                            Switch(
                                checked = vpnWakeLockEnabled,
                                onCheckedChange = { active ->
                                    vpnWakeLockEnabled = active
                                    VpnSettingsManager.setVpnWakeLockEnabled(context, active)
                                    LogManager.addLog("VPN WakeLock diubah ke: $active")
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF3B82F6),
                                    uncheckedThumbColor = Color(0xFFCBD5E1),
                                    uncheckedTrackColor = Color(0xFFE2E8F0)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        VpnItemCard(
                            icon = Icons.Default.Timer,
                            iconColor = Color(0xFF6366F1),
                            title = "Interval Keep-Alive (Ping)",
                            subtitle = when (keepAliveInterval) {
                                10 -> "10 Detik (Sangat Agresif)"
                                30 -> "30 Detik (Rekomendasi)"
                                60 -> "1 Menit (Hemat Baterai)"
                                120 -> "2 Menit (Sangat Hemat)"
                                300 -> "5 Menit (Ekstrem Hemat)"
                                else -> "$keepAliveInterval Detik"
                            },
                            onClick = { showKeepAliveDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Ubah Interval Ping",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 5. Auto Reconnect
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val newVal = !autoReconnectEnabled
                            autoReconnectEnabled = newVal
                            VpnSettingsManager.setAutoReconnectEnabled(context, newVal)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xE6FFFFFF)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(if (autoReconnectEnabled) Color(0xFFD1FAE5) else Color(0xFFFEE2E2), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Auto Reconnect",
                                        tint = if (autoReconnectEnabled) Color(0xFF059669) else Color(0xFFDC2626),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Auto Reconnect",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = if (autoReconnectEnabled) "Otomatis menyambung saat terputus" else "Nonaktif",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                            Switch(
                                checked = autoReconnectEnabled,
                                onCheckedChange = {
                                    autoReconnectEnabled = it
                                    VpnSettingsManager.setAutoReconnectEnabled(context, it)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // 5b. Auto Refresh IP Publik Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xE6FFFFFF)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(if (ipAutoRefreshEnabled) Color(0xFFDBEAFE) else Color(0xFFF3F4F6), shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Auto Refresh IP",
                                            tint = if (ipAutoRefreshEnabled) Color(0xFF1D4ED8) else Color(0xFF6B7280),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Auto Refresh IP Publik",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = if (ipAutoRefreshEnabled) "Refreshed setiap ${ipAutoRefreshInterval}s" else "Nonaktif",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                                Switch(
                                    checked = ipAutoRefreshEnabled,
                                    onCheckedChange = {
                                        ipAutoRefreshEnabled = it
                                        VpnSettingsManager.setIpAutoRefreshEnabled(context, it)
                                    }
                                )
                            }
                            if (ipAutoRefreshEnabled) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Atur Interval Refresh:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(5, 10, 15, 30, 60).forEach { interval ->
                                        val selected = ipAutoRefreshInterval == interval
                                        Button(
                                            onClick = {
                                                ipAutoRefreshInterval = interval
                                                VpnSettingsManager.setIpAutoRefreshInterval(context, interval)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selected) Color(0xFF1D4ED8) else Color(0xFFF1F5F9),
                                                contentColor = if (selected) Color.White else Color(0xFF475569)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(28.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("${interval}s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // 6. DNS Settings Card (Direct input)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xE6FFFFFF)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFCCFBF1), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = "DNS",
                                        tint = Color(0xFF0F766E),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "DNS Settings (Primary : Secondary)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Box {
                                OutlinedTextField(
                                    value = dns,
                                    onValueChange = {
                                        dns = it
                                        VpnSettingsManager.setDns(context, it)
                                    },
                                    label = { Text("DNS (Format: 94.140.14.14:94.140.15.15)", fontSize = 11.sp) },
                                    placeholder = { Text("Default: 94.140.14.14:94.140.15.15") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF3B82F6),
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedTextColor = Color(0xFF1E293B),
                                        unfocusedTextColor = Color(0xFF1E293B),
                                        focusedLabelColor = Color(0xFF3B82F6),
                                        unfocusedLabelColor = Color(0xFF64748B)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B)),
                                    trailingIcon = {
                                        IconButton(onClick = { showDnsDropdown = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih DNS")
                                        }
                                    }
                                )
                                DropdownMenu(
                                    expanded = showDnsDropdown,
                                    onDismissRequest = { showDnsDropdown = false }
                                ) {
                                    val dnsOptions = listOf(
                                        Pair("Google DNS", "8.8.8.8:8.8.4.4"),
                                        Pair("Cloudflare (1.1.1.1)", "1.1.1.1:1.0.0.1"),
                                        Pair("AdGuard DNS (Blokir Iklan)", "94.140.14.14:94.140.15.15"),
                                        Pair("Quad9 (Malware Block)", "9.9.9.9:149.112.112.112"),
                                        Pair("OpenDNS", "208.67.222.222:208.67.220.220"),
                                        Pair("ControlD (Uncensored)", "76.76.2.0:76.76.10.0")
                                    )
                                    dnsOptions.forEach { dnsOption ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(text = dnsOption.first, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text(text = dnsOption.second, fontSize = 12.sp, color = Color.Gray)
                                                }
                                            },
                                            onClick = {
                                                dns = dnsOption.second
                                                VpnSettingsManager.setDns(context, dnsOption.second)
                                                showDnsDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 6. UDPGW Settings Card (Direct input)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xE6FFFFFF)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFEDE9FE), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BarChart,
                                        contentDescription = "UDPGW Settings",
                                        tint = Color(0xFF6D28D9),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "UDPGW address:port",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = udpgw,
                                onValueChange = {
                                    udpgw = it
                                    VpnSettingsManager.setUdpgw(context, it)
                                },
                                label = { Text("UDPGW (Format: IP:Port)", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedTextColor = Color(0xFF1E293B),
                                    unfocusedTextColor = Color(0xFF1E293B),
                                    focusedLabelColor = Color(0xFF3B82F6),
                                    unfocusedLabelColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                             )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 6B. HevSocks Config Expandable Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xE6FFFFFF)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isHevSocksExpanded = !isHevSocksExpanded }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFEDE9FE), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "HevSocks Config",
                                        tint = Color(0xFF6D28D9),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1.0f)) {
                                    Text(
                                        text = "HevSocks Config",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = "Pengaturan dari hev-socks5-tunnel",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Icon(
                                    imageVector = if (isHevSocksExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Expand",
                                    tint = Color(0xFF64748B)
                                )
                            }

                            if (isHevSocksExpanded) {
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Reset to default button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sesuaikan Parameter Native",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    TextButton(
                                        onClick = {
                                            hevMtu = 8500
                                            hevMultiQueue = false
                                            hevIpv4 = "198.18.0.1"
                                            hevIpv6 = "fc00::1"
                                            hevDnsPort = 53
                                            hevDnsAddress = "94.140.14.14"
                                            hevSocks5Port = 1080
                                            hevSocks5Address = "127.0.0.1"
                                            hevSocks5Udp = "udp"
                                            hevTaskStackSize = 86016
                                            hevTcpBufferSize = 65536
                                            hevUdpRecvBufferSize = 524288
                                            hevUdpCopyBufferNums = 10
                                            hevMaxSessionCount = 0
                                            hevConnectTimeout = 10000
                                            hevTcpReadWriteTimeout = 300000
                                            hevUdpReadWriteTimeout = 60000
                                            hevLogFile = "stderr"
                                            hevLogLevel = "warn"

                                            VpnSettingsManager.setHevMtu(context, 8500)
                                            VpnSettingsManager.setHevMultiQueue(context, false)
                                            VpnSettingsManager.setHevIpv4(context, "198.18.0.1")
                                            VpnSettingsManager.setHevIpv6(context, "fc00::1")
                                            VpnSettingsManager.setHevDnsPort(context, 53)
                                            VpnSettingsManager.setHevDnsAddress(context, "94.140.14.14")
                                            VpnSettingsManager.setHevSocks5Port(context, 1080)
                                            VpnSettingsManager.setHevSocks5Address(context, "127.0.0.1")
                                            VpnSettingsManager.setHevSocks5Udp(context, "udp")
                                            VpnSettingsManager.setHevTaskStackSize(context, 86016)
                                            VpnSettingsManager.setHevTcpBufferSize(context, 65536)
                                            VpnSettingsManager.setHevUdpRecvBufferSize(context, 524288)
                                            VpnSettingsManager.setHevUdpCopyBufferNums(context, 10)
                                            VpnSettingsManager.setHevMaxSessionCount(context, 0)
                                            VpnSettingsManager.setHevConnectTimeout(context, 10000)
                                            VpnSettingsManager.setHevTcpReadWriteTimeout(context, 300000)
                                            VpnSettingsManager.setHevUdpReadWriteTimeout(context, 60000)
                                            VpnSettingsManager.setHevLogFile(context, "stderr")
                                            VpnSettingsManager.setHevLogLevel(context, "warn")
                                            Toast.makeText(context, "Reset Ke Default Berhasil", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reset Default", fontSize = 11.sp, color = Color(0xFF3B82F6))
                                    }
                                }
                                
                                Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 8.dp))

                                // Group 1: Tunnel Configuration
                                Text("TUNNEL CONFIGURATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                OutlinedTextField(
                                    value = if (hevMtu == 0) "" else hevMtu.toString(),
                                    onValueChange = {
                                        val v = it.toIntOrNull() ?: 0
                                        hevMtu = v
                                        VpnSettingsManager.setHevMtu(context, v)
                                    },
                                    label = { Text("MTU (Interface MTU)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF3B82F6),
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedTextColor = Color(0xFF1E293B)
                                    ),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Multi-Queue", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                                        Text("Wajib false di Android agar stabil", fontSize = 11.sp, color = Color(0xFF64748B))
                                    }
                                    Switch(
                                        checked = hevMultiQueue,
                                        onCheckedChange = {
                                            hevMultiQueue = it
                                            VpnSettingsManager.setHevMultiQueue(context, it)
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = hevIpv4,
                                        onValueChange = {
                                            hevIpv4 = it
                                            VpnSettingsManager.setHevIpv4(context, it)
                                        },
                                        label = { Text("IPv4 Address", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.0f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1),
                                            focusedTextColor = Color(0xFF1E293B)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = hevIpv6,
                                        onValueChange = {
                                            hevIpv6 = it
                                            VpnSettingsManager.setHevIpv6(context, it)
                                        },
                                        label = { Text("IPv6 Address", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.0f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1),
                                            focusedTextColor = Color(0xFF1E293B)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                                    )
                                }

                                Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 12.dp))

                                // Group 2: DNS Upstream
                                Text("DNS UPSTREAM (ANTI-IKLAN)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = hevDnsAddress,
                                        onValueChange = {
                                            hevDnsAddress = it
                                            VpnSettingsManager.setHevDnsAddress(context, it)
                                        },
                                        label = { Text("DNS Address", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.3f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1),
                                            focusedTextColor = Color(0xFF1E293B)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = if (hevDnsPort == 0) "" else hevDnsPort.toString(),
                                        onValueChange = {
                                            val v = it.toIntOrNull() ?: 0
                                            hevDnsPort = v
                                            VpnSettingsManager.setHevDnsPort(context, v)
                                        },
                                        label = { Text("DNS Port", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.7f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1),
                                            focusedTextColor = Color(0xFF1E293B)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                                    )
                                }

                                Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 12.dp))

                                // Group 3: Socks5 Settings
                                Text("SOCKS5 SETTINGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = hevSocks5Address,
                                        onValueChange = {
                                            hevSocks5Address = it
                                            VpnSettingsManager.setHevSocks5Address(context, it)
                                        },
                                        label = { Text("Socks5 Host", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.2f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1),
                                            focusedTextColor = Color(0xFF1E293B)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = if (hevSocks5Port == 0) "" else hevSocks5Port.toString(),
                                        onValueChange = {
                                            val v = it.toIntOrNull() ?: 0
                                            hevSocks5Port = v
                                            VpnSettingsManager.setHevSocks5Port(context, v)
                                        },
                                        label = { Text("Socks5 Port", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.8f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1),
                                            focusedTextColor = Color(0xFF1E293B)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Socks5 UDP Mode
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("UDP Relay Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("udp", "udpgw", "none").forEach { mode ->
                                            val isSelected = (hevSocks5Udp == mode)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1.0f)
                                                    .background(
                                                        color = if (isSelected) Color(0xFF6D28D9) else Color(0xFFF1F5F9),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) Color(0xFF6D28D9) else Color(0xFFCBD5E1),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        hevSocks5Udp = mode
                                                        VpnSettingsManager.setHevSocks5Udp(context, mode)
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = mode,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color.White else Color(0xFF334155)
                                                )
                                            }
                                        }
                                    }
                                }

                                Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 12.dp))

                                // Group 4: Buffers & Resource Optimisation
                                Text("OPTIMIZATION & MISCELLANEOUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = if (hevTaskStackSize == 0) "" else hevTaskStackSize.toString(),
                                        onValueChange = {
                                            val v = it.toIntOrNull() ?: 0
                                            hevTaskStackSize = v
                                            VpnSettingsManager.setHevTaskStackSize(context, v)
                                        },
                                        label = { Text("Task Stack Size", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.0f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFF1E293B))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = if (hevTcpBufferSize == 0) "" else hevTcpBufferSize.toString(),
                                        onValueChange = {
                                            val v = it.toIntOrNull() ?: 0
                                            hevTcpBufferSize = v
                                            VpnSettingsManager.setHevTcpBufferSize(context, v)
                                        },
                                        label = { Text("TCP Buffer Size", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.0f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFF1E293B))
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = if (hevUdpRecvBufferSize == 0) "" else hevUdpRecvBufferSize.toString(),
                                        onValueChange = {
                                            val v = it.toIntOrNull() ?: 0
                                            hevUdpRecvBufferSize = v
                                            VpnSettingsManager.setHevUdpRecvBufferSize(context, v)
                                        },
                                        label = { Text("UDP Recv Buf Size", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.0f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFF1E293B))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = if (hevUdpCopyBufferNums == 0) "" else hevUdpCopyBufferNums.toString(),
                                        onValueChange = {
                                            val v = it.toIntOrNull() ?: 0
                                            hevUdpCopyBufferNums = v
                                            VpnSettingsManager.setHevUdpCopyBufferNums(context, v)
                                        },
                                        label = { Text("UDP Copy Buf Nums", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.0f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFF1E293B))
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = if (hevMaxSessionCount == 0) "0 (Unlimited)" else hevMaxSessionCount.toString(),
                                        onValueChange = {
                                            val filtered = it.replace("0 (Unlimited)", "0")
                                            val v = filtered.toIntOrNull() ?: 0
                                            hevMaxSessionCount = v
                                            VpnSettingsManager.setHevMaxSessionCount(context, v)
                                        },
                                        label = { Text("Max Session Count", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.0f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFF1E293B))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = if (hevConnectTimeout == 0) "" else hevConnectTimeout.toString(),
                                        onValueChange = {
                                            val v = it.toIntOrNull() ?: 0
                                            hevConnectTimeout = v
                                            VpnSettingsManager.setHevConnectTimeout(context, v)
                                        },
                                        label = { Text("Conn Timeout (ms)", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.0f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFF1E293B))
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = if (hevTcpReadWriteTimeout == 0) "" else hevTcpReadWriteTimeout.toString(),
                                        onValueChange = {
                                            val v = it.toIntOrNull() ?: 0
                                            hevTcpReadWriteTimeout = v
                                            VpnSettingsManager.setHevTcpReadWriteTimeout(context, v)
                                        },
                                        label = { Text("TCP RW Timeout (ms)", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.0f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color(0xFF1E293B))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = if (hevUdpReadWriteTimeout == 0) "" else hevUdpReadWriteTimeout.toString(),
                                        onValueChange = {
                                            val v = it.toIntOrNull() ?: 0
                                            hevUdpReadWriteTimeout = v
                                            VpnSettingsManager.setHevUdpReadWriteTimeout(context, v)
                                        },
                                        label = { Text("UDP RW Timeout (ms)", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.0f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color(0xFF1E293B))
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = hevLogFile,
                                        onValueChange = {
                                            hevLogFile = it
                                            VpnSettingsManager.setHevLogFile(context, it)
                                        },
                                        label = { Text("Log Target (e.g. stderr)", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.0f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFFCBD5E1)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFF1E293B))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    // Log Level dropdown/chips
                                    Column(modifier = Modifier.weight(1.0f)) {
                                        Text("Log Level", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                                            listOf("debug", "info", "warn", "error").forEach { lvl ->
                                                val isSelected = (hevLogLevel == lvl)
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1.0f)
                                                        .background(
                                                            color = if (isSelected) Color(0xFF3B82F6) else Color(0xFFF1F5F9),
                                                            shape = RoundedCornerShape(4.dp)
                                                        )
                                                        .clickable {
                                                            hevLogLevel = lvl
                                                            VpnSettingsManager.setHevLogLevel(context, lvl)
                                                        }
                                                        .padding(vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = lvl,
                                                        fontSize = 8.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) Color.White else Color(0xFF334155)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Auto ping check
                    VpnItemCard(
                        icon = Icons.Default.CompareArrows,
                        iconColor = Color(0xFFF43F5E),
                        title = "Auto ping",
                        subtitle = if (autoPing) "Ping active for keep-alive service" else "Ping Off for keep-alive service",
                        onClick = {
                            autoPing = !autoPing
                            VpnSettingsManager.setAutoPing(context, autoPing)
                        }
                    ) {
                        Checkbox(
                            checked = autoPing,
                            onCheckedChange = { active ->
                                autoPing = active
                                VpnSettingsManager.setAutoPing(context, active)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom Ping Target Setting Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xE6FFFFFF)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFEDE9FE), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NetworkCheck,
                                        contentDescription = "Custom Ping Target",
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Target Ping Kustom",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = "Kosongkan untuk otomatis menggunakan host SSH",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = pingAddress,
                                onValueChange = {
                                    pingAddress = it
                                    VpnSettingsManager.setPingAddress(context, it)
                                    LogManager.addLog("Target ping diubah: $it")
                                },
                                label = { Text("Host Ping / IPAddress:Port (Contoh: 1.1.1.1:53)", fontSize = 11.sp) },
                                placeholder = { Text("Contoh: google.com atau 8.8.8.8") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedTextColor = Color(0xFF1E293B),
                                    unfocusedTextColor = Color(0xFF1E293B),
                                    focusedLabelColor = Color(0xFF3B82F6),
                                    unfocusedLabelColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Forcing TLS Mode Card
                    VpnItemCard(
                        icon = Icons.Default.Info,
                        iconColor = Color(0xFF0EA5E9),
                        title = "Forcing TLS",
                        subtitle = forcingTls,
                        onClick = { showTlsDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open TLS Menu",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // ================= DIALOG POPUPS =================

        // 1. Profile Picker Dialog (Screenshot 3 style)
        if (showProfileDialog) {
            AlertDialog(
                onDismissRequest = { showProfileDialog = false },
                title = { Text("Profile", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        profileList.forEach { profileOption ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentProfile = profileOption
                                        VpnSettingsManager.setCurrentProfile(context, profileOption)
                                        showProfileDialog = false
                                        LogManager.addLog("Switched profile to: $profileOption")
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (currentProfile == profileOption),
                                    onClick = {
                                        currentProfile = profileOption
                                        VpnSettingsManager.setCurrentProfile(context, profileOption)
                                        showProfileDialog = false
                                        LogManager.addLog("Switched profile to: $profileOption")
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = profileOption,
                                    fontSize = 16.sp,
                                    color = Color(0xFF334155),
                                    fontWeight = if (currentProfile == profileOption) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showProfileDialog = false }) {
                        Text("BATAL", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                containerColor = Color.White
            )
        }

        // 2. Add custom profile name Dialog
        if (showAddProfileDialog) {
            var newProfileName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddProfileDialog = false },
                title = { Text("Create New Profile", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = newProfileName,
                            onValueChange = { newProfileName = it },
                            label = { Text("Profile Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B),
                                focusedLabelColor = Color(0xFF3B82F6),
                                unfocusedLabelColor = Color(0xFF64748B)
                            ),
                            textStyle = LocalTextStyle.current.copy(color = Color(0xFF1E293B))
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newProfileName.isNotBlank()) {
                                VpnSettingsManager.addProfile(context, newProfileName.trim())
                                profileList = VpnSettingsManager.getProfiles(context).toList()
                                currentProfile = newProfileName.trim()
                                VpnSettingsManager.setCurrentProfile(context, newProfileName.trim())
                                LogManager.addLog("Created profile: $newProfileName")
                                showAddProfileDialog = false
                            } else {
                                Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("TAMBAH", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddProfileDialog = false }) {
                        Text("BATAL", color = Color(0xFF64748B))
                    }
                },
                shape = RoundedCornerShape(12.dp),
                containerColor = Color.White
            )
        }



        // 4. Payload Editor Dialog
        if (showPayloadDialog) {
            var tempPayload by remember { mutableStateOf(payload) }
            AlertDialog(
                onDismissRequest = { showPayloadDialog = false },
                title = { Text("Payload Editor", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = tempPayload,
                            onValueChange = { tempPayload = it },
                            label = { Text("Payload string") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B),
                                focusedLabelColor = Color(0xFF3B82F6),
                                unfocusedLabelColor = Color(0xFF64748B)
                            ),
                            textStyle = LocalTextStyle.current.copy(color = Color(0xFF1E293B))
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            payload = tempPayload
                            VpnSettingsManager.setPayload(context, tempPayload)
                            LogManager.addLog("Payload updated.")
                            showPayloadDialog = false
                        }
                    ) {
                        Text("SIMPAN", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPayloadDialog = false }) {
                        Text("BATAL", color = Color(0xFF64748B))
                    }
                },
                shape = RoundedCornerShape(12.dp),
                containerColor = Color.White
            )
        }

        // 8. Split Tunneling (Bypass Aplikasi) Dialog
        if (showSplitTunnelingDialog) {
            var appList by remember { mutableStateOf<List<AppBypassItem>>(emptyList()) }
            var isLoadingApps by remember { mutableStateOf(false) }
            var appSearchQuery by remember { mutableStateOf("") }
            val currentContext = context

            LaunchedEffect(Unit) {
                isLoadingApps = true
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val pm = currentContext.packageManager
                        val installed = pm.getInstalledPackages(0)
                        val items = installed.mapNotNull { pack ->
                            val appInfo = pack.applicationInfo ?: return@mapNotNull null
                            if (pack.packageName == currentContext.packageName) return@mapNotNull null
                            val appLabel = appInfo.loadLabel(pm).toString()
                            val icon = try {
                                appInfo.loadIcon(pm)
                            } catch (e: Exception) {
                                null
                            }
                            AppBypassItem(
                                name = appLabel,
                                packageName = pack.packageName,
                                isBypassed = bypassApps.contains(pack.packageName),
                                icon = icon
                            )
                        }.sortedWith(compareBy<AppBypassItem> { !it.isBypassed }.thenBy { it.name.lowercase() })
                        appList = items
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error loading apps", e)
                    } finally {
                        isLoadingApps = false
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showSplitTunnelingDialog = false },
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "Split Tunneling",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (appsFilterMode == "filter") "Rute Aplikasi: Only Tunnel" else "Rute Aplikasi: Bypass",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 18.sp
                            )
                        }
                        Text(
                            text = if (appsFilterMode == "filter")
                                "Hanya aplikasi yang dichecklist yang akan disalurkan melalui VPN (Aplikasi lain bypass)."
                                else "Aplikasi yang dichecklist akan langsung melewati VPN (tanpa tunnel).",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Aktifkan Rute Khusus", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(
                                checked = splitTunnelingEnabled,
                                onCheckedChange = { active ->
                                    splitTunnelingEnabled = active
                                    VpnSettingsManager.setSplitTunnelingEnabled(context, active)
                                    LogManager.addLog("Rute khusus aplikasi diubah: $active")
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF3B82F6)
                                )
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        // Routing Mode Selector Segmented Control
                        Text(
                            text = "Mode Rute Aplikasi",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Bypass option
                            val isBypass = appsFilterMode == "bypass"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isBypass) Color.White else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        appsFilterMode = "bypass"
                                        VpnSettingsManager.setAppsFilterMode(currentContext, "bypass")
                                        LogManager.addLog("Mode rute aplikasi diubah ke: Bypass (Exclude)")
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Bypass (Exclude)",
                                    fontSize = 12.sp,
                                    fontWeight = if (isBypass) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isBypass) Color(0xFF3B82F6) else Color(0xFF64748B)
                                )
                            }
                            // Filter option
                            val isFilter = appsFilterMode == "filter"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isFilter) Color.White else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        appsFilterMode = "filter"
                                        VpnSettingsManager.setAppsFilterMode(currentContext, "filter")
                                        LogManager.addLog("Mode rute aplikasi diubah ke: Apps Filter (Only Tunnel)")
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Only Tunnel (Include)",
                                    fontSize = 12.sp,
                                    fontWeight = if (isFilter) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isFilter) Color(0xFF3B82F6) else Color(0xFF64748B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Search bar
                        OutlinedTextField(
                            value = appSearchQuery,
                            onValueChange = { appSearchQuery = it },
                            placeholder = { Text("Cari aplikasi...", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B)
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Cari",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF1E293B))
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isLoadingApps) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF3B82F6), modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Memuat daftar aplikasi...", fontSize = 12.sp, color = Color(0xFF64748B))
                                }
                            }
                        } else {
                            val filteredApps = appList.filter {
                                it.name.contains(appSearchQuery, ignoreCase = true) ||
                                        it.packageName.contains(appSearchQuery, ignoreCase = true)
                            }

                            if (filteredApps.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aplikasi tidak ditemukan",
                                        fontSize = 13.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filteredApps.size) { index ->
                                        val item = filteredApps[index]
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val updatedBypass = if (item.isBypassed) {
                                                        bypassApps - item.packageName
                                                    } else {
                                                        bypassApps + item.packageName
                                                    }
                                                    bypassApps = updatedBypass
                                                    VpnSettingsManager.setBypassApps(context, updatedBypass)
                                                    appList = appList.map {
                                                        if (it.packageName == item.packageName) {
                                                            it.copy(isBypassed = !item.isBypassed)
                                                        } else {
                                                            it
                                                        }
                                                    }
                                                }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (item.icon != null) {
                                                val appIconBitmap = remember(item.packageName) {
                                                    try {
                                                        item.icon.toBitmap().asImageBitmap()
                                                    } catch (e: Exception) {
                                                        null
                                                    }
                                                }
                                                if (appIconBitmap != null) {
                                                    Image(
                                                        bitmap = appIconBitmap,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .background(Color(0xFFF1F5F9), shape = CircleShape)
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(Color(0xFFF1F5F9), shape = CircleShape)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF1E293B)
                                                )
                                                Text(
                                                    text = item.packageName,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }

                                            Checkbox(
                                                checked = item.isBypassed,
                                                onCheckedChange = { checked ->
                                                    val updatedBypass = if (checked) {
                                                        bypassApps + item.packageName
                                                    } else {
                                                        bypassApps - item.packageName
                                                    }
                                                    bypassApps = updatedBypass
                                                    VpnSettingsManager.setBypassApps(context, updatedBypass)
                                                    appList = appList.map {
                                                        if (it.packageName == item.packageName) {
                                                            it.copy(isBypassed = checked)
                                                        } else {
                                                            it
                                                        }
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color(0xFF3B82F6),
                                                    uncheckedColor = Color(0xFF94A3B8)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showSplitTunnelingDialog = false }
                    ) {
                        Text("SELESAI", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                containerColor = Color.White
            )
        }

        // Kill Switch advanced informational & setup dialog
        if (showKillSwitchDialog) {
            AlertDialog(
                onDismissRequest = { showKillSwitchDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Kill Switch",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kill Switch (Anti Bocor)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF0F172A)
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Aktifkan Kill Switch", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(
                                checked = killSwitchEnabled,
                                onCheckedChange = { active ->
                                    killSwitchEnabled = active
                                    VpnSettingsManager.setKillSwitchEnabled(context, active)
                                    LogManager.addLog("Kill Switch diubah: $active")
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFEF4444)
                                )
                            )
                        }
                        Text(
                            text = "Kill Switch memastikan data pribadi Anda tidak bocor saat koneksi VPN bermasalah atau terputus secara mendadak.",
                            fontSize = 13.sp,
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = "Secara terprogram, aplikasi ini akan menahan koneksi dan mengunci akses data unencrypted saat terjadi pemutusan jaringan tidak terduga atau saat melakukan rekoneksi otomatis.",
                            fontSize = 13.sp,
                            color = Color(0xFF334155)
                        )
                        Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                        Text(
                            text = "💡 REKOMENDASI SISTEM:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Untuk perlindungan tingkat mutlak (bahkan ketika aplikasi dihentikan paksa), aktifkan fitur bawaan Android:\n\n" +
                                    "1. Klik tombol 'BUKA PENGATURAN' di bawah.\n" +
                                    "2. Klik ikon gir di sebelah 'SIVPN Cepat'.\n" +
                                    "3. Aktifkan 'VPN Selalu Aktif' (Always-on VPN).\n" +
                                    "4. Aktifkan 'Blokir koneksi tanpa VPN'.",
                            fontSize = 12.sp,
                            color = Color(0xFF475569)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent("android.settings.VPN_SETTINGS")
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val fallbackIntent = Intent("android.net.vpn.SETTINGS")
                                    fallbackIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(fallbackIntent)
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "Gagal membuka menu pengaturan VPN", Toast.LENGTH_SHORT).show()
                                }
                            }
                            showKillSwitchDialog = false
                        }
                    ) {
                        Text("BUKA PENGATURAN", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showKillSwitchDialog = false }
                    ) {
                        Text("TUTUP", color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                containerColor = Color.White
            )
        }





        // 9. Forcing TLS Picker Dialog (Screenshot 4 style)
        if (showTlsDialog) {
            val tlsOptions = listOf("Auto", "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3")
            AlertDialog(
                onDismissRequest = { showTlsDialog = false },
                title = { Text("Forcing TLS", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        tlsOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        forcingTls = option
                                        VpnSettingsManager.setForcingTls(context, option)
                                        showTlsDialog = false
                                        LogManager.addLog("TLS Protocol forced to: $option")
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (forcingTls == option),
                                    onClick = {
                                        forcingTls = option
                                        VpnSettingsManager.setForcingTls(context, option)
                                        showTlsDialog = false
                                        LogManager.addLog("TLS Protocol forced to: $option")
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = option,
                                    fontSize = 16.sp,
                                    color = Color(0xFF334155),
                                    fontWeight = if (forcingTls == option) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showTlsDialog = false }) {
                        Text("BATAL", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                containerColor = Color.White
            )
        }

        // 5. Connection Limit Picker Dialog
        if (showLimitDialog) {
            val limits = listOf(1, 5, 15, 30, 60, 180, 360)
            AlertDialog(
                onDismissRequest = { showLimitDialog = false },
                title = { Text("Pengaturan Waktu & Status", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 1. Dashboard visibility switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Tampilkan Info Status",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "Tampilkan dashboard durasi aktif di halaman utama",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Switch(
                                checked = statusCardVisible,
                                onCheckedChange = { isChecked ->
                                    statusCardVisible = isChecked
                                    VpnSettingsManager.setStatusCardVisible(context, isChecked)
                                    LogManager.addLog("Dashboard status diatur: ${if (isChecked) "Tampil" else "Sembunyi"}")
                                }
                            )
                        }

                        Divider(color = Color(0x2694A3B8), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                        // 2. Limit feature toggle switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Batasi Durasi Koneksi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "Putuskan VPN otomatis bila durasi tercapai",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Switch(
                                checked = connectionLimitEnabled,
                                onCheckedChange = { isChecked ->
                                    connectionLimitEnabled = isChecked
                                    VpnSettingsManager.setConnectionLimitEnabled(context, isChecked)
                                    if (isChecked && connectionLimitMinutes <= 0) {
                                        connectionLimitMinutes = 15
                                        VpnSettingsManager.setConnectionLimitMinutes(context, 15)
                                    }
                                    LogManager.addLog("Batas waktu koneksi diatur: ${if (isChecked) "Aktif" else "Nonaktif"}")
                                }
                            )
                        }

                        // 3. Selection list - active ONLY when limit toggle is on
                        if (connectionLimitEnabled) {
                            Divider(color = Color(0x2694A3B8), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = "Pilih Batas Waktu:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            
                            limits.forEach { limitValue ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            connectionLimitMinutes = limitValue
                                            VpnSettingsManager.setConnectionLimitMinutes(context, limitValue)
                                            LogManager.addLog("Batas waktu koneksi diatur ke: ${getLimitLabel(limitValue)}")
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (connectionLimitMinutes == limitValue),
                                        onClick = {
                                            connectionLimitMinutes = limitValue
                                            VpnSettingsManager.setConnectionLimitMinutes(context, limitValue)
                                            LogManager.addLog("Batas waktu koneksi diatur ke: ${getLimitLabel(limitValue)}")
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = getLimitLabel(limitValue),
                                        fontSize = 15.sp,
                                        color = Color(0xFF334155),
                                        fontWeight = if (connectionLimitMinutes == limitValue) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLimitDialog = false }) {
                        Text("OK", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = null,
                shape = RoundedCornerShape(12.dp),
                containerColor = Color.White
            )
        }

        if (showKeepAliveDialog) {
            val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val dialogBg = if (isDarkTheme) Color(0xFF1E293B) else Color.White
            val titleColor = if (isDarkTheme) Color.White else Color(0xFF1E293B)
            val descColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

            Dialog(onDismissRequest = { showKeepAliveDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = dialogBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Interval Keep-Alive (Ping)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = titleColor
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Sesuaikan interval ping di latar belakang. Semakin lama intervalnya, semakin sedikit penggunaan CPU dan baterai smartphone Anda.",
                            fontSize = 12.sp,
                            color = descColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Preset intervals
                        val presets = listOf(10, 30, 60, 120, 300)
                        presets.forEach { seconds ->
                            val isSelected = keepAliveInterval == seconds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        keepAliveInterval = seconds
                                        VpnSettingsManager.setKeepAliveInterval(context, seconds)
                                        LogManager.addLog("Interval keep-alive diubah ke: $seconds detik")
                                        showKeepAliveDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val label = when (seconds) {
                                    10 -> "10 Detik (Sangat Agresif)"
                                    30 -> "30 Detik (Rekomendasi)"
                                    60 -> "1 Menit (Hemat Baterai)"
                                    120 -> "2 Menit (Sangat Hemat)"
                                    300 -> "5 Menit (Ekstrem Hemat)"
                                    else -> "$seconds Detik"
                                }
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF3B82F6) else titleColor
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Terpilih",
                                        tint = Color(0xFF3B82F6)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { showKeepAliveDialog = false }) {
                            Text("Batal", color = Color(0xFF3B82F6))
                        }
                    }
                }
            }
        }

        // 10. Real-time Terminal Log Popup Dialog
        if (showLogDialog) {
            Dialog(onDismissRequest = { showLogDialog = false }) {
                val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val consoleBg = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF1F5F9)
                val consoleAccent = if (isDarkTheme) Color(0xFF10B981) else Color(0xFF0D9488)
                val dividerColor = if (isDarkTheme) Color(0x2694A3B8) else Color(0x26475569)
                val clearColor = if (isDarkTheme) Color(0xFFF87171) else Color(0xFFDC2626)
                
                var showSettingsInLogs by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = consoleBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header
                        Row(
                           modifier = Modifier
                               .fillMaxWidth()
                               .padding(horizontal = 16.dp, vertical = 14.dp),
                           verticalAlignment = Alignment.CenterVertically,
                           horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                           Row(verticalAlignment = Alignment.CenterVertically) {
                               Icon(
                                   imageVector = if (showSettingsInLogs) Icons.Default.Settings else Icons.Default.Terminal,
                                   contentDescription = "Logs Icon",
                                   tint = consoleAccent,
                                   modifier = Modifier.size(20.dp)
                               )
                               Spacer(modifier = Modifier.width(8.dp))
                               Text(
                                   text = if (showSettingsInLogs) "Pengaturan Bersih Log" else "Terminal Console Logs",
                                   fontWeight = FontWeight.Bold,
                                   fontSize = 15.sp,
                                   color = consoleAccent
                               )
                           }
                           
                           IconButton(
                               onClick = { showSettingsInLogs = !showSettingsInLogs },
                               modifier = Modifier.size(28.dp)
                           ) {
                               Icon(
                                   imageVector = if (showSettingsInLogs) Icons.Default.Terminal else Icons.Default.Settings,
                                   contentDescription = if (showSettingsInLogs) "Tampilkan Log" else "Tampilkan Pengaturan",
                                   tint = consoleAccent,
                                   modifier = Modifier.size(20.dp)
                               )
                           }
                        }

                        Divider(color = dividerColor, thickness = 1.dp)

                        if (!showSettingsInLogs) {
                            // Scrollable log lines list area
                            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                            
                            // Auto-scroll to the latest log line dynamically when log size updates
                            LaunchedEffect(logs.size) {
                                if (logs.isNotEmpty()) {
                                    listState.animateScrollToItem(logs.size - 1)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(logs) { logLine ->
                                        val textColor = when {
                                            logLine.contains("[SSH]") -> if (isDarkTheme) Color(0xFFFBBF24) else Color(0xFFB45309)
                                            logLine.contains("[HEV]") -> if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
                                            logLine.contains("Error") || logLine.contains("failed") -> if (isDarkTheme) Color(0xFFF87171) else Color(0xFFB91C1C)
                                            else -> if (isDarkTheme) Color(0xFF34D399) else Color(0xFF0F5A3E)
                                        }
                                        Text(
                                            text = logLine,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = textColor,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Divider(color = dividerColor, thickness = 1.dp)

                            // Control Action panel: CLEAR and CLOSE
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        LogManager.clearLogs()
                                        LogManager.clearPhysicalLogFile(context)
                                        LogManager.addLog("--- Konsol Berhasil Dibersihkan ---")
                                        scope.launch {
                                            com.sivpn.cepat.vpn.SystemInfoHelper.logSystemInfo(context)
                                        }
                                        Toast.makeText(context, "Semua log dibersihkan", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = "CLEAR ALL",
                                        color = clearColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = { showLogDialog = false }
                                ) {
                                    Text(
                                        text = "CLOSE",
                                        color = consoleAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            // Settings section code
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // 1. Toggle Auto Clean
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = "Pembersihan Otomatis (Auto Clean)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = "Hapus log berkala untuk membebaskan penyimpanan & RAM.",
                                            fontSize = 11.sp,
                                            color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                                        )
                                    }
                                    Switch(
                                        checked = autoCleanLogsEnabled,
                                        onCheckedChange = { isChecked ->
                                            autoCleanLogsEnabled = isChecked
                                            VpnSettingsManager.setAutoCleanLogsEnabled(context, isChecked)
                                            LogManager.addLog("Pembersihan log otomatis diatur: ${if (isChecked) "Aktif" else "Nonaktif"}")
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = consoleAccent
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // 2. Auto Clean interval choice
                                if (autoCleanLogsEnabled) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                        Text(
                                            text = "Interval Pengosongan Otomatis",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
                                        )
                                        Text(
                                            text = "Tentukan siklus pembersihan (menit):",
                                            fontSize = 11.sp,
                                            color = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF64748B),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(1, 5, 10, 30, 60).forEach { mins ->
                                                val isSelected = autoCleanInterval == mins
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(
                                                            if (isSelected) consoleAccent.copy(alpha = 0.2f) else (if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isSelected) consoleAccent else Color.Transparent,
                                                            shape = RoundedCornerShape(6.dp)
                                                        )
                                                        .clickable {
                                                            autoCleanInterval = mins
                                                            VpnSettingsManager.setAutoCleanInterval(context, mins)
                                                            LogManager.addLog("Interval pembersihan otomatis diatur: $mins menit")
                                                        }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (mins == 60) "1 Jam" else "${mins}m",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) consoleAccent else (if (isDarkTheme) Color.White else Color(0xFF475569))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                // 3. Max local log limit
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                    Text(
                                        text = "Batas Baris Log Maksimal (Buffer RAM)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
                                    )
                                    Text(
                                        text = "Batasan baris log di daftar visual memori:",
                                        fontSize = 11.sp,
                                        color = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF64748B),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(100, 300, 500, 1000).forEach { lines ->
                                            val isSelected = maxLogLines == lines
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(
                                                        if (isSelected) consoleAccent.copy(alpha = 0.2f) else (if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) consoleAccent else Color.Transparent,
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                                    .clickable {
                                                        maxLogLines = lines
                                                        LogManager.maxLogLines = lines
                                                        VpnSettingsManager.setMaxLogLines(context, lines)
                                                        LogManager.addLog("Batas baris log maksimal diatur: $lines baris")
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$lines",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) consoleAccent else (if (isDarkTheme) Color.White else Color(0xFF475569))
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // 4. Hard Manual Clear Button
                                Button(
                                    onClick = {
                                        LogManager.clearLogs()
                                        LogManager.clearPhysicalLogFile(context)
                                        LogManager.addLog("--- Pembersihan Manual Berhasil ---")
                                        scope.launch {
                                            com.sivpn.cepat.vpn.SystemInfoHelper.logSystemInfo(context)
                                        }
                                        Toast.makeText(context, "Log memori & berkas log fisik dibersihkan!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = clearColor.copy(alpha = 0.15f),
                                        contentColor = clearColor
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, clearColor.copy(alpha = 0.3f))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Pembersihan Total",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "BERSIHKAN UNTUK SEMUA LOG SEKARANG",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Divider(color = dividerColor, thickness = 1.dp)

                            // Close action panel
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { showSettingsInLogs = false }
                                ) {
                                    Text(
                                        text = "KEMBALI KE CONSOLE",
                                        color = consoleAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 11. Tethering Options Popup Dialog (Hotshare)
        if (showTetherDialog) {
            Dialog(onDismissRequest = { showTetherDialog = false }) {
                val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val dialogBg = if (isDarkTheme) Color(0xFF1E293B) else Color.White
                val primaryTextColor = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A)
                val secondaryTextColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
                val cardStepBg = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                val borderColor = if (isDarkTheme) Color(0x3394A3B8) else Color(0xFFE2E8F0)
                val infoStyle = TextStyle(fontSize = 13.sp, color = secondaryTextColor)

                var hotshareSocksPortText by remember { mutableStateOf(com.sivpn.cepat.vpn.VpnSettingsManager.getHotshareSocksPort(context).toString()) }
                var hotshareHttpPortText by remember { mutableStateOf(com.sivpn.cepat.vpn.VpnSettingsManager.getHotshareHttpPort(context).toString()) }

                var qrProxyType by remember { mutableStateOf("HTTP") }
                var qrProxyIp by remember { mutableStateOf("192.168.43.1") }
                var qrFormatType by remember { mutableStateOf("URL") }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = dialogBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Hotshare",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = primaryTextColor,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Info Section
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("SSID", style = infoStyle); Text("-", style = infoStyle) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Sandi", style = infoStyle); Text("-", style = infoStyle) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Proxy IP", style = infoStyle); Text("192.168.43.1 / 192.168.42.129", style = infoStyle) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { 
                            Text("Proxy Port (HTTP / SOCKS)", style = infoStyle)
                            Text("$hotshareHttpPortText / $hotshareSocksPortText", style = infoStyle) 
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        var autoKillHotshare by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = autoKillHotshare, onCheckedChange = { autoKillHotshare = it })
                            Text("Matikan Hotshare secara otomatis jika VPN terputus", style = infoStyle)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Step 1", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryTextColor)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = cardStepBg),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Start Wi-Fi hotspot (recommended) atau USB Tethering", fontSize = 12.sp, color = secondaryTextColor, modifier = Modifier.padding(bottom = 8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                                            try { context.startActivity(intent) } catch (e: Exception) {}
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF168199))
                                    ) { Text("Start Wi-Fi Hotspot") }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(onClick = { /* Settings */ }) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = secondaryTextColor)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Step 2", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryTextColor)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = cardStepBg),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                var useHttp by remember { mutableStateOf(true) }
                                var useSocks by remember { mutableStateOf(true) }
                                var isRootHotspotEnabled by remember { mutableStateOf(false) }
                                var isHotshareActive by remember { mutableStateOf(com.sivpn.cepat.vpn.LocalPortForwarder.isRunning || com.sivpn.cepat.vpn.HttpProxyServer.isRunning) }
                                val connectedClients by com.sivpn.cepat.vpn.HotshareClientManager.connectedClientsFlow.collectAsStateWithLifecycle()
                                var isClientsListExpanded by remember { mutableStateOf(false) }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = useHttp, onCheckedChange = { useHttp = it })
                                    Text("HTTP", fontSize = 13.sp, color = primaryTextColor)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Checkbox(checked = useSocks, onCheckedChange = { useSocks = it })
                                    Text("SOCKS", fontSize = 13.sp, color = primaryTextColor)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = hotshareWakeLockEnabled,
                                        onCheckedChange = {
                                            hotshareWakeLockEnabled = it
                                            com.sivpn.cepat.vpn.VpnSettingsManager.setHotshareWakeLockEnabled(context, it)
                                        }
                                    )
                                    Text(
                                        text = "WakeLock Khusus Hotshare (CPU Tetap Aktif)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = hotshareHttpPortText,
                                        onValueChange = { newValue ->
                                            if (newValue.all { it.isDigit() } && newValue.length <= 5) {
                                                hotshareHttpPortText = newValue
                                                newValue.toIntOrNull()?.let {
                                                    com.sivpn.cepat.vpn.VpnSettingsManager.setHotshareHttpPort(context, it)
                                                }
                                            }
                                        },
                                        label = { Text("Port HTTP", fontSize = 11.sp, color = secondaryTextColor) },
                                        singleLine = true,
                                        enabled = useHttp && !isHotshareActive,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF168199),
                                            focusedLabelColor = Color(0xFF168199),
                                            unfocusedBorderColor = borderColor,
                                            unfocusedLabelColor = secondaryTextColor,
                                            focusedTextColor = primaryTextColor,
                                            unfocusedTextColor = primaryTextColor
                                        ),
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(fontSize = 13.sp)
                                    )
                                    
                                    OutlinedTextField(
                                        value = hotshareSocksPortText,
                                        onValueChange = { newValue ->
                                            if (newValue.all { it.isDigit() } && newValue.length <= 5) {
                                                hotshareSocksPortText = newValue
                                                newValue.toIntOrNull()?.let {
                                                    com.sivpn.cepat.vpn.VpnSettingsManager.setHotshareSocksPort(context, it)
                                                }
                                            }
                                        },
                                        label = { Text("Port SOCKS", fontSize = 11.sp, color = secondaryTextColor) },
                                        singleLine = true,
                                        enabled = useSocks && !isHotshareActive,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF168199),
                                            focusedLabelColor = Color(0xFF168199),
                                            unfocusedBorderColor = borderColor,
                                            unfocusedLabelColor = secondaryTextColor,
                                            focusedTextColor = primaryTextColor,
                                            unfocusedTextColor = primaryTextColor
                                        ),
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(fontSize = 13.sp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        if (!isHotshareActive) {
                                            if (!useHttp && !useSocks) {
                                                Toast.makeText(context, "Pilih setidaknya satu tipe proxy (HTTP / SOCKS)!", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            
                                            val httpPortInt = hotshareHttpPortText.toIntOrNull() ?: 8080
                                            val socksPortInt = hotshareSocksPortText.toIntOrNull() ?: 1080

                                            com.sivpn.cepat.vpn.VpnSettingsManager.setHotshareHttpPort(context, httpPortInt)
                                            com.sivpn.cepat.vpn.VpnSettingsManager.setHotshareSocksPort(context, socksPortInt)

                                            if (useHttp) {
                                                com.sivpn.cepat.vpn.HttpProxyServer.start(context = context, localBindPort = httpPortInt, socksPort = socksPortInt)
                                            }
                                            if (useSocks) {
                                                com.sivpn.cepat.vpn.LocalPortForwarder.start(context = context, localBindPort = socksPortInt)
                                            }
                                            
                                            isHotshareActive = com.sivpn.cepat.vpn.HttpProxyServer.isRunning || com.sivpn.cepat.vpn.LocalPortForwarder.isRunning
                                            if (isHotshareActive) {
                                                val activeModes = mutableListOf<String>()
                                                if (com.sivpn.cepat.vpn.HttpProxyServer.isRunning) {
                                                    activeModes.add("HTTP (${com.sivpn.cepat.vpn.HttpProxyServer.activePort})")
                                                }
                                                if (com.sivpn.cepat.vpn.LocalPortForwarder.isRunning) {
                                                    activeModes.add("SOCKS (${com.sivpn.cepat.vpn.LocalPortForwarder.activePort})")
                                                }
                                                Toast.makeText(context, "Hotshare aktif: ${activeModes.joinToString(", ")}", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Gagal mengaktifkan Hotshare (Port bentrok).", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            com.sivpn.cepat.vpn.HttpProxyServer.stop()
                                            com.sivpn.cepat.vpn.LocalPortForwarder.stop()
                                            isHotshareActive = false
                                            Toast.makeText(context, "Hotshare nonaktif.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isHotshareActive) Color(0xFFEF4444) else Color(0xFF168199)
                                    )
                                ) { Text(if (isHotshareActive) "STOP HOTSHARE" else "START HOTSHARE (PROXY)") }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                // Displays connected client devices list elegantly!
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (connectedClients.isNotEmpty()) Color(0x0C168199) else Color(0x0894A3B8),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (connectedClients.isNotEmpty()) {
                                                isClientsListExpanded = !isClientsListExpanded
                                            }
                                        }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Devices,
                                                contentDescription = "Connected Devices",
                                                tint = if (connectedClients.isNotEmpty()) Color(0xFF168199) else Color(0xFF64748B),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Perangkat Terhubung (Klien)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = primaryTextColor
                                             )
                                        }
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (connectedClients.isNotEmpty()) Color(0xFF168199) else Color(0xFFE2E8F0),
                                                        shape = CircleShape
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "${connectedClients.size}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (connectedClients.isNotEmpty()) Color.White else Color(0xFF64748B)
                                                )
                                            }
                                            if (connectedClients.isNotEmpty()) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = if (isClientsListExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Toggle List",
                                                    tint = Color(0xFF168199),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (connectedClients.isNotEmpty() && isClientsListExpanded) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        connectedClients.forEach { client ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = client.ip,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = primaryTextColor
                                                    )
                                                    Text(
                                                        text = "MAC: ${client.macAddress}",
                                                        fontSize = 10.sp,
                                                        color = secondaryTextColor
                                                    )
                                                }
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (client.isProxyActive) Color(0xFFD1FAE5) else Color(0xFFF3F4F6),
                                                            shape = RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (client.isProxyActive) "Proxy Aktif" else "Siaga",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (client.isProxyActive) Color(0xFF065F46) else Color(0xFF374151)
                                                     )
                                                }
                                            }
                                        }
                                    } else if (connectedClients.isEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Belum ada perangkat terhubung.",
                                            fontSize = 11.sp,
                                            color = secondaryTextColor,
                                            modifier = Modifier.padding(start = 26.dp)
                                        )
                                    }
                                }
                                 
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = borderColor, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Button(
                                    onClick = {
                                        if (!isRootHotspotEnabled) {
                                            com.sivpn.cepat.vpn.RootHotspotManager.startHotspotRouting()
                                            isRootHotspotEnabled = true
                                            Toast.makeText(context, "Root Hotspot aktif", Toast.LENGTH_SHORT).show()
                                        } else {
                                            com.sivpn.cepat.vpn.RootHotspotManager.stopHotspotRouting()
                                            isRootHotspotEnabled = false
                                            Toast.makeText(context, "Root Hotspot nonaktif", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRootHotspotEnabled) Color(0xFFEF4444) else Color(0xFF3B82F6)
                                    )
                                ) { Text(if (isRootHotspotEnabled) "STOP ROOT HOTSPOT" else "START ROOT HOTSPOT") }
                                Text("Gunakan Start Root Hotspot untuk mem-bypass setelan proxy secara otomatis menggunakan IPTables (Khusus Rooted).", fontSize = 11.sp, color = secondaryTextColor, modifier = Modifier.padding(top = 4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Step 3", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryTextColor)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = cardStepBg),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val currentActiveSocksPort = if (com.sivpn.cepat.vpn.LocalPortForwarder.isRunning) com.sivpn.cepat.vpn.LocalPortForwarder.activePort else (hotshareSocksPortText.toIntOrNull() ?: 1080)
                                val currentActiveHttpPort = if (com.sivpn.cepat.vpn.HttpProxyServer.isRunning) com.sivpn.cepat.vpn.HttpProxyServer.activePort else (hotshareHttpPortText.toIntOrNull() ?: 8080)
                                Text("1. Hubungkan perangkat lain ke Hotspot/USB.\n\n2. Atur setelan proxy pada perangkat yang terhubung sesuai tipe:\n   - SOCKS5: IP 192.168.43.1 Port $currentActiveSocksPort\n   - HTTP: IP 192.168.43.1 Port $currentActiveHttpPort\n\n(Catatan: Untuk USB tethering, gunakan IP Gateway USB Anda, contoh: 192.168.42.129)", fontSize = 13.sp, color = secondaryTextColor)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Step 4 - Pembuat Kode QR Proksi", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryTextColor)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = cardStepBg),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Gunakan fitur ini untuk membuat kode QR pengaturan proksi secara instan. Pindai kode QR dari perangkat klien untuk menyalin konfigurasi proksi.", fontSize = 12.sp, color = secondaryTextColor, modifier = Modifier.padding(bottom = 8.dp))

                                // Select Type
                                Text("Tipe Proksi:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("HTTP", "SOCKS5").forEach { type ->
                                        val selected = qrProxyType == type
                                        Button(
                                            onClick = { qrProxyType = type },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selected) Color(0xFF168199) else cardStepBg,
                                                contentColor = if (selected) Color.White else primaryTextColor
                                            ),
                                            border = if (selected) null else BorderStroke(1.dp, borderColor),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.weight(1f).height(36.dp)
                                        ) {
                                            Text(type, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Quick IP Selector
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Pilih IP Gateway (IP Hotspot):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("192.168.43.1", "192.168.42.129", "192.168.49.1").forEach { ip ->
                                        val selected = qrProxyIp == ip
                                        Button(
                                            onClick = { qrProxyIp = ip },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selected) Color(0xFF168199).copy(alpha = 0.15f) else cardStepBg,
                                                contentColor = if (selected) Color(0xFF168199) else secondaryTextColor
                                            ),
                                            border = BorderStroke(1.dp, if (selected) Color(0xFF168199) else borderColor),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.weight(1f).height(30.dp)
                                        ) {
                                            Text(ip, fontSize = 10.sp, fontWeight = FontWeight.Normal)
                                        }
                                    }
                                }

                                // Custom IP Input Field
                                OutlinedTextField(
                                    value = qrProxyIp,
                                    onValueChange = { qrProxyIp = it },
                                    label = { Text("Kustom IP Proksi", fontSize = 11.sp, color = secondaryTextColor) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF168199),
                                        focusedLabelColor = Color(0xFF168199),
                                        unfocusedBorderColor = borderColor,
                                        unfocusedLabelColor = secondaryTextColor,
                                        focusedTextColor = primaryTextColor,
                                        unfocusedTextColor = primaryTextColor
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    textStyle = TextStyle(fontSize = 13.sp)
                                )

                                // QR Format selector
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Format Kode QR:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "URL" to "Format URL (Sangat disukai oleh software klien proksi)",
                                        "TEKS" to "Format Teks (Deskripsi Lengkap)"
                                    ).forEach { (format, label) ->
                                        val selected = qrFormatType == format
                                        Button(
                                            onClick = { qrFormatType = format },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selected) Color(0xFF168199) else cardStepBg,
                                                contentColor = if (selected) Color.White else primaryTextColor
                                            ),
                                            border = if (selected) null else BorderStroke(1.dp, borderColor),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.weight(1f).height(36.dp)
                                        ) {
                                            Text(format, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // QR Code Display (Nice centered rounded white card for scanning readability)
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .background(Color.White, shape = RoundedCornerShape(12.dp))
                                            .border(1.dp, borderColor, shape = RoundedCornerShape(12.dp))
                                            .padding(16.dp)
                                    ) {
                                        val portVal = if (qrProxyType == "HTTP") {
                                            if (com.sivpn.cepat.vpn.HttpProxyServer.isRunning) com.sivpn.cepat.vpn.HttpProxyServer.activePort else (hotshareHttpPortText.toIntOrNull() ?: 8080)
                                        } else {
                                            if (com.sivpn.cepat.vpn.LocalPortForwarder.isRunning) com.sivpn.cepat.vpn.LocalPortForwarder.activePort else (hotshareSocksPortText.toIntOrNull() ?: 1080)
                                        }
                                        
                                        val qrText = if (qrFormatType == "URL") {
                                            if (qrProxyType == "HTTP") "http://$qrProxyIp:$portVal" else "socks5://$qrProxyIp:$portVal"
                                        } else {
                                            "Tipe Proxy: $qrProxyType\nProxy IP: $qrProxyIp\nProxy Port: $portVal"
                                        }

                                        val qrBitmap = remember(qrText) {
                                            com.sivpn.cepat.vpn.QrCodeGenerator.generateQr(qrText)
                                        }
                                        
                                        if (qrBitmap != null) {
                                            Image(
                                                bitmap = qrBitmap.asImageBitmap(),
                                                contentDescription = "QR Code Proksi",
                                                modifier = Modifier.size(160.dp)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.size(160.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Gagal membuat QR Code", color = Color.Red, fontSize = 12.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = qrText,
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                color = Color.Black,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("Proxy settings", qrText)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Setelan proxy telah disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF168199)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text("Salin Hubungan Proxy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showTetherDialog = false }) {
                                Text("TUTUP", color = Color(0xFF168199), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // JNI Native (.so) Online Downloader Dialog
        if (showJniDownloader) {
            var hostUrl by remember { mutableStateOf("https://raw.githubusercontent.com/toipgunarto01/sivpn-binaries/main/") }
            var isCustomUrlByPass by remember { mutableStateOf(false) }
            var customSshUrl by remember { mutableStateOf("") }
            var customHevUrl by remember { mutableStateOf("") }

            var downloadStatus by remember { mutableStateOf("") }
            var downloadProgress by remember { mutableStateOf(0f) }
            var isDownloading by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }

            val systemAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val resolvedAbi = when {
                systemAbi.startsWith("arm64") -> "arm64-v8a"
                systemAbi.startsWith("armeabi-v7") -> "armeabi-v7a"
                systemAbi.startsWith("x86_64") -> "x86_64"
                systemAbi.startsWith("x86") -> "x86"
                else -> systemAbi
            }

            AlertDialog(
                onDismissRequest = { if (!isDownloading) showJniDownloader = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unduh Library JNI Native", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Arsitektur CPU perangkat Anda: $resolvedAbi",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (!isDownloading) {
                            OutlinedTextField(
                                value = hostUrl,
                                onValueChange = { hostUrl = it },
                                label = { Text("Base URL Server / Repositori") },
                                placeholder = { Text("https://example.com/binaries/") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isCustomUrlByPass,
                                textStyle = TextStyle(fontSize = 12.sp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isCustomUrlByPass,
                                    onCheckedChange = { isCustomUrlByPass = it }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kostumisasi URL individual", fontSize = 11.sp)
                            }

                            if (isCustomUrlByPass) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = customSshUrl,
                                    onValueChange = { customSshUrl = it },
                                    label = { Text("URL Langsung Untuk libssh.so") },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 11.sp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = customHevUrl,
                                    onValueChange = { customHevUrl = it },
                                    label = { Text("URL Langsung Untuk libhev-socks5-tunnel.so") },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 11.sp)
                                )
                            }
                        } else {
                            // Downloading progress UI
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(downloadStatus, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (downloadProgress >= 0f) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Progress: ${(downloadProgress * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Sedang mengunduh file...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        errorMessage?.let { error ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Error: $error",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!isDownloading) {
                        Button(
                            onClick = {
                                isDownloading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val finalSshUrl = if (isCustomUrlByPass) {
                                            customSshUrl.trim()
                                        } else {
                                            val base = if (hostUrl.endsWith("/")) hostUrl else "$hostUrl/"
                                            "$base$resolvedAbi/libssh.so"
                                        }

                                        val finalHevUrl = if (isCustomUrlByPass) {
                                            customHevUrl.trim()
                                        } else {
                                            val base = if (hostUrl.endsWith("/")) hostUrl else "$hostUrl/"
                                            "$base$resolvedAbi/libhev-socks5-tunnel.so"
                                        }

                                        if (finalSshUrl.isEmpty() || finalHevUrl.isEmpty()) {
                                            throw Exception("URL tidak boleh kosong!")
                                        }

                                        // 1. Download libssh.so
                                        downloadStatus = "Mengunduh libssh.so..."
                                        com.sivpn.cepat.vpn.JniLibHelper.downloadNativeLibrary(
                                            context,
                                            finalSshUrl,
                                            "libssh.so"
                                        ) { progress, count, total ->
                                            downloadProgress = progress
                                        }

                                        // 2. Download libhev-socks5-tunnel.so
                                        downloadStatus = "Mengunduh libhev-socks5-tunnel.so..."
                                        com.sivpn.cepat.vpn.JniLibHelper.downloadNativeLibrary(
                                            context,
                                            finalHevUrl,
                                            "libhev-socks5-tunnel.so"
                                        ) { progress, count, total ->
                                            downloadProgress = progress
                                        }

                                        // 3. Load libraries
                                        downloadStatus = "Berhasil mengunduh, sedang menyambungkan..."
                                        val loaded = com.sivpn.cepat.vpn.JniLibHelper.loadDownloadedLibs(context)

                                        isNativeSshLoadedState = com.sivpn.cepat.vpn.NativeSshTunnel.isLibraryLoaded
                                        isHevLoadedState = com.sivpn.cepat.TProxyService.isLibraryLoaded

                                        if (isNativeSshLoadedState && isHevLoadedState) {
                                            Toast.makeText(context, "Selesai! Library JNI berhasil terpasang secara online.", Toast.LENGTH_LONG).show()
                                            showJniDownloader = false
                                        } else {
                                            throw Exception("File terunduh namun gagal dimuat via JNI (UnsatisfiedLinkError). Pastikan file .so kompatibel dengan CPU perangkat.")
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = e.localizedMessage ?: "Terjadi kesalahan yang tidak diketahui."
                                    } finally {
                                        isDownloading = false
                                    }
                                }
                            }
                        ) {
                            Text("UNDUH SEKARANG")
                        }
                    }
                },
                dismissButton = {
                    if (!isDownloading) {
                        TextButton(onClick = { showJniDownloader = false }) {
                            Text("BATAL")
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical blue indicator bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .background(Color(0xFF3B82F6), shape = RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 13.sp
            ),
            color = Color(0xFF3B82F6) // distinct accent color
        )
    }
}

@Composable
fun VpnItemCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingElement: @Composable (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xE6FFFFFF) // Semi-transparent clean white
        ),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)), // faint slate border to pop out beautifully
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circle wrapper for the icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFF1F5F9), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B) // High contrast Charcoal Slate
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B) // Subtle Gray
                    )
                }
            }

            if (trailingElement != null) {
                trailingElement()
            }
        }
    }
}

@Composable
fun TerminalLogPane(logs: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val consoleBg = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF1F5F9)
    val consoleAccent = if (isDarkTheme) Color(0xFF10B981) else Color(0xFF0D9488)
    val consoleMuted = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
    val dividerColor = if (isDarkTheme) Color(0x3394A3B8) else Color(0x33475569)
    val clearColor = if (isDarkTheme) Color(0xFFF87171) else Color(0xFFDC2626)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (expanded) Modifier.height(240.dp) else Modifier.height(52.dp)
            ),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = consoleBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Logs",
                        tint = consoleAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Terminal Console Logs",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = consoleAccent
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            LogManager.addLog("--- Cleared Console ---")
                            scope.launch {
                                com.sivpn.cepat.vpn.SystemInfoHelper.logSystemInfo(context)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("CLEAR", color = clearColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Expand",
                        tint = consoleMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (expanded) {
                Divider(color = dividerColor, thickness = 1.dp)

                // Scrollable log lines
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                
                // Auto-scroll to the latest log line
                LaunchedEffect(logs.size) {
                    if (logs.isNotEmpty()) {
                        listState.animateScrollToItem(logs.size - 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(logs) { logLine ->
                        val textColor = when {
                            logLine.contains("[SSH]") -> if (isDarkTheme) Color(0xFFFBBF24) else Color(0xFFB45309)
                            logLine.contains("[HEV]") -> if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
                            logLine.contains("Error") || logLine.contains("failed") -> if (isDarkTheme) Color(0xFFF87171) else Color(0xFFB91C1C)
                            else -> if (isDarkTheme) Color(0xFF34D399) else Color(0xFF0F5A3E)
                        }
                        Text(
                            text = logLine,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = textColor,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        "%02d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}

fun getLimitLabel(minutes: Int): String {
    return when (minutes) {
        0 -> "Tanpa Batas"
        1 -> "1 Menit"
        5 -> "5 Menit"
        15 -> "15 Menit"
        30 -> "30 Menit"
        60 -> "1 Jam"
        180 -> "3 Jam"
        360 -> "6 Jam"
        else -> "$minutes Menit"
    }
}

fun formatSpeed(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB/s", bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> String.format("%.1f KB/s", bytes.toDouble() / 1024)
        else -> "$bytes B/s"
    }
}

data class AppBypassItem(
    val name: String,
    val packageName: String,
    val isBypassed: Boolean,
    val icon: Drawable?
)

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) {
        return bitmap
    }
    val bitmap = Bitmap.createBitmap(
        if (intrinsicWidth > 0) intrinsicWidth else 48,
        if (intrinsicHeight > 0) intrinsicHeight else 48,
        Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
