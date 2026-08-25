package io.smartycoder.bignum

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** How a field carries its heart rate or power zone. */
enum class ZoneColorMode {
    /** No zone colour at all; every value in the normal text colour. */
    OFF,

    /** The number itself takes the zone colour. */
    TEXT,

    /** The whole field is filled with the zone colour, the number set in black or white. */
    FILL,
    ;

    companion object {
        fun from(name: String?): ZoneColorMode? = entries.firstOrNull { it.name == name }
    }
}

/** Typeface the numbers and headers are drawn in. */
enum class NumberFont {
    /** Static Bold, no axes: [FontSetting.width] and [FontSetting.weight] do not apply. */
    OSWALD,

    /** Variable, with tabular figures. */
    SAIRA,
    ;

    companion object {
        fun from(name: String?): NumberFont? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The typeface and, for a variable one, the axes it is drawn at.
 *
 * Width is the setting that decides how big the number ends up: a field's bitmap is exactly as
 * tall as the digits and as wide as its widest value, and the view scales it to fit, so in
 * every field where the width is the binding constraint -- which is most of them -- a narrower
 * face buys height. Weight is taste, and compensates for how much lighter a face looks once it
 * is narrow.
 */
data class FontSetting(
    val font: NumberFont,
    /** Percent on Saira's wdth axis. */
    val width: Int,
    /** Position on Saira's wght axis; 700 is Bold. */
    val weight: Int,
) {
    /** Whether [width] and [weight] mean anything for this typeface. */
    val hasAxes: Boolean get() = font == NumberFont.SAIRA
}

/**
 * Everything about how a field is drawn, as opposed to what it says. Carried as one object so a
 * field still combines five flows: the typed combine() stops at five, and the stream, the
 * profile, the zone mode and test mode already take four of them.
 */
data class Appearance(val font: FontSetting, val raisedTail: Boolean)

/**
 * Settings that apply to every BigNum field at once, edited in the BigNum app.
 *
 * The activity and the extension service share a process, so a change reaches a live field
 * through the flows below without any IPC of our own.
 */
object Settings {

    private const val PREFS = "bignum"
    private const val KEY_ZONE_COLOR_MODE = "zone_color_mode"
    private const val KEY_TEST_MODE = "test_mode"
    private const val KEY_FONT = "number_font"
    private const val KEY_FONT_WIDTH = "font_width"
    private const val KEY_FONT_WEIGHT = "font_weight"
    private const val KEY_RAISED_TAIL = "raised_decimals"

    /** Saira's axis ranges; anything read back from preferences is clamped into them. */
    private val WIDTH_RANGE = 50..125
    private val WEIGHT_RANGE = 100..900

    /**
     * Offered in the app, narrowest first because that is the one worth reaching for. Two
     * percent a step: near the narrow end that is worth 2-3px of digit height on a half-width
     * field, which is visible; past about 100 it is under a pixel, but the steps stay even
     * rather than bunching, so the list reads as a scale instead of a set of opinions.
     */
    val WIDTHS = (WIDTH_RANGE.first..WIDTH_RANGE.last step 2).toList()
    val WEIGHTS = (WEIGHT_RANGE.first..WEIGHT_RANGE.last step 100).toList()

    /** What a fresh install draws with. Internal so a test can hold it to that. */
    internal val DEFAULT_FONT = FontSetting(NumberFont.SAIRA, width = 50, weight = 700)

    /** Replaced by [KEY_ZONE_COLOR_MODE]; still read once so an existing install keeps its choice. */
    private const val LEGACY_KEY_ZONE_COLORS = "zone_colors"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun zoneColorMode(context: Context): ZoneColorMode {
        val prefs = prefs(context)
        return resolveMode(
            stored = prefs.getString(KEY_ZONE_COLOR_MODE, null),
            legacyZoneColors = prefs.getBoolean(LEGACY_KEY_ZONE_COLORS, true),
        )
    }

    /**
     * The stored mode, or what an install that predates it should get. The setting used to be a
     * boolean: colours on meant colouring the number, which is what [ZoneColorMode.TEXT] does now.
     *
     * Split out from [zoneColorMode] so the migration is testable without SharedPreferences.
     */
    internal fun resolveMode(stored: String?, legacyZoneColors: Boolean): ZoneColorMode =
        ZoneColorMode.from(stored)
            ?: if (legacyZoneColors) ZoneColorMode.TEXT else ZoneColorMode.OFF

    fun setZoneColorMode(context: Context, mode: ZoneColorMode) {
        prefs(context).edit().putString(KEY_ZONE_COLOR_MODE, mode.name).apply()
    }

    /**
     * Makes every field show its demo value instead of live data, so the fields can be
     * screenshotted and looked at without a ride or a sensor.
     *
     * Debug builds only, and enforced here rather than only in the UI: a release installed over a
     * debug build inherits its data directory, so hiding the switch would leave a rider with demo
     * values and no way to turn them off. Plausible-but-false power and heart rate on a bike
     * computer are worth keeping out of reach.
     */
    fun testMode(context: Context): Boolean =
        BuildConfig.DEBUG && prefs(context).getBoolean(KEY_TEST_MODE, false)

    fun setTestMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TEST_MODE, enabled).apply()
    }

