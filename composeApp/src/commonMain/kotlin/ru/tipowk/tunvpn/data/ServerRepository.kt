package ru.tipowk.tunvpn.data

import kotlinx.coroutines.flow.Flow
import ru.tipowk.tunvpn.model.ServerConfig

interface ServerRepository {
    fun getServers(): Flow<List<ServerConfig>>
    suspend fun getServerById(id: String): ServerConfig?
    suspend fun addServer(config: ServerConfig)
    suspend fun updateServer(config: ServerConfig)
    suspend fun deleteServer(id: String)
}
