package net.rcdevgames.simpletunnel.data.ssh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.rcdevgames.simpletunnel.MainActivity
import net.rcdevgames.simpletunnel.R
import net.rcdevgames.simpletunnel.data.local.EncryptedCredentialStore
import net.rcdevgames.simpletunnel.domain.model.AuthType
import net.rcdevgames.simpletunnel.domain.model.TunnelConfig

data class NetworkStats(
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val rxSpeed: Long = 0,
    val txSpeed: Long = 0
)

data class TunnelServiceState(
    val activeTunnel: TunnelConfig? = null,
    val isConnected: Boolean = false,
    val networkStats: NetworkStats = NetworkStats()
)

class TunnelService : Service() {

    companion object {
        const val CHANNEL_ID = "tunnel_service_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_CONNECT = "net.rcdevgames.simpletunnel.CONNECT"
        const val ACTION_DISCONNECT = "net.rcdevgames.simpletunnel.DISCONNECT"
        const val EXTRA_TUNNEL_ID = "tunnel_id"
        const val EXTRA_TUNNEL_NAME = "tunnel_name"
        const val EXTRA_TUNNEL_HOST = "tunnel_host"
        const val EXTRA_TUNNEL_PORT = "tunnel_port"
        const val EXTRA_TUNNEL_USERNAME = "tunnel_username"
        const val EXTRA_TUNNEL_AUTH_TYPE = "tunnel_auth_type"
        const val EXTRA_PORT_MAPPINGS = "port_mappings"
        const val EXTRA_CREDENTIAL = "credential"

        private val _serviceState = MutableStateFlow(TunnelServiceState())
        val serviceState: StateFlow<TunnelServiceState> = _serviceState.asStateFlow()

        fun getInstance(): TunnelService? = _instance
        private var _instance: TunnelService? = null
    }

    private var session: Session? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTime = 0L

    private val networkStatsRunnable = object : Runnable {
        override fun run() {
            updateNetworkStats()
            handler.postDelayed(this, 1000)
        }
    }

    private lateinit var credentialStore: EncryptedCredentialStore

    override fun onCreate() {
        super.onCreate()
        _instance = this
        credentialStore = EncryptedCredentialStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TunnelService", "onStartCommand: ${intent?.action}, startId: $startId")
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                Log.d("TunnelService", "ACTION_DISCONNECT")
                disconnectTunnel()
                stopSelf()
            }
            ACTION_CONNECT -> {
                Log.d("TunnelService", "ACTION_CONNECT")
                val tunnel = buildTunnelFromIntent(intent)
                val credential = intent.getStringExtra(EXTRA_CREDENTIAL) ?: ""
                Log.d("TunnelService", "tunnel: ${tunnel?.name}, credential length: ${credential.length}")
                tunnel?.let { connectTunnel(it, credential) }
            }
            null -> {
                Log.d("TunnelService", "intent.action is null")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        disconnectTunnel()
        _instance = null
        super.onDestroy()
    }

