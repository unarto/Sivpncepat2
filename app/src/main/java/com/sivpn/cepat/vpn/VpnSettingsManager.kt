package com.sivpn.cepat.vpn

import android.content.Context
import com.tencent.mmkv.MMKV

/**
 * Manages all application settings securely using MMKV.
 * Provides thread-safe, high-performance configuration storage.
 */
object VpnSettingsManager {
    private const val PREFS_NAME = "sivpn_settings"
    
    internal object Keys {
        const val MIGRATED = "migrated"
        const val THEME_MODE = "theme_mode"
        const val SSH_HOST = "ssh_host"
        const val SSH_PORT = "ssh_port"
        const val SSH_USERNAME = "ssh_username"
        const val SSH_PASSWORD = "ssh_password"
        const val PAYLOAD = "payload"
        const val PROXY_HOST = "proxy_host"
        const val PROXY_PORT = "proxy_port"
        const val AUTO_RECONNECT_ENABLED = "auto_reconnect_enabled"
        const val SNI = "sni"
        const val DNS = "dns"
        const val UDPGW = "udpgw"
        const val AUTO_PING = "auto_ping"
        const val FORCING_TLS = "forcing_tls"
        const val CURRENT_PROFILE = "current_profile"
        const val PROFILES_LIST = "profiles_list"
        const val CONNECTION_LIMIT_MINUTES = "connection_limit_minutes"
        const val CONNECTION_LIMIT_ENABLED = "connection_limit_enabled"
        const val STATUS_CARD_VISIBLE = "status_card_visible"
        const val PING_ADDRESS = "ping_address"
        const val BYPASS_APPS_LIST = "bypass_apps_list"
        const val SPLIT_TUNNELING_ENABLED = "split_tunneling_enabled"
        const val APPS_FILTER_MODE = "apps_filter_mode"
        const val KILL_SWITCH_ENABLED = "kill_switch_enabled"
        const val SPEEDOMETER_ENABLED = "speedometer_enabled"
        const val HOTSHARE_SOCKS_PORT = "hotshare_socks_port"
        const val HOTSHARE_HTTP_PORT = "hotshare_http_port"
        const val IP_AUTO_REFRESH_ENABLED = "ip_auto_refresh_enabled"
        const val IP_AUTO_REFRESH_INTERVAL = "ip_auto_refresh_interval"
        const val HOTSHARE_WAKELOCK_ENABLED = "hotshare_wakelock_enabled"
        const val VPN_WAKELOCK_ENABLED = "vpn_wakelock_enabled"
        const val KEEP_ALIVE_INTERVAL = "keep_alive_interval"
        const val AUTO_CLEAN_LOGS_ENABLED = "auto_clean_logs_enabled"
        const val AUTO_CLEAN_LOGS_INTERVAL = "auto_clean_logs_interval"
        const val MAX_LOG_LINES = "max_log_lines"
        
        const val HEV_MTU = "hev_mtu"
        const val HEV_MULTI_QUEUE = "hev_multi_queue"
        const val HEV_IPV4 = "hev_ipv4"
        const val HEV_IPV6 = "hev_ipv6"
        const val HEV_DNS_PORT = "hev_dns_port"
        const val HEV_DNS_ADDRESS = "hev_dns_address"
        const val HEV_SOCKS5_PORT = "hev_socks5_port"
        const val HEV_SOCKS5_ADDRESS = "hev_socks5_address"
        const val HEV_SOCKS5_UDP = "hev_socks5_udp"
        const val HEV_TASK_STACK_SIZE = "hev_task_stack_size"
        const val HEV_TCP_BUFFER_SIZE = "hev_tcp_buffer_size"
        const val HEV_UDP_RECV_BUFFER_SIZE = "hev_udp_recv_buffer_size"
        const val HEV_UDP_COPY_BUFFER_NUMS = "hev_udp_copy_buffer_nums"
        const val HEV_MAX_SESSION_COUNT = "hev_max_session_count"
        const val HEV_CONNECT_TIMEOUT = "hev_connect_timeout"
        const val HEV_TCP_READ_WRITE_TIMEOUT = "hev_tcp_read_write_timeout"
        const val HEV_UDP_READ_WRITE_TIMEOUT = "hev_udp_read_write_timeout"
        const val HEV_LOG_FILE = "hev_log_file"
        const val HEV_LOG_LEVEL = "hev_log_level"
    }

    @Volatile
    private var mmkvInstance: MMKV? = null

