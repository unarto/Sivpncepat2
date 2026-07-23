package com.sivpn.cepat.repository

import android.content.Context
import com.sivpn.cepat.vpn.VpnSettingsManager

class SettingsRepository(private val context: Context) {

    fun getThemeMode(): Int = VpnSettingsManager.getThemeMode(context)
    fun setThemeMode(mode: Int) = VpnSettingsManager.setThemeMode(context, mode)

    fun getCurrentProfile(): String = VpnSettingsManager.getCurrentProfile(context)
    fun setCurrentProfile(profile: String) = VpnSettingsManager.setCurrentProfile(context, profile)

    fun getProfiles(): Set<String> = VpnSettingsManager.getProfiles(context)
    fun addProfile(profile: String) = VpnSettingsManager.addProfile(context, profile)
    fun removeProfile(profile: String) = VpnSettingsManager.removeProfile(context, profile)

    fun getSshHost(): String = VpnSettingsManager.getSshHost(context)
    fun setSshHost(host: String) = VpnSettingsManager.setSshHost(context, host)

    fun getSshPort(): Int = VpnSettingsManager.getSshPort(context)
    fun setSshPort(port: Int) = VpnSettingsManager.setSshPort(context, port)

    fun getSshUsername(): String = VpnSettingsManager.getSshUsername(context)
    fun setSshUsername(user: String) = VpnSettingsManager.setSshUsername(context, user)

    fun getSshPassword(): String = VpnSettingsManager.getSshPassword(context)
    fun setSshPassword(pass: String) = VpnSettingsManager.setSshPassword(context, pass)

    fun getPayload(): String = VpnSettingsManager.getPayload(context)
    fun setPayload(payload: String) = VpnSettingsManager.setPayload(context, payload)

    fun getProxyHost(): String = VpnSettingsManager.getProxyHost(context)
    fun setProxyHost(host: String) = VpnSettingsManager.setProxyHost(context, host)

    fun getProxyPort(): Int = VpnSettingsManager.getProxyPort(context)
    fun setProxyPort(port: Int) = VpnSettingsManager.setProxyPort(context, port)

    fun getSni(): String = VpnSettingsManager.getSni(context)
    fun setSni(sni: String) = VpnSettingsManager.setSni(context, sni)

    fun getDns(): String = VpnSettingsManager.getDns(context)
    fun setDns(dns: String) = VpnSettingsManager.setDns(context, dns)

    fun getUdpgw(): String = VpnSettingsManager.getUdpgw(context)
    fun setUdpgw(udpgw: String) = VpnSettingsManager.setUdpgw(context, udpgw)

    fun getAutoPing(): Boolean = VpnSettingsManager.getAutoPing(context)
    fun setAutoPing(enabled: Boolean) = VpnSettingsManager.setAutoPing(context, enabled)

    fun getPingAddress(): String = VpnSettingsManager.getPingAddress(context)
    fun setPingAddress(addr: String) = VpnSettingsManager.setPingAddress(context, addr)

    fun getSplitTunnelingEnabled(): Boolean = VpnSettingsManager.getSplitTunnelingEnabled(context)
    fun setSplitTunnelingEnabled(enabled: Boolean) = VpnSettingsManager.setSplitTunnelingEnabled(context, enabled)

    fun getAppsFilterMode(): String = VpnSettingsManager.getAppsFilterMode(context)
    fun setAppsFilterMode(mode: String) = VpnSettingsManager.setAppsFilterMode(context, mode)

    fun getBypassApps(): Set<String> = VpnSettingsManager.getBypassApps(context)
    fun setBypassApps(apps: Set<String>) = VpnSettingsManager.setBypassApps(context, apps)

    fun getKillSwitchEnabled(): Boolean = VpnSettingsManager.getKillSwitchEnabled(context)
    fun setKillSwitchEnabled(enabled: Boolean) = VpnSettingsManager.setKillSwitchEnabled(context, enabled)

