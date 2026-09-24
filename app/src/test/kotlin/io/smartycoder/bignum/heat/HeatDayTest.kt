package io.smartycoder.bignum.heat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The one piece of state [HeatTracker] keeps between rides, and how it carries across days. */
class HeatDayTest {

    private val monday = 20_000L

    @Test fun `a day with a load closes into the next day's starting score`() {
        val trained = HeatDay(monday, 0.0, 6.0, HeatAdaptation.Day(10.0, 0))
        val tuesday = trained.rolledTo(monday + 1)
        assertEquals(monday + 1, tuesday.epochDay)
        assertEquals(0.0, tuesday.load, 0.0)
        assertEquals(HeatAdaptation.Day(16.0, 0), tuesday.before)
    }

    @Test fun `days nobody rode are closed as days off`() {
        val trained = HeatDay(monday, 0.0, 6.0, HeatAdaptation.Day(40.0, 0))
        val thursday = trained.rolledTo(monday + 3)
        val closedMonday = HeatAdaptation.step(HeatAdaptation.Day(40.0, 0), 6.0)
        // Tuesday free, Wednesday -2.5.
        assertEquals(closedMonday.score - 2.5, thursday.before.score, 1e-9)
        assertEquals(2, thursday.before.restStreak)
    }

    @Test fun `a long absence decays to zero without walking every day`() {
        val trained = HeatDay(monday, 0.0, 10.0, HeatAdaptation.Day(95.0, 0))
        assertEquals(0.0, trained.rolledTo(monday + 10_000).before.score, 0.0)
    }

    @Test fun `the same day, or a clock set back, changes nothing`() {
        val day = HeatDay(monday, 0.5, 3.0, HeatAdaptation.Day(30.0, 0))
        assertSame(day, day.rolledTo(monday))
        assertSame(day, day.rolledTo(monday - 1))
    }

    @Test fun `today's score moves with today's load`() {
        val day = HeatDay(monday, 0.0, 0.0, HeatAdaptation.Day(20.0, 0))
        assertEquals(20.0, day.adaptation, 0.0)
        assertEquals(29.6, day.copy(highLoad = 5.0).adaptation, 0.05)
    }

    @Test fun `a second day off shows its decay from the morning`() {
        val day = HeatDay(monday, 0.0, 0.0, HeatAdaptation.Day(50.0, restStreak = 1))
        assertEquals(47.5, day.adaptation, 0.0)
    }
}
