package net.rcdevgames.simpletunnel.data.local

import androidx.room.TypeConverter
import net.rcdevgames.simpletunnel.domain.model.AuthType
import net.rcdevgames.simpletunnel.domain.model.PortMapping

class Converters {
    @TypeConverter
    fun fromAuthType(value: AuthType): String = value.name

    @TypeConverter
    fun toAuthType(value: String): AuthType = AuthType.valueOf(value)

    @TypeConverter
    fun fromPortMappings(mappings: List<PortMapping>): String =
        mappings.joinToString(",") { "${it.localPort}:${it.remotePort}" }

    @TypeConverter
    fun toPortMappings(value: String): List<PortMapping> {
        if (value.isBlank()) return listOf(PortMapping(8080, 8080))
        return value.split(",").mapNotNull { pair ->
            val parts = pair.split(":")
            if (parts.size == 2) {
                PortMapping(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
            } else null
        }
    }
}
