package com.kenneth.shotbubble

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.StringRes
import kotlin.math.roundToInt

/** Status screen plus settings: menu layout and the quick-capture gesture. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var restrictedGroup: LinearLayout
    private lateinit var showBubble: Button
    private val slotSpinners = ArrayList<Spinner>()
    private val quickOptions = ArrayList<View>()

    private val pad by lazy { dp(24) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        status = TextView(this).apply { textSize = 18f }
        val openSettings = Button(this).apply {
            setText(R.string.open_settings)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        // Safety net for users who hid the bubble from its menu but never added the Quick Settings tile.
        showBubble = Button(this).apply {
            setText(R.string.show_bubble)
            setOnClickListener {
                Prefs.setBubbleVisible(this@MainActivity, true)
                ShotAccessibilityService.instance?.setBubbleVisible(true)
                updateShowBubble()
            }
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

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                addView(status)
                addView(openSettings)
                addView(showBubble)
                addView(restrictedGroup)
                addView(buildMenuLayoutSection())
                addView(buildQuickCaptureSection())
                addView(help)
            })
        })
    }

    override fun onResume() {
        super.onResume()
        val enabled = isServiceEnabled()
        status.setText(if (enabled) R.string.status_enabled else R.string.status_disabled)
        restrictedGroup.visibility = if (enabled) View.GONE else View.VISIBLE
        updateShowBubble()
    }

    private fun updateShowBubble() {
        val hidden = ShotAccessibilityService.instance != null && !Prefs.isBubbleVisible(this)
        showBubble.visibility = if (hidden) View.VISIBLE else View.GONE
        if (hidden) status.setText(R.string.status_hidden)
        else if (isServiceEnabled()) status.setText(R.string.status_enabled)
    }

    /** A 2×2 grid of pickers mirroring the menu. Picking an action already in another slot swaps the two. */
    private fun buildMenuLayoutSection() = section(R.string.settings_menu_title, R.string.settings_menu_desc).apply {
        val actions = MenuAction.entries
        val labels = actions.map { getString(it.label) }
        val slotNames = listOf(R.string.slot_top_left, R.string.slot_top_right, R.string.slot_bottom_left, R.string.slot_bottom_right)
        val current = Prefs.menuSlots(this@MainActivity)

        slotNames.indices.chunked(2).forEach { rowSlots ->
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                rowSlots.forEach { slot ->
                    val spinner = Spinner(this@MainActivity).apply {
                        adapter = ArrayAdapter(
                            this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels,
                        )
                        setSelection(current[slot].ordinal, false)
                        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                                onSlotChosen(slot, actions[position])

                            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                        }
                    }
                    slotSpinners += spinner
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, dp(8), dp(8), 0)
                        addView(TextView(this@MainActivity).apply {
                            setText(slotNames[slot])
                            textSize = 13f
                        })
                        addView(spinner)
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                }
            })
        }
        addView(Button(this@MainActivity).apply {
            setText(R.string.settings_menu_reset)
            setOnClickListener {
                Prefs.setMenuSlots(this@MainActivity, Prefs.DEFAULT_MENU_SLOTS)
                refreshSlotSpinners()
            }
        })
    }

    private fun onSlotChosen(slot: Int, action: MenuAction) {
        val slots = Prefs.menuSlots(this).toMutableList()
        if (slots[slot] == action) return // Also swallows the callbacks fired by refreshSlotSpinners.
        val other = slots.indexOf(action)
        slots[other] = slots[slot]
        slots[slot] = action
        Prefs.setMenuSlots(this, slots)
        refreshSlotSpinners()
    }

    private fun refreshSlotSpinners() {
        val slots = Prefs.menuSlots(this)
        slotSpinners.forEachIndexed { i, spinner -> spinner.setSelection(slots[i].ordinal, false) }
    }

    private fun buildQuickCaptureSection() = section(R.string.settings_quick_title, R.string.settings_quick_desc).apply {
        val enabled = Prefs.isQuickCaptureEnabled(this@MainActivity)
        addView(Switch(this@MainActivity).apply {
            setText(R.string.settings_quick_switch)
            textSize = 16f
            isChecked = enabled
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, checked ->
                Prefs.setQuickCaptureEnabled(this@MainActivity, checked)
                quickOptions.forEach { it.setEnabledDeep(checked) }
            }
        })

        addView(radioGroup(
            R.string.settings_quick_mode,
            CaptureMode.entries.map { it to if (it == CaptureMode.CLIPBOARD) R.string.quick_mode_copy else R.string.quick_mode_gallery },
            Prefs.quickCaptureMode(this@MainActivity),
        ) { Prefs.setQuickCaptureMode(this@MainActivity, it) })

        addView(radioGroup(
            R.string.settings_quick_trigger,
            QuickTrigger.entries.map { it to if (it == QuickTrigger.DOUBLE_TAP) R.string.quick_trigger_double else R.string.quick_trigger_long },
            Prefs.quickTrigger(this@MainActivity),
        ) { Prefs.setQuickTrigger(this@MainActivity, it) })

        addView(TextView(this@MainActivity).apply {
            setText(R.string.settings_quick_note)
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
            quickOptions += this
        })
        quickOptions.forEach { it.setEnabledDeep(enabled) }
    }

    private fun <T> radioGroup(
        @StringRes title: Int,
        options: List<Pair<T, Int>>,
        selected: T,
        onChange: (T) -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, 0)
        addView(TextView(this@MainActivity).apply {
            setText(title)
            textSize = 13f
        })
        addView(RadioGroup(this@MainActivity).apply {
            orientation = RadioGroup.HORIZONTAL
            options.forEach { (value, label) ->
                val button = RadioButton(this@MainActivity).apply {
                    id = View.generateViewId()
                    setText(label)
                    setPadding(0, 0, dp(16), 0)
                }
                addView(button)
                if (value == selected) check(button.id)
                button.setOnCheckedChangeListener { _, checked -> if (checked) onChange(value) }
            }
        })
        quickOptions += this
    }

    private fun section(@StringRes title: Int, @StringRes description: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, pad, 0, 0)
        addView(TextView(this@MainActivity).apply {
            setText(title)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(this@MainActivity).apply {
            setText(description)
            textSize = 14f
            alpha = 0.8f
        })
    }

    private fun View.setEnabledDeep(enabled: Boolean) {
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.4f
        if (this is android.view.ViewGroup) {
            for (i in 0 until childCount) getChildAt(i).isEnabled = enabled
            (0 until childCount).map(::getChildAt).filterIsInstance<RadioGroup>().forEach { group ->
                for (i in 0 until group.childCount) group.getChildAt(i).isEnabled = enabled
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

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
