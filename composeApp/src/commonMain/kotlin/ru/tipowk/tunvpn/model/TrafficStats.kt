package ru.tipowk.tunvpn.model

data class TrafficStats(
    val uploadSpeed: Long = 0L,
    val downloadSpeed: Long = 0L,
    val totalUpload: Long = 0L,
    val totalDownload: Long = 0L,
)
