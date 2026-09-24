package io.smartycoder.bignum.heat

/**
 * CORE's Heat Adaptation Score: how adapted to the heat a rider is, 0 to 100 %, built up day by day
 * from the daily Heat Training Loads.
 *
 * CORE publishes the rules in words and ten worked scenarios as charts, but not the formula. The
 * model here is fitted to those charts and reproduces them to within about a point, including the
 * two end values CORE prints on them (72.1 % after two weeks of daily loads of 5, 17.5 % after six
 * weeks of loads of 3 two or three times a week):
 *
 * - A day only counts when its load is above 2. It then adds `load x multiplier(score)`: exactly
 *   the load while the score is under 20, and from 20 up a share that shrinks as the score nears
 *   100, which is why the last few points take the longest.
 * - A day without a counting load costs nothing if the day before had one. Each further day in a
 *   row without one costs 2.5 points -- the decay CORE warns about, and the reason frequency
 *   matters more than size once a rider is well adapted.
 *
 * Reference: help.corebodytemp.com, "Heat Adaptation Score" and "Boosting Your Heat Adaptation
 * Score".
 */
object HeatAdaptation {

    /** A day's load has to be above this to raise the score. */
    const val MIN_LOAD = 2.0

    /** Points lost on each day, after the first, in a run of days without a counting load. */
    const val DAILY_DECAY = 2.5

    const val MAX = 100.0

    // Below this the score rises by exactly the day's load; from here the multiplier jumps and
    // then falls off in a straight line. The jump is in CORE's charts, not an artefact of the
    // fit: in every scenario the day that starts at 20 gains nearly twice its load.
    private const val ONBOARDING = 20.0
    private const val SLOPE = 0.0232
    private const val FLOOR = 0.07

    /** The score at the end of a day, and how many days in a row have gone without a load. */
    data class Day(val score: Double, val restStreak: Int) {
        companion object {
            val START = Day(0.0, 0)
        }
    }

    /** How much of a counting day's load reaches the score, at [score]. */
    fun multiplier(score: Double): Double =
        if (score < ONBOARDING) 1.0 else SLOPE * (MAX - score) + FLOOR

    /** The day after [before], on which the rider earned [load]. */
    fun step(before: Day, load: Double): Day {
        if (load > MIN_LOAD) {
            val gained = before.score + load * multiplier(before.score)
            return Day(gained.coerceAtMost(MAX), restStreak = 0)
        }
        val streak = before.restStreak + 1
        val score = if (streak >= 2) (before.score - DAILY_DECAY).coerceAtLeast(0.0) else before.score
        return Day(score, streak)
    }

    /** CORE's four levels, by the score each starts at. */
    enum class Level(val floor: Double) {
        THERMAL_ROOKIE(0.0),
        HEAT_ACCUSTOMED(25.0),
        HEAT_ADAPTED(50.0),
        HEAT_CHAMPION(90.0),
    }

    /** The level [score] is at, decided on the tenth the field prints. */
    fun level(score: Double): Level {
        val shown = Math.round(score * 10) / 10.0
        return Level.entries.last { shown >= it.floor }
    }

    // CORE draws the four levels as a light-to-dark blue ramp behind its charts. Those tints are
    // chart backgrounds, and the lightest of them is all but invisible as a number on the Karoo's
    // white card, so the same hue is used one step deeper: CORE's second, third and fourth tints
    // for the first three levels, and a deeper blue again for Heat Champion.
    private val levelPalette = intArrayOf(
        0xFFA2DBED.toInt(), // Thermal Rookie
        0xFF73C9E4.toInt(), // Heat Accustomed
        0xFF45B8DB.toInt(), // Heat Adapted
        0xFF1C8DB5.toInt(), // Heat Champion
    )

    fun color(score: Double): Int? =
        if (score.isFinite()) levelPalette[level(score).ordinal] else null
}
