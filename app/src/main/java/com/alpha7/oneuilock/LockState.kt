package com.alpha7.oneuilock

import android.os.SystemClock

/**
 * In-memory record of which protected apps the user has unlocked.
 *
 * An app stays unlocked while it is in the foreground. After the user leaves it, it stays
 * unlocked for a short grace period ([GRACE_MS]) so quick app switches don't re-prompt.
 */
object LockState {
    private const val GRACE_MS = 10_000L
    private const val FOREGROUND = Long.MAX_VALUE

    private val unlocked = HashMap<String, Long>()

    @Synchronized
    fun markUnlocked(pkg: String) {
        unlocked[pkg] = FOREGROUND
    }

    @Synchronized
    fun markLeft(pkg: String) {
        if (unlocked[pkg] == FOREGROUND) unlocked[pkg] = SystemClock.elapsedRealtime()
    }

    @Synchronized
    fun markLocked(pkg: String) {
        unlocked.remove(pkg)
    }

    @Synchronized
    fun isUnlocked(pkg: String): Boolean {
        val leftAt = unlocked[pkg] ?: return false
        if (leftAt == FOREGROUND) return true
        if (SystemClock.elapsedRealtime() - leftAt < GRACE_MS) return true
        unlocked.remove(pkg)
        return false
    }

    @Synchronized
    fun clearAll() = unlocked.clear()
}