    private fun buildTunnelFromIntent(intent: Intent): TunnelConfig? {
        val id = intent.getLongExtra(EXTRA_TUNNEL_ID, -1)
        val name = intent.getStringExtra(EXTRA_TUNNEL_NAME) ?: return null
        val host = intent.getStringExtra(EXTRA_TUNNEL_HOST) ?: return null
        val port = intent.getIntExtra(EXTRA_TUNNEL_PORT, 22)
        val username = intent.getStringExtra(EXTRA_TUNNEL_USERNAME) ?: return null
        val authTypeOrdinal = intent.getIntExtra(EXTRA_TUNNEL_AUTH_TYPE, 0)
        val portMappingsStr = intent.getStringExtra(EXTRA_PORT_MAPPINGS) ?: "8080:8080"

        val portMappings = portMappingsStr.split(",").mapNotNull { pair ->
            val parts = pair.split(":")
            if (parts.size == 2) {
                Pair(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
            } else null
        }.map { net.rcdevgames.simpletunnel.domain.model.PortMapping(it.first, it.second) }

        return TunnelConfig(
            id = id,
            name = name,
            host = host,
            port = port,
            username = username,
            authType = AuthType.entries[authTypeOrdinal],
            portMappings = portMappings,
            isActive = true
        )
    }

    private fun connectTunnel(tunnel: TunnelConfig, credential: String) {
        Log.d("TunnelService", "connectTunnel: ${tunnel.name}")
        // ponytail: startForeground immediately - Android requires it within 5s of startForegroundService
        startForeground(NOTIFICATION_ID, buildNotification(tunnel))

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Disconnect existing first
                disconnectTunnel()

                val jsch = JSch()

                if (tunnel.authType == AuthType.PRIVATE_KEY) {
                    jsch.addIdentity("tunnel_key", credential.toByteArray(), null, null)
                }

                session = jsch.getSession(tunnel.username, tunnel.host, tunnel.port)

                if (tunnel.authType == AuthType.PASSWORD) {
                    session?.setPassword(credential)
                }

                session?.setConfig("StrictHostKeyChecking", "no")
                session?.connect(10000)

                // Set up port mappings - L = local port on phone forwards to remote host
                tunnel.portMappings.forEach { mapping ->
                    session?.setPortForwardingL(mapping.localPort, "127.0.0.1", mapping.remotePort)
                }

                _serviceState.value = TunnelServiceState(
                    activeTunnel = tunnel,
                    isConnected = true
                )

                lastRxBytes = TrafficStats.getTotalRxBytes()
                lastTxBytes = TrafficStats.getTotalTxBytes()
                lastTime = System.currentTimeMillis()
                handler.post(networkStatsRunnable)

            } catch (e: Exception) {
                Log.e("TunnelService", "connectTunnel error: ${e.message}", e)
                _serviceState.value = TunnelServiceState()
                stopSelf()
            }
        }
    }

    private fun disconnectTunnel() {
        handler.removeCallbacks(networkStatsRunnable)
        session?.disconnect()
        session = null
        _serviceState.value = TunnelServiceState()
    }

    private fun updateNetworkStats() {
        val currentRx = TrafficStats.getTotalRxBytes()
        val currentTx = TrafficStats.getTotalTxBytes()
        val currentTime = System.currentTimeMillis()

        val timeDiff = (currentTime - lastTime) / 1000.0
        val rxSpeed = if (timeDiff > 0) ((currentRx - lastRxBytes) / timeDiff).toLong() else 0
        val txSpeed = if (timeDiff > 0) ((currentTx - lastTxBytes) / timeDiff).toLong() else 0

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastTime = currentTime

        _serviceState.value = _serviceState.value.copy(
            networkStats = NetworkStats(
                rxBytes = currentRx,
                txBytes = currentTx,
                rxSpeed = rxSpeed.coerceAtLeast(0),
                txSpeed = txSpeed.coerceAtLeast(0)
            )
        )

        // Update notification
        _serviceState.value.activeTunnel?.let { tunnel ->
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(tunnel))
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH Tunnel",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows tunnel connection status"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(tunnel: TunnelConfig): android.app.Notification {
        val state = _serviceState.value
        val stats = state.networkStats

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TunnelService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val portsText = tunnel.portMappings.joinToString(", ") { "${it.localPort}→${it.remotePort}" }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("${tunnel.name} • Connected")
            .setContentText("${tunnel.host} | $portsText")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectIntent)
            .setSubText("↓ ${formatBytes(stats.rxBytes)} | ↑ ${formatBytes(stats.txBytes)}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Host: ${tunnel.host}:${tunnel.port}\nPorts: $portsText\n↓ ${formatSpeed(stats.rxSpeed)} | ↑ ${formatSpeed(stats.txSpeed)}"))
            .build()
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec < 1024 -> "$bytesPerSec B/s"
            bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024} KB/s"
            else -> "${bytesPerSec / (1024 * 1024)} MB/s"
        }
    }
}
