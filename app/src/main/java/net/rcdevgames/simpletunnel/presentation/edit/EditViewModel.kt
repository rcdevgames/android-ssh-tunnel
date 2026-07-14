package net.rcdevgames.simpletunnel.presentation.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.rcdevgames.simpletunnel.data.local.EncryptedCredentialStore
import net.rcdevgames.simpletunnel.domain.model.AuthType
import net.rcdevgames.simpletunnel.domain.model.PortMapping
import net.rcdevgames.simpletunnel.domain.model.TunnelConfig
import net.rcdevgames.simpletunnel.domain.repository.TunnelRepository

data class EditUiState(
    val tunnelId: Long? = null,
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val portMappings: List<PortMapping> = listOf(PortMapping(8080, 8080)),
    val password: String = "",
    val privateKey: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

class EditViewModel(
    private val repository: TunnelRepository,
    private val credentialStore: EncryptedCredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    fun loadTunnel(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getTunnelById(id)?.let { tunnel ->
                val savedPassword = credentialStore.getPassword(id)
                val savedKey = credentialStore.getPrivateKey(id)
                _uiState.update {
                    it.copy(
                        tunnelId = tunnel.id,
                        name = tunnel.name,
                        host = tunnel.host,
                        port = tunnel.port.toString(),
                        username = tunnel.username,
                        authType = tunnel.authType,
                        portMappings = tunnel.portMappings.ifEmpty { listOf(PortMapping(8080, 8080)) },
                        password = savedPassword ?: "",
                        privateKey = savedKey ?: "",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value) }
    fun updateHost(value: String) = _uiState.update { it.copy(host = value) }
    fun updatePort(value: String) = _uiState.update { it.copy(port = value) }
    fun updateUsername(value: String) = _uiState.update { it.copy(username = value) }
    fun updateAuthType(value: AuthType) = _uiState.update { it.copy(authType = value) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value) }
    fun updatePrivateKey(value: String) = _uiState.update { it.copy(privateKey = value) }

    fun addPortMapping() {
        _uiState.update {
            it.copy(portMappings = it.portMappings + PortMapping(8080, 8080))
        }
    }

    fun removePortMapping(index: Int) {
        _uiState.update {
            it.copy(portMappings = it.portMappings.filterIndexed { i, _ -> i != index })
        }
    }

    fun updatePortMapping(index: Int, localPort: Int, remotePort: Int) {
        _uiState.update {
            it.copy(
                portMappings = it.portMappings.mapIndexed { i, mapping ->
                    if (i == index) PortMapping(localPort, remotePort) else mapping
                }
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank() || state.host.isBlank() || state.username.isBlank()) {
            _uiState.update { it.copy(error = "Name, host, and username are required") }
            return
        }

        if (state.portMappings.isEmpty()) {
            _uiState.update { it.copy(error = "At least one port mapping is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val tunnel = TunnelConfig(
                    id = state.tunnelId ?: 0,
                    name = state.name,
                    host = state.host,
                    port = state.port.toIntOrNull() ?: 22,
                    username = state.username,
                    authType = state.authType,
                    portMappings = state.portMappings
                )

                val id = if (state.tunnelId != null) {
                    repository.updateTunnel(tunnel)
                    state.tunnelId
                } else {
                    repository.insertTunnel(tunnel)
                }

                when (state.authType) {
                    AuthType.PASSWORD -> {
                        credentialStore.savePassword(id, state.password)
                        credentialStore.deletePrivateKey(id)
                    }
                    AuthType.PRIVATE_KEY -> {
                        credentialStore.savePrivateKey(id, state.privateKey)
                        credentialStore.deletePassword(id)
                    }
                }

                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
