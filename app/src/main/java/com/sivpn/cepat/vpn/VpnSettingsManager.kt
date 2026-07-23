package com.sivpn.cepat.vpn

import android.content.Context
import com.tencent.mmkv.MMKV

object VpnSettingsManager {
    private const val PREFS_NAME = "sivpn_settings"
    
    @Volatile private var initialized = false
    private fun settings(c: Context): MMKV {
        if (!initialized) { synchronized(this) { if (!initialized) { MMKV.initialize(c.applicationContext); initialized = true } } }
        return MMKV.mmkvWithID(PREFS_NAME, MMKV.SINGLE_PROCESS_MODE).also { m ->
            if (!m.decodeBool("migrated", false)) {
                listOf(PREFS_NAME, c.packageName + "_preferences").forEach { p ->
                    try {
                        val prefs = c.getSharedPreferences(p, Context.MODE_PRIVATE)
                        if (prefs.all.isNotEmpty()) { m.importFromSharedPreferences(prefs); prefs.edit().clear().apply() }
                    } catch (_: Exception) {}
                }
                m.encode("migrated", true)
            }
        }
    }

    private fun getStr(c: Context, k: String, d: String) = settings(c).decodeString(k, d) ?: d
    private fun setStr(c: Context, k: String, v: String) = settings(c).encode(k, v)
    private fun getInt(c: Context, k: String, d: Int) = try { settings(c).decodeInt(k, d) } catch(_: Exception) { d }
    private fun setInt(c: Context, k: String, v: Int) = settings(c).encode(k, v)
    private fun getBool(c: Context, k: String, d: Boolean) = settings(c).decodeBool(k, d)
    private fun setBool(c: Context, k: String, v: Boolean) = settings(c).encode(k, v)
    private fun getSet(c: Context, k: String, d: Set<String>) = settings(c).decodeStringSet(k, d) ?: d
    private fun setSet(c: Context, k: String, v: Set<String>) = settings(c).encode(k, v)

    fun getThemeMode(c: Context) = getInt(c, "theme_mode", 0)
    fun setThemeMode(c: Context, v: Int) = setInt(c, "theme_mode", v)
    
    fun getSshHost(c: Context) = getStr(c, "ssh_host", "yu.xhmt.web.id")
    fun setSshHost(c: Context, v: String) = setStr(c, "ssh_host", v)
    fun getSshPort(c: Context) = getInt(c, "ssh_port", 80).coerceIn(1, 65535)
    fun setSshPort(c: Context, v: Int) = setInt(c, "ssh_port", v.coerceIn(1, 65535))
    fun getSshUsername(c: Context) = getStr(c, "ssh_username", "80@xxxxxxxxxx")
    fun setSshUsername(c: Context, v: String) = setStr(c, "ssh_username", v)
    fun getSshPassword(c: Context) = getStr(c, "ssh_password", "x")
    fun setSshPassword(c: Context, v: String) = setStr(c, "ssh_password", v)
    
    fun getPayload(c: Context) = getStr(c, "payload", "GET /cdn-cgi/trace HTTP/1.1[crlf]Host: open.spotify.com[crlf][crlf]")
    fun setPayload(c: Context, v: String) = setStr(c, "payload", v)
    fun getProxyHost(c: Context) = getStr(c, "proxy_host", "investors.spotify.com")
    fun setProxyHost(c: Context, v: String) = setStr(c, "proxy_host", v)
    fun getProxyPort(c: Context) = getInt(c, "proxy_port", 80).coerceIn(1, 65535)
    fun setProxyPort(c: Context, v: Int) = setInt(c, "proxy_port", v.coerceIn(1, 65535))
    
    fun getAutoReconnectEnabled(c: Context) = getBool(c, "auto_reconnect_enabled", true)
    fun setAutoReconnectEnabled(c: Context, v: Boolean) = setBool(c, "auto_reconnect_enabled", v)
    fun getSni(c: Context) = getStr(c, "sni", "investors.spotify.com")
    fun setSni(c: Context, v: String) = setStr(c, "sni", v)
    
    fun getDns(c: Context): String {
        val saved = settings(c).decodeString("dns", null)
        if (saved == null || saved == "8.8.8.8:8.8.4.4") {
            setStr(c, "dns", "94.140.14.14:94.140.15.15")
            return "94.140.14.14:94.140.15.15"
        }
        return saved
    }
    fun setDns(c: Context, v: String) = setStr(c, "dns", v)
    
    fun getUdpgw(c: Context) = getStr(c, "udpgw", "127.0.0.1:7300")
    fun setUdpgw(c: Context, v: String) = setStr(c, "udpgw", v)
    fun getAutoPing(c: Context) = getBool(c, "auto_ping", false)
    fun setAutoPing(c: Context, v: Boolean) = setBool(c, "auto_ping", v)
    fun getForcingTls(c: Context) = getStr(c, "forcing_tls", "Auto")
    fun setForcingTls(c: Context, v: String) = setStr(c, "forcing_tls", v)
    
