package ru.tipowk.tunvpn.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.tipowk.tunvpn.model.ServerConfig

class DataStoreServerRepository(
    private val dataStore: DataStore<Preferences>,
) : ServerRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val serversKey = stringPreferencesKey("servers")

    override fun getServers(): Flow<List<ServerConfig>> {
        return dataStore.data.map { prefs ->
            val raw = prefs[serversKey] ?: "[]"
            json.decodeFromString<List<ServerConfig>>(raw)
        }
    }

    override suspend fun getServerById(id: String): ServerConfig? {
        var result: ServerConfig? = null
        dataStore.edit { prefs ->
            val raw = prefs[serversKey] ?: "[]"
            val servers = json.decodeFromString<List<ServerConfig>>(raw)
            result = servers.find { it.id == id }
        }
        return result
    }

    override suspend fun addServer(config: ServerConfig) {
        dataStore.edit { prefs ->
            val raw = prefs[serversKey] ?: "[]"
            val servers = json.decodeFromString<List<ServerConfig>>(raw).toMutableList()
            servers.add(config)
            prefs[serversKey] = json.encodeToString(servers)
        }
    }

    override suspend fun updateServer(config: ServerConfig) {
        dataStore.edit { prefs ->
            val raw = prefs[serversKey] ?: "[]"
            val servers = json.decodeFromString<List<ServerConfig>>(raw).toMutableList()
            val index = servers.indexOfFirst { it.id == config.id }
            if (index >= 0) {
                servers[index] = config
                prefs[serversKey] = json.encodeToString(servers)
            }
        }
    }

    override suspend fun deleteServer(id: String) {
        dataStore.edit { prefs ->
            val raw = prefs[serversKey] ?: "[]"
            val servers = json.decodeFromString<List<ServerConfig>>(raw)
                .filter { it.id != id }
            prefs[serversKey] = json.encodeToString(servers)
        }
    }
}
