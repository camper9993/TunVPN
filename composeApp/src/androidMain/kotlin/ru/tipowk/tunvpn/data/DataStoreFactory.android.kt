package ru.tipowk.tunvpn.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import ru.tipowk.tunvpn.TunVpnApp

actual fun createDataStore(): DataStore<Preferences> {
    val context = TunVpnApp.appContext
    val path = context.filesDir.resolve("tunvpn_prefs.preferences_pb").absolutePath
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { path.toPath() }
    )
}
