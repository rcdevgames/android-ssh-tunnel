package net.rcdevgames.simpletunnel.data.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import net.rcdevgames.simpletunnel.data.local.EncryptedCredentialStore
import net.rcdevgames.simpletunnel.domain.model.AuthType
import net.rcdevgames.simpletunnel.domain.model.TunnelConfig

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class SSHConnectionManager(
    private val credentialStore: EncryptedCredentialStore
) {
    private val _connections = mutableMapOf<Long, Session>()
    private val _connectionStates = MutableStateFlow<Map<Long, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<Long, ConnectionState>> = _connectionStates

    private fun updateState(tunnelId: Long, state: ConnectionState) {
        _connectionStates.value = _connectionStates.value.toMutableMap().apply {
            put(tunnelId, state)
        }
    }

    suspend fun connect(tunnel: TunnelConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            updateState(tunnel.id, ConnectionState.Connecting)

            val jsch = JSch()
            val credentials = credentialStore.getPassword(tunnel.id)
                ?: credentialStore.getPrivateKey(tunnel.id)

            if (credentials == null) {
                updateState(tunnel.id, ConnectionState.Error("No credentials found"))
                return@withContext Result.failure(Exception("No credentials found"))
            }

            val session = jsch.getSession(tunnel.username, tunnel.host, tunnel.port)

            when (tunnel.authType) {
                AuthType.PASSWORD -> session.setPassword(credentials)
                AuthType.PRIVATE_KEY -> {
                    jsch.addIdentity(
                        "tunnel_key",
                        credentials.toByteArray(),
                        null,
                        null
                    )
                }
            }

            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000)

            // Set up all port mappings
            tunnel.portMappings.forEach { mapping ->
                session.setPortForwardingR(
                    mapping.localPort,
                    "localhost",
                    mapping.remotePort
                )
            }

            _connections[tunnel.id] = session
            updateState(tunnel.id, ConnectionState.Connected)
            Result.success(Unit)
        } catch (e: Exception) {
            updateState(tunnel.id, ConnectionState.Error(e.message ?: "Connection failed"))
            Result.failure(e)
        }
    }

    fun disconnect(tunnelId: Long) {
        _connections[tunnelId]?.let { session ->
            session.disconnect()
            _connections.remove(tunnelId)
            updateState(tunnelId, ConnectionState.Disconnected)
        }
    }

    fun disconnectAll() {
        _connections.forEach { (_, session) ->
            session.disconnect()
        }
        _connections.clear()
        _connectionStates.value = emptyMap()
    }

    fun isConnected(tunnelId: Long): Boolean =
        _connections[tunnelId]?.isConnected == true
}