    fun getForcingTls(): String = VpnSettingsManager.getForcingTls(context)
    fun setForcingTls(enabled: String) = VpnSettingsManager.setForcingTls(context, enabled)

    fun getSpeedometerEnabled(): Boolean = VpnSettingsManager.getSpeedometerEnabled(context)
    fun setSpeedometerEnabled(enabled: Boolean) = VpnSettingsManager.setSpeedometerEnabled(context, enabled)

    fun getAutoReconnectEnabled(): Boolean = VpnSettingsManager.getAutoReconnectEnabled(context)
    fun setAutoReconnectEnabled(enabled: Boolean) = VpnSettingsManager.setAutoReconnectEnabled(context, enabled)

    fun getIpAutoRefreshEnabled(): Boolean = VpnSettingsManager.getIpAutoRefreshEnabled(context)
    fun setIpAutoRefreshEnabled(enabled: Boolean) = VpnSettingsManager.setIpAutoRefreshEnabled(context, enabled)

    fun getIpAutoRefreshInterval(): Int = VpnSettingsManager.getIpAutoRefreshInterval(context)
    fun setIpAutoRefreshInterval(seconds: Int) = VpnSettingsManager.setIpAutoRefreshInterval(context, seconds)

    fun getHotshareWakeLockEnabled(): Boolean = VpnSettingsManager.getHotshareWakeLockEnabled(context)
    fun setHotshareWakeLockEnabled(enabled: Boolean) = VpnSettingsManager.setHotshareWakeLockEnabled(context, enabled)

    fun getVpnWakeLockEnabled(): Boolean = VpnSettingsManager.getVpnWakeLockEnabled(context)
    fun setVpnWakeLockEnabled(enabled: Boolean) = VpnSettingsManager.setVpnWakeLockEnabled(context, enabled)

    fun getKeepAliveInterval(): Int = VpnSettingsManager.getKeepAliveInterval(context)
    fun setKeepAliveInterval(seconds: Int) = VpnSettingsManager.setKeepAliveInterval(context, seconds)

    fun getAutoCleanLogsEnabled(): Boolean = VpnSettingsManager.getAutoCleanLogsEnabled(context)
    fun setAutoCleanLogsEnabled(enabled: Boolean) = VpnSettingsManager.setAutoCleanLogsEnabled(context, enabled)

    fun getAutoCleanInterval(): Int = VpnSettingsManager.getAutoCleanInterval(context)
    fun setAutoCleanInterval(minutes: Int) = VpnSettingsManager.setAutoCleanInterval(context, minutes)

    fun getMaxLogLines(): Int = VpnSettingsManager.getMaxLogLines(context)
    fun setMaxLogLines(lines: Int) = VpnSettingsManager.setMaxLogLines(context, lines)

    fun getConnectionLimitMinutes(): Int = VpnSettingsManager.getConnectionLimitMinutes(context)
    fun setConnectionLimitMinutes(minutes: Int) = VpnSettingsManager.setConnectionLimitMinutes(context, minutes)

    fun getConnectionLimitEnabled(): Boolean = VpnSettingsManager.getConnectionLimitEnabled(context)
    fun setConnectionLimitEnabled(enabled: Boolean) = VpnSettingsManager.setConnectionLimitEnabled(context, enabled)

    fun getStatusCardVisible(): Boolean = VpnSettingsManager.getStatusCardVisible(context)
    fun setStatusCardVisible(visible: Boolean) = VpnSettingsManager.setStatusCardVisible(context, visible)

    // HevSocks configuration getters/setters
    fun getHevMtu(): Int = VpnSettingsManager.getHevMtu(context)
    fun setHevMtu(mtu: Int) = VpnSettingsManager.setHevMtu(context, mtu)

    fun getHevMultiQueue(): Boolean = VpnSettingsManager.getHevMultiQueue(context)
    fun setHevMultiQueue(mq: Boolean) = VpnSettingsManager.setHevMultiQueue(context, mq)