    /**
     * Thread-safe initialization and migration of MMKV instance.
     * Prevents multiple initializations and ensures backward compatibility with SharedPreferences.
     */
    private fun settings(c: Context): MMKV {
        return mmkvInstance ?: synchronized(this) {
            mmkvInstance ?: run {
                MMKV.initialize(c.applicationContext)
                val m = MMKV.mmkvWithID(PREFS_NAME, MMKV.SINGLE_PROCESS_MODE)
                if (!m.decodeBool(Keys.MIGRATED, false)) {
                    listOf(PREFS_NAME, c.packageName + "_preferences").forEach { p ->
                        try {
                            val prefs = c.getSharedPreferences(p, Context.MODE_PRIVATE)
                            if (prefs.all.isNotEmpty()) {
                                m.importFromSharedPreferences(prefs)
                                prefs.edit().clear().apply()
                            }
                        } catch (_: Exception) {}
                    }
                    m.encode(Keys.MIGRATED, true)
                }
                m.also { mmkvInstance = it }
            }
        }
    }

    // --- Core Operations ---
    private fun getStr(c: Context, k: String, d: String): String = settings(c).decodeString(k, d) ?: d
    private fun setStr(c: Context, k: String, v: String) = settings(c).encode(k, v)
    
    private fun getInt(c: Context, k: String, d: Int): Int = try { settings(c).decodeInt(k, d) } catch(_: Exception) { d }
    private fun setInt(c: Context, k: String, v: Int) = settings(c).encode(k, v)
    
    private fun getBool(c: Context, k: String, d: Boolean): Boolean = settings(c).decodeBool(k, d)
    private fun setBool(c: Context, k: String, v: Boolean) = settings(c).encode(k, v)
    
    private fun getSet(c: Context, k: String, d: Set<String>): Set<String> = settings(c).decodeStringSet(k, d) ?: d
    private fun setSet(c: Context, k: String, v: Set<String>) = settings(c).encode(k, v)

    // --- Getters and Setters ---
    
    fun getThemeMode(c: Context) = getInt(c, Keys.THEME_MODE, 0)
    fun setThemeMode(c: Context, v: Int) = setInt(c, Keys.THEME_MODE, v)
    
    fun getSshHost(c: Context) = getStr(c, Keys.SSH_HOST, "yu.xhmt.web.id")
    fun setSshHost(c: Context, v: String) = setStr(c, Keys.SSH_HOST, v.trim())
    
    fun getSshPort(c: Context) = getInt(c, Keys.SSH_PORT, 80).coerceIn(1, 65535)
    fun setSshPort(c: Context, v: Int) = setInt(c, Keys.SSH_PORT, v.coerceIn(1, 65535))
    
    fun getSshUsername(c: Context) = getStr(c, Keys.SSH_USERNAME, "80@xxxxxxxxxx")
    fun setSshUsername(c: Context, v: String) = setStr(c, Keys.SSH_USERNAME, v)
    
    fun getSshPassword(c: Context) = getStr(c, Keys.SSH_PASSWORD, "x")
    fun setSshPassword(c: Context, v: String) = setStr(c, Keys.SSH_PASSWORD, v)
    
    fun getPayload(c: Context) = getStr(c, Keys.PAYLOAD, "GET /cdn-cgi/trace HTTP/1.1[crlf]Host: open.spotify.com[crlf][crlf]")
    fun setPayload(c: Context, v: String) = setStr(c, Keys.PAYLOAD, v)
    
    fun getProxyHost(c: Context) = getStr(c, Keys.PROXY_HOST, "investors.spotify.com")
    fun setProxyHost(c: Context, v: String) = setStr(c, Keys.PROXY_HOST, v.trim())
    
    fun getProxyPort(c: Context) = getInt(c, Keys.PROXY_PORT, 80).coerceIn(1, 65535)
    fun setProxyPort(c: Context, v: Int) = setInt(c, Keys.PROXY_PORT, v.coerceIn(1, 65535))
    
    fun getAutoReconnectEnabled(c: Context) = getBool(c, Keys.AUTO_RECONNECT_ENABLED, true)
    fun setAutoReconnectEnabled(c: Context, v: Boolean) = setBool(c, Keys.AUTO_RECONNECT_ENABLED, v)
    
    fun getSni(c: Context) = getStr(c, Keys.SNI, "investors.spotify.com")
    fun setSni(c: Context, v: String) = setStr(c, Keys.SNI, v.trim())
    