    fun fontSetting(context: Context): FontSetting {
        val prefs = prefs(context)
        return resolveFont(
            font = prefs.getString(KEY_FONT, null),
            width = prefs.getInt(KEY_FONT_WIDTH, DEFAULT_FONT.width),
            weight = prefs.getInt(KEY_FONT_WEIGHT, DEFAULT_FONT.weight),
        )
    }

    /**
     * Split out from [fontSetting] so the clamping is testable without SharedPreferences. Both
     * axes are clamped rather than rejected: a value from a future build that offers more steps
     * should land at the nearest one this build can draw, not throw the whole setting away.
     */
    internal fun resolveFont(font: String?, width: Int, weight: Int): FontSetting = FontSetting(
        font = NumberFont.from(font) ?: DEFAULT_FONT.font,
        width = width.coerceIn(WIDTH_RANGE),
        weight = weight.coerceIn(WEIGHT_RANGE),
    )

    fun setFontSetting(context: Context, setting: FontSetting) {
        prefs(context).edit()
            .putString(KEY_FONT, setting.font.name)
            .putInt(KEY_FONT_WIDTH, setting.width)
            .putInt(KEY_FONT_WEIGHT, setting.weight)
            .apply()
    }

    /**
     * Whether a value's decimal, or a ride time's seconds, are drawn small and raised. On by
     * default, which is the closer of the two to what elapsed time did before this was a choice
     * -- though not identical: the separator is dropped now, so "1:34" + ":17" reads "1:34" +
     * "17". Off puts every value back at one size, including the time.
     */
    fun raisedTail(context: Context): Boolean = prefs(context).getBoolean(KEY_RAISED_TAIL, true)

    fun setRaisedTail(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RAISED_TAIL, enabled).apply()
    }

    fun zoneColorModeFlow(context: Context): Flow<ZoneColorMode> =
        prefFlow(context, setOf(KEY_ZONE_COLOR_MODE), ::zoneColorMode)

    fun testModeFlow(context: Context): Flow<Boolean> = prefFlow(context, setOf(KEY_TEST_MODE), ::testMode)

    fun appearanceFlow(context: Context): Flow<Appearance> =
        prefFlow(context, setOf(KEY_FONT, KEY_FONT_WIDTH, KEY_FONT_WEIGHT, KEY_RAISED_TAIL)) {
            Appearance(fontSetting(it), raisedTail(it))
        }

    private fun <T> prefFlow(
        context: Context,
        keys: Set<String>,
        read: (Context) -> T,
    ): Flow<T> = callbackFlow {
        val prefs = prefs(context)
        trySendBlocking(read(context))
        // The listener fires on the main thread, so send without blocking. changed is null when
        // the preferences are cleared wholesale (API 30+), which also means our value changed.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == null || changed in keys) trySend(read(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        // Writing the three font keys fires the listener three times for one edit; without this
        // a field would redraw twice for nothing.
    }.distinctUntilChanged()
}
