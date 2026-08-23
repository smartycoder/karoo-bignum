package io.smartycoder.bignum

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hint = TextView(this).apply {
            text = getString(R.string.activity_hint)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        val zoneColorsLabel = TextView(this).apply {
            text = getString(R.string.setting_zone_colors)
            textSize = 18f
        }

        // Order matches ZoneColorMode so the spinner position is the ordinal.
        val modes = ZoneColorMode.entries
        val modeLabels = modes.map {
            getString(
                when (it) {
                    ZoneColorMode.OFF -> R.string.setting_zone_mode_off
                    ZoneColorMode.TEXT -> R.string.setting_zone_mode_text
                    ZoneColorMode.FILL -> R.string.setting_zone_mode_fill
                },
            )
        }
        val zoneColors = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                modeLabels,
            )
            setSelection(modes.indexOf(Settings.zoneColorMode(context)))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    Settings.setZoneColorMode(context, modes[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

        val note = TextView(this).apply {
            text = getString(R.string.setting_zone_colors_desc)
            textSize = 13f
            setPadding(0, 8, 0, 0)
        }

        val testMode = Switch(this).apply {
            text = getString(R.string.setting_test_mode)
            textSize = 18f
            isChecked = Settings.testMode(context)
            setPadding(0, 40, 0, 0)
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                Settings.setTestMode(context, checked)
            }
        }

        val testModeNote = TextView(this).apply {
            text = getString(R.string.setting_test_mode_desc)
            textSize = 13f
            setPadding(0, 8, 0, 0)
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 64, 48, 48)
                addView(hint)
                addView(zoneColorsLabel)
                addView(zoneColors)
                addView(note)
                // Debug builds only. Plausible-but-false power and heart rate are worth
                // keeping out of a rider's reach; this exists to shoot screenshots and to
                // look at field layout without a ride.
                if (BuildConfig.DEBUG) {
                    addView(testMode)
                    addView(testModeNote)
                }
            },
        )
    }
}
