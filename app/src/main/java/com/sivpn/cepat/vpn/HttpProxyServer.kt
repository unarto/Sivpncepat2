package com.sivpn.cepat.vpn

import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*

object HttpProxyServer {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isRunning = false
        private set

    var activePort = 8080
        private set

    fun start(context: android.content.Context, localBindPort: Int = 8080, socksHost: String = "127.0.0.1", socksPort: Int = 1080) {
        if (socksHost.isBlank() || socksPort !in 1..65535) {
            LogManager.addLog("[Hotshare HTTP] SOCKS target tidak valid, server tidak dijalankan.")
            isRunning = false
            return
        }

        if (isRunning) {
            stop()
        }

        isRunning = true
        serverJob = scope.launch {
            var portToTry = localBindPort.coerceIn(1, 65535)
            var success = false

            val maxPort = (portToTry + 10).coerceAtMost(65535)
            while (portToTry <= maxPort && !success && isRunning) {
                try {
                     serverSocket = ServerSocket()
                     serverSocket?.reuseAddress = true
                     serverSocket?.bind(InetSocketAddress("0.0.0.0", portToTry))
                     activePort = portToTry
                     success = true
                     LogManager.addLog("[Hotshare HTTP] Server aktif di 0.0.0.0:$activePort -> SOCKS5 $socksHost:$socksPort")
                     HotshareWakeLockManager.acquire(context)
                } catch (e: Exception) {
                     LogManager.addLog("[Hotshare HTTP] Gagal menggunakan port $portToTry: ${e.message}. Mencoba port berikutnya...")
                     portToTry++
                }
            }

            if (!success) {
                LogManager.addLog("[Hotshare HTTP] Gagal memulai server HTTP Proxy pada rentang port $localBindPort-$maxPort.")
                isRunning = false
                return@launch
            }

            try {
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch {
                        handleClient(clientSocket, socksHost, socksPort)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    LogManager.addLog("[Hotshare HTTP] Server error: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleClient(clientSocket: Socket, socksHost: String, socksPort: Int) = withContext(Dispatchers.IO) {
        var socksSocket: Socket? = null
        try {
            val clientIp = clientSocket.inetAddress?.hostAddress
            HotshareClientManager.registerClientActivity(clientIp)

            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            // Read headers byte-by-byte until \r\n\r\n to prevent body data loss
            val headerBuffer = ByteArrayOutputStream()
            var state = 0
            while (true) {
                val b = clientIn.read()
                if (b == -1) break
                headerBuffer.write(b)
                if (state == 0 && b == '\r'.code) state = 1
                else if (state == 1 && b == '\n'.code) state = 2
                else if (state == 2 && b == '\r'.code) state = 3
                else if (state == 3 && b == '\n'.code) {
                    break
                } else {
                    state = 0
                }
                if (headerBuffer.size() > 8192) break
            }

            val headersStr = headerBuffer.toString("ISO-8859-1")
            val lines = headersStr.split("\r\n")
            if (lines.isEmpty() || lines[0].isEmpty()) return@withContext

            val firstLine = lines[0]
            val tokens = StringTokenizer(firstLine)
            if (tokens.countTokens() < 2) return@withContext

            val method = tokens.nextToken()
            val url = tokens.nextToken()

            val isConnect = method.equals("CONNECT", ignoreCase = true)
            val (targetHost, targetPort) = if (isConnect) {
                parseHostAndPort(url, 443)
            } else {
                val tempUrl = when {
                    url.startsWith("http://", ignoreCase = true) -> url.substring(7)
                    url.startsWith("https://", ignoreCase = true) -> url.substring(8)
                    else -> {
                        val hostHeader = lines.firstOrNull { it.startsWith("Host:", ignoreCase = true) }
                            ?.substringAfter(':')
                            ?.trim()
                        hostHeader ?: ""
                    }
                }
                val slashIdx = tempUrl.indexOf('/')
                val hostPortPart = if (slashIdx != -1) tempUrl.substring(0, slashIdx) else tempUrl
                parseHostAndPort(hostPortPart, 80)
            }

            if (targetHost.isBlank()) {
                clientOut.write("HTTP/1.1 400 Bad Request\r\n\r\n".toByteArray())
                clientOut.flush()
                return@withContext
            }

            try {
                socksSocket = connectViaSocks5(socksHost, socksPort, targetHost, targetPort)
            } catch (e: Exception) {
                LogManager.addLog("[Hotshare HTTP] Gagal koneksi SOCKS5 ke $targetHost:$targetPort - ${e.message}")
                if (isConnect) {
                    clientOut.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                    clientOut.flush()
                }
                return@withContext
            }

            val socksOut = socksSocket.getOutputStream()
            val socksIn = socksSocket.getInputStream()

            if (isConnect) {
                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                clientOut.flush()
            } else {
                socksOut.write(rewriteRequestHeaderToOriginForm(headersStr, url))
                socksOut.flush()
            }

            // Bi-directional stream pipe
            val clientToSocks = launch { pipeStream(clientIn, socksOut) }
            val socksToClient = launch { pipeStream(socksIn, clientOut) }

            joinAll(clientToSocks, socksToClient)
        } catch (e: Exception) {
            // closed connections
        } finally {
            try { clientSocket.close() } catch (e: Exception) {}
            try { socksSocket?.close() } catch (e: Exception) {}
        }
    }

    private fun parseHostAndPort(hostPort: String, defaultPort: Int): Pair<String, Int> {
        val trimmed = hostPort.trim()
        if (trimmed.startsWith("[")) {
            val endBracket = trimmed.indexOf(']')
            if (endBracket > 0) {
                val host = trimmed.substring(1, endBracket)
                val port = trimmed.substring(endBracket + 1).removePrefix(":").toIntOrNull() ?: defaultPort
                return host to port.coerceIn(1, 65535)
            }
        }

        val colonIndex = trimmed.lastIndexOf(':')
        val hasSingleColon = colonIndex > 0 && trimmed.indexOf(':') == colonIndex
        if (hasSingleColon) {
            val host = trimmed.substring(0, colonIndex)
            val port = trimmed.substring(colonIndex + 1).toIntOrNull() ?: defaultPort
            return host to port.coerceIn(1, 65535)
        }

        return trimmed to defaultPort
    }

    private fun rewriteRequestHeaderToOriginForm(headers: String, originalUrl: String): ByteArray {
        if (!originalUrl.startsWith("http://", ignoreCase = true) && !originalUrl.startsWith("https://", ignoreCase = true)) {
            return headers.toByteArray(Charsets.ISO_8859_1)
        }

        val lines = headers.split("\r\n").toMutableList()
        if (lines.isNotEmpty()) {
            val firstLineTokens = StringTokenizer(lines[0])
            if (firstLineTokens.countTokens() >= 3) {
                val method = firstLineTokens.nextToken()
                val absoluteUrl = firstLineTokens.nextToken()
                val protocol = firstLineTokens.nextToken()
                val schemeOffset = absoluteUrl.indexOf("://")
                val pathStart = if (schemeOffset >= 0) absoluteUrl.indexOf('/', schemeOffset + 3) else -1
                val originPath = if (pathStart >= 0) absoluteUrl.substring(pathStart) else "/"
                lines[0] = "$method $originPath $protocol"
            }
        }

        return lines.joinToString("\r\n").toByteArray(Charsets.ISO_8859_1)
    }

    private fun connectViaSocks5(socksHost: String, socksPort: Int, targetHost: String, targetPort: Int): Socket {
        val socket = Socket()
        socket.connect(InetSocketAddress(socksHost, socksPort), 10000)
        val out = DataOutputStream(socket.getOutputStream())
        val inStream = DataInputStream(socket.getInputStream())

        // SOCKS5 Handshake
        out.writeByte(0x05)
        out.writeByte(0x01)
        out.writeByte(0x00)
        out.flush()

        val ver = inStream.readByte().toInt()
        val method = inStream.readByte().toInt()
        if (ver != 5 || method != 0) {
            socket.close()
            throw IOException("SOCKS5 Shake failed")
        }

        // Connect Command
        out.writeByte(0x05)
        out.writeByte(0x01)
        out.writeByte(0x00)
        out.writeByte(0x03) // Domain name type
        val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
        out.writeByte(hostBytes.size)
        out.write(hostBytes)
        out.writeShort(targetPort)
        out.flush()

        val rVer = inStream.readByte().toInt()
        val rRep = inStream.readByte().toInt()
        inStream.readByte()
        val rAtyp = inStream.readByte().toInt()

        if (rVer != 5 || rRep != 0) {
            socket.close()
            throw IOException("SOCKS5 Target Connect failed: $rRep")
        }

        when (rAtyp) {
            0x01 -> {
                val dummy = ByteArray(6)
                inStream.readFully(dummy)
            }
            0x03 -> {
                val len = inStream.readByte().toInt() and 0xFF
                val dummy = ByteArray(len + 2)
                inStream.readFully(dummy)
            }
            0x04 -> {
                val dummy = ByteArray(18)
                inStream.readFully(dummy)
            }
        }

        return socket
    }

    private fun pipeStream(input: InputStream, output: OutputStream) {
        var inputChannel: java.nio.channels.ReadableByteChannel? = null
        var outputChannel: java.nio.channels.WritableByteChannel? = null
        try {
            inputChannel = java.nio.channels.Channels.newChannel(input)
            outputChannel = java.nio.channels.Channels.newChannel(output)
            val buffer = java.nio.ByteBuffer.allocateDirect(32768)
            
            while (inputChannel.read(buffer) != -1) {
                buffer.flip()
                outputChannel.write(buffer)
                buffer.compact()
            }
            buffer.flip()
            while (buffer.hasRemaining()) {
                outputChannel.write(buffer)
            }
        } catch (e: Exception) {
            // expected
        } finally {
            try { inputChannel?.close() } catch (e: Exception) {}
            try { outputChannel?.close() } catch (e: Exception) {}
            try { input.close() } catch (e: Exception) {}
            try { output.close() } catch (e: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        LogManager.addLog("[Hotshare HTTP] Server dihentikan.")
        
        if (!HttpProxyServer.isRunning && !LocalPortForwarder.isRunning) {
            HotshareWakeLockManager.release()
        }
    }
}