    /**
     * Retrieves DNS with a fallback mechanism that automatically migrates
     * outdated DNS configs to AdGuard DNS.
     */
    fun getDns(c: Context): String {
        val saved = settings(c).decodeString(Keys.DNS, null)
        if (saved == null || saved == "8.8.8.8:8.8.4.4") {
            val defaultDns = "94.140.14.14:94.140.15.15"
            setStr(c, Keys.DNS, defaultDns)
            return defaultDns
        }
        return saved
    }
    fun setDns(c: Context, v: String) = setStr(c, Keys.DNS, v.trim())
    
    fun getUdpgw(c: Context) = getStr(c, Keys.UDPGW, "127.0.0.1:7300")
    fun setUdpgw(c: Context, v: String) = setStr(c, Keys.UDPGW, v.trim())
    
    fun getAutoPing(c: Context) = getBool(c, Keys.AUTO_PING, false)
    fun setAutoPing(c: Context, v: Boolean) = setBool(c, Keys.AUTO_PING, v)
    
    fun getForcingTls(c: Context) = getStr(c, Keys.FORCING_TLS, "Auto")
    fun setForcingTls(c: Context, v: String) = setStr(c, Keys.FORCING_TLS, v)
    
    fun getCurrentProfile(c: Context) = getStr(c, Keys.CURRENT_PROFILE, "Default")
    fun setCurrentProfile(c: Context, v: String) = setStr(c, Keys.CURRENT_PROFILE, v)
    
    fun getProfiles(c: Context): Set<String> {
        val defaultProfiles = setOf("Default", "Premium SG", "VIP US")
        val saved = getSet(c, Keys.PROFILES_LIST, defaultProfiles)
        return LinkedHashSet(saved)
    }
    
    fun addProfile(c: Context, v: String) {
        val cur = LinkedHashSet(getProfiles(c))
        cur.add(v)
        setSet(c, Keys.PROFILES_LIST, cur)
    }
    
    fun removeProfile(c: Context, v: String) {
        val cur = LinkedHashSet(getProfiles(c))
        if (cur.size > 1) {
            cur.remove(v)
            setSet(c, Keys.PROFILES_LIST, cur)
            if (getCurrentProfile(c) == v) {
                setCurrentProfile(c, cur.first())
            }
        }
    }
    
    fun getConnectionLimitMinutes(c: Context) = getInt(c, Keys.CONNECTION_LIMIT_MINUTES, 1).coerceAtLeast(1)
    fun setConnectionLimitMinutes(c: Context, v: Int) = setInt(c, Keys.CONNECTION_LIMIT_MINUTES, v.coerceAtLeast(1))
    
    fun getConnectionLimitEnabled(c: Context) = getBool(c, Keys.CONNECTION_LIMIT_ENABLED, false)
    fun setConnectionLimitEnabled(c: Context, v: Boolean) = setBool(c, Keys.CONNECTION_LIMIT_ENABLED, v)
    
    fun getStatusCardVisible(c: Context) = getBool(c, Keys.STATUS_CARD_VISIBLE, true)
    fun setStatusCardVisible(c: Context, v: Boolean) = setBool(c, Keys.STATUS_CARD_VISIBLE, v)
    
    fun getPingAddress(c: Context) = getStr(c, Keys.PING_ADDRESS, "")
    fun setPingAddress(c: Context, v: String) = setStr(c, Keys.PING_ADDRESS, v.trim())
    
    fun getBypassApps(c: Context): Set<String> = LinkedHashSet(getSet(c, Keys.BYPASS_APPS_LIST, emptySet()))
    fun setBypassApps(c: Context, v: Set<String>) = setSet(c, Keys.BYPASS_APPS_LIST, LinkedHashSet(v))
    
    fun getSplitTunnelingEnabled(c: Context) = getBool(c, Keys.SPLIT_TUNNELING_ENABLED, false)
    fun setSplitTunnelingEnabled(c: Context, v: Boolean) = setBool(c, Keys.SPLIT_TUNNELING_ENABLED, v)
    
    fun getAppsFilterMode(c: Context) = getStr(c, Keys.APPS_FILTER_MODE, "bypass")
    fun setAppsFilterMode(c: Context, v: String) = setStr(c, Keys.APPS_FILTER_MODE, v)
    
    fun getKillSwitchEnabled(c: Context) = getBool(c, Keys.KILL_SWITCH_ENABLED, false)
    fun setKillSwitchEnabled(c: Context, v: Boolean) = setBool(c, Keys.KILL_SWITCH_ENABLED, v)
    
    fun getSpeedometerEnabled(c: Context) = getBool(c, Keys.SPEEDOMETER_ENABLED, true)
    fun setSpeedometerEnabled(c: Context, v: Boolean) = setBool(c, Keys.SPEEDOMETER_ENABLED, v)
    
