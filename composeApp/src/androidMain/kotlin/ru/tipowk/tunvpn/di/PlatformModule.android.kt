package ru.tipowk.tunvpn.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.tipowk.tunvpn.data.AndroidClipboardProvider
import ru.tipowk.tunvpn.data.AndroidInstalledAppsProvider
import ru.tipowk.tunvpn.data.AndroidVpnController
import ru.tipowk.tunvpn.data.ClipboardProvider
import ru.tipowk.tunvpn.data.VpnController
import ru.tipowk.tunvpn.ui.apps.InstalledAppsProvider
import ru.tipowk.tunvpn.vpn.VpnConnectionManager

val platformModule = module {
    // VPN connection manager - singleton
    single { VpnConnectionManager(androidContext()) }

    singleOf(::AndroidInstalledAppsProvider) bind InstalledAppsProvider::class
    single<VpnController> { AndroidVpnController(androidContext(), get()) }
    singleOf(::AndroidClipboardProvider) bind ClipboardProvider::class
}
