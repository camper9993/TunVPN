package ru.tipowk.tunvpn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.tipowk.tunvpn.data.SettingsRepository
import ru.tipowk.tunvpn.model.VpnSettings

data class SettingsUiState(
    val settings: VpnSettings = VpnSettings(),
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _uiState.value = SettingsUiState(settings = settings)
            }
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.settings.copy(darkTheme = enabled)
            settingsRepository.updateSettings(updated)
        }
    }
}
