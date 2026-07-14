package net.rcdevgames.simpletunnel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TunnelEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TunnelDatabase : RoomDatabase() {
    abstract fun tunnelDao(): TunnelDao
}
