package com.freefcc.edge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Builds the launchable-app list and launch intents straight from
 * [PackageManager]. Icons load directly — no image-loading library, no icon
 * packs, no pseudo-shortcuts. The MAIN/LAUNCHER query is scoped by the manifest
 * `<queries>` element, so this works without the broad package-visibility
 * permission.
 */
class LauncherAppRepository(context: Context) {

    private val pm: PackageManager = context.applicationContext.packageManager

    /**
     * Every launchable app visible to us, de-duplicated and sorted by label.
     * This enumerates and loads every icon, so it must run off the main thread
     * (MainActivity's selection UI does this on a background thread).
     */
    fun loadLaunchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .map { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                val icon = runCatching { resolveInfo.loadIcon(pm) }
                    .getOrDefault(pm.defaultActivityIcon)
                AppEntry(pkg, label, icon)
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Resolve ONLY the user-selected packages, in label order. This avoids the
     * full launchable-app enumeration on the service's panel-open path: each
     * package is resolved directly and any package that can no longer be
     * launched (uninstalled, no launcher activity) is dropped fail-closed.
     */
    fun loadSelectedApps(selected: Set<String>): List<AppEntry> =
        selected.mapNotNull { pkg -> resolveApp(pkg) }
            .sortedBy { it.label.lowercase() }

    private fun resolveApp(packageName: String): AppEntry? {
        // Fail-closed: only include packages that are actually launchable.
        if (pm.getLaunchIntentForPackage(packageName) == null) return null
        return runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            AppEntry(
                packageName = packageName,
                label = pm.getApplicationLabel(info).toString(),
                icon = pm.getApplicationIcon(info),
            )
        }.getOrNull()
    }

    /**
     * A MAIN launch intent for [packageName] with NEW_TASK set, or null if the
     * package cannot be launched (uninstalled, no launcher activity).
     */
    fun launchIntentFor(packageName: String): Intent? =
        pm.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