    fun getCurrentProfile(c: Context) = getStr(c, "current_profile", "Default")
    fun setCurrentProfile(c: Context, v: String) = setStr(c, "current_profile", v)
    fun getProfiles(c: Context) = getSet(c, "profiles_list", setOf("Default", "Premium SG", "VIP US"))
    fun addProfile(c: Context, v: String) {
        val cur = getProfiles(c).toMutableSet()
        cur.add(v)
        setSet(c, "profiles_list", cur)
    }
    fun removeProfile(c: Context, v: String) {
        val cur = getProfiles(c).toMutableSet()
        if (cur.size > 1) {
            cur.remove(v)
            setSet(c, "profiles_list", cur)
            if (getCurrentProfile(c) == v) setCurrentProfile(c, cur.first())
        }
    }
    
    fun getConnectionLimitMinutes(c: Context) = getInt(c, "connection_limit_minutes", 1).coerceAtLeast(1)
    fun setConnectionLimitMinutes(c: Context, v: Int) = setInt(c, "connection_limit_minutes", v.coerceAtLeast(1))
    fun getConnectionLimitEnabled(c: Context) = getBool(c, "connection_limit_enabled", false)
    fun setConnectionLimitEnabled(c: Context, v: Boolean) = setBool(c, "connection_limit_enabled", v)
    
    fun getStatusCardVisible(c: Context) = getBool(c, "status_card_visible", true)
    fun setStatusCardVisible(c: Context, v: Boolean) = setBool(c, "status_card_visible", v)
    fun getPingAddress(c: Context) = getStr(c, "ping_address", "")
    fun setPingAddress(c: Context, v: String) = setStr(c, "ping_address", v)
    
    fun getBypassApps(c: Context) = getSet(c, "bypass_apps_list", emptySet())
    fun setBypassApps(c: Context, v: Set<String>) = setSet(c, "bypass_apps_list", v)
    fun getSplitTunnelingEnabled(c: Context) = getBool(c, "split_tunneling_enabled", false)
    fun setSplitTunnelingEnabled(c: Context, v: Boolean) = setBool(c, "split_tunneling_enabled", v)
    fun getAppsFilterMode(c: Context) = getStr(c, "apps_filter_mode", "bypass")
    fun setAppsFilterMode(c: Context, v: String) = setStr(c, "apps_filter_mode", v)
    
    fun getKillSwitchEnabled(c: Context) = getBool(c, "kill_switch_enabled", false)
    fun setKillSwitchEnabled(c: Context, v: Boolean) = setBool(c, "kill_switch_enabled", v)
    fun getSpeedometerEnabled(c: Context) = getBool(c, "speedometer_enabled", true)
    fun setSpeedometerEnabled(c: Context, v: Boolean) = setBool(c, "speedometer_enabled", v)
    
    fun getHotshareSocksPort(c: Context) = getInt(c, "hotshare_socks_port", 1080).coerceIn(1, 65535)
    fun setHotshareSocksPort(c: Context, v: Int) = setInt(c, "hotshare_socks_port", v.coerceIn(1, 65535))
    fun getHotshareHttpPort(c: Context) = getInt(c, "hotshare_http_port", 8080).coerceIn(1, 65535)
    fun setHotshareHttpPort(c: Context, v: Int) = setInt(c, "hotshare_http_port", v.coerceIn(1, 65535))
    
    fun getIpAutoRefreshEnabled(c: Context) = getBool(c, "ip_auto_refresh_enabled", true)
    fun setIpAutoRefreshEnabled(c: Context, v: Boolean) = setBool(c, "ip_auto_refresh_enabled", v)
    fun getIpAutoRefreshInterval(c: Context) = getInt(c, "ip_auto_refresh_interval", 15).coerceAtLeast(1)
    fun setIpAutoRefreshInterval(c: Context, v: Int) = setInt(c, "ip_auto_refresh_interval", v.coerceAtLeast(1))
    
    fun getHotshareWakeLockEnabled(c: Context) = getBool(c, "hotshare_wakelock_enabled", true)
    fun setHotshareWakeLockEnabled(c: Context, v: Boolean) = setBool(c, "hotshare_wakelock_enabled", v)
    fun getVpnWakeLockEnabled(c: Context) = getBool(c, "vpn_wakelock_enabled", true)
    fun setVpnWakeLockEnabled(c: Context, v: Boolean) = setBool(c, "vpn_wakelock_enabled", v)
    
    fun getKeepAliveInterval(c: Context) = getInt(c, "keep_alive_interval", 30).coerceAtLeast(1)
    fun setKeepAliveInterval(c: Context, v: Int) = setInt(c, "keep_alive_interval", v.coerceAtLeast(1))
    