    fun getHotshareSocksPort(c: Context) = getInt(c, Keys.HOTSHARE_SOCKS_PORT, 1080).coerceIn(1, 65535)
    fun setHotshareSocksPort(c: Context, v: Int) = setInt(c, Keys.HOTSHARE_SOCKS_PORT, v.coerceIn(1, 65535))
    
    fun getHotshareHttpPort(c: Context) = getInt(c, Keys.HOTSHARE_HTTP_PORT, 8080).coerceIn(1, 65535)
    fun setHotshareHttpPort(c: Context, v: Int) = setInt(c, Keys.HOTSHARE_HTTP_PORT, v.coerceIn(1, 65535))
    
    fun getIpAutoRefreshEnabled(c: Context) = getBool(c, Keys.IP_AUTO_REFRESH_ENABLED, true)
    fun setIpAutoRefreshEnabled(c: Context, v: Boolean) = setBool(c, Keys.IP_AUTO_REFRESH_ENABLED, v)
    
    fun getIpAutoRefreshInterval(c: Context) = getInt(c, Keys.IP_AUTO_REFRESH_INTERVAL, 15).coerceAtLeast(1)
    fun setIpAutoRefreshInterval(c: Context, v: Int) = setInt(c, Keys.IP_AUTO_REFRESH_INTERVAL, v.coerceAtLeast(1))
    
    fun getHotshareWakeLockEnabled(c: Context) = getBool(c, Keys.HOTSHARE_WAKELOCK_ENABLED, true)
    fun setHotshareWakeLockEnabled(c: Context, v: Boolean) = setBool(c, Keys.HOTSHARE_WAKELOCK_ENABLED, v)
    
    fun getVpnWakeLockEnabled(c: Context) = getBool(c, Keys.VPN_WAKELOCK_ENABLED, true)
    fun setVpnWakeLockEnabled(c: Context, v: Boolean) = setBool(c, Keys.VPN_WAKELOCK_ENABLED, v)
    
    fun getKeepAliveInterval(c: Context) = getInt(c, Keys.KEEP_ALIVE_INTERVAL, 30).coerceAtLeast(1)
    fun setKeepAliveInterval(c: Context, v: Int) = setInt(c, Keys.KEEP_ALIVE_INTERVAL, v.coerceAtLeast(1))
    
    fun getAutoCleanLogsEnabled(c: Context) = getBool(c, Keys.AUTO_CLEAN_LOGS_ENABLED, false)
    fun setAutoCleanLogsEnabled(c: Context, v: Boolean) = setBool(c, Keys.AUTO_CLEAN_LOGS_ENABLED, v)
    
    fun getAutoCleanInterval(c: Context) = getInt(c, Keys.AUTO_CLEAN_LOGS_INTERVAL, 10).coerceAtLeast(1)
    fun setAutoCleanInterval(c: Context, v: Int) = setInt(c, Keys.AUTO_CLEAN_LOGS_INTERVAL, v.coerceAtLeast(1))
    
    fun getMaxLogLines(c: Context) = getInt(c, Keys.MAX_LOG_LINES, 1000).coerceAtLeast(1)
    fun setMaxLogLines(c: Context, v: Int) = setInt(c, Keys.MAX_LOG_LINES, v.coerceAtLeast(1))
    
    // --- HevSocks5Tunnel Configurations ---
    
    fun getHevMtu(c: Context) = getInt(c, Keys.HEV_MTU, 8500).coerceAtLeast(576)
    fun setHevMtu(c: Context, v: Int) = setInt(c, Keys.HEV_MTU, v.coerceAtLeast(576))
    
    fun getHevMultiQueue(c: Context) = getBool(c, Keys.HEV_MULTI_QUEUE, false)
    fun setHevMultiQueue(c: Context, v: Boolean) = setBool(c, Keys.HEV_MULTI_QUEUE, v)
    
    fun getHevIpv4(c: Context) = getStr(c, Keys.HEV_IPV4, "198.18.0.1")
    fun setHevIpv4(c: Context, v: String) = setStr(c, Keys.HEV_IPV4, v.trim())
    
    fun getHevIpv6(c: Context) = getStr(c, Keys.HEV_IPV6, "fc00::1")
    fun setHevIpv6(c: Context, v: String) = setStr(c, Keys.HEV_IPV6, v.trim())
    
    fun getHevDnsPort(c: Context) = getInt(c, Keys.HEV_DNS_PORT, 53).coerceIn(1, 65535)
    fun setHevDnsPort(c: Context, v: Int) = setInt(c, Keys.HEV_DNS_PORT, v.coerceIn(1, 65535))
    
