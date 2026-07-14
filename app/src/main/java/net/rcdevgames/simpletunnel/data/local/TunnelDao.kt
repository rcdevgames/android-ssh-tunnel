package net.rcdevgames.simpletunnel.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TunnelDao {
    @Query("SELECT * FROM tunnels ORDER BY name ASC")
    fun getAllTunnels(): Flow<List<TunnelEntity>>

    @Query("SELECT * FROM tunnels WHERE id = :id")
    suspend fun getTunnelById(id: Long): TunnelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTunnel(tunnel: TunnelEntity): Long

    @Update
    suspend fun updateTunnel(tunnel: TunnelEntity)

    @Delete
    suspend fun deleteTunnel(tunnel: TunnelEntity)

    @Query("UPDATE tunnels SET isActive = :isActive WHERE id = :id")
    suspend fun toggleTunnelState(id: Long, isActive: Boolean)
}
