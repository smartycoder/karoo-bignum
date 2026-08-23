package io.smartycoder.bignum

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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

    fun zoneColorModeFlow(context: Context): Flow<ZoneColorMode> =
        prefFlow(context, KEY_ZONE_COLOR_MODE, ::zoneColorMode)

    fun testModeFlow(context: Context): Flow<Boolean> = prefFlow(context, KEY_TEST_MODE, ::testMode)

    private fun <T> prefFlow(
        context: Context,
        key: String,
        read: (Context) -> T,
    ): Flow<T> = callbackFlow {
        val prefs = prefs(context)
        trySendBlocking(read(context))
        // The listener fires on the main thread, so send without blocking. changed is null when
        // the preferences are cleared wholesale (API 30+), which also means our value changed.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == null || changed == key) trySend(read(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
