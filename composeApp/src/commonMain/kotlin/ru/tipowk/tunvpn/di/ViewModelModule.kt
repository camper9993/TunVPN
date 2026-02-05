package ru.tipowk.tunvpn.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.tipowk.tunvpn.ui.apps.AppSelectionViewModel
import ru.tipowk.tunvpn.ui.home.HomeViewModel
import ru.tipowk.tunvpn.ui.servers.ServerEditViewModel
import ru.tipowk.tunvpn.ui.servers.ServerListViewModel
import ru.tipowk.tunvpn.ui.settings.SettingsViewModel

val viewModelModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::ServerListViewModel)
    viewModelOf(::ServerEditViewModel)
    viewModelOf(::AppSelectionViewModel)
    viewModelOf(::SettingsViewModel)
}
