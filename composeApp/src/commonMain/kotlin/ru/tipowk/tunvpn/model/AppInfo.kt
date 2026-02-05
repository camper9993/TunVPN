package ru.tipowk.tunvpn.model

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false,
)
