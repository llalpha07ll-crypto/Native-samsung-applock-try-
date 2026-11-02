package com.alpha7.oneuilock

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Prompt shown when a protected app is opened (AOSP App Lock "Figure 3").
 *
 * The user unlocks with the App Lock PIN, or with the device screen lock / biometrics.
 * Going back or cancelling sends the user Home - it never reveals the protected app.
 * "Forgot PIN?" verifies the device screen lock, then lets the user set a new PIN.
 */
class LockActivity : FragmentActivity() {
    private lateinit var pad: PinPadView
    private lateinit var screenLock: TextView
    private var target = ""
    private var authenticators: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        authenticators = pickAuthenticators()

        pad = PinPadView(this)
        pad.onSubmit = ::onPin

        screenLock = link(R.string.use_screen_lock) {
            authenticate(getString(R.string.unlock_title, appLabel()), getString(R.string.unlock_subtitle)) {
                unlock()
            }
        }
        val forgot = link(R.string.forgot_pin) { recover() }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(pad, LinearLayout.LayoutParams(-2, -2))
            addView(screenLock, LinearLayout.LayoutParams(-2, -2))
            addView(forgot, LinearLayout.LayoutParams(-2, -2))
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(246, 248, 248)) }
        root.addView(column, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        showTarget(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showTarget(intent)
    }

    private fun showTarget(intent: Intent) {
        target = intent.getStringExtra(EXTRA_TARGET).orEmpty()
        if (target.isEmpty() || !PinStore.isEnabled(this)) {
            finishAndRemoveTask()
            return
        }
        pad.clear()
        pad.showError(null)
        pad.setKeysEnabled(true)
        pad.setTitle(getString(R.string.lock_title, appLabel()))
        pad.setSubtitle(getString(R.string.lock_subtitle))
        screenLock.visibility = if (authenticators != null) android.view.View.VISIBLE else android.view.View.GONE
        val lockout = PinStore.lockoutRemaining(this)
        if (lockout > 0) pad.showFailure(VerifyResult.Locked(lockout))
    }

    private fun onPin(pin: String) {
        when (val result = PinStore.verify(this, pin)) {
            VerifyResult.Ok -> unlock()
            else -> pad.showFailure(result)
        }
    }

    private fun unlock() {
        LockState.markUnlocked(target)
        finishAndRemoveTask()
    }

    private fun goHome() {
        LockState.markLocked(target)
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finishAndRemoveTask()
    }

    /**
     * PIN recovery. The AOSP reference verifies a Google account and also clears the locked
     * apps' data. A normal app can do neither, so this verifies the device screen lock instead
     * and only resets the PIN.
     */
    private fun recover() {
        if (authenticators == null) {
            Toast.makeText(this, R.string.recover_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        authenticate(getString(R.string.recover_title), getString(R.string.recover_subtitle)) {
            PinStore.clear(this)
            LockState.clearAll()
            Toast.makeText(this, R.string.recovered, Toast.LENGTH_LONG).show()
            startActivity(
                PinSetupActivity.intent(this, PinSetupActivity.MODE_SET, skipVerify = true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finishAndRemoveTask()
        }
    }

    private fun authenticate(title: String, subtitle: String, onSuccess: () -> Unit) {
        val allowed = authenticators ?: return
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                // Errors and cancellation leave the PIN pad on screen.
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowed)
            .apply {
                if (allowed and BiometricManager.Authenticators.DEVICE_CREDENTIAL == 0) {
                    setNegativeButtonText(getString(R.string.cancel))
                }
            }
            .build()
        prompt.authenticate(info)
    }

    /** First authenticator combination this device supports, or null if none. */
    private fun pickAuthenticators(): Int? {
        val manager = BiometricManager.from(this)
        return listOf(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ).firstOrNull { manager.canAuthenticate(it) == BiometricManager.BIOMETRIC_SUCCESS }
    }

    private fun appLabel(): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString()
    } catch (_: Exception) {
        getString(R.string.protected_app)
    }

    private fun link(textRes: Int, onClick: () -> Unit) = TextView(this).apply {
        text = getString(textRes)
        textSize = 16f
        setTextColor(Color.rgb(0, 137, 129))
        gravity = Gravity.CENTER
        val inset = (12 * resources.displayMetrics.density).toInt()
        setPadding(inset * 2, inset, inset * 2, inset)
        setOnClickListener { onClick() }
    }

    companion object {
        const val EXTRA_TARGET = "target_package"
    }
}
