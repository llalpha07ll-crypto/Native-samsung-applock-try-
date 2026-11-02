package com.alpha7.oneuilock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Sets, changes or removes the App Lock PIN.
 *
 *  - [MODE_SET]: verify the current PIN (if one exists), then enter and confirm a new one.
 *  - [MODE_DISABLE]: verify the current PIN, then turn App Lock off.
 *
 * Finishes with RESULT_OK when the change was made.
 */
class PinSetupActivity : Activity() {
    private enum class Step { VERIFY_CURRENT, ENTER_NEW, CONFIRM_NEW }

    private lateinit var pad: PinPadView
    private lateinit var mode: String
    private lateinit var step: Step
    private var firstEntry = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SET
        val skipVerify = intent.getBooleanExtra(EXTRA_SKIP_VERIFY, false)
        val needsVerify = PinStore.hasPin(this) && !skipVerify
        step = if (needsVerify) Step.VERIFY_CURRENT else Step.ENTER_NEW

        pad = PinPadView(this)
        pad.onSubmit = ::onPin

        val cancel = TextView(this).apply {
            text = getString(R.string.cancel)
            textSize = 16f
            setTextColor(Color.rgb(0, 137, 129))
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
            setOnClickListener { cancelAndFinish() }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(pad, LinearLayout.LayoutParams(-2, -2))
            addView(cancel, LinearLayout.LayoutParams(-2, -2))
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(246, 248, 248)) }
        root.addView(column, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(root)

        renderStep()
        val lockout = PinStore.lockoutRemaining(this)
        if (step == Step.VERIFY_CURRENT && lockout > 0) pad.showFailure(VerifyResult.Locked(lockout))
    }

    private fun renderStep() {
        when (step) {
            Step.VERIFY_CURRENT -> {
                pad.setTitle(getString(R.string.pin_enter_current))
                pad.setSubtitle(getString(R.string.pin_enter_current_hint))
            }
            Step.ENTER_NEW -> {
                pad.setTitle(getString(R.string.pin_create))
                pad.setSubtitle(getString(R.string.pin_create_hint, PinStore.MIN_LENGTH, PinStore.MAX_LENGTH))
            }
            Step.CONFIRM_NEW -> {
                pad.setTitle(getString(R.string.pin_confirm))
                pad.setSubtitle(getString(R.string.pin_confirm_hint))
            }
        }
    }

    private fun onPin(pin: String) {
        when (step) {
            Step.VERIFY_CURRENT -> when (val result = PinStore.verify(this, pin)) {
                VerifyResult.Ok -> {
                    if (mode == MODE_DISABLE) {
                        PinStore.clear(this)
                        LockState.clearAll()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        step = Step.ENTER_NEW
                        renderStep()
                    }
                }
                else -> pad.showFailure(result)
            }
            Step.ENTER_NEW -> {
                firstEntry = pin
                step = Step.CONFIRM_NEW
                renderStep()
            }
            Step.CONFIRM_NEW -> {
                if (pin == firstEntry) {
                    PinStore.setPin(this, pin)
                    LockState.clearAll()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    firstEntry = ""
                    step = Step.ENTER_NEW
                    renderStep()
                    pad.showError(getString(R.string.pin_mismatch))
                }
            }
        }
    }

    @Deprecated("Handled explicitly so the result is always reported")
    override fun onBackPressed() = cancelAndFinish()

    private fun cancelAndFinish() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val MODE_SET = "set"
        const val MODE_DISABLE = "disable"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_SKIP_VERIFY = "skip_verify"

        fun intent(context: Context, mode: String, skipVerify: Boolean = false) =
            Intent(context, PinSetupActivity::class.java)
                .putExtra(EXTRA_MODE, mode)
                .putExtra(EXTRA_SKIP_VERIFY, skipVerify)
    }
}
