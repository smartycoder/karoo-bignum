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

    // One constant per sampled colour, named for the effort rather than for a zone number: the
    // two scales share these swatches but put them at different indices -- red is Z5 on the heart
    // rate scale and Z6 on the power one -- so a name like "Z5" would be wrong on one of them.
    // Copied as literals into both arrays, they were seven values maintained as twelve, and a
    // correction to one sampled swatch could reach one scale and not the other.
    private const val RECOVERY = 0xFF60EEB2.toInt()
    private const val ENDURANCE = 0xFF00B988.toInt()
    private const val TEMPO = 0xFFFFF500.toInt()
    private const val THRESHOLD = 0xFFFB8C65.toInt()
    private const val VO2 = 0xFFFE581F.toInt()
    private const val ANAEROBIC = 0xFFD60404.toInt()
    private const val NEUROMUSCULAR = 0xFFB700A2.toInt()

    // The arrays stay separate, and their ORDER is the thing each one owns: which effort a scale
    // puts at which zone is exactly where the two diverge.
    private val hrPalette = intArrayOf(
        RECOVERY,     // Z1 Active Recovery
        ENDURANCE,    // Z2 Endurance
        TEMPO,        // Z3 Tempo
        THRESHOLD,    // Z4 Lactate Threshold
        ANAEROBIC,    // Z5 Max
    )

    private val powerPalette = intArrayOf(
        RECOVERY,     // Z1 Active Recovery
        ENDURANCE,    // Z2 Endurance
        TEMPO,        // Z3 Tempo
        THRESHOLD,    // Z4 Lactate Threshold
        VO2,          // Z5 VO2 Max
        ANAEROBIC,    // Z6 Anaerobic Capacity
        NEUROMUSCULAR, // Z7 Neuromuscular
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
     * The rider's own zones for [kind], or an empty list where there are none. Public because
     * [ZoneBar] needs the same list this class picks a colour from, and two places reading
     * [UserProfile] for the same thing would be two places to keep in step.
     */
    /**
     * The colour of zone 1 on [kind]'s scale. Both scales start on the same colour, but they are
     * kept as two lookups rather than one constant so a future divergence at the bottom of the
     * scales is a palette edit and not a bug.
     *
     * For the zone bar below the first zone's floor, where [color] has no zone to name: the fill
     * is zero-width there, and this is what the icon and the value are contrasted against.
     */
    fun baseColor(kind: ZoneKind): Int = when (kind) {
        ZoneKind.HR -> hrPalette.first()
        ZoneKind.POWER -> powerPalette.first()
    }

    /**
     * Index of the zone [value] falls in, or -1 when it is under the first zone's floor.
     *
     * The one place that answers "which zone is this", for both the colour lookup and the bar's
     * fill. It used to be written twice, once on the Double and once on `value.toInt()` -- which
     * happen to agree, because every zone boundary is an Int and `floor(v) >= min` is the same
     * question as `v >= min` there. Two spellings of one rule is how a later change to boundary
     * handling reaches the colour and not the bar, and paints a fill that disagrees with it.
     */
    fun zoneIndex(value: Double, zones: List<UserProfile.Zone>): Int =
        zones.indexOfLast { value >= it.min }

    fun zones(kind: ZoneKind, profile: UserProfile?): List<UserProfile.Zone> {
        // The null check is its own statement so the `when` below stays exhaustive over
        // ZoneKind: an `else ->` here would silently hand a future kind the power zones instead
        // of failing to compile, which is the one mistake this list cannot survive.
        if (profile == null) return emptyList()
        return when (kind) {
            ZoneKind.HR -> profile.heartRateZones
            ZoneKind.POWER -> profile.powerZones
        }
    }

    /**
     * Zone colour for [value], or null when no zone applies -- no profile, a non-positive
     * value, or a profile that carries no zones. The caller substitutes its own default;
     * returning a colour here would override the light/dark text colour from [Theme].
     */
    fun color(kind: ZoneKind, value: Double, profile: UserProfile?): Int? {
        if (profile == null || value <= 0) return null
        val zones = zones(kind, profile)
        if (zones.isEmpty()) return null
        val palette = when (kind) {
            ZoneKind.HR -> hrPalette
            ZoneKind.POWER -> powerPalette
        }
        // coerceAtLeast(0): a reading under the first zone's floor has no zone of its own, and
        // the lowest colour is the honest answer for it -- unlike ZoneBar.fraction, which wants
        // an empty bar there rather than a first-zone-worth of fill.
        val idx = zoneIndex(value, zones)
            .coerceAtLeast(0)
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
