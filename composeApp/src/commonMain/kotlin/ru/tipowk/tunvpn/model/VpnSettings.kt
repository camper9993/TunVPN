package ru.tipowk.tunvpn.model

import kotlinx.serialization.Serializable

@Serializable
data class VpnSettings(
    val darkTheme: Boolean = false,
    val activeServerId: String? = null,
    val selectedAppPackages: Set<String> = emptySet(),
)
