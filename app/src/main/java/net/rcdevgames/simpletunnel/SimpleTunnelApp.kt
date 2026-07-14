package net.rcdevgames.simpletunnel

import android.app.Application
import androidx.room.Room
import net.rcdevgames.simpletunnel.data.local.EncryptedCredentialStore
import net.rcdevgames.simpletunnel.data.local.TunnelDatabase
import net.rcdevgames.simpletunnel.data.repository.TunnelRepositoryImpl
import net.rcdevgames.simpletunnel.data.ssh.SSHConnectionManager
import net.rcdevgames.simpletunnel.domain.repository.TunnelRepository

class SimpleTunnelApp : Application() {

    // Manual DI container
    val database: TunnelDatabase by lazy {
        Room.databaseBuilder(this, TunnelDatabase::class.java, "tunnel_database")
            .fallbackToDestructiveMigration()
            .build()
    }

    val tunnelDao by lazy { database.tunnelDao() }

    val credentialStore: EncryptedCredentialStore by lazy {
        EncryptedCredentialStore(this)
    }

    val sshConnectionManager: SSHConnectionManager by lazy {
        SSHConnectionManager(credentialStore)
    }

    val tunnelRepository: TunnelRepository by lazy {
        TunnelRepositoryImpl(tunnelDao)
    }
}
