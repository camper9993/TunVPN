package ru.tipowk.tunvpn.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import ru.tipowk.tunvpn.model.AppInfo
import ru.tipowk.tunvpn.ui.apps.InstalledAppsProvider

class AndroidInstalledAppsProvider(
    private val context: Context,
) : InstalledAppsProvider {

    override suspend fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return packages.map { appInfo ->
            AppInfo(
                packageName = appInfo.packageName,
                label = appInfo.loadLabel(pm).toString(),
                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            )
        }.sortedBy { it.label.lowercase() }
    }
}
