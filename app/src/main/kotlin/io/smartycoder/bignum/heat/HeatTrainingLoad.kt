package io.smartycoder.bignum.heat

/**
 * CORE's Heat Training Load: how much a day's riding contributes to heat adaptation, from 0 to 10.
 *
 * CORE describes it as time spent at an elevated Heat Strain Index and publishes a table of the
 * load a session reaches when a given HSI is held for a given time, after a 20-40 minute climb up
 * to it. It does not publish the formula. The rates below are fitted to that table -- a per-hour
 * rate for each HSI, with a 30-minute ramp up to it -- and reproduce every one of its 36 cells to
 * within 0.13.
 *
 * Two features of the table shape the model beyond a plain rate:
 * - Zone 1 earns nothing, and the jump into zone 3 is steep: an hour at HSI 3.5 is worth nearly
 *   three hours at 2.5, which is why CORE calls zone 3 the one to train in.
 * - Below HSI 2 the load stops growing no matter how long it runs -- the table's HSI 1.5 column
 *   tops out at 1.4 -- so what is earned there goes into a bucket of its own, capped at 1.4.
 *
 * Reference: help.corebodytemp.com, "Heat Training Load".
 */
object HeatTrainingLoad {

    /** A day's load never goes past this, however many sessions it holds. */
    const val MAX = 10.0

    /** Load earned while HSI is under [LOW_STRAIN_HSI] stops at this, however long it runs. */
    const val LOW_STRAIN_CAP = 1.4
    const val LOW_STRAIN_HSI = 2.0

    // (HSI, load per hour held there). Nothing under 1.0; flat above 6.5, since zone 4 is not
    // recommended for heat training and the table says nothing about it.
    private val rates = arrayOf(
        1.0 to 0.0,
        1.5 to 1.0,
        2.5 to 2.3,
        3.5 to 6.4,
        4.5 to 7.4,
        5.5 to 8.0,
        6.5 to 8.2,
    )

    /** Load earned per hour spent at [hsi]. */
    fun ratePerHour(hsi: Double): Double {
        if (!hsi.isFinite() || hsi <= rates.first().first) return 0.0
        for (i in 1 until rates.size) {
            val (x1, y1) = rates[i]
            if (hsi <= x1) {
                val (x0, y0) = rates[i - 1]
                return y0 + (y1 - y0) * (hsi - x0) / (x1 - x0)
            }
        }
        return rates.last().second
    }
}
