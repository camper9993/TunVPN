package ru.tipowk.tunvpn.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.tipowk.tunvpn.data.ClipboardProvider
import ru.tipowk.tunvpn.data.IosClipboardProvider
import ru.tipowk.tunvpn.data.IosVpnController
import ru.tipowk.tunvpn.data.VpnController
import ru.tipowk.tunvpn.model.AppInfo
import ru.tipowk.tunvpn.ui.apps.InstalledAppsProvider

val platformModule = module {
    single<InstalledAppsProvider> {
        object : InstalledAppsProvider {
            override suspend fun getInstalledApps(): List<AppInfo> = emptyList()
        }
    }
    singleOf(::IosVpnController) bind VpnController::class
    singleOf(::IosClipboardProvider) bind ClipboardProvider::class
}
