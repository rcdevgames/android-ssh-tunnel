package net.rcdevgames.simpletunnel.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.rcdevgames.simpletunnel.domain.model.AuthType
import net.rcdevgames.simpletunnel.domain.model.PortMapping
import net.rcdevgames.simpletunnel.domain.model.TunnelConfig

@Entity(tableName = "tunnels")
data class TunnelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: AuthType,
    @ColumnInfo(name = "port_mappings")
    val portMappings: List<PortMapping>,
    val isActive: Boolean = false
) {
    fun toDomain() = TunnelConfig(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authType = authType,
        portMappings = portMappings,
        isActive = isActive
    )

    companion object {
        fun fromDomain(config: TunnelConfig) = TunnelEntity(
            id = config.id,
            name = config.name,
            host = config.host,
            port = config.port,
            username = config.username,
            authType = config.authType,
            portMappings = config.portMappings,
            isActive = config.isActive
        )
    }
}
