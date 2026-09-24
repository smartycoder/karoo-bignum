package io.smartycoder.bignum.heat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The adaptation model against the scenarios CORE charts in "Boosting Your Heat Adaptation Score".
 * Where CORE prints the final score on a chart, the test holds the model to it.
 */
class HeatAdaptationTest {

    /** The score at the end of each day, from a score of [start] with these daily loads. */
    private fun run(loads: List<Double>, start: Double = 0.0): List<Double> {
        var day = HeatAdaptation.Day(start, 0)
        return loads.map { load -> HeatAdaptation.step(day, load).also { day = it }.score }
    }

    /** [weeks] of days, with [load] on each of the 1-based [sessions] and nothing on the rest. */
    private fun schedule(weeks: Int, load: Double, sessions: List<Int>) =
        List(weeks * 7) { i -> if ((i + 1) in sessions) load else 0.0 }

    // Two or three sessions a week, the pattern of CORE's six-week charts.
    private val sixWeeks = listOf(2, 5, 8, 10, 12, 16, 19, 22, 24, 26, 30, 33, 36, 38, 40)

    /** Six days on, one off, twice: CORE's two-week charts. */
    private fun twoWeeks(load: Double) = List(6) { load } + 0.0 + List(6) { load } + 0.0

    @Test fun `two weeks of daily loads of 5 end at 72_1, as CORE prints`() {
        assertEquals(72.1, run(twoWeeks(5.0)).last(), 0.1)
    }

    @Test fun `two weeks of daily loads of 10 make a Heat Champion`() {
        val score = run(twoWeeks(10.0)).last()
        assertEquals(97.4, score, 0.5)
        assertEquals(HeatAdaptation.Level.HEAT_CHAMPION, HeatAdaptation.level(score))
    }

    @Test fun `six weeks of loads of 5 two or three times a week end at 63_6, as CORE prints`() {
        assertEquals(63.6, run(schedule(6, 5.0, sixWeeks)).last(), 0.1)
    }

    @Test fun `six weeks of loads of 3 two or three times a week end at 17_5, as CORE prints`() {
        assertEquals(17.5, run(schedule(6, 3.0, sixWeeks)).last(), 0.1)
    }

    @Test fun `a load of 2 or less does not count`() {
        assertEquals(listOf(0.0, 0.0), run(listOf(2.0, 1.0)))
    }

    @Test fun `below 20 a day adds exactly its load`() {
        assertEquals(listOf(3.0, 8.0, 15.0), run(listOf(3.0, 5.0, 7.0)))
    }

    @Test fun `from 20 a day adds nearly twice its load, less the nearer the score is to 100`() {
        assertEquals(29.6, run(listOf(5.0), start = 20.0).single(), 0.05)
        assertEquals(95.65, run(listOf(3.5), start = 95.0).single(), 0.01)
    }

    @Test fun `the first day off is free and each one after it costs 2_5`() {
        assertEquals(listOf(50.0, 47.5, 45.0), run(listOf(0.0, 0.0, 0.0), start = 50.0))
    }

    @Test fun `a training day resets the run of days off`() {
        val days = run(listOf(0.0, 0.0, 6.0, 0.0), start = 50.0)
        assertEquals(days[2], days[3], 0.0)
    }

    @Test fun `the score stays between 0 and 100`() {
        assertEquals(0.0, run(List(5) { 0.0 }, start = 1.0).last(), 0.0)
        assertEquals(100.0, run(List(60) { 10.0 }).last(), 0.0)
    }

    @Test fun `levels follow CORE's bands on the printed tenth`() {
        assertEquals(HeatAdaptation.Level.THERMAL_ROOKIE, HeatAdaptation.level(24.94))
        assertEquals(HeatAdaptation.Level.HEAT_ACCUSTOMED, HeatAdaptation.level(24.96))
        assertEquals(HeatAdaptation.Level.HEAT_ACCUSTOMED, HeatAdaptation.level(49.9))
        assertEquals(HeatAdaptation.Level.HEAT_ADAPTED, HeatAdaptation.level(50.0))
        assertEquals(HeatAdaptation.Level.HEAT_ADAPTED, HeatAdaptation.level(89.9))
        assertEquals(HeatAdaptation.Level.HEAT_CHAMPION, HeatAdaptation.level(90.0))
    }

    @Test fun `each level has its own colour`() {
        val colors = listOf(10.0, 30.0, 70.0, 95.0).map { HeatAdaptation.color(it) }
        assertEquals(4, colors.toSet().size)
    }
}
