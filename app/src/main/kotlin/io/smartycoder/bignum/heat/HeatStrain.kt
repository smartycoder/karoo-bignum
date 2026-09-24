package io.smartycoder.bignum.heat

/**
 * CORE's Heat Strain Index and the four Heat Zones built on it.
 *
 * The sensor works the index out itself and broadcasts it, but karoo-ext hands over only the core
 * and skin temperature. [CoreSensorLink] reads the sensor's own index over Bluetooth; this is the
 * estimate used whenever it cannot -- no permission, no connection to spare, or firmware older
 * than 0.8.7. CORE does not publish the formula -- its help centre describes
 * HSI as an estimate of mean body temperature, from core and skin, scaled to run from 0 to about
 * 10 -- but it does publish a chart of the four zones over the core/skin plane. The tables below
 * are that chart's three zone boundaries, HSI 1.0, 3.0 and 7.0, read off it every half degree of
 * skin temperature, plus the one point the text pins down: HSI 10 is a core of 40 °C with a skin
 * of 37 °C. Between the boundaries the index is interpolated.
 *
 * Reference: help.corebodytemp.com, "Heat Strain Index" and "Heat Zones".
 */
object HeatStrain {

    /** The chart's skin axis: 28 to 38 °C, a row every half degree. */
    private const val SKIN_MIN = 28.0
    private const val SKIN_STEP = 0.5

    // Core temperature (°C) at which the index reaches 1.0, 3.0 and 7.0, one entry per row of
    // skin temperature from 28.0 to 38.0. The boundaries curve: on cool skin the core can run
    // well past 38 before anything registers, while on hot skin the whole scale compresses.
    private val HSI_1 = doubleArrayOf(
        38.80, 38.73, 38.67, 38.61, 38.54, 38.48, 38.41, 38.34, 38.26, 38.19, 38.10,
        38.01, 37.92, 37.81, 37.70, 37.59, 37.46, 37.33, 37.19, 37.04, 36.88,
    )
    private val HSI_3 = doubleArrayOf(
        39.62, 39.60, 39.56, 39.51, 39.43, 39.35, 39.25, 39.14, 39.02, 38.90, 38.77,
        38.63, 38.51, 38.37, 38.24, 38.11, 37.98, 37.85, 37.72, 37.61, 37.49,
    )
    private val HSI_7 = doubleArrayOf(
        40.00, 40.00, 40.00, 40.00, 39.99, 39.97, 39.95, 39.93, 39.90, 39.86, 39.82,
        39.77, 39.72, 39.65, 39.58, 39.50, 39.41, 39.31, 39.21, 39.10, 38.98,
    )

    /**
     * Index per degree of core above the HSI 7 boundary. The chart stops at 7, so this is the
     * one slope the text gives: from the 7.0 boundary at 37 °C skin (39.21) to HSI 10 at 40 °C.
     */
    private const val SLOPE_ABOVE_7 = 3.0 / (40.0 - 39.21)

    /** The index is clamped here: CORE calls 10 exceptionally high, and the field is sized for it. */
    const val MAX = 10.0

    // Outside these a reading is a sensor that is not on a body -- sitting in a jersey pocket, or
    // not yet settled -- rather than a temperature worth an index.
    private val PLAUSIBLE_CORE = 30.0..45.0
    private val PLAUSIBLE_SKIN = 15.0..45.0

    /**
     * The Heat Strain Index for a core and a skin temperature in °C, or null when either is
     * missing or not a plausible body temperature.
     *
     * Skin outside the chart's 28-38 °C is read on the nearest edge row, which is the
     * conservative choice at the cold end and the only one available at the hot end.
     */
    fun index(coreC: Double?, skinC: Double?): Double? {
        if (coreC == null || skinC == null) return null
        if (!coreC.isFinite() || !skinC.isFinite()) return null
        if (coreC !in PLAUSIBLE_CORE || skinC !in PLAUSIBLE_SKIN) return null

        val row = ((skinC - SKIN_MIN) / SKIN_STEP).coerceIn(0.0, (HSI_1.size - 1).toDouble())
        val t1 = rowValue(HSI_1, row)
        val t3 = rowValue(HSI_3, row)
        val t7 = rowValue(HSI_7, row)

        val hsi = when {
            // Below the first boundary the 1-to-3 gradient is carried on down until it reaches
            // zero: the chart has no 0 line, and zone 1 is where it would be.
            coreC < t1 -> 1.0 - 2.0 * (t1 - coreC) / (t3 - t1)
            coreC < t3 -> 1.0 + 2.0 * (coreC - t1) / (t3 - t1)
            coreC < t7 -> 3.0 + 4.0 * (coreC - t3) / (t7 - t3)
            else -> 7.0 + SLOPE_ABOVE_7 * (coreC - t7)
        }
        return hsi.coerceIn(0.0, MAX)
    }

    private fun rowValue(table: DoubleArray, row: Double): Double {
        val lo = row.toInt().coerceAtMost(table.size - 2)
        val f = row - lo
        return table[lo] + (table[lo + 1] - table[lo]) * f
    }

    /**
     * Heat Zone 1-4 for [hsi]: 0-0.9, 1.0-2.9, 3.0-6.9 and 7.0 up.
     *
     * Decided on the value rounded to the tenth the field prints, so a 2.96 drawn as "3.0" is in
     * zone 3 along with its colour, not zone 2 by a margin the rider cannot see.
     */
    fun zone(hsi: Double): Int {
        val shown = Math.round(hsi * 10) / 10.0
        return when {
            shown < 1.0 -> 1
            shown < 3.0 -> 2
            shown < 7.0 -> 3
            else -> 4
        }
    }

    // CORE's own zone colours, sampled from the zone labels on its Heat Zones chart.
    private val zonePalette = intArrayOf(
        0xFF37CA94.toInt(), // Zone 1 No heat strain
        0xFFFFC655.toInt(), // Zone 2 Moderate heat strain
        0xFFFFA06A.toInt(), // Zone 3 High heat strain
        0xFFF35264.toInt(), // Zone 4 Extremely high heat strain
    )

    /** Colour of Heat Zone [zone], or null for anything that is not a zone. */
    fun colorOfZone(zone: Int): Int? = zonePalette.getOrNull(zone - 1)

    /** Colour of the Heat Zone [hsi] falls in. */
    fun color(hsi: Double): Int? = if (hsi.isFinite()) colorOfZone(zone(hsi)) else null
}
