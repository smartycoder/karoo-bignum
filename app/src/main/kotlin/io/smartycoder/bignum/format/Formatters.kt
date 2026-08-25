package io.smartycoder.bignum.format

import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import io.hammerhead.karooext.models.UserProfile.PreferredUnit.UnitType
import java.util.Locale

object Formatters {

    val speed: (Double, PreferredUnit?) -> Pair<String, String> = { v, p ->
        when (p?.distance) {
            UnitType.IMPERIAL -> compact(v * 2.23694) to "mph"
            else              -> compact(v * 3.6)     to "km/h"
        }
    }

    val bpm: (Double, PreferredUnit?) -> Pair<String, String> =
        { v, _ -> "${v.toInt()}" to "bpm" }

    val watts: (Double, PreferredUnit?) -> Pair<String, String> =
        { v, _ -> "${v.toInt()}" to "W" }

    val wattsPerKg: (Double, PreferredUnit?) -> Pair<String, String> =
        { v, _ -> "%.1f".fmt(v) to "W/kg" }

    /**
     * Watts per kilogram of rider weight, or null when there is no usable weight. Null rather
     * than the raw watts: a field showing 250 where it promises W/kg is worse than showing "--".
     */
    fun perKilogram(watts: Double, weightKg: Float?): Double? =
        // isFinite before the comparison: NaN <= 0f is false, so a NaN weight would slip past a
        // bare range check and put "NaN" on the rider's screen.
        if (weightKg == null || !weightKg.isFinite() || weightKg <= 0f) null else watts / weightKg

    val count: (Double, PreferredUnit?) -> Pair<String, String> =
        { v, _ -> "${v.toInt()}" to "" }

    val rpm: (Double, PreferredUnit?) -> Pair<String, String> =
        { v, _ -> "${v.toInt()}" to "rpm" }

    val percent: (Double, PreferredUnit?) -> Pair<String, String> =
        { v, _ -> compact(v) to "%" }

    val distance: (Double, PreferredUnit?) -> Pair<String, String> = { v, p ->
        when (p?.distance) {
            UnitType.IMPERIAL -> compact(v / 1609.345) to "mi"
            else              -> compact(v / 1000.0)   to "km"
        }
    }

    val elevation: (Double, PreferredUnit?) -> Pair<String, String> = { v, p ->
        when (p?.elevation) {
            UnitType.IMPERIAL -> "${(v * 3.28084).toInt()}" to "ft"
            else              -> "${v.toInt()}"            to "m"
        }
    }

    val temperature: (Double, PreferredUnit?) -> Pair<String, String> = { v, p ->
        when (p?.temperature) {
            UnitType.IMPERIAL -> "${(v * 9.0 / 5.0 + 32).toInt()}" to "°F"
            else              -> "${v.toInt()}"                    to "°C"
        }
    }

    /**
     * Always h:mm:ss, including the leading "0:" of the first hour and a standing clock's
     * "0:00:00". Dropping the hours below one would save no room -- the field is sized against
     * a "0:00:00" template either way -- and would have the field change shape under the rider
     * the moment the ride starts.
     */
    val time: (Double, PreferredUnit?) -> Pair<String, String> = { v, _ ->
        val s = (v / 1000.0).toInt().coerceAtLeast(0)
        "%d:%02d:%02d".fmt(s / 3600, (s % 3600) / 60, s % 60) to ""
    }

    /**
     * One decimal while it fits the field's width budget, none once the value grows past it.
     * A field is sized for a fixed number of glyphs; without this, "105.4" or "-12.5" would be
     * rendered ~20% smaller than every other value just to fit.
     */
    // Branch on the rounded value, not the raw one: 99.96 is below 100 but "%.1f" prints it
    // as "100.0", one glyph wider than the "00.0" budget this exists to hold.
    private fun compact(v: Double): String =
        if (v <= -10.0 || Math.round(v * 10) >= 1000) "${Math.round(v)}" else "%.1f".fmt(v)

    private fun String.fmt(vararg args: Any): String =
        String.format(Locale.US, this, *args)
}
