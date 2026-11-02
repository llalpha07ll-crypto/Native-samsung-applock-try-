package com.alpha7.oneuilock

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Numeric PIN pad: title, subtitle, entered-digit dots, error line and a 3x4 key grid.
 * Sizes come from the `pin_pad_*` dimens, which use the same names as the AOSP App Lock code.
 */
class PinPadView(context: Context) : LinearLayout(context) {
    /** Called when the user confirms a PIN of valid length with the check key. */
    var onSubmit: ((String) -> Unit)? = null

    private val teal = Color.rgb(0, 137, 129)
    private val pin = StringBuilder()
    private val titleView = TextView(context)
    private val subtitleView = TextView(context)
    private val dotsView = TextView(context)
    private val errorView = TextView(context)
    private val keys = mutableListOf<TextView>()
    private var countdown: Runnable? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        titleView.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dim(R.dimen.pin_pad_title_text_size))
            setTextColor(Color.rgb(25, 35, 38))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        subtitleView.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dim(R.dimen.pin_pad_subtitle_text_size))
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }
        dotsView.apply {
            textSize = 26f
            setTextColor(teal)
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
        }
        errorView.apply {
            textSize = 14f
            setTextColor(Color.rgb(200, 40, 40))
            gravity = Gravity.CENTER
            minHeight = (24 * resources.displayMetrics.density).toInt()
        }

        val gap = (16 * resources.displayMetrics.density).toInt()
        addView(titleView, LinearLayout.LayoutParams(-2, -2))
        addView(subtitleView, LinearLayout.LayoutParams(-2, -2).apply { topMargin = gap / 4 })
        addView(dotsView, LinearLayout.LayoutParams(-2, -2).apply { topMargin = gap * 2 })
        addView(errorView, LinearLayout.LayoutParams(-2, -2).apply { topMargin = gap / 2 })
        buildKeypad(gap)
        render()
    }

    fun setTitle(text: CharSequence) { titleView.text = text }

    fun setSubtitle(text: CharSequence) { subtitleView.text = text }

    fun showError(text: CharSequence?) { errorView.text = text ?: "" }

    fun clear() {
        pin.setLength(0)
        render()
    }

    fun setKeysEnabled(enabled: Boolean) {
        keys.forEach {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.4f
        }
    }

    /** Shows the outcome of a failed [PinStore.verify]; for a lockout, counts down on screen. */
    fun showFailure(result: VerifyResult) {
        clear()
        when (result) {
            is VerifyResult.Wrong -> showError(context.getString(R.string.pin_wrong))
            is VerifyResult.Locked -> startCountdown(result.millis)
            VerifyResult.Ok -> showError(null)
        }
    }

    private fun startCountdown(millis: Long) {
        val end = SystemClock.elapsedRealtime() + millis
        countdown?.let { removeCallbacks(it) }
        setKeysEnabled(false)
        val tick = object : Runnable {
            override fun run() {
                val left = end - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    countdown = null
                    showError(null)
                    setKeysEnabled(true)
                } else {
                    showError(context.getString(R.string.pin_locked, (left + 999) / 1000))
                    postDelayed(this, 500)
                }
            }
        }
        countdown = tick
        tick.run()
    }

    override fun onDetachedFromWindow() {
        countdown?.let { removeCallbacks(it) }
        super.onDetachedFromWindow()
    }

    private fun dim(id: Int) = resources.getDimension(id)

    private fun buildKeypad(topGap: Int) {
        val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", DELETE, "0", OK)
        val diameter = resources.getDimensionPixelSize(R.dimen.pin_pad_key_diameter)
        val rowSpacing = resources.getDimensionPixelSize(R.dimen.pin_pad_row_spacing)
        val colSpacing = resources.getDimensionPixelSize(R.dimen.pin_pad_col_spacing)
        for (r in 0 until 4) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (c in 0 until 3) {
                val key = makeKey(labels[r * 3 + c], diameter)
                row.addView(key, LinearLayout.LayoutParams(diameter, diameter).apply {
                    leftMargin = colSpacing / 2
                    rightMargin = colSpacing / 2
                })
                keys.add(key)
            }
            addView(row, LinearLayout.LayoutParams(-2, -2).apply {
                topMargin = if (r == 0) topGap else rowSpacing
            })
        }
    }

    private fun makeKey(label: String, diameter: Int): TextView {
        val isOk = label == OK
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            val pad = resources.getDimensionPixelSize(R.dimen.pin_pad_key_padding)
            setPadding(pad, pad, pad, pad)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dim(R.dimen.pin_pad_key_text_size))
            setTextColor(if (isOk) Color.WHITE else Color.rgb(25, 35, 38))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isOk) teal else Color.rgb(232, 237, 237))
                setSize(diameter, diameter)
            }
            contentDescription = when (label) {
                DELETE -> context.getString(R.string.pin_key_delete)
                OK -> context.getString(R.string.pin_key_confirm)
                else -> label
            }
            isClickable = true
            setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onKey(label)
            }
        }
    }

    private fun onKey(label: String) {
        showError(null)
        when (label) {
            DELETE -> if (pin.isNotEmpty()) pin.deleteCharAt(pin.length - 1)
            OK -> {
                if (pin.length < PinStore.MIN_LENGTH) {
                    showError(context.getString(R.string.pin_too_short, PinStore.MIN_LENGTH))
                } else {
                    val entered = pin.toString()
                    clear()
                    onSubmit?.invoke(entered)
                    return
                }
            }
            else -> if (pin.length < PinStore.MAX_LENGTH) pin.append(label)
        }
        render()
    }

    private fun render() {
        val shown = maxOf(pin.length, PinStore.MIN_LENGTH)
        dotsView.text = (0 until shown).joinToString(" ") { if (it < pin.length) "●" else "○" }
    }

    private companion object {
        const val DELETE = "⌫"
        const val OK = "✓"
    }
}
