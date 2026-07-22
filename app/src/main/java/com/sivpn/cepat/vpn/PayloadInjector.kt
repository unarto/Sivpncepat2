package com.sivpn.cepat.vpn

import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object PayloadInjector {
    private var serverSocket: ServerSocket? = null
    @Volatile var isRunning = false
    var localPort = 0
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

        scope.launch {
            try {
                serverSocket = ServerSocket(0)
                localPort = serverSocket?.localPort ?: 0
                LogManager.addLog("Local Proxy started on port $localPort")
                bindLatch.countDown()

                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleClient(clientSocket, targetHost, targetPort, sshHost, sshPort, payload, sni, tlsVersion) }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    LogManager.addLog("Local Proxy error: ${e.message}")
                }
                isRunning = false
                localPort = 0
                bindLatch.countDown()
            }
        }

        if (!bindLatch.await(2, TimeUnit.SECONDS) || localPort == 0) {
            LogManager.addLog("Local Proxy gagal bind port tepat waktu.")
            stop()
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {}
        scope.coroutineContext.cancelChildren()
    }

    private suspend fun handleClient(clientSocket: Socket, remoteHost: String, remotePort: Int, sshHost: String, sshPort: Int, payload: String, sni: String, tlsVersion: String) {
        var remoteSocket: Socket? = null
        try {
            withContext(Dispatchers.IO) {
                if (sni.isNotEmpty()) {
                    LogManager.addLog("Connecting via SSL/TLS (SNI: $sni)...")
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val sslSocket = factory.createSocket(remoteHost, remotePort) as SSLSocket
                    
                    val params = sslSocket.sslParameters
                    params.serverNames = listOf(SNIHostName(sni))
                    
                    if (tlsVersion != "Auto") {
                        sslSocket.enabledProtocols = arrayOf(tlsVersion)
                    }
                    sslSocket.sslParameters = params
                    sslSocket.startHandshake()
                    remoteSocket = sslSocket
                    LogManager.addLog("SSL Handshake successful.")
                } else {
                    remoteSocket = Socket()
                    remoteSocket.soTimeout = 30000
                    remoteSocket.connect(InetSocketAddress(remoteHost, remotePort), 10000)
                }

                // Send payload if enabled
                if (payload.isNotEmpty()) {
                    val parsedPayload = payload
                        .replace("[host_port]", "$sshHost:$sshPort")
                        .replace("[host]", sshHost)
                        .replace("[port]", sshPort.toString())
                        .replace("[protocol]", "HTTP/1.1")
                        .replace("[cr]", "\r")
                        .replace("[lf]", "\n")
                        .replace("[crlf]", "\r\n")
                        .replace("\\r", "\r")
                        .replace("\\n", "\n")
                    
                    LogManager.addLog("Injecting payload...")
                    remoteSocket.outputStream.write(parsedPayload.toByteArray())
                    remoteSocket.outputStream.flush()
                }

                val connectedSocket = remoteSocket ?: throw IllegalStateException("Remote socket belum siap")

                // Forwarding streams concurrently using Coroutines instead of threads
                // Using 32KB buffers to optimize Garbage Collection overhead
                val clientToRemote = launch { forwardStream(clientSocket.inputStream, connectedSocket.outputStream) }
                val remoteToClient = launch { forwardStream(connectedSocket.inputStream, clientSocket.outputStream) }
                joinAll(clientToRemote, remoteToClient)
            }
        } catch (e: Exception) {
            LogManager.addLog("Payload/SNI connection failed: ${e.message}")
        } finally {
            try { clientSocket.close() } catch (ex: Exception) {}
            try { remoteSocket?.close() } catch (ex: Exception) {}
        }
    }

    private fun forwardStream(input: InputStream, output: OutputStream) {
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
            // Ignored, stream closed
        } finally {
            try { inputChannel?.close() } catch (e: Exception) {}
            try { outputChannel?.close() } catch (e: Exception) {}
            try { input.close() } catch (e: Exception) {}
            try { output.close() } catch (e: Exception) {}
        }
    }
}
