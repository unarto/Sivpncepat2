package com.sivpn.cepat.vpn

import android.util.Base64
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

object WebSocketEngine {
    private val secureRandom = SecureRandom()

    fun extractWsKey(payload: String): String? {
        val lines = payload.split("\r\n", "\n")
        for (line in lines) {
            if (line.lowercase(Locale.ENGLISH).startsWith("sec-websocket-key:")) {
                val key = line.substringAfter(":").trim()
                if (key.isNotEmpty()) return key
            }
        }
        return null
    }

    fun generateWsKey(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun calculateWsAccept(key: String): String {
        val concat = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(concat.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    fun prepareWsPayload(parsedPayload: String): Pair<String, String> {
        var secWsKey = extractWsKey(parsedPayload)
        var finalPayload = parsedPayload
        if (secWsKey == null) {
            secWsKey = generateWsKey()
            if (!finalPayload.contains("Sec-WebSocket-Key", ignoreCase = true)) {
                val headersToInject = "Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: $secWsKey\r\n\r\n"
                if (finalPayload.endsWith("\r\n\r\n")) {
                    finalPayload = finalPayload.substring(0, finalPayload.length - 2) + headersToInject
                } else if (finalPayload.endsWith("\r\n")) {
                    finalPayload += headersToInject
                } else {
                    finalPayload += "\r\n" + headersToInject
                }
            }
        }
        return Pair(finalPayload, secWsKey)
    }

    fun readWsHandshakeResponse(input: InputStream, expectedKey: String): Boolean {
        val headerBytes = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = try {
                input.read()
            } catch (e: Exception) {
                return false
            }
            if (b == -1) return false
            headerBytes.write(b)
            last4 = ((last4 shl 8) or b) and 0xFFFFFFFF.toInt()
            if (last4 == 0x0D0A0D0A) {
                break
            }
            if (headerBytes.size() > 16 * 1024) {
                return false
            }
        }
        val headers = headerBytes.toString("UTF-8")
        val lines = headers.split(Regex("\r?\n")).map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty() || !lines[0].contains("101")) return false

        var hasUpgrade = false
        var hasConnection = false
        var acceptValid = false

        val expectedAccept = if (expectedKey.isNotBlank()) calculateWsAccept(expectedKey) else ""

        for (line in lines) {
            val lower = line.lowercase(Locale.ENGLISH)
            if (lower.startsWith("upgrade:") && lower.contains("websocket")) hasUpgrade = true
            if (lower.startsWith("connection:") && lower.contains("upgrade")) hasConnection = true
            if (lower.startsWith("sec-websocket-accept:")) {
                val acceptVal = line.substringAfter(":").trim()
                if (expectedAccept.isNotEmpty()) {
                    if (acceptVal == expectedAccept) {
                        acceptValid = true
                    }
                } else {
                    if (acceptVal.isNotEmpty()) {
                        acceptValid = true
                    }
                }
            }
        }

        return hasUpgrade && hasConnection && acceptValid
    }

    fun startAutoPing(output: OutputStream, scope: CoroutineScope): Job {
        return scope.launch {
            while (isActive) {
                delay(30_000)
                try {
                    val mKey = ByteArray(4)
                    secureRandom.nextBytes(mKey)
                    val pingHeader = byteArrayOf(0x89.toByte(), 0x80.toByte(), mKey[0], mKey[1], mKey[2], mKey[3])
                    synchronized(output) {
                        output.write(pingHeader)
                        output.flush()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    suspend fun forwardWsEncode(input: InputStream, output: OutputStream) {
        WebSocketFramer.forwardWsEncode(input, output)
    }

    suspend fun forwardWsDecode(input: InputStream, clientOutput: OutputStream, remoteOutput: OutputStream) {
        WebSocketFramer.forwardWsDecode(input, clientOutput, remoteOutput)
    }
}
