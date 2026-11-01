package com.alpha7.oneuilock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppLockAccessibilityService : AccessibilityService() {
    private var lastPackage = ""
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == lastPackage) return
        lastPackage = pkg
        val locked = getSharedPreferences("protected_apps", MODE_PRIVATE).getBoolean(pkg, false)
        if (locked) {
            startActivity(Intent(this, LockActivity::class.java).apply {
                putExtra("target_package", pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            })
        }
    }
    override fun onInterrupt() { lastPackage = "" }
}
