package com.kenneth.shotbubble

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Minimal status screen: shows whether the service is on and links to Accessibility settings. */
class MainActivity : Activity() {

    private lateinit var status: TextView

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
            addView(help)
        })
    }

    override fun onResume() {
        super.onResume()
        status.setText(
            if (ShotAccessibilityService.instance != null) R.string.status_enabled else R.string.status_disabled
        )
    }
}
