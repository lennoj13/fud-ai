package com.apoorvdarshan.calorietracker.services.mcp

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.apoorvdarshan.calorietracker.FudAIApp
import com.apoorvdarshan.calorietracker.MainActivity
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.services.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Android Foreground Service managing McpHttpServer and McpTunnelManager,
 * providing both local LAN and public HTTPS endpoints for ChatGPT and Claude.
 */
class McpForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var httpServer: McpHttpServer? = null
    private var tunnelManager: McpTunnelManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startServer()
        return START_STICKY
    }

    private fun startServer() {
        val app = application as? FudAIApp ?: return
        val container = app.container

        serviceScope.launch {
            try {
                val port = container.prefs.mcpServerPort.first()
                val token = container.prefs.mcpAuthToken.first()
                val tunnelEnabled = container.prefs.mcpPublicTunnelEnabled.first()
                val localIp = getLocalIpAddress()

                val initialNotification = buildForegroundNotification(localIp, port, null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            NotificationService.MCP_NOTIFICATION_ID,
                            initialNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    } else {
                        startForeground(
                            NotificationService.MCP_NOTIFICATION_ID,
                            initialNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    }
                } else {
                    startForeground(NotificationService.MCP_NOTIFICATION_ID, initialNotification)
                }

                val executor = McpToolExecutor(
                    foodRepository = container.foodRepository,
                    weightRepository = container.weightRepository,
                    waterRepository = container.waterRepository,
                    fastingRepository = container.fastingRepository,
                    profileRepository = container.profileRepository,
                    workoutRepository = container.workoutRepository,
                    imageStore = container.imageStore,
                    prefs = container.prefs
                )

                httpServer?.stop()
                val server = McpHttpServer(port, token, executor)
                server.start()
                httpServer = server

                _isRunning.value = true
                _serverUrl.value = "http://$localIp:$port"
                container.prefs.setMcpServerEnabled(true)

                // Start automatic public tunnel for ChatGPT over the internet
                if (tunnelEnabled) {
                    tunnelManager?.stop()
                    val tunnel = McpTunnelManager(port)
                    tunnelManager = tunnel

                    launch {
                        tunnel.publicUrl.collect { publicUrl ->
                            _publicTunnelUrl.value = publicUrl
                            if (publicUrl != null) {
                                try {
                                    val updatedNotification = buildForegroundNotification(localIp, port, publicUrl)
                                    NotificationManagerCompat.from(this@McpForegroundService)
                                        .notify(NotificationService.MCP_NOTIFICATION_ID, updatedNotification)
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    launch {
                        tunnel.status.collect { status ->
                            _tunnelStatus.value = status
                        }
                    }

                    tunnel.start()
                }

                Log.i(TAG, "McpForegroundService running at http://$localIp:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MCP server in foreground service", e)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tunnelManager?.stop()
        tunnelManager = null
        httpServer?.stop()
        httpServer = null

        _isRunning.value = false
        _serverUrl.value = null
        _publicTunnelUrl.value = null
        _tunnelStatus.value = TunnelStatus.DISCONNECTED

        val app = application as? FudAIApp
        app?.let {
            CoroutineScope(Dispatchers.IO).launch {
                it.container.prefs.setMcpServerEnabled(false)
            }
        }
        Log.i(TAG, "McpForegroundService destroyed")
    }

    private fun buildForegroundNotification(ip: String, port: Int, publicUrl: String?): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, McpForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (publicUrl != null) {
            "Túnel Público Activo: $publicUrl"
        } else {
            "Escuchando en http://$ip:$port"
        }

        val bigText = if (publicUrl != null) {
            "Conectado a Internet para ChatGPT:\n" +
                "URL Pública: $publicUrl\n" +
                "URL Local: http://$ip:$port"
        } else {
            "Iniciando túnel para ChatGPT...\n" +
                "URL Local: http://$ip:$port"
        }

        return NotificationCompat.Builder(this, NotificationService.CHANNEL_MCP)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Servidor MCP Fud AI Activo")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "McpForegroundService"
        const val ACTION_START = "com.apoorvdarshan.calorietracker.mcp.START"
        const val ACTION_STOP = "com.apoorvdarshan.calorietracker.mcp.STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _serverUrl = MutableStateFlow<String?>(null)
        val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

        private val _publicTunnelUrl = MutableStateFlow<String?>(null)
        val publicTunnelUrl: StateFlow<String?> = _publicTunnelUrl.asStateFlow()

        private val _tunnelStatus = MutableStateFlow(TunnelStatus.DISCONNECTED)
        val tunnelStatus: StateFlow<TunnelStatus> = _tunnelStatus.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, McpForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, McpForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun getLocalIpAddress(): String {
            return try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                var foundIp: String? = null
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val ip = addr.hostAddress ?: continue
                            if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                                return ip
                            }
                            foundIp = ip
                        }
                    }
                }
                foundIp ?: "127.0.0.1"
            } catch (_: Exception) {
                "127.0.0.1"
            }
        }
    }
}
