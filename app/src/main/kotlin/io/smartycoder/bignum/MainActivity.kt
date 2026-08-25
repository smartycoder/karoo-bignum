package io.smartycoder.bignum

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    /** A dropdown over [labels], starting at [selected], reporting the position picked. */
    private fun spinner(labels: List<String>, selected: Int, onPick: (Int) -> Unit) =
        Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                labels,
            )
            setSelection(selected)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    onPick(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

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
        val zoneColors = spinner(modeLabels, modes.indexOf(Settings.zoneColorMode(this))) {
            Settings.setZoneColorMode(this, modes[it])
        }

        val note = TextView(this).apply {
            text = getString(R.string.setting_zone_colors_desc)
            textSize = 13f
            setPadding(0, 8, 0, 0)
        }

        // Font, and the two axes it can be drawn at. Kept as three spinners rather than
        // sliders: the Karoo is a small touchscreen usually operated with gloves on.
        val fontLabel = TextView(this).apply {
            text = getString(R.string.setting_font)
            textSize = 18f
            setPadding(0, 40, 0, 0)
        }

        var setting = Settings.fontSetting(this)

        val fonts = NumberFont.entries
        val fontNames = fonts.map {
            getString(
                when (it) {
                    NumberFont.OSWALD -> R.string.setting_font_oswald
                    NumberFont.SAIRA -> R.string.setting_font_saira
                },
            )
        }

        val widthLabel = TextView(this).apply {
            text = getString(R.string.setting_font_width)
            textSize = 18f
            setPadding(0, 24, 0, 0)
        }
        val weightLabel = TextView(this).apply {
            text = getString(R.string.setting_font_weight)
            textSize = 18f
            setPadding(0, 24, 0, 0)
        }

        // The nearest offered step, so a width stored by a build with a different list -- or
        // clamped on the way out of Settings -- still selects something instead of nothing.
        fun nearest(values: List<Int>, value: Int) =
            values.indices.minBy { kotlin.math.abs(values[it] - value) }

        val width = spinner(Settings.WIDTHS.map { "$it%" }, nearest(Settings.WIDTHS, setting.width)) {
            setting = setting.copy(width = Settings.WIDTHS[it])
            Settings.setFontSetting(this, setting)
        }
        val weight = spinner(Settings.WEIGHTS.map(Int::toString), nearest(Settings.WEIGHTS, setting.weight)) {
            setting = setting.copy(weight = Settings.WEIGHTS[it])
            Settings.setFontSetting(this, setting)
        }

        // Oswald is a static Bold with no axes, so leaving the two enabled would offer a
        // choice that changes nothing on screen.
        fun axesEnabled(enabled: Boolean) {
            for (view in listOf(widthLabel, width, weightLabel, weight)) {
                view.isEnabled = enabled
                view.alpha = if (enabled) 1f else 0.4f
            }
        }

        val font = spinner(fontNames, fonts.indexOf(setting.font)) {
            setting = setting.copy(font = fonts[it])
            Settings.setFontSetting(this, setting)
            axesEnabled(setting.hasAxes)
        }
        axesEnabled(setting.hasAxes)

        val fontNote = TextView(this).apply {
            text = getString(R.string.setting_font_desc)
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

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            addView(hint)
            addView(zoneColorsLabel)
            addView(zoneColors)
            addView(note)
            addView(fontLabel)
            addView(font)
            addView(widthLabel)
            addView(width)
            addView(weightLabel)
            addView(weight)
            addView(fontNote)
            // Debug builds only. Plausible-but-false power and heart rate are worth
            // keeping out of a rider's reach; this exists to shoot screenshots and to
            // look at field layout without a ride.
            if (BuildConfig.DEBUG) {
                addView(testMode)
                addView(testModeNote)
            }
        }

        // Scrollable: the settings outgrew the Karoo's 480x800 screen when the font controls
        // arrived, and a bare LinearLayout silently clips whatever does not fit -- which put
        // every one of those controls out of reach.
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
