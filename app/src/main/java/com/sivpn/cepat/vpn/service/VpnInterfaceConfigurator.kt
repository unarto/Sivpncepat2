package com.sivpn.cepat.vpn.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.sivpn.cepat.vpn.LogManager
import com.sivpn.cepat.vpn.VpnSettingsManager

object VpnInterfaceConfigurator {
    fun configure(builder: VpnService.Builder, context: Context) {
        builder.setBlocking(false)
        builder.addAddress("10.0.0.2", 24)
        builder.addRoute("0.0.0.0", 0)

        val dnsString = VpnSettingsManager.getDns(context)
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

        try {
            val configIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                Intent(context, Class.forName("com.sivpn.cepat.MainActivity")),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            builder.setConfigureIntent(configIntent)
        } catch (e: Exception) {
            LogManager.addLog("Gagal menetapkan Configure Intent: ${e.message}")
        }

        if (VpnSettingsManager.getKillSwitchEnabled(context)) {
            LogManager.addLog("Kill Switch aktif: Mengunci lalu lintas internet selama rekoneksi.")
        } else {
            LogManager.addLog("Kill Switch nonaktif.")
        }

        if (VpnSettingsManager.getSplitTunnelingEnabled(context)) {
            val bypassApps = VpnSettingsManager.getBypassApps(context)
            val filterMode = VpnSettingsManager.getAppsFilterMode(context)
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
            val filterMode = VpnSettingsManager.getAppsFilterMode(context)
            val splitTunneling = VpnSettingsManager.getSplitTunnelingEnabled(context)
            if (!splitTunneling || filterMode != "filter") {
                builder.addDisallowedApplication(context.packageName)
                LogManager.addLog("Aplikasi sendiri (SIVPN) dibypass untuk menghindari routing loop.")
            }
        } catch (e: Exception) {
            LogManager.addLog("Gagal menambahkan self-bypass: ${e.message}")
        }
    }
}
