package com.alpha7.oneuilock

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("protected_apps", MODE_PRIVATE) }

    private lateinit var lockSwitch: Switch
    private lateinit var lockStatus: TextView
    private lateinit var changePin: Button
    private lateinit var serviceStatus: TextView
    private var refreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        refreshHeader()
    }

    @Deprecated("Result codes are only used to refresh the header")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refreshHeader()
        if (requestCode == REQ_SET && resultCode == RESULT_OK &&
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // Lets AppLock suggest protecting apps right after they are installed.
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 248, 248))
            setPadding(dp(20), dp(26), dp(20), dp(16))
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 30f
            setTextColor(Color.rgb(25, 35, 38))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.main_tagline)
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(5), 0, dp(14))
        })

        // App Lock master switch: disabled by default; enabling it asks for a PIN.
        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val switchText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        switchText.addView(TextView(this).apply {
            text = getString(R.string.applock_switch)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(30, 40, 42))
        })
        lockStatus = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }
        switchText.addView(lockStatus)
        switchRow.addView(switchText, LinearLayout.LayoutParams(0, -2, 1f))
        lockSwitch = Switch(this).apply {
            setOnCheckedChangeListener { _, checked ->
                if (refreshing) return@setOnCheckedChangeListener
                val mode = if (checked) PinSetupActivity.MODE_SET else PinSetupActivity.MODE_DISABLE
                startActivityForResult(
                    PinSetupActivity.intent(this@MainActivity, mode),
                    if (checked) REQ_SET else REQ_DISABLE
                )
            }
        }
        switchRow.addView(lockSwitch)
        root.addView(switchRow, LinearLayout.LayoutParams(-1, -2))

        changePin = Button(this).apply {
            text = getString(R.string.change_pin)
            setOnClickListener {
                startActivityForResult(
                    PinSetupActivity.intent(this@MainActivity, PinSetupActivity.MODE_SET),
                    REQ_CHANGE
                )
            }
        }
        root.addView(changePin, LinearLayout.LayoutParams(-1, -2))

        serviceStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(10), 0, dp(2))
        }
        root.addView(serviceStatus)
        root.addView(Button(this).apply {
            text = getString(R.string.service_button)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }, LinearLayout.LayoutParams(-1, -2))

        root.addView(TextView(this).apply {
            text = getString(R.string.protected_apps)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(30, 40, 42))
            setPadding(0, dp(18), 0, dp(10))
        })
        val search = EditText(this).apply {
            hint = getString(R.string.search_apps)
            isSingleLine = true
            textSize = 16f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))

        val policy = Lockable.from(this)
        val apps = packageManager.getInstalledApplications(0)
            .filter { policy.allows(it) }
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
                    row.addView(ImageView(this).apply {
                        setImageDrawable(packageManager.getApplicationIcon(app))
                    }, LinearLayout.LayoutParams(dp(42), dp(42)))
                    row.addView(TextView(this).apply {
                        text = packageManager.getApplicationLabel(app)
                        textSize = 15f
                        setTextColor(Color.rgb(35, 40, 42))
                        setPadding(dp(12), 0, 0, 0)
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(Switch(this).apply {
                        isChecked = prefs.getBoolean(app.packageName, false)
                        setOnCheckedChangeListener { _, checked ->
                            prefs.edit().putBoolean(app.packageName, checked).apply()
                            if (!checked) LockState.markLocked(app.packageName)
                        }
                    })
                    list.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(row)
                        addView(View(this@MainActivity).apply {
                            setBackgroundColor(Color.rgb(235, 239, 239))
                        }, LinearLayout.LayoutParams(-1, dp(1)))
                    })
                }
        }
        populate("")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populate(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        setContentView(root)
    }

    private fun refreshHeader() {
        val enabled = PinStore.isEnabled(this)
        refreshing = true
        lockSwitch.isChecked = enabled
        refreshing = false
        lockStatus.text = getString(if (enabled) R.string.applock_on else R.string.applock_off)
        changePin.visibility = if (enabled) View.VISIBLE else View.GONE

        val serviceOn = isServiceEnabled()
        serviceStatus.text = getString(if (serviceOn) R.string.service_on else R.string.service_off)
        serviceStatus.setTextColor(if (serviceOn) Color.rgb(0, 120, 80) else Color.rgb(200, 40, 40))
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val mine = ComponentName(this, AppLockAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    private companion object {
        const val REQ_SET = 1
        const val REQ_DISABLE = 2
        const val REQ_CHANGE = 3
        const val REQ_NOTIFICATIONS = 4
    }
}
