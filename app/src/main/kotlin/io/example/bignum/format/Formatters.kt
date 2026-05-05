package io.example.bignum.format

import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import io.hammerhead.karooext.models.UserProfile.PreferredUnit.UnitType
import java.util.Locale

object Formatters {

    val speed: (Double, PreferredUnit?) -> Pair<String, String> = { v, p ->
        when (p?.distance) {
            UnitType.IMPERIAL -> "%.1f".fmt(v * 2.23694) to "mph"
            else              -> "%.1f".fmt(v * 3.6)     to "km/h"
        }
    }

    val bpm: (Double, PreferredUnit?) -> Pair<String, String> =
        { v, _ -> "${v.toInt()}" to "bpm" }

    val watts: (Double, PreferredUnit?) -> Pair<String, String> =
        { v, _ -> "${v.toInt()}" to "W" }

    val rpm: (Double, PreferredUnit?) -> Pair<String, String> =
        { v, _ -> "${v.toInt()}" to "rpm" }

    val percent: (Double, PreferredUnit?) -> Pair<String, String> =
        { v, _ -> "%.1f".fmt(v) to "%" }

    val distance: (Double, PreferredUnit?) -> Pair<String, String> = { v, p ->
        when (p?.distance) {
            UnitType.IMPERIAL -> "%.1f".fmt(v / 1609.345) to "mi"
            else              -> "%.1f".fmt(v / 1000.0)   to "km"
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

    val time: (Double, PreferredUnit?) -> Pair<String, String> = { v, _ ->
        val s = (v / 1000.0).toInt().coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        val text = if (h > 0) "%d:%02d:%02d".fmt(h, m, ss) else "%d:%02d".fmt(m, ss)
        text to ""
    }

    private fun String.fmt(vararg args: Any): String =
        String.format(Locale.US, this, *args)
}
