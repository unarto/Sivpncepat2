package com.sivpn.cepat.vpn.service

import android.content.Context
import com.sivpn.cepat.vpn.LogManager
import com.sivpn.cepat.vpn.VpnSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object HevTunnelConfigurator {

    suspend fun startHevTunnel(context: Context, fd: Int) = withContext(Dispatchers.IO) {
        try {
            val hevMtu = VpnSettingsManager.getHevMtu(context)
            val hevMultiQueue = VpnSettingsManager.getHevMultiQueue(context)
            val hevIpv4 = VpnSettingsManager.getHevIpv4(context)
            val hevIpv6 = VpnSettingsManager.getHevIpv6(context)

            val hevDnsPort = VpnSettingsManager.getHevDnsPort(context)
            val hevDnsAddress = VpnSettingsManager.getHevDnsAddress(context)

            val hevSocks5Port = VpnSettingsManager.getHevSocks5Port(context)
            val hevSocks5Address = VpnSettingsManager.getHevSocks5Address(context)
            val hevSocks5Udp = VpnSettingsManager.getHevSocks5Udp(context)

            val hevTaskStackSize = VpnSettingsManager.getHevTaskStackSize(context)
            val hevTcpBufferSize = VpnSettingsManager.getHevTcpBufferSize(context)
            val hevUdpRecvBufferSize = VpnSettingsManager.getHevUdpRecvBufferSize(context)
            val hevUdpCopyBufferNums = VpnSettingsManager.getHevUdpCopyBufferNums(context)
            val hevMaxSessionCount = VpnSettingsManager.getHevMaxSessionCount(context)
            val hevConnectTimeout = VpnSettingsManager.getHevConnectTimeout(context)
            val hevTcpReadWriteTimeout = VpnSettingsManager.getHevTcpReadWriteTimeout(context)
            val hevUdpReadWriteTimeout = VpnSettingsManager.getHevUdpReadWriteTimeout(context)
            val hevLogFile = VpnSettingsManager.getHevLogFile(context)
            val hevLogLevel = VpnSettingsManager.getHevLogLevel(context)

            val udpgwServer = VpnSettingsManager.getUdpgw(context)
            val socks5UdpLineStyle = if (hevSocks5Udp == "udpgw" && udpgwServer.isNotBlank()) {
                val (host, port) = VpnMonitors.parseHostAndPort(udpgwServer, 7300)
                "  udp: 'udpgw'\n  udpgw-address: $host\n  udpgw-port: $port"
            } else {
                "  udp: '$hevSocks5Udp'"
            }

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
            
            val configFile = File(context.filesDir, "hev_config.yml")
            configFile.writeText(configContent)

            LogManager.addLog("Starting HevSocks5Tunnel natively via JNI...")
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
}
