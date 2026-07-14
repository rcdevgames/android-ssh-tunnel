package net.rcdevgames.simpletunnel.presentation.home

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.rcdevgames.simpletunnel.data.local.EncryptedCredentialStore
import net.rcdevgames.simpletunnel.data.ssh.ConnectionState
import net.rcdevgames.simpletunnel.data.ssh.TunnelService
import net.rcdevgames.simpletunnel.domain.model.AuthType
import net.rcdevgames.simpletunnel.domain.model.TunnelConfig
import net.rcdevgames.simpletunnel.domain.repository.TunnelRepository

data class HomeUiState(
    val tunnels: List<TunnelConfig> = emptyList(),
    val connectionStates: Map<Long, ConnectionState> = emptyMap(),
    val activeTunnelId: Long? = null,
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val repository: TunnelRepository,
    private val credentialStore: EncryptedCredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getAllTunnels(),
                TunnelService.serviceState
            ) { tunnels, serviceState ->
                val states = tunnels.associate { tunnel ->
                    val state = when {
                        serviceState.activeTunnel?.id == tunnel.id && serviceState.isConnected ->
                            ConnectionState.Connected
                        tunnel.isActive && serviceState.activeTunnel == null ->
                            ConnectionState.Disconnected
                        else -> ConnectionState.Disconnected
                    }
                    tunnel.id to state
                }
                HomeUiState(
                    tunnels = tunnels,
                    connectionStates = states,
                    activeTunnelId = if (serviceState.isConnected) serviceState.activeTunnel?.id else null,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    // ponytail: expose for HomeScreen to check if key is already saved
    fun hasPrivateKey(tunnelId: Long): Boolean {
        val key = credentialStore.getPrivateKey(tunnelId)
        Log.d("HomeViewModel", "hasPrivateKey($tunnelId): ${key != null}")
        return key != null
    }

    fun toggleTunnel(context: Context, tunnel: TunnelConfig, privateKeyContent: String? = null) {
        Log.d("HomeViewModel", "toggleTunnel called: ${tunnel.name}")
        viewModelScope.launch {
            val serviceState = TunnelService.serviceState.value
            Log.d("HomeViewModel", "serviceState.isConnected: ${serviceState.isConnected}, activeTunnel: ${serviceState.activeTunnel?.id}")

            // If clicking on active tunnel, disconnect
            if (serviceState.activeTunnel?.id == tunnel.id) {
                Log.d("HomeViewModel", "Disconnecting active tunnel")
                context.startService(Intent(context, TunnelService::class.java).apply {
                    action = TunnelService.ACTION_DISCONNECT
                })
                repository.toggleTunnelState(tunnel.id, false)
                return@launch
            }

            // If another tunnel is active, disconnect first
            if (serviceState.isConnected) {
                Log.d("HomeViewModel", "Disconnecting other tunnel first")
                context.startService(Intent(context, TunnelService::class.java).apply {
                    action = TunnelService.ACTION_DISCONNECT
                })
                // Give it a moment to disconnect
                kotlinx.coroutines.delay(500)
            }

            // Get credentials
            val credential = when (tunnel.authType) {
                AuthType.PASSWORD -> {
                    Log.d("HomeViewModel", "Getting password for ${tunnel.id}")
                    credentialStore.getPassword(tunnel.id)
                }
                AuthType.PRIVATE_KEY -> {
                    Log.d("HomeViewModel", "Getting private key, privateKeyContent: ${privateKeyContent != null}")
                    privateKeyContent ?: credentialStore.getPrivateKey(tunnel.id)
                }
            }

            Log.d("HomeViewModel", "credential null: ${credential == null}")
            if (credential == null) {
                return@launch
            }

            // Start tunnel service
            Log.d("HomeViewModel", "Starting foreground service")
            val intent = Intent(context, TunnelService::class.java).apply {
                action = TunnelService.ACTION_CONNECT
                putExtra(TunnelService.EXTRA_TUNNEL_ID, tunnel.id)
                putExtra(TunnelService.EXTRA_TUNNEL_NAME, tunnel.name)
                putExtra(TunnelService.EXTRA_TUNNEL_HOST, tunnel.host)
                putExtra(TunnelService.EXTRA_TUNNEL_PORT, tunnel.port)
                putExtra(TunnelService.EXTRA_TUNNEL_USERNAME, tunnel.username)
                putExtra(TunnelService.EXTRA_TUNNEL_AUTH_TYPE, tunnel.authType.ordinal)
                putExtra(TunnelService.EXTRA_PORT_MAPPINGS,
                    tunnel.portMappings.joinToString(",") { "${it.localPort}:${it.remotePort}" })
                putExtra(TunnelService.EXTRA_CREDENTIAL, credential)
            }
            context.startForegroundService(intent)
            repository.toggleTunnelState(tunnel.id, true)
        }
    }

    fun deleteTunnel(tunnel: TunnelConfig) {
        viewModelScope.launch {
            val serviceState = TunnelService.serviceState.value
            if (serviceState.activeTunnel?.id == tunnel.id) {
                // Can't delete active tunnel
                return@launch
            }
            credentialStore.deleteAllCredentials(tunnel.id)
            repository.deleteTunnel(tunnel)
        }
    }
}
