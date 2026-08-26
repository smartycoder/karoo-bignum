package io.smartycoder.bignum.format

import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import io.hammerhead.karooext.models.UserProfile.PreferredUnit.UnitType
import java.util.Locale
import java.util.TimeZone

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
     * h:mm:ss from one hour, m:ss below it.
     *
     * The leading "0:" is dropped rather than kept for a steady shape, because the field is
     * scaled against [timeTemplate] and two glyphs fewer is height the number gets to keep: on
     * every tile where width is the binding constraint -- which is most of them -- "12:23" is
     * drawn appreciably taller than "0:12:23". Where height binds instead, the value simply
     * comes out shorter at the same size. The price is a visible step down as the clock passes
     * the hour.
     */
    val time: (Double, PreferredUnit?) -> Pair<String, String> = { v, _ ->
        val s = (v / 1000.0).toInt().coerceAtLeast(0)
        val hours = s / 3600
        val text =
            if (hours > 0) "%d:%02d:%02d".fmt(hours, (s % 3600) / 60, s % 60)
            else "%d:%02d".fmt(s / 60, s % 60)
        text to ""
    }

    /**
     * The width budget for a duration [time] has already formatted, which is the shape that
     * value was drawn in. Without this the short form would be scaled against "0:00:00" and
     * would gain nothing at all.
     */
    // "0" rather than "8" in both: on Saira every digit is the same width, but Oswald's are
    // not and its "0" is the widest, so an "8" template would be narrower than values the field
    // really draws for a rider who picked Oswald.
    fun timeTemplate(text: String): String =
        if (text.count { it == ':' } > 1) "0:00:00" else "00:00"

    /**
     * A wall clock as h:mm, in the device's own time zone.
     *
     * karoo-ext does not say what TIME_OF_ARRIVAL carries and the Karoo is not consistent about
     * it -- elapsed time arrives in milliseconds, a FIT timestamp in seconds -- so the magnitude
     * decides which of the four plausible encodings it is.
     * ponytail: collapses to one branch the day it is measured against a real route.
     */
    val clock: (Double, PreferredUnit?) -> Pair<String, String> = { v, _ ->
        val m = minuteOfDay(v)
        "%d:%02d".fmt(m / 60, m % 60) to ""
    }

    /**
     * Minutes past local midnight for a value that may be an instant or an offset into the day.
     *
     * The four ranges do not overlap: seconds into a day stop at 86_399, and an epoch in seconds
     * has passed 1e9 since 2001. The one blind spot is the first 86 seconds after midnight in
     * milliseconds-into-the-day, read as seconds; an ETA lands there about a minute a day.
     */
    internal fun minuteOfDay(v: Double): Int = when {
        v >= 1e11 -> localMinutes(v.toLong())
        v >= 1e8 -> localMinutes((v * 1000.0).toLong())
        v >= 86_400 -> ((v / 60_000).toInt()) % 1440
        else -> ((v / 60).toInt()) % 1440
    }

    private fun localMinutes(epochMillis: Long): Int {
        val local = epochMillis + TimeZone.getDefault().getOffset(epochMillis)
        return (Math.floorMod(local / 60_000, 1440L)).toInt()
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
