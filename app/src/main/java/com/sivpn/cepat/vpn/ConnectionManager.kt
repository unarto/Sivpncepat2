package com.sivpn.cepat.vpn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ConnectionManager {

    suspend fun extractAssets(context: Context) = withContext(Dispatchers.IO) {
        val filesToExtract = listOf("hev-socks5-tunnel")
        for (fileName in filesToExtract) {
            val outFile = File(context.filesDir, fileName)
            if (!outFile.exists()) {
                try {
                    context.assets.open(fileName).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    LogManager.addLog("Failed to extract $fileName: ${e.message}")
                    continue
                }
            }
            
            try {
                val executableSet = outFile.setExecutable(true, false)
                val readableSet = outFile.setReadable(true, false)
                if (executableSet && readableSet) {
                    LogManager.addLog("Asset ready & executable: $fileName")
                } else {
                    LogManager.addLog("Asset extracted, but permission update may be incomplete: $fileName")
                }
            } catch (e: Exception) {
                LogManager.addLog("Failed to update permissions for $fileName: ${e.message}")
            }
        }
    }
}
