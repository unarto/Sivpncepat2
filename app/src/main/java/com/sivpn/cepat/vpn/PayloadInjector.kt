package com.sivpn.cepat.vpn

import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.security.SecureRandom
import android.util.Base64
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class TunnelMode {
    TCP,
    SSL,
    WS,
    WSS
}

object PayloadInjector {
    private var serverSocket: ServerSocket? = null
    @Volatile var isRunning = false
    var localPort = 0
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clientJobs = mutableListOf<Job>()
    private var acceptJob: Job? = null

    private const val BUFFER_SIZE = 32 * 1024
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val HANDSHAKE_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 0 // Infinite for long-lived tunnel

    fun start(proxyHost: String, proxyPort: Int, sshHost: String, sshPort: Int, payload: String, sni: String, tlsVersion: String) {
        val targetHost = if (proxyHost.isNotEmpty() && proxyPort > 0) proxyHost else sshHost
        val targetPort = if (proxyHost.isNotEmpty() && proxyPort > 0) proxyPort else sshPort

        if (targetHost.isBlank() || targetPort !in 1..65535) {
            LogManager.addLog("Local Proxy tidak dijalankan: target host/port tidak valid.")
            isRunning = false
            localPort = 0
            return
        }

        if (isRunning) {
            stop()
        }

        val bindLatch = CountDownLatch(1)
        localPort = 0
        isRunning = true

        acceptJob = scope.launch {
            try {
                serverSocket = ServerSocket(0)
                serverSocket?.reuseAddress = true
                serverSocket?.soTimeout = 1000
                localPort = serverSocket?.localPort ?: 0
                LogManager.addLog("Local Proxy started on port $localPort")
                bindLatch.countDown()
                
                while (isRunning) {
                    var clientSocket: Socket? = null
                    try {
                        clientSocket = serverSocket?.accept()
                    } catch (e: SocketTimeoutException) {
                        continue
                    } catch (e: SocketException) {
                        break
                    } catch (e: Exception) {
                        break
                    }

                    if (clientSocket != null) {
                        val job = launch {
                            handleClient(clientSocket, targetHost, targetPort, sshHost, sshPort, payload, sni, tlsVersion)
                        }
                        synchronized(clientJobs) {
                            clientJobs.add(job)
                        }
                        job.invokeOnCompletion {
                            synchronized(clientJobs) {
                                clientJobs.remove(job)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    LogManager.addLog("Local Proxy error: ${e.message}")
                }
                isRunning = false
                localPort = 0
                bindLatch.countDown()
            } finally {
                isRunning = false
            }
        }

        if (!bindLatch.await(2, TimeUnit.SECONDS) || localPort == 0) {
            LogManager.addLog("Local Proxy gagal bind port tepat waktu.")
            stop()
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        
        val aJob = acceptJob
        acceptJob = null
        
        val jobsToCancel = synchronized(clientJobs) {
            clientJobs.toList()
        }
        
        scope.launch {
            aJob?.cancelAndJoin()
            jobsToCancel.forEach { it.cancel() }
            jobsToCancel.forEach { it.join() }
        }
    }

    private fun determineMode(payload: String, sni: String): TunnelMode {
        val isWs = payload.contains("websocket", ignoreCase = true)
        val isSsl = sni.isNotEmpty()
        return when {
            isSsl && isWs -> TunnelMode.WSS
            isSsl -> TunnelMode.SSL
            isWs -> TunnelMode.WS
            else -> TunnelMode.TCP
        }
    }

    private suspend fun handleClient(clientSocket: Socket, remoteHost: String, remotePort: Int, sshHost: String, sshPort: Int, payload: String, sni: String, tlsVersion: String) = coroutineScope {
        var remoteSocket: Socket? = null
        var autoPingJob: Job? = null
        try {
            val mode = determineMode(payload, sni)
            LogManager.addLog("Injector Mode: $mode")

            clientSocket.tcpNoDelay = true
            clientSocket.keepAlive = true
            clientSocket.soTimeout = READ_TIMEOUT_MS
            clientSocket.receiveBufferSize = BUFFER_SIZE
            clientSocket.sendBufferSize = BUFFER_SIZE

            var connected = false
            var retryCount = 0
            val maxRetries = 3
            var currentDelay = 1000L

            while (!connected && retryCount < maxRetries && isActive) {
                try {
                    val sock = Socket()
                    sock.tcpNoDelay = true
                    sock.keepAlive = true
                    sock.soTimeout = HANDSHAKE_TIMEOUT_MS
                    sock.receiveBufferSize = BUFFER_SIZE
                    sock.sendBufferSize = BUFFER_SIZE
                    sock.connect(InetSocketAddress(remoteHost, remotePort), CONNECT_TIMEOUT_MS)

                    LogManager.addLog("TCP Connected to $remoteHost:$remotePort")

                    if (mode == TunnelMode.SSL || mode == TunnelMode.WSS) {
                        LogManager.addLog("Handshake Start (SNI: $sni)...")
                        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                        val sslSocket = factory.createSocket(sock, remoteHost, remotePort, true) as SSLSocket
                        
                        val params = sslSocket.sslParameters
                        params.serverNames = listOf(SNIHostName(sni))
                        
                        if (tlsVersion != "Auto" && tlsVersion.isNotBlank()) {
                            val supported = sslSocket.supportedProtocols
                            if (supported.contains(tlsVersion)) {
                                sslSocket.enabledProtocols = arrayOf(tlsVersion)
                            }
                        }
                        sslSocket.sslParameters = params
                        sslSocket.startHandshake()
                        
                        sslSocket.soTimeout = READ_TIMEOUT_MS
                        remoteSocket = sslSocket
                        LogManager.addLog("Handshake Success (SSL/TLS)")
                    } else {
                        sock.soTimeout = READ_TIMEOUT_MS
                        remoteSocket = sock
                    }
                    connected = true
                } catch (e: Exception) {
                    retryCount++
                    try { remoteSocket?.close() } catch (ex: Exception) {}
                    remoteSocket = null
                    if (retryCount >= maxRetries) {
                        LogManager.addLog("Handshake Failed setelah $maxRetries percobaan.")
                        throw Exception("Koneksi gagal: ${e.message}")
                    }
                    LogManager.addLog("Retry ($retryCount/$maxRetries) karena: ${e.message}")
                    delay(currentDelay)
                    currentDelay *= 2
                }
            }

            val connectedSocket = remoteSocket ?: throw IllegalStateException("Remote socket belum siap")

            if (payload.isNotEmpty()) {
                var parsedPayload = PayloadFormatter.formatPayload(payload, sshHost, sshPort)

                if (mode == TunnelMode.WS || mode == TunnelMode.WSS) {
                    val secWsKey = generateWsKey()
                    if (!parsedPayload.contains("Sec-WebSocket-Key", ignoreCase = true)) {
                        val headersToInject = "Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: $secWsKey\r\n\r\n"
                        if (parsedPayload.endsWith("\r\n\r\n")) {
                            parsedPayload = parsedPayload.substring(0, parsedPayload.length - 2) + headersToInject
                        } else if (parsedPayload.endsWith("\r\n")) {
                            parsedPayload += headersToInject
                        } else {
                            parsedPayload += "\r\n" + headersToInject
                        }
                    }
                    
                    connectedSocket.outputStream.write(parsedPayload.toByteArray())
                    connectedSocket.outputStream.flush()
                    
                    if (!readWsHandshakeResponse(connectedSocket.inputStream)) {
                        throw Exception("Invalid WebSocket Handshake Response (Gagal verifikasi RFC 6455)")
                    }
                    LogManager.addLog("WS Connected (Handshake 101 sukses)")
                    
                    autoPingJob = startAutoPing(connectedSocket.outputStream)
                } else {
                    connectedSocket.outputStream.write(parsedPayload.toByteArray())
                    connectedSocket.outputStream.flush()
                }
            }

            if (mode == TunnelMode.WS || mode == TunnelMode.WSS) {
                val clientToRemote = launch { 
                    try {
                        WebSocketFramer.forwardWsEncode(clientSocket.inputStream, connectedSocket.outputStream)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                val remoteToClient = launch { 
                    try {
                        WebSocketFramer.forwardWsDecode(connectedSocket.inputStream, clientSocket.outputStream, connectedSocket.outputStream)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                
                clientToRemote.invokeOnCompletion { remoteToClient.cancel() }
                remoteToClient.invokeOnCompletion { clientToRemote.cancel() }
                
                joinAll(clientToRemote, remoteToClient)
                LogManager.addLog("WS Closed")
            } else {
                val bufferPool1 = ByteBuffer.allocateDirect(BUFFER_SIZE)
                val bufferPool2 = ByteBuffer.allocateDirect(BUFFER_SIZE)

                val clientToRemote = launch { 
                    try {
                        forwardStream(clientSocket.inputStream, connectedSocket.outputStream, bufferPool1)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                val remoteToClient = launch { 
                    try {
                        forwardStream(connectedSocket.inputStream, clientSocket.outputStream, bufferPool2)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                
                clientToRemote.invokeOnCompletion { remoteToClient.cancel() }
                remoteToClient.invokeOnCompletion { clientToRemote.cancel() }
                
                joinAll(clientToRemote, remoteToClient)
            }
        } catch (e: CancellationException) {
            // Ignored
        } catch (e: Exception) {
            LogManager.addLog("PayloadInjector error: ${e.message}")
        } finally {
            autoPingJob?.cancel()
            gracefulClose(clientSocket)
            gracefulClose(remoteSocket)
        }
    }

    private fun startAutoPing(output: OutputStream): Job {
        return scope.launch {
            while (isActive) {
                delay(30_000)
                try {
                    val mKey = ByteArray(4)
                    SecureRandom().nextBytes(mKey)
                    val pingHeader = byteArrayOf(0x89.toByte(), 0x80.toByte(), mKey[0], mKey[1], mKey[2], mKey[3])
                    synchronized(output) {
                        output.write(pingHeader)
                        output.flush()
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun generateWsKey(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun readWsHandshakeResponse(input: InputStream): Boolean {
        val headerBytes = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b == -1) return false
            headerBytes.write(b)
            last4 = ((last4 shl 8) or b) and 0xFFFFFFFF.toInt()
            if (last4 == 0x0D0A0D0A) {
                break
            }
        }
        val headers = headerBytes.toString("UTF-8")
        val lines = headers.split("\r\n")
        if (lines.isEmpty() || !lines[0].contains("101")) return false
        
        var hasUpgrade = false
        var hasConnection = false
        var hasAccept = false
        
        for (line in lines) {
            val lower = line.lowercase(Locale.ENGLISH)
            if (lower.startsWith("upgrade:") && lower.contains("websocket")) hasUpgrade = true
            if (lower.startsWith("connection:") && lower.contains("upgrade")) hasConnection = true
            if (lower.startsWith("sec-websocket-accept:")) hasAccept = true
        }
        
        return hasUpgrade && hasConnection && hasAccept
    }

    

    

    private fun forwardStream(input: InputStream, output: OutputStream, buffer: ByteBuffer) {
        var inputChannel: java.nio.channels.ReadableByteChannel? = null
        var outputChannel: java.nio.channels.WritableByteChannel? = null
        try {
            inputChannel = Channels.newChannel(input)
            outputChannel = Channels.newChannel(output)

            while (inputChannel.read(buffer) != -1) {
                buffer.flip()
                while (buffer.hasRemaining()) {
                    outputChannel.write(buffer)
                }
                buffer.clear()
            }
        } finally {
            try { inputChannel?.close() } catch (e: Exception) {}
            try { outputChannel?.close() } catch (e: Exception) {}
        }
    }

    private fun gracefulClose(socket: Socket?) {
        if (socket == null || socket.isClosed) return
        try {
            if (!socket.isInputShutdown) socket.shutdownInput()
        } catch (e: Exception) {}
        try {
            if (!socket.isOutputShutdown) socket.shutdownOutput()
        } catch (e: Exception) {}
        try {
            socket.close()
        } catch (e: Exception) {}
    }
}
