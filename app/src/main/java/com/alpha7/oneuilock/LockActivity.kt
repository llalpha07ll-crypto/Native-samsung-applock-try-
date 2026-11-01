package com.alpha7.oneuilock

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/**
 * Shows Android's genuine system-owned biometric/device-credential sheet.
 * No full-screen third-party lock screen is drawn over the target app.
 */
class LockActivity : FragmentActivity() {
    private var authenticated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra("target_package").orEmpty()
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(target, 0)
            ).toString()
        } catch (_: Exception) {
            "Protected app"
        }

        // Keep this host activity visually empty; the OS owns the prompt UI.
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        window.setLayout(1, 1)

        authenticate(appName)
    }

    private fun authenticate(appName: String) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            finish()
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    authenticated = true
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    finish()
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(appName)
            .setSubtitle("Unlock to continue")
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(info)
    }
}
