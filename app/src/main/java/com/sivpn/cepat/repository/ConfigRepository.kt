package com.sivpn.cepat.repository

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.sivpn.cepat.vpn.LogManager
import com.sivpn.cepat.vpn.VpnSettingsManager

class ConfigRepository(private val context: Context) {

    fun exportConfigAsJson(): String {
        val json = org.json.JSONObject()
        json.put("ssh_host", VpnSettingsManager.getSshHost(context))
        json.put("ssh_port", VpnSettingsManager.getSshPort(context))
        json.put("ssh_username", VpnSettingsManager.getSshUsername(context))
        json.put("ssh_password", VpnSettingsManager.getSshPassword(context))
        json.put("payload", VpnSettingsManager.getPayload(context))
        json.put("proxy_host", VpnSettingsManager.getProxyHost(context))
        json.put("proxy_port", VpnSettingsManager.getProxyPort(context))
        json.put("sni", VpnSettingsManager.getSni(context))
        json.put("dns", VpnSettingsManager.getDns(context))
        json.put("udpgw", VpnSettingsManager.getUdpgw(context))
        json.put("auto_ping", VpnSettingsManager.getAutoPing(context))
        json.put("forcing_tls", VpnSettingsManager.getForcingTls(context))
        json.put("connection_limit_minutes", VpnSettingsManager.getConnectionLimitMinutes(context))
        json.put("connection_limit_enabled", VpnSettingsManager.getConnectionLimitEnabled(context))
        json.put("ping_address", VpnSettingsManager.getPingAddress(context))
        json.put("split_tunneling_enabled", VpnSettingsManager.getSplitTunnelingEnabled(context))
        json.put("apps_filter_mode", VpnSettingsManager.getAppsFilterMode(context))
        json.put("kill_switch_enabled", VpnSettingsManager.getKillSwitchEnabled(context))
        json.put("speedometer_enabled", VpnSettingsManager.getSpeedometerEnabled(context))
        json.put("hotshare_socks_port", VpnSettingsManager.getHotshareSocksPort(context))
        json.put("hotshare_http_port", VpnSettingsManager.getHotshareHttpPort(context))
        json.put("ip_auto_refresh_enabled", VpnSettingsManager.getIpAutoRefreshEnabled(context))
        json.put("ip_auto_refresh_interval", VpnSettingsManager.getIpAutoRefreshInterval(context))
        json.put("hotshare_wakelock_enabled", VpnSettingsManager.getHotshareWakeLockEnabled(context))
        json.put("vpn_wakelock_enabled", VpnSettingsManager.getVpnWakeLockEnabled(context))
        json.put("keep_alive_interval", VpnSettingsManager.getKeepAliveInterval(context))
        json.put("auto_clean_logs_enabled", VpnSettingsManager.getAutoCleanLogsEnabled(context))
        json.put("auto_clean_logs_interval", VpnSettingsManager.getAutoCleanInterval(context))
        json.put("max_log_lines", VpnSettingsManager.getMaxLogLines(context))

        json.put("hev_mtu", VpnSettingsManager.getHevMtu(context))
        json.put("hev_multi_queue", VpnSettingsManager.getHevMultiQueue(context))
        json.put("hev_ipv4", VpnSettingsManager.getHevIpv4(context))
        json.put("hev_ipv6", VpnSettingsManager.getHevIpv6(context))
        json.put("hev_dns_port", VpnSettingsManager.getHevDnsPort(context))
        json.put("hev_dns_address", VpnSettingsManager.getHevDnsAddress(context))
        json.put("hev_socks5_port", VpnSettingsManager.getHevSocks5Port(context))
        json.put("hev_socks5_address", VpnSettingsManager.getHevSocks5Address(context))
        json.put("hev_socks5_udp", VpnSettingsManager.getHevSocks5Udp(context))
        json.put("hev_task_stack_size", VpnSettingsManager.getHevTaskStackSize(context))
        json.put("hev_tcp_buffer_size", VpnSettingsManager.getHevTcpBufferSize(context))
        json.put("hev_udp_recv_buffer_size", VpnSettingsManager.getHevUdpRecvBufferSize(context))
        json.put("hev_udp_copy_buffer_nums", VpnSettingsManager.getHevUdpCopyBufferNums(context))
        json.put("hev_max_session_count", VpnSettingsManager.getHevMaxSessionCount(context))
        json.put("hev_connect_timeout", VpnSettingsManager.getHevConnectTimeout(context))
        json.put("hev_tcp_read_write_timeout", VpnSettingsManager.getHevTcpReadWriteTimeout(context))
        json.put("hev_udp_read_write_timeout", VpnSettingsManager.getHevUdpReadWriteTimeout(context))
        json.put("hev_log_file", VpnSettingsManager.getHevLogFile(context))
        json.put("hev_log_level", VpnSettingsManager.getHevLogLevel(context))

        val bypassAppsJson = org.json.JSONArray()
        VpnSettingsManager.getBypassApps(context).forEach { bypassAppsJson.put(it) }
        json.put("bypass_apps_list", bypassAppsJson)

        val jsonString = json.toString()
        val base64Encoded = android.util.Base64.encodeToString(jsonString.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        return "SIVPN://" + base64Encoded
    }

    fun importConfigFromJson(encodedString: String): Boolean {
        return try {
            val contentToDecode = if (encodedString.startsWith("SIVPN://")) {
                encodedString.substring("SIVPN://".length)
            } else {
                encodedString
            }
            val jsonString = if (contentToDecode.trim().startsWith("{")) {
                contentToDecode
            } else {
                val decodedBytes = android.util.Base64.decode(contentToDecode, android.util.Base64.DEFAULT)
                String(decodedBytes, Charsets.UTF_8)
            }
            val json = org.json.JSONObject(jsonString)
            
            if (json.has("ssh_host")) VpnSettingsManager.setSshHost(context, json.getString("ssh_host"))
            if (json.has("ssh_port")) VpnSettingsManager.setSshPort(context, json.getInt("ssh_port"))
            if (json.has("ssh_username")) VpnSettingsManager.setSshUsername(context, json.getString("ssh_username"))
            if (json.has("ssh_password")) VpnSettingsManager.setSshPassword(context, json.getString("ssh_password"))
            if (json.has("payload")) VpnSettingsManager.setPayload(context, json.getString("payload"))
            if (json.has("proxy_host")) VpnSettingsManager.setProxyHost(context, json.getString("proxy_host"))
            if (json.has("proxy_port")) VpnSettingsManager.setProxyPort(context, json.getInt("proxy_port"))
            if (json.has("sni")) VpnSettingsManager.setSni(context, json.getString("sni"))
            if (json.has("dns")) VpnSettingsManager.setDns(context, json.getString("dns"))
            if (json.has("udpgw")) VpnSettingsManager.setUdpgw(context, json.getString("udpgw"))
            if (json.has("auto_ping")) VpnSettingsManager.setAutoPing(context, json.getBoolean("auto_ping"))
            if (json.has("forcing_tls")) VpnSettingsManager.setForcingTls(context, json.getString("forcing_tls"))
            if (json.has("connection_limit_minutes")) VpnSettingsManager.setConnectionLimitMinutes(context, json.getInt("connection_limit_minutes"))
            if (json.has("connection_limit_enabled")) VpnSettingsManager.setConnectionLimitEnabled(context, json.getBoolean("connection_limit_enabled"))
            if (json.has("ping_address")) VpnSettingsManager.setPingAddress(context, json.getString("ping_address"))
            if (json.has("split_tunneling_enabled")) VpnSettingsManager.setSplitTunnelingEnabled(context, json.getBoolean("split_tunneling_enabled"))
            if (json.has("apps_filter_mode")) VpnSettingsManager.setAppsFilterMode(context, json.getString("apps_filter_mode"))
            if (json.has("kill_switch_enabled")) VpnSettingsManager.setKillSwitchEnabled(context, json.getBoolean("kill_switch_enabled"))
            if (json.has("speedometer_enabled")) VpnSettingsManager.setSpeedometerEnabled(context, json.getBoolean("speedometer_enabled"))
            if (json.has("hotshare_socks_port")) VpnSettingsManager.setHotshareSocksPort(context, json.getInt("hotshare_socks_port"))
            if (json.has("hotshare_http_port")) VpnSettingsManager.setHotshareHttpPort(context, json.getInt("hotshare_http_port"))
            if (json.has("ip_auto_refresh_enabled")) VpnSettingsManager.setIpAutoRefreshEnabled(context, json.getBoolean("ip_auto_refresh_enabled"))
            if (json.has("ip_auto_refresh_interval")) VpnSettingsManager.setIpAutoRefreshInterval(context, json.getInt("ip_auto_refresh_interval"))
            if (json.has("hotshare_wakelock_enabled")) VpnSettingsManager.setHotshareWakeLockEnabled(context, json.getBoolean("hotshare_wakelock_enabled"))
            if (json.has("vpn_wakelock_enabled")) VpnSettingsManager.setVpnWakeLockEnabled(context, json.getBoolean("vpn_wakelock_enabled"))
            if (json.has("keep_alive_interval")) VpnSettingsManager.setKeepAliveInterval(context, json.getInt("keep_alive_interval"))
            if (json.has("auto_clean_logs_enabled")) VpnSettingsManager.setAutoCleanLogsEnabled(context, json.getBoolean("auto_clean_logs_enabled"))
            if (json.has("auto_clean_logs_interval")) VpnSettingsManager.setAutoCleanInterval(context, json.getInt("auto_clean_logs_interval"))
            if (json.has("max_log_lines")) VpnSettingsManager.setMaxLogLines(context, json.getInt("max_log_lines"))

            if (json.has("hev_mtu")) VpnSettingsManager.setHevMtu(context, json.getInt("hev_mtu"))
            if (json.has("hev_multi_queue")) VpnSettingsManager.setHevMultiQueue(context, json.getBoolean("hev_multi_queue"))
            if (json.has("hev_ipv4")) VpnSettingsManager.setHevIpv4(context, json.getString("hev_ipv4"))
            if (json.has("hev_ipv6")) VpnSettingsManager.setHevIpv6(context, json.getString("hev_ipv6"))
            if (json.has("hev_dns_port")) VpnSettingsManager.setHevDnsPort(context, json.getInt("hev_dns_port"))
            if (json.has("hev_dns_address")) VpnSettingsManager.setHevDnsAddress(context, json.getString("hev_dns_address"))
            if (json.has("hev_socks5_port")) VpnSettingsManager.setHevSocks5Port(context, json.getInt("hev_socks5_port"))
            if (json.has("hev_socks5_address")) VpnSettingsManager.setHevSocks5Address(context, json.getString("hev_socks5_address"))
            if (json.has("hev_socks5_udp")) VpnSettingsManager.setHevSocks5Udp(context, json.getString("hev_socks5_udp"))
            if (json.has("hev_task_stack_size")) VpnSettingsManager.setHevTaskStackSize(context, json.getInt("hev_task_stack_size"))
            if (json.has("hev_tcp_buffer_size")) VpnSettingsManager.setHevTcpBufferSize(context, json.getInt("hev_tcp_buffer_size"))
            if (json.has("hev_udp_recv_buffer_size")) VpnSettingsManager.setHevUdpRecvBufferSize(context, json.getInt("hev_udp_recv_buffer_size"))
            if (json.has("hev_udp_copy_buffer_nums")) VpnSettingsManager.setHevUdpCopyBufferNums(context, json.getInt("hev_udp_copy_buffer_nums"))
            if (json.has("hev_max_session_count")) VpnSettingsManager.setHevMaxSessionCount(context, json.getInt("hev_max_session_count"))
            if (json.has("hev_connect_timeout")) VpnSettingsManager.setHevConnectTimeout(context, json.getInt("hev_connect_timeout"))
            if (json.has("hev_tcp_read_write_timeout")) VpnSettingsManager.setHevTcpReadWriteTimeout(context, json.getInt("hev_tcp_read_write_timeout"))
            if (json.has("hev_udp_read_write_timeout")) VpnSettingsManager.setHevUdpReadWriteTimeout(context, json.getInt("hev_udp_read_write_timeout"))
            if (json.has("hev_log_file")) VpnSettingsManager.setHevLogFile(context, json.getString("hev_log_file"))
            if (json.has("hev_log_level")) VpnSettingsManager.setHevLogLevel(context, json.getString("hev_log_level"))

            if (json.has("bypass_apps_list")) {
                val array = json.getJSONArray("bypass_apps_list")
                val bypassSet = mutableSetOf<String>()
                for (i in 0 until array.length()) {
                    bypassSet.add(array.getString(i))
                }
                VpnSettingsManager.setBypassApps(context, bypassSet)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("ConfigRepository", "Error importing config", e)
            false
        }
    }

    fun copyToClipboard(label: String, text: String): Boolean {
        return try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboardManager.setPrimaryClip(clip)
            LogManager.addLog("Konfigurasi disalin ke clipboard.")
            true
        } catch (e: Exception) {
            LogManager.addLog("Gagal menyalin ke clipboard: ${e.message}")
            false
        }
    }

    fun readFromClipboard(): String {
        return try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