    fun getHevIpv4(): String = VpnSettingsManager.getHevIpv4(context)
    fun setHevIpv4(ip: String) = VpnSettingsManager.setHevIpv4(context, ip)

    fun getHevIpv6(): String = VpnSettingsManager.getHevIpv6(context)
    fun setHevIpv6(ip: String) = VpnSettingsManager.setHevIpv6(context, ip)

    fun getHevDnsPort(): Int = VpnSettingsManager.getHevDnsPort(context)
    fun setHevDnsPort(port: Int) = VpnSettingsManager.setHevDnsPort(context, port)

    fun getHevDnsAddress(): String = VpnSettingsManager.getHevDnsAddress(context)
    fun setHevDnsAddress(addr: String) = VpnSettingsManager.setHevDnsAddress(context, addr)

    fun getHevSocks5Port(): Int = VpnSettingsManager.getHevSocks5Port(context)
    fun setHevSocks5Port(port: Int) = VpnSettingsManager.setHevSocks5Port(context, port)

    fun getHevSocks5Address(): String = VpnSettingsManager.getHevSocks5Address(context)
    fun setHevSocks5Address(addr: String) = VpnSettingsManager.setHevSocks5Address(context, addr)

    fun getHevSocks5Udp(): String = VpnSettingsManager.getHevSocks5Udp(context)
    fun setHevSocks5Udp(udp: String) = VpnSettingsManager.setHevSocks5Udp(context, udp)

    fun getHevTaskStackSize(): Int = VpnSettingsManager.getHevTaskStackSize(context)
    fun setHevTaskStackSize(size: Int) = VpnSettingsManager.setHevTaskStackSize(context, size)

    fun getHevTcpBufferSize(): Int = VpnSettingsManager.getHevTcpBufferSize(context)
    fun setHevTcpBufferSize(size: Int) = VpnSettingsManager.setHevTcpBufferSize(context, size)

    fun getHevUdpRecvBufferSize(): Int = VpnSettingsManager.getHevUdpRecvBufferSize(context)
    fun setHevUdpRecvBufferSize(size: Int) = VpnSettingsManager.setHevUdpRecvBufferSize(context, size)

    fun getHevUdpCopyBufferNums(): Int = VpnSettingsManager.getHevUdpCopyBufferNums(context)
    fun setHevUdpCopyBufferNums(nums: Int) = VpnSettingsManager.setHevUdpCopyBufferNums(context, nums)

    fun getHevMaxSessionCount(): Int = VpnSettingsManager.getHevMaxSessionCount(context)
    fun setHevMaxSessionCount(count: Int) = VpnSettingsManager.setHevMaxSessionCount(context, count)

    fun getHevConnectTimeout(): Int = VpnSettingsManager.getHevConnectTimeout(context)
    fun setHevConnectTimeout(sec: Int) = VpnSettingsManager.setHevConnectTimeout(context, sec)

    fun getHevTcpReadWriteTimeout(): Int = VpnSettingsManager.getHevTcpReadWriteTimeout(context)
    fun setHevTcpReadWriteTimeout(sec: Int) = VpnSettingsManager.setHevTcpReadWriteTimeout(context, sec)

    fun getHevUdpReadWriteTimeout(): Int = VpnSettingsManager.getHevUdpReadWriteTimeout(context)
    fun setHevUdpReadWriteTimeout(sec: Int) = VpnSettingsManager.setHevUdpReadWriteTimeout(context, sec)

    fun getHevLogFile(): String = VpnSettingsManager.getHevLogFile(context)
    fun setHevLogFile(path: String) = VpnSettingsManager.setHevLogFile(context, path)

    fun getHevLogLevel(): String = VpnSettingsManager.getHevLogLevel(context)
    fun setHevLogLevel(level: String) = VpnSettingsManager.setHevLogLevel(context, level)
}
