package com.kenneth.shotbubble

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Minimal status screen: shows whether the service is on and links to Accessibility settings. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var restrictedGroup: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        val pad = (24 * resources.displayMetrics.density).roundToInt()

        status = TextView(this).apply { textSize = 18f }
        val openSettings = Button(this).apply {
            setText(R.string.open_settings)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        // Sideloaded APKs hit Android's "restricted settings" block; guide users to App info → ⋮.
        restrictedGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, pad, 0, 0)
            addView(TextView(this@MainActivity).apply {
                setText(R.string.restricted_help)
                textSize = 15f
            })
            addView(Button(this@MainActivity).apply {
                setText(R.string.open_app_info)
                setOnClickListener {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    )
                }
            })
        }
        val help = TextView(this).apply {
            setText(R.string.help)
            textSize = 15f
            setPadding(0, pad, 0, 0)
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(pad, pad, pad, pad)
            addView(status)
            addView(openSettings)
            addView(restrictedGroup)
            addView(help)
        })
    }

    override fun onResume() {
        super.onResume()
        val enabled = isServiceEnabled()
        status.setText(if (enabled) R.string.status_enabled else R.string.status_disabled)
        restrictedGroup.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    /**
     * Reads the system's enabled-services setting rather than checking ShotAccessibilityService.instance
     * or the bound-services list, both of which lag for a moment after an app update.
     */
    private fun isServiceEnabled(): Boolean =
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')
            ?.any { ComponentName.unflattenFromString(it)?.packageName == packageName }
            ?: false
}
