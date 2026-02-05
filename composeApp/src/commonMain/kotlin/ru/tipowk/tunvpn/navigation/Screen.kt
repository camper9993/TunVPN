package ru.tipowk.tunvpn.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes.
 * Each object/class represents a screen in the app.
 */
sealed interface Screen {

    @Serializable
    data object Home : Screen

    @Serializable
    data object ServerList : Screen

    @Serializable
    data class ServerEdit(val serverId: String? = null) : Screen

    @Serializable
    data object AppSelection : Screen

    @Serializable
    data object Settings : Screen
}
