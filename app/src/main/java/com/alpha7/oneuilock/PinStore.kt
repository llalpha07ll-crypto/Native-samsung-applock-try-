package com.alpha7.oneuilock

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed interface VerifyResult {
    data object Ok : VerifyResult
    data class Wrong(val failures: Int) : VerifyResult
    data class Locked(val millis: Long) : VerifyResult
}

/**
 * Stores the App Lock PIN as a salted PBKDF2 hash (never the PIN itself) and enforces an
 * escalating lockout after repeated wrong attempts.
 *
 * App Lock is disabled until a PIN exists, mirroring AOSP App Lock: enabling the feature
 * prompts the user to set a PIN.
 */
object PinStore {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    private const val FILE = "applock_secure"
    private const val ITERATIONS = 100_000
    private const val FREE_ATTEMPTS = 5
    private const val BASE_LOCK_MS = 30_000L
    private const val MAX_LOCK_MS = 15 * 60_000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun hasPin(context: Context) = prefs(context).contains("hash")

    fun isEnabled(context: Context) =
        prefs(context).getBoolean("enabled", false) && hasPin(context)

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(context).edit()
            .putString("salt", encode(salt))
            .putString("hash", encode(hash(pin, salt)))
            .putBoolean("enabled", true)
            .putInt("failures", 0)
            .putLong("locked_until", 0L)
            .commit()
    }

    /** Removes the PIN and turns App Lock off. The list of chosen apps is kept. */
    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    fun lockoutRemaining(context: Context): Long {
        val left = prefs(context).getLong("locked_until", 0L) - SystemClock.elapsedRealtime()
        return left.coerceIn(0L, MAX_LOCK_MS)
    }

    fun verify(context: Context, pin: String): VerifyResult {
        val remaining = lockoutRemaining(context)
        if (remaining > 0) return VerifyResult.Locked(remaining)

        val p = prefs(context)
        val salt = p.getString("salt", null)?.let(::decode)
        val expected = p.getString("hash", null)?.let(::decode)
        if (salt == null || expected == null) return VerifyResult.Wrong(0)

        if (MessageDigest.isEqual(hash(pin, salt), expected)) {
            p.edit().putInt("failures", 0).putLong("locked_until", 0L).commit()
            return VerifyResult.Ok
        }

        val failures = p.getInt("failures", 0) + 1
        val editor = p.edit().putInt("failures", failures)
        if (failures % FREE_ATTEMPTS == 0) {
            val step = (failures / FREE_ATTEMPTS - 1).coerceAtMost(10)
            val lockMs = (BASE_LOCK_MS shl step).coerceAtMost(MAX_LOCK_MS)
            editor.putLong("locked_until", SystemClock.elapsedRealtime() + lockMs).commit()
            return VerifyResult.Locked(lockMs)
        }
        editor.commit()
        return VerifyResult.Wrong(failures)
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)
}
