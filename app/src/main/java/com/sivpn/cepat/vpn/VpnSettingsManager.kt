package com.sivpn.cepat.vpn

import android.content.Context
import com.tencent.mmkv.MMKV

object VpnSettingsManager {
    private const val PREFS_NAME = "sivpn_settings"
    private const val LEGACY_DEFAULT_PREFS_SUFFIX = "_preferences"
    private val defaultProfiles = setOf("Default", "Premium SG", "VIP US")


    private fun sanitizePort(value: Int, defaultValue: Int): Int =
        if (value in 1..65535) value else defaultValue

    private fun sanitizePositive(value: Int, defaultValue: Int): Int =
        if (value > 0) value else defaultValue

    private inline fun <T> readOrDefault(defaultValue: T, block: () -> T): T =
        try {
            block()
        } catch (_: Exception) {
            defaultValue
        }

    @Volatile
    private var initialized = false

    private fun settings(context: Context): MMKV {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    MMKV.initialize(context.applicationContext)
                    initialized = true
                }
            }
        }

        return MMKV.mmkvWithID(PREFS_NAME, MMKV.SINGLE_PROCESS_MODE).also { mmkv ->
            migrateSharedPreferencesIfNeeded(context, mmkv)
        }
    }

    private fun migrateSharedPreferencesIfNeeded(context: Context, mmkv: MMKV) {
        val migrationKey = "__shared_preferences_migrated"
        if (mmkv.decodeBool(migrationKey, false)) return

        val legacyPreferenceNames = linkedSetOf(
            PREFS_NAME,
            context.packageName + LEGACY_DEFAULT_PREFS_SUFFIX
        )

        legacyPreferenceNames.forEach { prefsName ->
            try {
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                if (prefs.all.isNotEmpty()) {
                    mmkv.importFromSharedPreferences(prefs)
                    prefs.edit().clear().apply()
                }
            } catch (_: Exception) {
                // Keep MMKV as the source of truth even if a legacy file is corrupt/unreadable.
            }
        }
        mmkv.encode(migrationKey, true)
    }


    fun getThemeMode(context: Context): Int =
        settings(context)
            .decodeInt("theme_mode", 0) // 0: Auto, 1: Light, 2: Dark

    fun setThemeMode(context: Context, value: Int) {
        settings(context).encode("theme_mode", value)
    }

    fun getSshHost(context: Context): String =
        settings(context)
            .decodeString("ssh_host", "yu.xhmt.web.id") ?: "yu.xhmt.web.id"

    fun setSshHost(context: Context, value: String) {
        settings(context).encode("ssh_host", value)
    }

    fun getSshPort(context: Context): Int =
        readOrDefault(80) { sanitizePort(settings(context).decodeInt("ssh_port", 80), 80) }

    fun setSshPort(context: Context, value: Int) {
        settings(context).encode("ssh_port", sanitizePort(value, 80))
    }

    fun getSshUsername(context: Context): String =
        settings(context)
            .decodeString("ssh_username", "80@xxxxxxxxxx") ?: "80@xxxxxxxxxx"

    fun setSshUsername(context: Context, value: String) {
        settings(context).encode("ssh_username", value)
    }

    fun getSshPassword(context: Context): String =
        settings(context)
            .decodeString("ssh_password", "x") ?: "x"

    fun setSshPassword(context: Context, value: String) {
        settings(context).encode("ssh_password", value)
    }

    fun getPayload(context: Context): String =
        settings(context).decodeString(
            "payload",
            "GET /cdn-cgi/trace HTTP/1.1[crlf]Host: open.spotify.com[crlf][crlf]"
        ) ?: "GET /cdn-cgi/trace HTTP/1.1[crlf]Host: open.spotify.com[crlf][crlf]"

    fun setPayload(context: Context, value: String) {
        settings(context).encode("payload", value)
    }

    fun getProxyHost(context: Context): String =
        settings(context)
            .decodeString("proxy_host", "investors.spotify.com") ?: "investors.spotify.com"

    fun setProxyHost(context: Context, value: String) {
        settings(context).encode("proxy_host", value)
    }

    fun getProxyPort(context: Context): Int =
        readOrDefault(80) { sanitizePort(settings(context).decodeInt("proxy_port", 80), 80) }

    fun setProxyPort(context: Context, value: Int) {
        settings(context).encode("proxy_port", sanitizePort(value, 80))
    }

    fun getAutoReconnectEnabled(context: Context): Boolean =
        settings(context)
            .decodeBool("auto_reconnect_enabled", true)

    fun setAutoReconnectEnabled(context: Context, value: Boolean) {
        settings(context).encode("auto_reconnect_enabled", value)
    }
    fun getSni(context: Context): String =
        settings(context)
            .decodeString("sni", "investors.spotify.com") ?: "investors.spotify.com"

    fun setSni(context: Context, value: String) {
        settings(context).encode("sni", value)
    }

    fun getDns(context: Context): String {
        val prefs = settings(context)
        val saved = prefs.decodeString("dns", null)
        if (saved == null || saved == "8.8.8.8:8.8.4.4") {
            prefs.encode("dns", "94.140.14.14:94.140.15.15")
            return "94.140.14.14:94.140.15.15"
        }
        return saved
    }

    fun setDns(context: Context, value: String) {
        settings(context).encode("dns", value)
    }

    fun getUdpgw(context: Context): String =
        settings(context)
            .decodeString("udpgw", "127.0.0.1:7300") ?: "127.0.0.1:7300"

    fun setUdpgw(context: Context, value: String) {
        settings(context).encode("udpgw", value)
    }

    fun getAutoPing(context: Context): Boolean =
        settings(context)
            .decodeBool("auto_ping", false)

    fun setAutoPing(context: Context, value: Boolean) {
        settings(context).encode("auto_ping", value)
    }

    fun getForcingTls(context: Context): String =
        settings(context)
            .decodeString("forcing_tls", "Auto") ?: "Auto"

    fun setForcingTls(context: Context, value: String) {
        settings(context).encode("forcing_tls", value)
    }

    fun getCurrentProfile(context: Context): String =
        settings(context)
            .decodeString("current_profile", "Default") ?: "Default"

    fun setCurrentProfile(context: Context, value: String) {
        settings(context).encode("current_profile", value)
    }

    fun getProfiles(context: Context): Set<String> =
        settings(context)
            .decodeStringSet("profiles_list", defaultProfiles) ?: defaultProfiles

    fun addProfile(context: Context, value: String) {
        val current = getProfiles(context).toMutableSet()
        current.add(value)
        settings(context).encode("profiles_list", current)
    }

    fun removeProfile(context: Context, value: String) {
        val current = getProfiles(context).toMutableSet()
        if (current.size > 1) {
            current.remove(value)
            settings(context).encode("profiles_list", current)
            if (getCurrentProfile(context) == value) {
                setCurrentProfile(context, current.first())
            }
        }
    }

    fun getConnectionLimitMinutes(context: Context): Int =
        readOrDefault(1) { sanitizePositive(settings(context).decodeInt("connection_limit_minutes", 1), 1) }

    fun setConnectionLimitMinutes(context: Context, value: Int) {
        settings(context).encode("connection_limit_minutes", sanitizePositive(value, 1))
    }

    fun getConnectionLimitEnabled(context: Context): Boolean =
        settings(context)
            .decodeBool("connection_limit_enabled", false)

    fun setConnectionLimitEnabled(context: Context, value: Boolean) {
        settings(context).encode("connection_limit_enabled", value)
    }

    fun getStatusCardVisible(context: Context): Boolean =
        settings(context)
            .decodeBool("status_card_visible", true)

    fun setStatusCardVisible(context: Context, value: Boolean) {
        settings(context).encode("status_card_visible", value)
    }

    fun getPingAddress(context: Context): String =
        settings(context)
            .decodeString("ping_address", "") ?: ""

    fun setPingAddress(context: Context, value: String) {
        settings(context).encode("ping_address", value)
    }

    fun getBypassApps(context: Context): Set<String> =
        settings(context)
            .decodeStringSet("bypass_apps_list", emptySet()) ?: emptySet()

    fun setBypassApps(context: Context, value: Set<String>) {
        settings(context).encode("bypass_apps_list", value)
    }

    fun getSplitTunnelingEnabled(context: Context): Boolean =
        settings(context)
            .decodeBool("split_tunneling_enabled", false)

    fun setSplitTunnelingEnabled(context: Context, value: Boolean) {
        settings(context).encode("split_tunneling_enabled", value)
    }

    fun getAppsFilterMode(context: Context): String =
        settings(context)
            .decodeString("apps_filter_mode", "bypass") ?: "bypass"

    fun setAppsFilterMode(context: Context, value: String) {
        settings(context).encode("apps_filter_mode", value)
    }

    fun getKillSwitchEnabled(context: Context): Boolean =
        settings(context)
            .decodeBool("kill_switch_enabled", false)

    fun setKillSwitchEnabled(context: Context, value: Boolean) {
        settings(context).encode("kill_switch_enabled", value)
    }

    fun getSpeedometerEnabled(context: Context): Boolean =
        settings(context)
            .decodeBool("speedometer_enabled", true)

    fun setSpeedometerEnabled(context: Context, value: Boolean) {
        settings(context).encode("speedometer_enabled", value)
    }

    fun getHotshareSocksPort(context: Context): Int =
        readOrDefault(1080) { sanitizePort(settings(context).decodeInt("hotshare_socks_port", 1080), 1080) }

    fun setHotshareSocksPort(context: Context, value: Int) {
        settings(context).encode("hotshare_socks_port", sanitizePort(value, 1080))
    }

    fun getHotshareHttpPort(context: Context): Int =
        readOrDefault(8080) { sanitizePort(settings(context).decodeInt("hotshare_http_port", 8080), 8080) }

    fun setHotshareHttpPort(context: Context, value: Int) {
        settings(context).encode("hotshare_http_port", sanitizePort(value, 8080))
    }

    fun getIpAutoRefreshEnabled(context: Context): Boolean =
        settings(context)
            .decodeBool("ip_auto_refresh_enabled", true)

    fun setIpAutoRefreshEnabled(context: Context, value: Boolean) {
        settings(context).encode("ip_auto_refresh_enabled", value)
    }

    fun getIpAutoRefreshInterval(context: Context): Int =
        readOrDefault(15) { sanitizePositive(settings(context).decodeInt("ip_auto_refresh_interval", 15), 15) }

    fun setIpAutoRefreshInterval(context: Context, value: Int) {
        settings(context).encode("ip_auto_refresh_interval", sanitizePositive(value, 15))
    }

    fun getHotshareWakeLockEnabled(context: Context): Boolean =
        settings(context)
            .decodeBool("hotshare_wakelock_enabled", true)

    fun setHotshareWakeLockEnabled(context: Context, value: Boolean) {
        settings(context).encode("hotshare_wakelock_enabled", value)
    }

    fun getVpnWakeLockEnabled(context: Context): Boolean =
        settings(context)
            .decodeBool("vpn_wakelock_enabled", true)

    fun setVpnWakeLockEnabled(context: Context, value: Boolean) {
        settings(context).encode("vpn_wakelock_enabled", value)
    }

    fun getKeepAliveInterval(context: Context): Int =
        readOrDefault(30) { sanitizePositive(settings(context).decodeInt("keep_alive_interval", 30), 30) } // default 30 seconds

    fun setKeepAliveInterval(context: Context, value: Int) {
        settings(context).encode("keep_alive_interval", sanitizePositive(value, 30))
    }

    fun getAutoCleanLogsEnabled(context: Context): Boolean =
        settings(context)
            .decodeBool("auto_clean_logs_enabled", false)

    fun setAutoCleanLogsEnabled(context: Context, value: Boolean) {
        settings(context).encode("auto_clean_logs_enabled", value)
    }

    fun getAutoCleanInterval(context: Context): Int =
        readOrDefault(10) { sanitizePositive(settings(context).decodeInt("auto_clean_logs_interval", 10), 10) } // default 10 minutes

    fun setAutoCleanInterval(context: Context, value: Int) {
        settings(context).encode("auto_clean_logs_interval", sanitizePositive(value, 10))
    }

    fun getMaxLogLines(context: Context): Int =
        readOrDefault(1000) { sanitizePositive(settings(context).decodeInt("max_log_lines", 1000), 1000) } // default 1000 lines

    fun setMaxLogLines(context: Context, value: Int) {
        settings(context).encode("max_log_lines", sanitizePositive(value, 1000))
    }

    fun getHevMtu(context: Context): Int =
        settings(context)
            .decodeInt("hev_mtu", 8500)

    fun setHevMtu(context: Context, value: Int) {
        settings(context).encode("hev_mtu", value)
    }

    fun getHevMultiQueue(context: Context): Boolean =
        settings(context)
            .decodeBool("hev_multi_queue", false)

    fun setHevMultiQueue(context: Context, value: Boolean) {
        settings(context).encode("hev_multi_queue", value)
    }

    fun getHevIpv4(context: Context): String =
        settings(context)
            .decodeString("hev_ipv4", "198.18.0.1") ?: "198.18.0.1"

    fun setHevIpv4(context: Context, value: String) {
        settings(context).encode("hev_ipv4", value)
    }

    fun getHevIpv6(context: Context): String =
        settings(context)
            .decodeString("hev_ipv6", "fc00::1") ?: "fc00::1"

    fun setHevIpv6(context: Context, value: String) {
        settings(context).encode("hev_ipv6", value)
    }

    fun getHevDnsPort(context: Context): Int =
        readOrDefault(53) { sanitizePort(settings(context).decodeInt("hev_dns_port", 53), 53) }

    fun setHevDnsPort(context: Context, value: Int) {
        settings(context).encode("hev_dns_port", sanitizePort(value, 53))
    }

    fun getHevDnsAddress(context: Context): String =
        settings(context)
            .decodeString("hev_dns_address", "94.140.14.14") ?: "94.140.14.14"

    fun setHevDnsAddress(context: Context, value: String) {
        settings(context).encode("hev_dns_address", value)
    }

    fun getHevSocks5Port(context: Context): Int =
        readOrDefault(1080) { sanitizePort(settings(context).decodeInt("hev_socks5_port", 1080), 1080) }

    fun setHevSocks5Port(context: Context, value: Int) {
        settings(context).encode("hev_socks5_port", sanitizePort(value, 1080))
    }

    fun getHevSocks5Address(context: Context): String =
        settings(context)
            .decodeString("hev_socks5_address", "127.0.0.1") ?: "127.0.0.1"

    fun setHevSocks5Address(context: Context, value: String) {
        settings(context).encode("hev_socks5_address", value)
    }

    fun getHevSocks5Udp(context: Context): String =
        settings(context)
            .decodeString("hev_socks5_udp", "udp") ?: "udp"

    fun setHevSocks5Udp(context: Context, value: String) {
        settings(context).encode("hev_socks5_udp", value)
    }

    fun getHevTaskStackSize(context: Context): Int =
        settings(context)
            .decodeInt("hev_task_stack_size", 86016)

    fun setHevTaskStackSize(context: Context, value: Int) {
        settings(context).encode("hev_task_stack_size", value)
    }

    fun getHevTcpBufferSize(context: Context): Int =
        settings(context)
            .decodeInt("hev_tcp_buffer_size", 65536)

    fun setHevTcpBufferSize(context: Context, value: Int) {
        settings(context).encode("hev_tcp_buffer_size", value)
    }

    fun getHevUdpRecvBufferSize(context: Context): Int =
        settings(context)
            .decodeInt("hev_udp_recv_buffer_size", 524288)

    fun setHevUdpRecvBufferSize(context: Context, value: Int) {
        settings(context).encode("hev_udp_recv_buffer_size", value)
    }

    fun getHevUdpCopyBufferNums(context: Context): Int =
        settings(context)
            .decodeInt("hev_udp_copy_buffer_nums", 10)

    fun setHevUdpCopyBufferNums(context: Context, value: Int) {
        settings(context).encode("hev_udp_copy_buffer_nums", value)
    }

    fun getHevMaxSessionCount(context: Context): Int =
        settings(context)
            .decodeInt("hev_max_session_count", 0)

    fun setHevMaxSessionCount(context: Context, value: Int) {
        settings(context).encode("hev_max_session_count", value)
    }

    fun getHevConnectTimeout(context: Context): Int =
        settings(context)
            .decodeInt("hev_connect_timeout", 10000)

    fun setHevConnectTimeout(context: Context, value: Int) {
        settings(context).encode("hev_connect_timeout", value)
    }

    fun getHevTcpReadWriteTimeout(context: Context): Int =
        settings(context)
            .decodeInt("hev_tcp_read_write_timeout", 300000)

    fun setHevTcpReadWriteTimeout(context: Context, value: Int) {
        settings(context).encode("hev_tcp_read_write_timeout", value)
    }

    fun getHevUdpReadWriteTimeout(context: Context): Int =
        settings(context)
            .decodeInt("hev_udp_read_write_timeout", 60000)

    fun setHevUdpReadWriteTimeout(context: Context, value: Int) {
        settings(context).encode("hev_udp_read_write_timeout", value)
    }

    fun getHevLogFile(context: Context): String =
        settings(context)
            .decodeString("hev_log_file", "stderr") ?: "stderr"

    fun setHevLogFile(context: Context, value: String) {
        settings(context).encode("hev_log_file", value)
    }

    fun getHevLogLevel(context: Context): String =
        settings(context)
            .decodeString("hev_log_level", "warn") ?: "warn"

    fun setHevLogLevel(context: Context, value: String) {
        settings(context).encode("hev_log_level", value)
    }

    fun exportConfigAsJson(context: Context): String {
        val json = org.json.JSONObject()
        json.put("ssh_host", getSshHost(context))
        json.put("ssh_port", getSshPort(context))
        json.put("ssh_username", getSshUsername(context))
        json.put("ssh_password", getSshPassword(context))
        json.put("payload", getPayload(context))
        json.put("proxy_host", getProxyHost(context))
        json.put("proxy_port", getProxyPort(context))
        json.put("sni", getSni(context))
        json.put("dns", getDns(context))
        json.put("udpgw", getUdpgw(context))
        json.put("auto_ping", getAutoPing(context))
        json.put("forcing_tls", getForcingTls(context))
        json.put("connection_limit_minutes", getConnectionLimitMinutes(context))
        json.put("connection_limit_enabled", getConnectionLimitEnabled(context))
        json.put("ping_address", getPingAddress(context))
        json.put("split_tunneling_enabled", getSplitTunnelingEnabled(context))
        json.put("apps_filter_mode", getAppsFilterMode(context))
        json.put("kill_switch_enabled", getKillSwitchEnabled(context))
        json.put("speedometer_enabled", getSpeedometerEnabled(context))
        json.put("hotshare_socks_port", getHotshareSocksPort(context))
        json.put("hotshare_http_port", getHotshareHttpPort(context))
        json.put("ip_auto_refresh_enabled", getIpAutoRefreshEnabled(context))
        json.put("ip_auto_refresh_interval", getIpAutoRefreshInterval(context))
        json.put("hotshare_wakelock_enabled", getHotshareWakeLockEnabled(context))
        json.put("vpn_wakelock_enabled", getVpnWakeLockEnabled(context))
        json.put("keep_alive_interval", getKeepAliveInterval(context))
        json.put("auto_clean_logs_enabled", getAutoCleanLogsEnabled(context))
        json.put("auto_clean_logs_interval", getAutoCleanInterval(context))
        json.put("max_log_lines", getMaxLogLines(context))

        // HevSocks configs
        json.put("hev_mtu", getHevMtu(context))
        json.put("hev_multi_queue", getHevMultiQueue(context))
        json.put("hev_ipv4", getHevIpv4(context))
        json.put("hev_ipv6", getHevIpv6(context))
        json.put("hev_dns_port", getHevDnsPort(context))
        json.put("hev_dns_address", getHevDnsAddress(context))
        json.put("hev_socks5_port", getHevSocks5Port(context))
        json.put("hev_socks5_address", getHevSocks5Address(context))
        json.put("hev_socks5_udp", getHevSocks5Udp(context))
        json.put("hev_task_stack_size", getHevTaskStackSize(context))
        json.put("hev_tcp_buffer_size", getHevTcpBufferSize(context))
        json.put("hev_udp_recv_buffer_size", getHevUdpRecvBufferSize(context))
        json.put("hev_udp_copy_buffer_nums", getHevUdpCopyBufferNums(context))
        json.put("hev_max_session_count", getHevMaxSessionCount(context))
        json.put("hev_connect_timeout", getHevConnectTimeout(context))
        json.put("hev_tcp_read_write_timeout", getHevTcpReadWriteTimeout(context))
        json.put("hev_udp_read_write_timeout", getHevUdpReadWriteTimeout(context))
        json.put("hev_log_file", getHevLogFile(context))
        json.put("hev_log_level", getHevLogLevel(context))

        val bypassAppsJson = org.json.JSONArray()
        getBypassApps(context).forEach { bypassAppsJson.put(it) }
        json.put("bypass_apps_list", bypassAppsJson)

        val jsonString = json.toString()
        val base64Encoded = android.util.Base64.encodeToString(jsonString.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        return "SIVPN://" + base64Encoded
    }

    fun importConfigFromJson(context: Context, encodedString: String): Boolean {
        return try {
            val contentToDecode = if (encodedString.startsWith("SIVPN://")) {
                encodedString.substring("SIVPN://".length)
            } else {
                encodedString
            }
            // Allow backward compatibility with plain JSON string
            val jsonString = if (contentToDecode.trim().startsWith("{")) {
                contentToDecode
            } else {
                val decodedBytes = android.util.Base64.decode(contentToDecode, android.util.Base64.DEFAULT)
                String(decodedBytes, Charsets.UTF_8)
            }
            val json = org.json.JSONObject(jsonString)
            if (json.has("ssh_host")) setSshHost(context, json.getString("ssh_host"))
            if (json.has("ssh_port")) setSshPort(context, json.getInt("ssh_port"))
            if (json.has("ssh_username")) setSshUsername(context, json.getString("ssh_username"))
            if (json.has("ssh_password")) setSshPassword(context, json.getString("ssh_password"))
            if (json.has("payload")) setPayload(context, json.getString("payload"))
            if (json.has("proxy_host")) setProxyHost(context, json.getString("proxy_host"))
            if (json.has("proxy_port")) setProxyPort(context, json.getInt("proxy_port"))
            if (json.has("sni")) setSni(context, json.getString("sni"))
            if (json.has("dns")) setDns(context, json.getString("dns"))
            if (json.has("udpgw")) setUdpgw(context, json.getString("udpgw"))
            if (json.has("auto_ping")) setAutoPing(context, json.getBoolean("auto_ping"))
            if (json.has("forcing_tls")) setForcingTls(context, json.getString("forcing_tls"))
            if (json.has("connection_limit_minutes")) setConnectionLimitMinutes(context, json.getInt("connection_limit_minutes"))
            if (json.has("connection_limit_enabled")) setConnectionLimitEnabled(context, json.getBoolean("connection_limit_enabled"))
            if (json.has("ping_address")) setPingAddress(context, json.getString("ping_address"))
            if (json.has("split_tunneling_enabled")) setSplitTunnelingEnabled(context, json.getBoolean("split_tunneling_enabled"))
            if (json.has("apps_filter_mode")) setAppsFilterMode(context, json.getString("apps_filter_mode"))
            if (json.has("kill_switch_enabled")) setKillSwitchEnabled(context, json.getBoolean("kill_switch_enabled"))
            if (json.has("speedometer_enabled")) setSpeedometerEnabled(context, json.getBoolean("speedometer_enabled"))
            if (json.has("hotshare_socks_port")) setHotshareSocksPort(context, json.getInt("hotshare_socks_port"))
            if (json.has("hotshare_http_port")) setHotshareHttpPort(context, json.getInt("hotshare_http_port"))
            if (json.has("ip_auto_refresh_enabled")) setIpAutoRefreshEnabled(context, json.getBoolean("ip_auto_refresh_enabled"))
            if (json.has("ip_auto_refresh_interval")) setIpAutoRefreshInterval(context, json.getInt("ip_auto_refresh_interval"))
            if (json.has("hotshare_wakelock_enabled")) setHotshareWakeLockEnabled(context, json.getBoolean("hotshare_wakelock_enabled"))
            if (json.has("vpn_wakelock_enabled")) setVpnWakeLockEnabled(context, json.getBoolean("vpn_wakelock_enabled"))
            if (json.has("keep_alive_interval")) setKeepAliveInterval(context, json.getInt("keep_alive_interval"))
            if (json.has("auto_clean_logs_enabled")) setAutoCleanLogsEnabled(context, json.getBoolean("auto_clean_logs_enabled"))
            if (json.has("auto_clean_logs_interval")) setAutoCleanInterval(context, json.getInt("auto_clean_logs_interval"))
            if (json.has("max_log_lines")) setMaxLogLines(context, json.getInt("max_log_lines"))

            if (json.has("hev_mtu")) setHevMtu(context, json.getInt("hev_mtu"))
            if (json.has("hev_multi_queue")) setHevMultiQueue(context, json.getBoolean("hev_multi_queue"))
            if (json.has("hev_ipv4")) setHevIpv4(context, json.getString("hev_ipv4"))
            if (json.has("hev_ipv6")) setHevIpv6(context, json.getString("hev_ipv6"))
            if (json.has("hev_dns_port")) setHevDnsPort(context, json.getInt("hev_dns_port"))
            if (json.has("hev_dns_address")) setHevDnsAddress(context, json.getString("hev_dns_address"))
            if (json.has("hev_socks5_port")) setHevSocks5Port(context, json.getInt("hev_socks5_port"))
            if (json.has("hev_socks5_address")) setHevSocks5Address(context, json.getString("hev_socks5_address"))
            if (json.has("hev_socks5_udp")) setHevSocks5Udp(context, json.getString("hev_socks5_udp"))
            if (json.has("hev_task_stack_size")) setHevTaskStackSize(context, json.getInt("hev_task_stack_size"))
            if (json.has("hev_tcp_buffer_size")) setHevTcpBufferSize(context, json.getInt("hev_tcp_buffer_size"))
            if (json.has("hev_udp_recv_buffer_size")) setHevUdpRecvBufferSize(context, json.getInt("hev_udp_recv_buffer_size"))
            if (json.has("hev_udp_copy_buffer_nums")) setHevUdpCopyBufferNums(context, json.getInt("hev_udp_copy_buffer_nums"))
            if (json.has("hev_max_session_count")) setHevMaxSessionCount(context, json.getInt("hev_max_session_count"))
            if (json.has("hev_connect_timeout")) setHevConnectTimeout(context, json.getInt("hev_connect_timeout"))
            if (json.has("hev_tcp_read_write_timeout")) setHevTcpReadWriteTimeout(context, json.getInt("hev_tcp_read_write_timeout"))
            if (json.has("hev_udp_read_write_timeout")) setHevUdpReadWriteTimeout(context, json.getInt("hev_udp_read_write_timeout"))
            if (json.has("hev_log_file")) setHevLogFile(context, json.getString("hev_log_file"))
            if (json.has("hev_log_level")) setHevLogLevel(context, json.getString("hev_log_level"))

            if (json.has("bypass_apps_list")) {
                val array = json.getJSONArray("bypass_apps_list")
                val bypassSet = mutableSetOf<String>()
                for (i in 0 until array.length()) {
                    bypassSet.add(array.getString(i))
                }
                setBypassApps(context, bypassSet)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("VpnSettingsManager", "Error importing config", e)
            false
        }
    }
}
