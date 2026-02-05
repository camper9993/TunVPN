package ru.tipowk.tunvpn.data

import kotlinx.coroutines.flow.Flow
import ru.tipowk.tunvpn.model.VpnSettings

interface SettingsRepository {
    fun getSettings(): Flow<VpnSettings>
    suspend fun updateSettings(settings: VpnSettings)
}
