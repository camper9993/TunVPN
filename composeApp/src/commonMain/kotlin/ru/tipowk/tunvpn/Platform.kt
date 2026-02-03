package ru.tipowk.tunvpn

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform