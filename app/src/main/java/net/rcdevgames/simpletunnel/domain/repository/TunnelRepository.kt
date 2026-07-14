package net.rcdevgames.simpletunnel.domain.repository

import kotlinx.coroutines.flow.Flow
import net.rcdevgames.simpletunnel.domain.model.TunnelConfig

interface TunnelRepository {
    fun getAllTunnels(): Flow<List<TunnelConfig>>
    suspend fun getTunnelById(id: Long): TunnelConfig?
    suspend fun insertTunnel(tunnel: TunnelConfig): Long
    suspend fun updateTunnel(tunnel: TunnelConfig)
    suspend fun deleteTunnel(tunnel: TunnelConfig)
    suspend fun toggleTunnelState(id: Long, isActive: Boolean)
}