    fun getHevDnsAddress(c: Context) = getStr(c, Keys.HEV_DNS_ADDRESS, "94.140.14.14")
    fun setHevDnsAddress(c: Context, v: String) = setStr(c, Keys.HEV_DNS_ADDRESS, v.trim())
    
    fun getHevSocks5Port(c: Context) = getInt(c, Keys.HEV_SOCKS5_PORT, 1080).coerceIn(1, 65535)
    fun setHevSocks5Port(c: Context, v: Int) = setInt(c, Keys.HEV_SOCKS5_PORT, v.coerceIn(1, 65535))
    
    fun getHevSocks5Address(c: Context) = getStr(c, Keys.HEV_SOCKS5_ADDRESS, "127.0.0.1")
    fun setHevSocks5Address(c: Context, v: String) = setStr(c, Keys.HEV_SOCKS5_ADDRESS, v.trim())
    
    fun getHevSocks5Udp(c: Context) = getStr(c, Keys.HEV_SOCKS5_UDP, "udp")
    fun setHevSocks5Udp(c: Context, v: String) = setStr(c, Keys.HEV_SOCKS5_UDP, v)
    
    fun getHevTaskStackSize(c: Context) = getInt(c, Keys.HEV_TASK_STACK_SIZE, 86016).coerceAtLeast(8192)
    fun setHevTaskStackSize(c: Context, v: Int) = setInt(c, Keys.HEV_TASK_STACK_SIZE, v.coerceAtLeast(8192))
    
    fun getHevTcpBufferSize(c: Context) = getInt(c, Keys.HEV_TCP_BUFFER_SIZE, 65536).coerceAtLeast(4096)
    fun setHevTcpBufferSize(c: Context, v: Int) = setInt(c, Keys.HEV_TCP_BUFFER_SIZE, v.coerceAtLeast(4096))
    
    fun getHevUdpRecvBufferSize(c: Context) = getInt(c, Keys.HEV_UDP_RECV_BUFFER_SIZE, 524288).coerceAtLeast(4096)
    fun setHevUdpRecvBufferSize(c: Context, v: Int) = setInt(c, Keys.HEV_UDP_RECV_BUFFER_SIZE, v.coerceAtLeast(4096))
    
    fun getHevUdpCopyBufferNums(c: Context) = getInt(c, Keys.HEV_UDP_COPY_BUFFER_NUMS, 10).coerceAtLeast(1)
    fun setHevUdpCopyBufferNums(c: Context, v: Int) = setInt(c, Keys.HEV_UDP_COPY_BUFFER_NUMS, v.coerceAtLeast(1))
    
    fun getHevMaxSessionCount(c: Context) = getInt(c, Keys.HEV_MAX_SESSION_COUNT, 0).coerceAtLeast(0)
    fun setHevMaxSessionCount(c: Context, v: Int) = setInt(c, Keys.HEV_MAX_SESSION_COUNT, v.coerceAtLeast(0))
    
    fun getHevConnectTimeout(c: Context) = getInt(c, Keys.HEV_CONNECT_TIMEOUT, 10000).coerceAtLeast(1000)
    fun setHevConnectTimeout(c: Context, v: Int) = setInt(c, Keys.HEV_CONNECT_TIMEOUT, v.coerceAtLeast(1000))
    
    fun getHevTcpReadWriteTimeout(c: Context) = getInt(c, Keys.HEV_TCP_READ_WRITE_TIMEOUT, 300000).coerceAtLeast(1000)
    fun setHevTcpReadWriteTimeout(c: Context, v: Int) = setInt(c, Keys.HEV_TCP_READ_WRITE_TIMEOUT, v.coerceAtLeast(1000))
    
    fun getHevUdpReadWriteTimeout(c: Context) = getInt(c, Keys.HEV_UDP_READ_WRITE_TIMEOUT, 60000).coerceAtLeast(1000)
    fun setHevUdpReadWriteTimeout(c: Context, v: Int) = setInt(c, Keys.HEV_UDP_READ_WRITE_TIMEOUT, v.coerceAtLeast(1000))
    
    fun getHevLogFile(c: Context) = getStr(c, Keys.HEV_LOG_FILE, "stderr")
    fun setHevLogFile(c: Context, v: String) = setStr(c, Keys.HEV_LOG_FILE, v)
    
    fun getHevLogLevel(c: Context) = getStr(c, Keys.HEV_LOG_LEVEL, "warn")
    fun setHevLogLevel(c: Context, v: String) = setStr(c, Keys.HEV_LOG_LEVEL, v)
}
