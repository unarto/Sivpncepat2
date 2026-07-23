package com.sivpn.cepat.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sivpn.cepat.model.MainUiState
import com.sivpn.cepat.monitor.PingMonitor
import com.sivpn.cepat.monitor.PublicIpMonitor
import com.sivpn.cepat.monitor.SpeedMonitor
import com.sivpn.cepat.parser.ProxyParser
import com.sivpn.cepat.parser.SshParser
import com.sivpn.cepat.repository.ConfigRepository
import com.sivpn.cepat.repository.LogRepository
import com.sivpn.cepat.repository.SettingsRepository
import com.sivpn.cepat.vpn.NativeSshTunnel
import com.sivpn.cepat.vpn.SiVpnService
import com.sivpn.cepat.TProxyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository,
    private val configRepository: ConfigRepository,
    private val publicIpMonitor: PublicIpMonitor = PublicIpMonitor(),
    private val pingMonitor: PingMonitor = PingMonitor(),
    private val speedMonitor: SpeedMonitor = SpeedMonitor()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var ipJob: Job? = null
    private var pingJob: Job? = null
    private var speedJob: Job? = null
    private var timerJob: Job? = null

    init {
        loadSettings()
        startConnectionStatusPolling()
        observeLogs()
    }

    fun loadSettings() {
        val currentProfile = settingsRepository.getCurrentProfile()
        val sshHost = settingsRepository.getSshHost()
        val sshPort = settingsRepository.getSshPort().toString()
        val sshUsername = settingsRepository.getSshUsername()
        val sshPassword = settingsRepository.getSshPassword()
        val payload = settingsRepository.getPayload()
        val proxyHost = settingsRepository.getProxyHost()
        val proxyPort = settingsRepository.getProxyPort().toString()

        _uiState.update { state ->
            state.copy(
                themeMode = settingsRepository.getThemeMode(),
                currentProfile = currentProfile,
                profileList = settingsRepository.getProfiles().toList(),
                sshHost = sshHost,
                sshPort = sshPort,
                sshUsername = sshUsername,
                sshPassword = sshPassword,
                sshFullInput = "$sshHost:$sshPort@$sshUsername:$sshPassword",
                payload = payload,
                proxyHost = proxyHost,
                proxyPort = proxyPort,
                proxyFullInput = if (proxyHost.isEmpty()) "" else "$proxyHost:$proxyPort",
                sni = settingsRepository.getSni(),
                dns = settingsRepository.getDns(),
                udpgw = settingsRepository.getUdpgw(),
                autoPing = settingsRepository.getAutoPing(),
                pingAddress = settingsRepository.getPingAddress(),
                splitTunnelingEnabled = settingsRepository.getSplitTunnelingEnabled(),
                appsFilterMode = settingsRepository.getAppsFilterMode(),
                bypassApps = settingsRepository.getBypassApps(),
                killSwitchEnabled = settingsRepository.getKillSwitchEnabled(),
                forcingTls = settingsRepository.getForcingTls(),
                speedometerEnabled = settingsRepository.getSpeedometerEnabled(),
                autoReconnectEnabled = settingsRepository.getAutoReconnectEnabled(),
                ipAutoRefreshEnabled = settingsRepository.getIpAutoRefreshEnabled(),
                ipAutoRefreshInterval = settingsRepository.getIpAutoRefreshInterval(),
                hotshareWakeLockEnabled = settingsRepository.getHotshareWakeLockEnabled(),
                vpnWakeLockEnabled = settingsRepository.getVpnWakeLockEnabled(),
                keepAliveInterval = settingsRepository.getKeepAliveInterval(),
                autoCleanLogsEnabled = settingsRepository.getAutoCleanLogsEnabled(),
                autoCleanInterval = settingsRepository.getAutoCleanInterval(),
                maxLogLines = settingsRepository.getMaxLogLines(),
                isNativeSshLoadedState = NativeSshTunnel.isLibraryLoaded,
                isHevLoadedState = TProxyService.isLibraryLoaded,
                connectionLimitMinutes = settingsRepository.getConnectionLimitMinutes(),
                connectionLimitEnabled = settingsRepository.getConnectionLimitEnabled(),
                statusCardVisible = settingsRepository.getStatusCardVisible(),
                
                // HevSocks settings
                hevMtu = settingsRepository.getHevMtu(),
                hevMultiQueue = settingsRepository.getHevMultiQueue(),
                hevIpv4 = settingsRepository.getHevIpv4(),
                hevIpv6 = settingsRepository.getHevIpv6(),
                hevDnsPort = settingsRepository.getHevDnsPort(),
                hevDnsAddress = settingsRepository.getHevDnsAddress(),
                hevSocks5Port = settingsRepository.getHevSocks5Port(),
                hevSocks5Address = settingsRepository.getHevSocks5Address(),
                hevSocks5Udp = settingsRepository.getHevSocks5Udp(),
                hevTaskStackSize = settingsRepository.getHevTaskStackSize(),
                hevTcpBufferSize = settingsRepository.getHevTcpBufferSize(),
                hevUdpRecvBufferSize = settingsRepository.getHevUdpRecvBufferSize(),
                hevUdpCopyBufferNums = settingsRepository.getHevUdpCopyBufferNums(),
                hevMaxSessionCount = settingsRepository.getHevMaxSessionCount(),
                hevConnectTimeout = settingsRepository.getHevConnectTimeout(),
                hevTcpReadWriteTimeout = settingsRepository.getHevTcpReadWriteTimeout(),
                hevUdpReadWriteTimeout = settingsRepository.getHevUdpReadWriteTimeout(),
                hevLogFile = settingsRepository.getHevLogFile(),
                hevLogLevel = settingsRepository.getHevLogLevel()
            )
        }
        restartMonitors()
    }

    private fun observeLogs() {
        logRepository.setMaxLogLines(_uiState.value.maxLogLines)
    }

    private fun startConnectionStatusPolling() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val isActive = SiVpnService.isRunning
                val connState = SiVpnService.connectionState
                val startTime = SiVpnService.connectionStartTime

                val elapsed = if (isActive && connState == "CONNECTED" && startTime > 0L) {
                    (System.currentTimeMillis() - startTime) / 1000
                } else {
                    0L
                }

                _uiState.update {
                    it.copy(
                        isVpnActive = isActive,
                        connectionState = connState,
                        connectionStartTime = startTime,
                        elapsedSeconds = elapsed
                    )
                }
                delay(500)
            }
        }
    }

    fun restartMonitors() {
        restartPublicIpMonitor()
        restartPingMonitor()
    }

    fun restartPublicIpMonitor() {
        ipJob?.cancel()
        ipJob = viewModelScope.launch {
            val state = _uiState.value
            publicIpMonitor.monitorPublicIp(
                connectionState = state.connectionState,
                sshHost = state.sshHost,
                autoRefreshEnabled = state.ipAutoRefreshEnabled,
                intervalSeconds = state.ipAutoRefreshInterval,
                manualRefreshTrigger = 0
            ).collect { ip ->
                _uiState.update { it.copy(currentPublicIp = ip) }
            }
        }
    }

    fun restartPingMonitor() {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            val state = _uiState.value
            pingMonitor.monitorPing(
                connectionState = state.connectionState,
                sshHost = state.sshHost,
                sshPort = state.sshPort,
                pingAddress = state.pingAddress
            ).collect { latency ->
                _uiState.update { it.copy(currentPingMs = latency) }
            }
        }
    }

    fun startSpeedMonitor(context: Context) {
        speedJob?.cancel()
        speedJob = viewModelScope.launch {
            val state = _uiState.value
            speedMonitor.monitorSpeed(
                context = context,
                isVpnActive = state.isVpnActive,
                connectionState = state.connectionState,
                speedometerEnabled = state.speedometerEnabled
            ).collect { info ->
                _uiState.update {
                    it.copy(
                        rxSpeedBytesSec = info.rxBytesPerSec,
                        txSpeedBytesSec = info.txBytesPerSec
                    )
                }
            }
        }
    }

    // Profile Actions
    fun selectProfile(profile: String) {
        settingsRepository.setCurrentProfile(profile)
        loadSettings()
        logRepository.addLog("Switched profile to: $profile")
    }

    fun addProfile(name: String) {
        if (name.isNotBlank()) {
            settingsRepository.addProfile(name)
            selectProfile(name)
        }
    }

    fun deleteCurrentProfile(): Boolean {
        val list = settingsRepository.getProfiles()
        val current = settingsRepository.getCurrentProfile()
        if (list.size > 1) {
            settingsRepository.removeProfile(current)
            val newProfile = settingsRepository.getProfiles().firstOrNull() ?: "Default"
            selectProfile(newProfile)
            return true
        }
        return false
    }

    // Config Actions
    fun updateSshFullInput(input: String) {
        val creds = SshParser.parseFullInput(input, _uiState.value.sshPort)
        _uiState.update {
            it.copy(
                sshFullInput = input,
                sshHost = creds.host,
                sshPort = creds.port,
                sshUsername = creds.username,
                sshPassword = creds.password
            )
        }
        settingsRepository.setSshHost(creds.host)
        settingsRepository.setSshPort(creds.port.toIntOrNull() ?: 22)
        settingsRepository.setSshUsername(creds.username)
        settingsRepository.setSshPassword(creds.password)
    }

    fun updatePayload(payload: String) {
        _uiState.update { it.copy(payload = payload) }
        settingsRepository.setPayload(payload)
    }

    fun updateProxyFullInput(input: String) {
        val proxy = ProxyParser.parseFullInput(input, _uiState.value.proxyPort)
        _uiState.update {
            it.copy(
                proxyFullInput = input,
                proxyHost = proxy.host,
                proxyPort = proxy.port
            )
        }
        settingsRepository.setProxyHost(proxy.host)
        settingsRepository.setProxyPort(proxy.port.toIntOrNull() ?: 8080)
    }

    fun updateSni(sni: String) {
        _uiState.update { it.copy(sni = sni) }
        settingsRepository.setSni(sni)
    }

    fun updateDns(dns: String) {
        _uiState.update { it.copy(dns = dns) }
        settingsRepository.setDns(dns)
    }

    fun updateThemeMode(mode: Int) {
        _uiState.update { it.copy(themeMode = mode) }
        settingsRepository.setThemeMode(mode)
    }

    fun updateSpeedometerEnabled(enabled: Boolean) {
        _uiState.update { it.copy(speedometerEnabled = enabled) }
        settingsRepository.setSpeedometerEnabled(enabled)
        logRepository.addLog("Speedometer Real-time: $enabled")
    }

    fun updateSplitTunneling(enabled: Boolean, mode: String, apps: Set<String>) {
        _uiState.update {
            it.copy(
                splitTunnelingEnabled = enabled,
                appsFilterMode = mode,
                bypassApps = apps
            )
        }
        settingsRepository.setSplitTunnelingEnabled(enabled)
        settingsRepository.setAppsFilterMode(mode)
        settingsRepository.setBypassApps(apps)
    }

    fun updateKillSwitch(enabled: Boolean) {
        _uiState.update { it.copy(killSwitchEnabled = enabled) }
        settingsRepository.setKillSwitchEnabled(enabled)
    }


    // Clipboard & File import/export wrappers
    fun importFromClipboard(): Boolean {
        val content = configRepository.readFromClipboard()
        if (content.isNotEmpty()) {
            val success = configRepository.importConfigFromJson(content)
            if (success) {
                loadSettings()
                logRepository.addLog("Berhasil mengimpor dari clipboard.")
                return true
            } else {
                logRepository.addLog("Gagal mengimpor dari clipboard. Format tidak valid.")
            }
        }
        return false
    }

    fun exportToClipboard(): Boolean {
        val json = configRepository.exportConfigAsJson()
        return configRepository.copyToClipboard("SIVPN Config", json)
    }

    fun importConfigContent(content: String): Boolean {
        val success = configRepository.importConfigFromJson(content)
        if (success) {
            loadSettings()
            logRepository.addLog("Berhasil mengimpor konfigurasi dari berkas.")
        } else {
            logRepository.addLog("Gagal mengimpor: Berkas tidak valid.")
        }
        return success
    }

    fun exportConfigContent(): String {
        return configRepository.exportConfigAsJson()
    }
}
