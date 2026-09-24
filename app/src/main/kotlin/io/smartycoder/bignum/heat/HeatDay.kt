package io.smartycoder.bignum.heat

import kotlin.math.min

/**
 * Today's heat training so far, and where the adaptation score stood when today began.
 *
 * All [HeatTracker] keeps between rides: the score is a running state, so yesterday's end is
 * enough to carry it forward, and there is no history of daily loads to store. Pure, so the day
 * rollover and the accumulation can be tested without a Karoo.
 */
data class HeatDay(
    /** Local date as days since the epoch, as [java.time.LocalDate.toEpochDay] counts them. */
    val epochDay: Long,
    /** Load earned today at HSI under [HeatTrainingLoad.LOW_STRAIN_HSI]; capped on its own. */
    val lowLoad: Double,
    /** Load earned today at HSI from [HeatTrainingLoad.LOW_STRAIN_HSI] up. */
    val highLoad: Double,
    /** The adaptation score at the end of yesterday. */
    val before: HeatAdaptation.Day,
) {
    /** Today's Heat Training Load. */
    val load: Double get() = min(HeatTrainingLoad.MAX, lowLoad + highLoad)

    /**
     * Today's Heat Adaptation Score, as if the day ended now. It moves as the load does: a day's
     * load only counts once it passes [HeatAdaptation.MIN_LOAD], and a second day in a row
     * without one shows its decay from the morning.
     */
    val adaptation: Double get() = HeatAdaptation.step(before, load).score

    /** This day with [hours] more spent at [hsi]. */
    fun accumulate(hsi: Double, hours: Double): HeatDay {
        if (!hsi.isFinite() || !(hours > 0)) return this
        val earned = HeatTrainingLoad.ratePerHour(hsi) * hours
        if (earned <= 0) return this
        return if (hsi < HeatTrainingLoad.LOW_STRAIN_HSI) {
            copy(lowLoad = min(HeatTrainingLoad.LOW_STRAIN_CAP, lowLoad + earned))
        } else {
            copy(highLoad = min(HeatTrainingLoad.MAX, highLoad + earned))
        }
    }

    /**
     * This state carried forward to [today]: this day closed with its load, every day in between
     * closed with none, and a fresh day opened. Unchanged when [today] is not later -- a clock set
     * back should not wipe a day's training.
     */
    fun rolledTo(today: Long): HeatDay {
        if (today <= epochDay) return this
        var day = HeatAdaptation.step(before, load)
        // Past this many empty days every score has decayed to zero, so a Karoo left in a drawer
        // for a year does not loop a year's worth of days to find that out.
        val empty = (today - epochDay - 1).coerceAtMost(MAX_EMPTY_DAYS)
        repeat(empty.toInt()) { day = HeatAdaptation.step(day, 0.0) }
        return HeatDay(today, 0.0, 0.0, day)
    }

    companion object {
        private const val MAX_EMPTY_DAYS = 60L

        fun fresh(today: Long) = HeatDay(today, 0.0, 0.0, HeatAdaptation.Day.START)
    }
}
