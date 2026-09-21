package com.apoorvdarshan.calorietracker.services.mcp

import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

enum class TunnelStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Manages an outbound reverse SSH tunnel (via Serveo / Localhost.run) to expose
 * the embedded MCP server to a public HTTPS URL without port forwarding, root, or accounts.
 * Enables ChatGPT Web and mobile clients to reach the local Android server from anywhere.
 */
class McpTunnelManager(private val localPort: Int) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelJob: Job? = null
    private var activeSession: Session? = null

    private val _status = MutableStateFlow(TunnelStatus.DISCONNECTED)
    val status: StateFlow<TunnelStatus> = _status.asStateFlow()

    private val _publicUrl = MutableStateFlow<String?>(null)
    val publicUrl: StateFlow<String?> = _publicUrl.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun start() {
        if (tunnelJob?.isActive == true) return

        _status.value = TunnelStatus.CONNECTING
        _lastError.value = null

        tunnelJob = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    attempt++
                    Log.i(TAG, "Attempting to establish public tunnel (attempt $attempt)...")
                    _status.value = TunnelStatus.CONNECTING

                    connectAndMaintainTunnel()
                } catch (e: Exception) {
                    if (isActive) {
                        Log.w(TAG, "Tunnel connection lost: ${e.message}. Retrying in 5s...", e)
                        _status.value = TunnelStatus.ERROR
                        _lastError.value = e.message ?: "Error de conexión en el túnel"
                        _publicUrl.value = null
                        delay(5000)
                    }
                }
            }
        }
    }

    fun stop() {
        try {
            tunnelJob?.cancel()
            tunnelJob = null
            activeSession?.disconnect()
            activeSession = null
            _status.value = TunnelStatus.DISCONNECTED
            _publicUrl.value = null
            Log.i(TAG, "Tunnel stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping tunnel", e)
        }
    }

    private suspend fun connectAndMaintainTunnel() = withContext(Dispatchers.IO) {
        val jsch = JSch()

        // Use localhost.run (active, working global DNS with TLS termination https://xxxx.lhr.life)
        val session = try {
            val s = jsch.getSession("nokey", "localhost.run", 22)
            s.setConfig("StrictHostKeyChecking", "no")
            s.setServerAliveInterval(30000)
            s.connect(15000)
            s
        } catch (e: Exception) {
            Log.w(TAG, "localhost.run failed, falling back to serveo: ${e.message}")
            val randomSubdomain = "fudai-" + UUID.randomUUID().toString().take(6)
            val s = jsch.getSession(randomSubdomain, "serveo.net", 22)
            s.setConfig("StrictHostKeyChecking", "no")
            s.setServerAliveInterval(30000)
            s.connect(15000)
            s
        }
        activeSession = session

        // Request remote port 80 forwarding to localPort
        session.setPortForwardingR(80, "127.0.0.1", localPort)

        // Open exec channel to read banner containing assigned HTTPS URL
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand("")
        val input = channel.inputStream
        channel.connect(5000)

        val reader = BufferedReader(InputStreamReader(input))
        val urlRegex = Regex("https://[a-zA-Z0-9.-]+\\.(lhr\\.life|localhost\\.run|serveo(usercontent)?\\.com)")

        var line: String?
        val startTime = System.currentTimeMillis()

        // Read until we find the assigned URL (timeout 15s)
        while (isActive && session.isConnected && System.currentTimeMillis() - startTime < 15000) {
            if (reader.ready() || input.available() > 0) {
                line = reader.readLine()
                if (line != null) {
                    Log.d(TAG, "Tunnel banner: $line")
                    val match = urlRegex.find(line)
                    if (match != null) {
                        val assigned = match.value
                        Log.i(TAG, "Public tunnel URL established: $assigned")
                        _publicUrl.value = assigned
                        _status.value = TunnelStatus.CONNECTED
                        break
                    }
                }
            }
            delay(100)
        }

        if (_publicUrl.value == null) {
            channel.disconnect()
            session.disconnect()
            throw IllegalStateException("No se pudo obtener la URL pública del túnel")
        }

        // Keep connection alive while coroutine is active
        while (isActive && session.isConnected) {
            delay(10000)
            try {
                session.sendKeepAliveMsg()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send keep-alive, connection may be dead")
                break
            }
        }

        channel.disconnect()
        session.disconnect()
    }

    companion object {
        private const val TAG = "McpTunnelManager"
    }
}
