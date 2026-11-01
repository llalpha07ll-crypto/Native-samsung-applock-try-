package com.alpha7.oneuilock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("protected_apps", MODE_PRIVATE) }
    private val teal = Color.rgb(0, 137, 129)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 248, 248))
            setPadding(dp(20), dp(26), dp(20), dp(16))
        }
        val title = TextView(this).apply {
            text = "AppLock"
            textSize = 30f
            setTextColor(Color.rgb(25, 35, 38))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        root.addView(title)
        root.addView(TextView(this).apply {
            text = "Protect your apps with your phone's screen lock"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(5), 0, dp(18))
        })
        val enable = Button(this).apply {
            text = "Enable AppLock service"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(enable, matchWrap())
        root.addView(TextView(this).apply {
            text = "Protected apps"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(30, 40, 42))
            setPadding(0, dp(22), 0, dp(10))
        })
        val search = EditText(this).apply {
            hint = "Search apps"
            isSingleLine = true
            textSize = 16f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val apps = packageManager.getInstalledApplications(0)
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .filter { it.packageName != packageName }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }

        fun populate(query: String) {
            list.removeAllViews()
            apps.filter { packageManager.getApplicationLabel(it).toString().contains(query, true) }
                .forEach { app ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), dp(9), dp(8), dp(9))
                        setBackgroundColor(Color.WHITE)
                    }
                    val icon = ImageView(this).apply {
                        setImageDrawable(packageManager.getApplicationIcon(app))
                    }
                    row.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
                    val name = TextView(this).apply {
                        text = packageManager.getApplicationLabel(app)
                        textSize = 15f
                        setTextColor(Color.rgb(35, 40, 42))
                        setPadding(dp(12), 0, 0, 0)
                    }
                    row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
                    val toggle = Switch(this).apply {
                        isChecked = prefs.getBoolean(app.packageName, false)
                        setOnCheckedChangeListener { _, checked ->
                            prefs.edit().putBoolean(app.packageName, checked).apply()
                        }
                    }
                    row.addView(toggle)
                    val outer = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(row)
                        addView(View(this@MainActivity).apply {
                            setBackgroundColor(Color.rgb(235, 239, 239))
                        }, LinearLayout.LayoutParams(-1, dp(1)))
                    }
                    list.addView(outer)
                }
        }
        populate("")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { populate(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
}
