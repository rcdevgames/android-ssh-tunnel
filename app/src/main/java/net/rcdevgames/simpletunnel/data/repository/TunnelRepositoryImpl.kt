package net.rcdevgames.simpletunnel.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.rcdevgames.simpletunnel.data.local.TunnelDao
import net.rcdevgames.simpletunnel.data.local.TunnelEntity
import net.rcdevgames.simpletunnel.domain.model.TunnelConfig
import net.rcdevgames.simpletunnel.domain.repository.TunnelRepository

class TunnelRepositoryImpl(
    private val tunnelDao: TunnelDao
) : TunnelRepository {

    override fun getAllTunnels(): Flow<List<TunnelConfig>> =
        tunnelDao.getAllTunnels().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getTunnelById(id: Long): TunnelConfig? =
        tunnelDao.getTunnelById(id)?.toDomain()

    override suspend fun insertTunnel(tunnel: TunnelConfig): Long =
        tunnelDao.insertTunnel(TunnelEntity.fromDomain(tunnel))

    override suspend fun updateTunnel(tunnel: TunnelConfig) =
        tunnelDao.updateTunnel(TunnelEntity.fromDomain(tunnel))

    override suspend fun deleteTunnel(tunnel: TunnelConfig) =
        tunnelDao.deleteTunnel(TunnelEntity.fromDomain(tunnel))

    override suspend fun toggleTunnelState(id: Long, isActive: Boolean) =
        tunnelDao.toggleTunnelState(id, isActive)
}
