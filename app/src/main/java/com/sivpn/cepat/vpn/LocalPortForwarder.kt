package com.sivpn.cepat.vpn

import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

object LocalPortForwarder {
    private var serverSocket: ServerSocket? = null
    private var forwardJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private const val BUFFER_SIZE = 32 * 1024
    private const val CONNECT_TIMEOUT_MS = 5_000

    @Volatile
    var isRunning = false
        private set

    @Volatile
    var activePort = 1080
        private set

    fun start(context: android.content.Context, localBindPort: Int = 1080, targetHost: String = "127.0.0.1", targetPort: Int = 1080) {
        if (targetHost.isBlank() || targetPort !in 1..65535) {
            LogManager.addLog("[Hotshare] Target SOCKS tidak valid, server tidak dijalankan.")
            isRunning = false
            return
        }

        if (isRunning) {
            stop()
        }

        isRunning = true
        forwardJob = scope.launch {
            var portToTry = localBindPort
            var success = false
            
            // Coba bind ke port utama, jika bentrok/gagal, coba port fallback (sampai +10 port berikutnya)
            val startPort = localBindPort.coerceIn(1, 65535)
            val maxPort = (startPort + 10).coerceAtMost(65535)
            portToTry = startPort
            while (portToTry <= maxPort && !success && isRunning) {
                try {
                    serverSocket = ServerSocket()
                    serverSocket?.reuseAddress = true
                    serverSocket?.bind(InetSocketAddress("0.0.0.0", portToTry))
                    activePort = portToTry
                    success = true
                    LogManager.addLog("[Hotshare] Server SOCKS aktif di 0.0.0.0:$activePort -> meneruskan ke $targetHost:$targetPort")
                    HotshareWakeLockManager.acquire(context)
                } catch (e: Exception) {
                    LogManager.addLog("[Hotshare] Gagal menggunakan port $portToTry: ${e.message}. Mencoba port berikutnya...")
                    portToTry++
                }
            }

            if (!success) {
                LogManager.addLog("[Hotshare] Gagal memulai server Hotshare SOCKS pada rentang port $localBindPort-$maxPort.")
                isRunning = false
                return@launch
            }

            try {
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    // Handle client on IO dispatcher
                    launch {
                        handleClient(clientSocket, targetHost, targetPort)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    LogManager.addLog("[Hotshare] Server error: ${e.message}")
                }
            } finally {
                isRunning = false
                closeServerSocket()
                if (!HttpProxyServer.isRunning) {
                    HotshareWakeLockManager.release()
                }
            }
        }
    }

    private suspend fun handleClient(clientSocket: Socket, targetHost: String, targetPort: Int) = withContext(Dispatchers.IO) {
        val destinationSocket = Socket()
        try {
            val clientIp = clientSocket.inetAddress?.hostAddress
            HotshareClientManager.registerClientActivity(clientIp)

            destinationSocket.connect(InetSocketAddress(targetHost, targetPort), CONNECT_TIMEOUT_MS)
            
            // Bi-directional forwarding
            val clientToDest = launch {
                pipeStream(clientSocket.getInputStream(), destinationSocket.getOutputStream())
            }
            val destToClient = launch {
                pipeStream(destinationSocket.getInputStream(), clientSocket.getOutputStream())
            }

            joinAll(clientToDest, destToClient)
        } catch (e: Exception) {
            val clientIp = clientSocket.inetAddress?.hostAddress ?: "unknown"
            LogManager.addLog("[Hotshare] Koneksi client $clientIp gagal: ${e.message}")
        } finally {
            try { clientSocket.close() } catch (e: Exception) {}
            try { destinationSocket.close() } catch (e: Exception) {}
        }
    }

    private fun pipeStream(input: InputStream, output: OutputStream) {
        var inputChannel: java.nio.channels.ReadableByteChannel? = null
        var outputChannel: java.nio.channels.WritableByteChannel? = null
        try {
            inputChannel = java.nio.channels.Channels.newChannel(input)
            outputChannel = java.nio.channels.Channels.newChannel(output)
            val buffer = java.nio.ByteBuffer.allocateDirect(BUFFER_SIZE)
            
            while (inputChannel.read(buffer) != -1) {
                buffer.flip()
                outputChannel.write(buffer)
                buffer.compact()
            }
            buffer.flip()
            while (buffer.hasRemaining()) {
                outputChannel.write(buffer)
            }
        } catch (_: SocketException) {
            // Expected when either side closes the connection.
        } catch (_: Exception) {
            // Ignore per-connection pipe failures; handleClient logs connection-level failures.
        } finally {
            try { inputChannel?.close() } catch (e: Exception) {}
            try { outputChannel?.close() } catch (e: Exception) {}
            try { input.close() } catch (e: Exception) {}
            try { output.close() } catch (e: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        closeServerSocket()
        forwardJob?.cancel()
        forwardJob = null
        LogManager.addLog("[Hotshare] Server dihentikan.")
        
        if (!HttpProxyServer.isRunning && !LocalPortForwarder.isRunning) {
            HotshareWakeLockManager.release()
        }
    }

    private fun closeServerSocket() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        } finally {
            serverSocket = null
        }
    }
}
