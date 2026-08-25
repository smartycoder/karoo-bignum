package io.smartycoder.bignum.render

import io.hammerhead.karooext.models.UserProfile
import kotlin.math.abs

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

    // Karoo's own Climber thresholds -- 2 / 5 / 8 / 11 / 14 / 20, not Wahoo's -- matching the
    // device the extension runs on beats matching the device the idea came from. Kept next to
    // powerPalette rather than in a separate table so there is one place holding these colours.
    // Only the top one, 20.0, is named: it is the sole threshold wedgeHeightFraction also needs,
    // so it has to be a shared constant rather than a literal duplicated in two places; the other
    // five appear only here and stay bare literals.
    private const val GRADE_STEEP_PERCENT = 20.0

    /**
     * Zone colour for a grade [percent], banded on Karoo's Climber scale and resolved against
     * the same [powerPalette] the power zones use -- the hues match Karoo's own gradient scale
     * to within a degree.
     *
     * Banded on the signed value, not its magnitude: Karoo's lowest band already covers negative
     * grades, so a descent colours the same as flat ground. Steepness of a descent is carried by
     * the wedge's height and direction instead, not by colour.
     *
     * Non-finite [percent] takes the lowest band rather than falling through every `<` comparison
     * to the last `else` -- NaN < 2 is false same as every other comparison, so an unguarded scale
     * would paint a bogus reading magenta, the loudest colour in the palette for a value that
     * means nothing. Grade readings are known to spike, so this input is expected to misbehave.
     */
    fun grade(percent: Double): Int {
        if (!percent.isFinite()) return powerPalette[0]
        val idx = when {
            percent < 2 -> 0
            percent < 5 -> 1
            percent < 8 -> 2
            percent < 11 -> 3
            percent < 14 -> 4
            percent < GRADE_STEEP_PERCENT -> 5
            else -> 6
        }
        return powerPalette[idx]
    }

    // Floor so a near-zero grade still shows a sliver of wedge rather than nothing.
    private const val WEDGE_MIN_FRACTION = 0.04f

    /**
     * How far up the tile a grade wedge reaches: 0 at flat ground, 1 at |[percent]| ==
     * [GRADE_STEEP_PERCENT] -- Karoo's top band -- so the wedge spends its whole range on
     * gradients a rider actually meets instead of being swamped by a sensor-noise spike.
     *
     * Pure and internal rather than private so it can be exercised directly from tests: the
     * unit test suite runs with android.graphics unusable, so the wedge maths has to live where
     * it does not need a Canvas to check.
     */
    internal fun wedgeHeightFraction(percent: Double): Float {
        // Same guard as grade(): NaN would otherwise survive both coerce calls and reach the
        // canvas as a NaN coordinate in a lineTo(), an undefined path rather than a drawing error.
        if (!percent.isFinite()) return WEDGE_MIN_FRACTION
        val raw = (abs(percent) / GRADE_STEEP_PERCENT).toFloat().coerceAtMost(1f)
        return raw.coerceAtLeast(WEDGE_MIN_FRACTION)
    }
}
