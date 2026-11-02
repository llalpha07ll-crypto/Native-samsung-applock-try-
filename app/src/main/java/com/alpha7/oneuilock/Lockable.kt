package com.alpha7.oneuilock

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * Decides which installed apps can be protected, following the AOSP App Lock rules:
 *  - system apps aren't lockable unless listed in `system_lockable_packages`;
 *  - core apps (Settings, launcher, dialer, system UI...) are never lockable;
 *  - media apps can be excluded with `config_enableMediaAppsLocking`.
 *
 * Apps the user installed from a store, and system apps that received a store update
 * (Chrome, Gmail, Photos...), count as ordinary apps and are lockable.
 */
class Lockable private constructor(private val context: Context) {
    private val pm = context.packageManager
    private val res = context.resources

    private val systemLockable = res.getStringArray(R.array.system_lockable_packages).toSet()
    private val neverLockable: Set<String> = buildSet {
        addAll(res.getStringArray(R.array.never_lockable_packages))
        add(context.packageName)
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).forEach { add(it.activityInfo.packageName) }
    }
    private val mediaLockingEnabled = res.getBoolean(R.bool.config_enableMediaAppsLocking)
    private val mediaPackages: Set<String> =
        if (mediaLockingEnabled) emptySet()
        else pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0)
            .mapNotNull { it.serviceInfo?.packageName }
            .toSet()

    fun allows(app: ApplicationInfo): Boolean {
        val pkg = app.packageName
        if (pkg in neverLockable) return false
        if (pm.getLaunchIntentForPackage(pkg) == null) return false
        if (pkg in mediaPackages) return false
        val isPlatformApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
            app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
        return !isPlatformApp || pkg in systemLockable
    }

    companion object {
        fun from(context: Context) = Lockable(context)
    }
}
