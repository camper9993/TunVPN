package ru.tipowk.tunvpn.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import ru.tipowk.tunvpn.model.AppInfo
import ru.tipowk.tunvpn.ui.apps.InstalledAppsProvider

class AndroidInstalledAppsProvider(
    private val context: Context,
) : InstalledAppsProvider {

    companion object {
        private const val TAG = "InstalledAppsProvider"
    }

    // Cache to avoid reloading
    private var cachedApps: List<AppInfo>? = null

    override suspend fun getInstalledApps(): List<AppInfo> {
        // Return cached result if available
        cachedApps?.let {
            Log.d(TAG, "Returning ${it.size} cached apps")
            return it
        }

        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Loading installed apps...")
            val startTime = System.currentTimeMillis()

            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0) // Remove GET_META_DATA - not needed and slower

            Log.d(TAG, "Found ${packages.size} packages, loading labels...")

            // Load labels in parallel using coroutines
            val apps = packages.chunked(50).flatMap { chunk ->
                chunk.map { appInfo ->
                    async {
                        try {
                            AppInfo(
                                packageName = appInfo.packageName,
                                label = appInfo.loadLabel(pm).toString(),
                                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            )
                        } catch (e: Exception) {
                            // Fallback to package name if label loading fails
                            AppInfo(
                                packageName = appInfo.packageName,
                                label = appInfo.packageName,
                                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            )
                        }
                    }
                }.awaitAll()
            }.sortedBy { it.label.lowercase() }

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Loaded ${apps.size} apps in ${elapsed}ms")

            // Cache the result
            cachedApps = apps
            apps
        }
    }
}
