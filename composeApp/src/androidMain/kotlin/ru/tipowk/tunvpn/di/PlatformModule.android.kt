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

val platformModule = module {
    singleOf(::AndroidInstalledAppsProvider) bind InstalledAppsProvider::class
    single<VpnController> { AndroidVpnController(androidContext()) }
    singleOf(::AndroidClipboardProvider) bind ClipboardProvider::class
}
