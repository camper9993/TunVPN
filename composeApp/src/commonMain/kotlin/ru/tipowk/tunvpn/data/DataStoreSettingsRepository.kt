package ru.tipowk.tunvpn.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.tipowk.tunvpn.model.VpnSettings

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val settingsKey = stringPreferencesKey("vpn_settings")

    override fun getSettings(): Flow<VpnSettings> {
        return dataStore.data.map { prefs ->
            val raw = prefs[settingsKey]
            if (raw != null) {
                json.decodeFromString<VpnSettings>(raw)
            } else {
                VpnSettings()
            }
        }
    }

    override suspend fun updateSettings(settings: VpnSettings) {
        dataStore.edit { prefs ->
            prefs[settingsKey] = json.encodeToString(settings)
        }
    }
}
