package com.sivpn.cepat.vpn.service

import android.content.Context
import android.widget.Toast
import com.sivpn.cepat.vpn.LogManager
import com.sivpn.cepat.vpn.PingUtility
import com.sivpn.cepat.vpn.SiVpnService
import com.sivpn.cepat.vpn.VpnSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object VpnMonitors {
    suspend fun runDurationMonitor(context: Context, stopAction: () -> Unit) {
        while (SiVpnService.isRunning) {
            val limitMinutes = VpnSettingsManager.getConnectionLimitMinutes(context)
            val limitEnabled = VpnSettingsManager.getConnectionLimitEnabled(context)
            if (limitEnabled && limitMinutes > 0 && SiVpnService.connectionStartTime > 0L) {
                val elapsedMs = System.currentTimeMillis() - SiVpnService.connectionStartTime
                if (elapsedMs >= limitMinutes * 60 * 1000L) {
                    LogManager.addLog("Batas durasi koneksi ($limitMinutes menit) tercapai! Memutuskan koneksi otomatis.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Batas waktu koneksi $limitMinutes menit tercapai!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    stopAction()
                    break
                }
            }
            delay(2500)
        }
    }

    suspend fun runKeepAliveMonitor(context: Context) {
        while (SiVpnService.isRunning) {
            val autoPingEnabled = VpnSettingsManager.getAutoPing(context)
            if (autoPingEnabled && SiVpnService.connectionState == "CONNECTED") {
                val customPingAddress = VpnSettingsManager.getPingAddress(context).trim()
                val sshHost = VpnSettingsManager.getSshHost(context)
                val sshPort = VpnSettingsManager.getSshPort(context)
                
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
                
                val intervalSec = VpnSettingsManager.getKeepAliveInterval(context).coerceAtLeast(5)
                delay(intervalSec * 1000L)
            } else {
                delay(30000L)
            }
        }
    }

    suspend fun runSpeedometerMonitor(context: Context) {
        var lastRxBytes = android.net.TrafficStats.getTotalRxBytes()
        var lastTxBytes = android.net.TrafficStats.getTotalTxBytes()
        
        while (SiVpnService.isRunning) {
            val speedometerEnabled = VpnSettingsManager.getSpeedometerEnabled(context)
            if (speedometerEnabled && SiVpnService.connectionState == "CONNECTED") {
                val currentRxBytes = android.net.TrafficStats.getTotalRxBytes()
                val currentTxBytes = android.net.TrafficStats.getTotalTxBytes()
                
                val rxDiff = currentRxBytes - lastRxBytes
                val txDiff = currentTxBytes - lastTxBytes
                
                lastRxBytes = currentRxBytes
                lastTxBytes = currentTxBytes
                
                val rxSpeedStr = formatSpeed(rxDiff)
                val txSpeedStr = formatSpeed(txDiff)
                
                VpnNotificationManager.updateForegroundNotification(context, "↓ $rxSpeedStr | ↑ $txSpeedStr")
                delay(1000)
            } else {
                if (SiVpnService.connectionState == "CONNECTED") {
                    VpnNotificationManager.updateForegroundNotification(context, "Koneksi aman sedang berjalan")
                }
                delay(10000)
            }
        }
    }

    suspend fun runLogCleanupMonitor(context: Context) {
        LogManager.maxLogLines = VpnSettingsManager.getMaxLogLines(context)
        while (SiVpnService.isRunning) {
            val autoCleanLogsEnabled = VpnSettingsManager.getAutoCleanLogsEnabled(context)
            val intervalMins = VpnSettingsManager.getAutoCleanInterval(context)
            LogManager.maxLogLines = VpnSettingsManager.getMaxLogLines(context)
            
            if (autoCleanLogsEnabled && intervalMins > 0) {
                val delayMs = intervalMins * 60 * 1000L
                delay(delayMs)
                if (SiVpnService.isRunning && VpnSettingsManager.getAutoCleanLogsEnabled(context)) {
                    LogManager.addLog("--- Menjalankan Pembersihan Log Berkala Otomatis ---")
                    LogManager.clearLogs()
                    LogManager.clearPhysicalLogFile(context)
                    LogManager.addLog("--- Pembersihan Log Selesai ---")
                }
            } else {
                delay(30000L)
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 B/s"
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s", "TB/s")
        val digitGroups = (Math.log10(bytesPerSec.toDouble()) / Math.log10(1024.0)).toInt()
            .coerceIn(0, units.lastIndex)
        return String.format("%.1f %s", bytesPerSec / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun parseHostAndPort(value: String, defaultPort: Int): Pair<String, Int> {
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
}
