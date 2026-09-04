package io.smartycoder.bignum

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import io.hammerhead.karooext.KarooSystemService
import io.smartycoder.bignum.fields.FieldCatalog
import io.smartycoder.bignum.fields.ids
import io.smartycoder.bignum.fields.zoneCapable

class MainActivity : Activity() {

    // Labels only, and applicationContext to match BigNumExtension.onCreate: nothing here
    // connects -- KarooSystemService's constructor only allocates, it binds nothing until
    // connect() -- but an Activity handed to 58 long-lived field objects is a leak waiting
    // for the SDK to change.
    private val catalog by lazy { FieldCatalog.build("bignum", KarooSystemService(applicationContext)) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()


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

    /**
     * Every section's content view and chevron, so opening one can close the others. Cleared at
     * the top of [onCreate]: a re-created Activity builds fresh views, and a stale entry here
     * would leave the accordion collapsing a view that is no longer on screen.
     */
    private val openSections = mutableListOf<Pair<View, TextView>>()

    /**
     * A collapsible card in Barberfish's `CollapsibleSection` shape -- white background, 1dp
     * grey border, 6dp corners, a tappable header (icon, uppercase title, description, chevron)
     * and a content area shown or hidden on tap -- rebuilt with plain views because BigNum has
     * no Compose dependency to draw on and must not gain one.
     *
     * Sections behave as one accordion through [openSections]: opening any card closes the rest.
     * The state is in memory only -- the Karoo does not rotate, so there is nothing to restore
     * across a re-create and no preference key is worth adding just to remember which card the
     * rider left open.
     */
    private fun section(
        title: String,
        description: String,
        iconRes: Int,
        expandedInitially: Boolean,
        vararg content: View,
    ): View {

        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            // Decorative: the title text right beside it already names the section.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 14f
            isAllCaps = true
            setTypeface(typeface, Typeface.BOLD)
        }

        val descriptionView = TextView(this).apply {
            text = description
            textSize = 12f
        }

        // The icon sits on the title's row, and the description runs beneath BOTH of them rather
        // than being indented under the title alone. That is how Barberfish's sections read, and
        // the flush left edge is what makes a description look like the section's subtitle
        // instead of a second line of the title.
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(titleView, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(12) })
        }

        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleRow)
            addView(descriptionView, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) })
        }

        // Small triangles rather than a drawable: a chevron is two glyphs (open/closed) and a
        // TextView swap is simpler than a rotating ImageView for a project with no vector asset
        // for it yet.
        val chevron = TextView(this).apply {
            textSize = 16f
            text = if (expandedInitially) EXPANDED_CHEVRON else COLLAPSED_CHEVRON
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            isClickable = true
            addView(headerText, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(chevron)
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (expandedInitially) View.VISIBLE else View.GONE
            setPadding(dp(8), 0, dp(8), dp(8))
            content.forEach { addView(it) }
        }

        openSections += body to chevron

        header.setOnClickListener {
            // One section open at a time: close every section, then reopen this one unless it
            // was the one already open. Tapping the open section therefore closes it and leaves
            // the screen showing three headers, which is the state a rider scans from.
            val opening = body.visibility != View.VISIBLE
            openSections.forEach { (otherBody, otherChevron) ->
                otherBody.visibility = View.GONE
                otherChevron.text = COLLAPSED_CHEVRON
            }
            if (opening) {
                body.visibility = View.VISIBLE
                chevron.text = EXPANDED_CHEVRON
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), SECTION_BORDER_GREY)
            }
            addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    private companion object {
        /** Sampled off the Karoo's own app-store card, so ours sits beside it as a match. */
        const val KAROO_ALERT_YELLOW = 0xFFFFE900.toInt()

        /** A light, neutral border -- matches the weight of Barberfish's card outline. */
        const val SECTION_BORDER_GREY = 0xFFDDDDDD.toInt()
        const val EXPANDED_CHEVRON = "▾"
        const val COLLAPSED_CHEVRON = "▸"
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every view below is built fresh; anything left from a previous instance would have the
        // accordion reaching for views that are no longer on screen.
        openSections.clear()

        // Shaped like the cards the Karoo puts at the top of its own screens: a rounded yellow
        // panel with a circled mark, a short heading and the text below. The mark is an "i" and
        // the heading is not "HEADS UP", because on this device that pairing means a warning,
        // and this card only says where the fields live.
        val hintIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_info)
            setColorFilter(Color.BLACK)
            // Decorative: the heading next to it already says what this card is.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val hintTitle = TextView(this).apply {
            text = getString(R.string.hint_title)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = true
            letterSpacing = 0.06f
            setTextColor(Color.BLACK)
        }

        val hintHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(hintIcon, LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(
                hintTitle,
                LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) },
            )
        }

        val hintBody = TextView(this).apply {
            text = getString(R.string.activity_hint)
            textSize = 13f
            setTextColor(Color.BLACK)
            setPadding(0, dp(6), 0, 0)
        }

        val hint = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(KAROO_ALERT_YELLOW)
            }
            addView(hintHead)
            addView(hintBody)
        }

        val hudLeftLabel = TextView(this).apply {
            text = getString(R.string.hud_slot_left)
            textSize = 18f
            setPadding(0, dp(14), 0, 0)
        }

        val hudLabels = catalog.map { it.label }
        val hudTypeIds = catalog.map { it.typeId }

        // One mutable pair shared by both spinners, not two independent `var`s: setSelection
        // fires onItemSelected during construction, so both spinners call back before onCreate
        // returns, and two callbacks each closing over the other's initial value would write a
        // stale pair. Each callback below updates only its own half of this pair, then writes
        // the whole thing, the same shape as `var setting = Settings.fontSetting(this)` above.
        var slots = Settings.hudSlots(this, catalog.ids)

        val hudLeft = spinner(hudLabels, indexOrZero(hudTypeIds, slots.first)) {
            slots = slots.copy(first = catalog[it].typeId)
            Settings.setHudSlots(this, slots.first, slots.second)
        }

        val hudRightLabel = TextView(this).apply {
            text = getString(R.string.hud_slot_right)
            textSize = 18f
            setPadding(0, dp(14), 0, 0)
        }

        val hudRight = spinner(hudLabels, indexOrZero(hudTypeIds, slots.second)) {
            slots = slots.copy(second = catalog[it].typeId)
            Settings.setHudSlots(this, slots.first, slots.second)
        }

        val hudBarLabel = TextView(this).apply {
            text = getString(R.string.hud_bar)
            textSize = 18f
            setPadding(0, dp(14), 0, 0)
        }

        // Only the fields that carry a zone: everything else has no scale for a bar to fill
        // towards. "Off" is position 0 rather than a switch beside a picker, so choosing a source
        // and turning the bar on are one action instead of two that can disagree.
        val barFields = catalog.zoneCapable
        val barLabels = listOf(getString(R.string.hud_bar_off)) + barFields.map { it.label }
        val barTypeIds = barFields.map { it.typeId }
        // indexOf and not indexOrZero: this list has an "Off" row at position 0, so +1 on
        // indexOrZero's fallback would select the FIRST ZONE FIELD for an id this build no longer
        // has -- turning the bar on, with a source the rider never picked, exactly the failure
        // Settings.hudBarSource's KDoc says a bar cannot afford. Unreachable today because
        // hudBarSource filters against `known` first, and it stays unreachable if that ever
        // loosens.
        val barSelected = Settings.hudBarSource(this, barFields.ids)
            ?.let { barTypeIds.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?.plus(1)
            ?: 0

        val hudBar = spinner(barLabels, barSelected) {
            Settings.setHudBarSource(this, if (it == 0) null else barFields[it - 1].typeId)
        }

        val hudBarNote = TextView(this).apply {
            text = getString(R.string.hud_bar_desc)
            textSize = 13f
            setPadding(0, dp(7), 0, 0)
        }

        // Directly under the bar source, because it only ever describes the thing that spinner
        // switches on. Not hidden when no source is chosen: no other setting on this screen gates
        // itself on another, and a control that comes and goes is harder to find again than one
        // that is simply inert.
        val pillStyleLabel = TextView(this).apply {
            text = getString(R.string.setting_zone_pill_style)
            textSize = 18f
            setPadding(0, dp(20), 0, 0)
        }

        // Order matches ZonePillStyle so the spinner position is the ordinal.
        val pillStyles = ZonePillStyle.entries
        val pillStyleLabels = pillStyles.map {
            getString(
                when (it) {
                    ZonePillStyle.SEGMENTS -> R.string.setting_zone_pill_segments
                    ZonePillStyle.SOLID -> R.string.setting_zone_pill_solid
                },
            )
        }
        val pillStyle = spinner(pillStyleLabels, pillStyles.indexOf(Settings.zonePillStyle(this))) {
            Settings.setZonePillStyle(this, pillStyles[it])
        }

        val pillStyleNote = TextView(this).apply {
            text = getString(R.string.setting_zone_pill_style_desc)
            textSize = 13f
            setPadding(0, dp(7), 0, 0)
        }

        val hudNote = TextView(this).apply {
            text = getString(R.string.hud_desc)
            textSize = 13f
            setPadding(0, dp(7), 0, 0)
        }

        // No top padding here: this used to sit mid-list and needed a gap above it, but it is
        // now the first control in the GLOBAL section, and the section card's own header
        // already separates it from whatever is above -- the old dp(20) just doubled that gap.
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
            setPadding(0, dp(7), 0, 0)
        }

        // Font, and the two axes it can be drawn at. Kept as three spinners rather than
        // sliders: the Karoo is a small touchscreen usually operated with gloves on.
        val fontLabel = TextView(this).apply {
            text = getString(R.string.setting_font)
            textSize = 18f
            setPadding(0, dp(20), 0, 0)
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
            setPadding(0, dp(14), 0, 0)
        }
        val weightLabel = TextView(this).apply {
            text = getString(R.string.setting_font_weight)
            textSize = 18f
            setPadding(0, dp(14), 0, 0)
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
            setPadding(0, dp(7), 0, 0)
        }

        val raisedTail = Switch(this).apply {
            text = getString(R.string.setting_raised_tail)
            textSize = 18f
            isChecked = Settings.raisedTail(context)
            setPadding(0, dp(14), 0, 0)
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                Settings.setRaisedTail(context, checked)
            }
        }

        val raisedTailNote = TextView(this).apply {
            text = getString(R.string.setting_raised_tail_desc)
            textSize = 13f
            setPadding(0, dp(7), 0, 0)
        }

        val testMode = Switch(this).apply {
            text = getString(R.string.setting_test_mode)
            textSize = 18f
            isChecked = Settings.testMode(context)
            setPadding(0, dp(20), 0, 0)
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                Settings.setTestMode(context, checked)
            }
        }

        val testModeNote = TextView(this).apply {
            text = getString(R.string.setting_test_mode_desc)
            textSize = 13f
            setPadding(0, dp(7), 0, 0)
        }

        // Debug builds only, appended into GLOBAL below. Plausible-but-false power and heart
        // rate are worth keeping out of a rider's reach; this exists to shoot screenshots and
        // to look at field layout without a ride.
        val globalContent = mutableListOf(zoneColorsLabel, zoneColors, note).apply {
            if (BuildConfig.DEBUG) {
                add(testMode)
                add(testModeNote)
            }
        }

        // HUD sits last because it is the only section that configures one specific field,
        // after the two that apply to every field. It opens closed like the others: every
        // section's description already says what the card holds, so opening one on arrival only
        // costs the rider the scroll it takes to see the other two.
        val hudSection = section(
            getString(R.string.hud_section),
            getString(R.string.section_hud_desc),
            R.drawable.ic_bignum,
            false,
            hudLeftLabel, hudLeft, hudRightLabel, hudRight, hudBarLabel, hudBar, hudBarNote,
            pillStyleLabel, pillStyle, pillStyleNote, hudNote,
        )
        val appearanceSection = section(
            getString(R.string.section_appearance),
            getString(R.string.section_appearance_desc),
            R.drawable.ic_appearance,
            false,
            fontLabel, font, widthLabel, width, weightLabel, weight, fontNote, raisedTail, raisedTailNote,
        )
        val globalSection = section(
            getString(R.string.section_global),
            getString(R.string.section_global_desc),
            R.drawable.ic_global,
            false,
            *globalContent.toTypedArray(),
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // 6dp around the list of cards, 8dp between them, matching Barberfish's spacing.
            setPadding(dp(6), dp(6), dp(6), dp(6))
            addView(appearanceSection, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(8) })
            addView(globalSection, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(8) })
            addView(hudSection, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(16))
            addView(hint, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(18) })
            addView(controls)
        }

        // Scrollable: the settings outgrew the Karoo's 480x800 screen when the font controls
        // arrived, and a bare LinearLayout silently clips whatever does not fit -- which put
        // every one of those controls out of reach.
        setContentView(ScrollView(this).apply { addView(content) })
    }
}

/**
 * Position of [id] in [typeIds], or 0 if it is not there.
 *
 * Pulled out of onCreate as a top-level function so this one branch is testable without a
 * Context: [Settings.hudSlots] already resolves its result against catalog.ids, so [id] should
 * always be found, but if a resolved id ever were missing anyway, `indexOf` returning -1
 * into `Spinner.setSelection` would show an empty spinner instead of a chosen field.
 */
internal fun indexOrZero(typeIds: List<String>, id: String): Int = typeIds.indexOf(id).coerceAtLeast(0)
