package com.sivpn.cepat.repository

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.sivpn.cepat.vpn.LogManager
import com.sivpn.cepat.vpn.VpnSettingsManager

class ConfigRepository(private val context: Context) {

    fun exportConfigAsJson(): String {
        return VpnSettingsManager.exportConfigAsJson(context)
    }

    fun importConfigFromJson(json: String): Boolean {
        return VpnSettingsManager.importConfigFromJson(context, json)
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