    fun getAutoCleanLogsEnabled(c: Context) = getBool(c, "auto_clean_logs_enabled", false)
    fun setAutoCleanLogsEnabled(c: Context, v: Boolean) = setBool(c, "auto_clean_logs_enabled", v)
    fun getAutoCleanInterval(c: Context) = getInt(c, "auto_clean_logs_interval", 10).coerceAtLeast(1)
    fun setAutoCleanInterval(c: Context, v: Int) = setInt(c, "auto_clean_logs_interval", v.coerceAtLeast(1))
    fun getMaxLogLines(c: Context) = getInt(c, "max_log_lines", 1000).coerceAtLeast(1)
    fun setMaxLogLines(c: Context, v: Int) = setInt(c, "max_log_lines", v.coerceAtLeast(1))
    
    fun getHevMtu(c: Context) = getInt(c, "hev_mtu", 8500)
    fun setHevMtu(c: Context, v: Int) = setInt(c, "hev_mtu", v)
    fun getHevMultiQueue(c: Context) = getBool(c, "hev_multi_queue", false)
    fun setHevMultiQueue(c: Context, v: Boolean) = setBool(c, "hev_multi_queue", v)
    fun getHevIpv4(c: Context) = getStr(c, "hev_ipv4", "198.18.0.1")
    fun setHevIpv4(c: Context, v: String) = setStr(c, "hev_ipv4", v)
    fun getHevIpv6(c: Context) = getStr(c, "hev_ipv6", "fc00::1")
    fun setHevIpv6(c: Context, v: String) = setStr(c, "hev_ipv6", v)
    fun getHevDnsPort(c: Context) = getInt(c, "hev_dns_port", 53).coerceIn(1, 65535)
    fun setHevDnsPort(c: Context, v: Int) = setInt(c, "hev_dns_port", v.coerceIn(1, 65535))
    fun getHevDnsAddress(c: Context) = getStr(c, "hev_dns_address", "94.140.14.14")
    fun setHevDnsAddress(c: Context, v: String) = setStr(c, "hev_dns_address", v)
    fun getHevSocks5Port(c: Context) = getInt(c, "hev_socks5_port", 1080).coerceIn(1, 65535)
    fun setHevSocks5Port(c: Context, v: Int) = setInt(c, "hev_socks5_port", v.coerceIn(1, 65535))
    fun getHevSocks5Address(c: Context) = getStr(c, "hev_socks5_address", "127.0.0.1")
    fun setHevSocks5Address(c: Context, v: String) = setStr(c, "hev_socks5_address", v)
    fun getHevSocks5Udp(c: Context) = getStr(c, "hev_socks5_udp", "udp")
    fun setHevSocks5Udp(c: Context, v: String) = setStr(c, "hev_socks5_udp", v)
    fun getHevTaskStackSize(c: Context) = getInt(c, "hev_task_stack_size", 86016)
    fun setHevTaskStackSize(c: Context, v: Int) = setInt(c, "hev_task_stack_size", v)
    fun getHevTcpBufferSize(c: Context) = getInt(c, "hev_tcp_buffer_size", 65536)
    fun setHevTcpBufferSize(c: Context, v: Int) = setInt(c, "hev_tcp_buffer_size", v)
    fun getHevUdpRecvBufferSize(c: Context) = getInt(c, "hev_udp_recv_buffer_size", 524288)
    fun setHevUdpRecvBufferSize(c: Context, v: Int) = setInt(c, "hev_udp_recv_buffer_size", v)
    fun getHevUdpCopyBufferNums(c: Context) = getInt(c, "hev_udp_copy_buffer_nums", 10)
    fun setHevUdpCopyBufferNums(c: Context, v: Int) = setInt(c, "hev_udp_copy_buffer_nums", v)
    fun getHevMaxSessionCount(c: Context) = getInt(c, "hev_max_session_count", 0)
    fun setHevMaxSessionCount(c: Context, v: Int) = setInt(c, "hev_max_session_count", v)
    fun getHevConnectTimeout(c: Context) = getInt(c, "hev_connect_timeout", 10000)
    fun setHevConnectTimeout(c: Context, v: Int) = setInt(c, "hev_connect_timeout", v)
    fun getHevTcpReadWriteTimeout(c: Context) = getInt(c, "hev_tcp_read_write_timeout", 300000)
    fun setHevTcpReadWriteTimeout(c: Context, v: Int) = setInt(c, "hev_tcp_read_write_timeout", v)
    fun getHevUdpReadWriteTimeout(c: Context) = getInt(c, "hev_udp_read_write_timeout", 60000)
    fun setHevUdpReadWriteTimeout(c: Context, v: Int) = setInt(c, "hev_udp_read_write_timeout", v)
    fun getHevLogFile(c: Context) = getStr(c, "hev_log_file", "stderr")
    fun setHevLogFile(c: Context, v: String) = setStr(c, "hev_log_file", v)
    fun getHevLogLevel(c: Context) = getStr(c, "hev_log_level", "warn")
    fun setHevLogLevel(c: Context, v: String) = setStr(c, "hev_log_level", v)
}
