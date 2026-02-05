package ru.tipowk.tunvpn.data

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module providing data layer dependencies.
 * DataStore is created via expect/actual `createDataStore()` per platform.
 */
val dataModule = module {
    single { createDataStore() }
    singleOf(::DataStoreServerRepository) bind ServerRepository::class
    singleOf(::DataStoreSettingsRepository) bind SettingsRepository::class
}
