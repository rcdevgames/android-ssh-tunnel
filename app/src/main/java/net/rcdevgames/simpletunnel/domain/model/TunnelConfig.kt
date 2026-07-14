package net.rcdevgames.simpletunnel.domain.model

data class TunnelConfig(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: AuthType,
    val portMappings: List<PortMapping>,
    val isActive: Boolean = false
)

data class PortMapping(
    val localPort: Int,
    val remotePort: Int
)

enum class AuthType {
    PASSWORD,
    PRIVATE_KEY
}
