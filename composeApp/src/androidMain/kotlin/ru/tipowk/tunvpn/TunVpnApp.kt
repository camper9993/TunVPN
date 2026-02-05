package ru.tipowk.tunvpn

import android.app.Application
import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import ru.tipowk.tunvpn.data.dataModule
import ru.tipowk.tunvpn.di.platformModule
import ru.tipowk.tunvpn.di.viewModelModule

class TunVpnApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        startKoin {
            androidContext(this@TunVpnApp)
            modules(dataModule, viewModelModule, platformModule)
        }
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
