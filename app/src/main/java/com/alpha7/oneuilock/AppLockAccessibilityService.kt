package com.alpha7.oneuilock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Detects which app has come to the foreground and shows [LockActivity] over it when the app
 * is protected and hasn't been unlocked. Does nothing until App Lock is enabled with a PIN.
 */
class AppLockAccessibilityService : AccessibilityService() {
    private var foreground = ""
    private var ignoredPackages = emptySet<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Keyboards and system overlays open windows too; they aren't "leaving" the app.
        val keyboards = getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList.orEmpty().map { it.packageName }
        ignoredPackages = resources.getStringArray(R.array.transient_packages).toSet() + keyboards + packageName
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignoredPackages) return

        val needsLock = PinStore.isEnabled(this) &&
            getSharedPreferences("protected_apps", MODE_PRIVATE).getBoolean(pkg, false) &&
            !LockState.isUnlocked(pkg)

        // Same app again: only matters if it is protected and still locked (e.g. resumed
        // from Recents while the lock screen was covering it).
        if (pkg == foreground && !needsLock) return

        if (pkg != foreground) {
            if (foreground.isNotEmpty()) LockState.markLeft(foreground)
            foreground = pkg
        }

        if (needsLock) {
            startActivity(Intent(this, LockActivity::class.java).apply {
                putExtra(LockActivity.EXTRA_TARGET, pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            })
        }
    }

    override fun onInterrupt() {
        foreground = ""
    }
}
