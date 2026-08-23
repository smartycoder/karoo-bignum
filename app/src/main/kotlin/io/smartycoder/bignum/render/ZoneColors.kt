package io.smartycoder.bignum.render

import io.hammerhead.karooext.models.UserProfile

/**
 * Karoo's own zone colors, sampled from the zone dots on its Heart Rate Zones and Power Zones
 * settings screens. karoo-ext does not expose them -- UserProfile.Zone carries only min/max --
 * so matching the system palette means copying the values.
 *
 * The two scales differ in length: heart rate has five zones, power has seven. They share the
 * first four colors and diverge at the top, where power adds VO2 Max and Neuromuscular.
 */
object ZoneColors {

    private val hrPalette = intArrayOf(
        0xFF60EEB2.toInt(), // Z1 Active Recovery
        0xFF00B988.toInt(), // Z2 Endurance
        0xFFFFF500.toInt(), // Z3 Tempo
        0xFFFB8C65.toInt(), // Z4 Lactate Threshold
        0xFFD60404.toInt(), // Z5 Max
    )

    private val powerPalette = intArrayOf(
        0xFF60EEB2.toInt(), // Z1 Active Recovery
        0xFF00B988.toInt(), // Z2 Endurance
        0xFFFFF500.toInt(), // Z3 Tempo
        0xFFFB8C65.toInt(), // Z4 Lactate Threshold
        0xFFFE581F.toInt(), // Z5 VO2 Max
        0xFFD60404.toInt(), // Z6 Anaerobic Capacity
        0xFFB700A2.toInt(), // Z7 Neuromuscular
    )

    /**
     * Black or white, whichever reads better on [background]. Used when a field is filled with
     * its zone colour: the number, the label and the icon all sit on that fill.
     *
     * Plain WCAG relative luminance rather than anything cleverer -- across this palette the
     * worst pairing it picks still lands at 5.4:1, comfortably past the 4.5:1 threshold.
     */
    fun onColor(background: Int): Int =
        if (relativeLuminance(background) > 0.179) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    private fun relativeLuminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /**
     * Zone colour for [value], or null when no zone applies -- no profile, a non-positive
     * value, or a profile that carries no zones. The caller substitutes its own default;
     * returning a colour here would override the light/dark text colour from [Theme].
     */
    fun color(kind: ZoneKind, value: Double, profile: UserProfile?): Int? {
        if (profile == null || value <= 0) return null
        val zones = when (kind) {
            ZoneKind.HR -> profile.heartRateZones
            ZoneKind.POWER -> profile.powerZones
        }
        if (zones.isEmpty()) return null
        val palette = when (kind) {
            ZoneKind.HR -> hrPalette
            ZoneKind.POWER -> powerPalette
        }
        val v = value.toInt()
        val idx = zones.indexOfLast { v >= it.min }
            .let { if (it < 0) 0 else it }
            .coerceAtMost(palette.lastIndex)
        return palette[idx]
    }
}
