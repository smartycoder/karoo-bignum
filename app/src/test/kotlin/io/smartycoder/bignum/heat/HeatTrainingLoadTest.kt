package io.smartycoder.bignum.heat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The load model against CORE's own table: the Heat Training Load a session reaches when a given
 * HSI is held for a given time, after climbing to it over 20-40 minutes.
 *
 * Driven through [HeatDay.accumulate], a minute at a time, so the low-strain cap and the day cap
 * are exercised along with the rates.
 */
class HeatTrainingLoadTest {

    /** A session: a 30-minute climb from 0 to [hsi], then [minutes] held there. */
    private fun session(hsi: Double, minutes: Int): Double {
        var day = HeatDay.fresh(0)
        val ramp = 30
        for (m in 0 until ramp + minutes) {
            val now = if (m < ramp) hsi * (m + 0.5) / ramp else hsi
            day = day.accumulate(now, 1 / 60.0)
        }
        return day.load
    }

    // CORE's table: rows are minutes held at the final HSI, columns HSI 1.5 to 6.5.
    private val minutes = listOf(30, 45, 60, 90, 120, 180)
    private val table = mapOf(
        1.5 to listOf(0.6, 0.9, 1.1, 1.3, 1.4, 1.4),
        2.5 to listOf(1.4, 2.0, 2.6, 3.8, 4.9, 7.3),
        3.5 to listOf(4.0, 5.6, 7.3, 10.0, 10.0, 10.0),
        4.5 to listOf(5.1, 7.0, 8.8, 10.0, 10.0, 10.0),
        5.5 to listOf(5.9, 7.9, 9.8, 10.0, 10.0, 10.0),
        6.5 to listOf(6.3, 8.3, 10.0, 10.0, 10.0, 10.0),
    )

    @Test fun `every cell of CORE's table is reproduced to within 0_15`() {
        for ((hsi, row) in table) {
            for ((held, expected) in minutes.zip(row)) {
                assertEquals("HSI $hsi held $held min", expected, session(hsi, held), 0.15)
            }
        }
    }

    @Test fun `CORE's classic session - an hour at HSI 4_5 after a 30 minute warm-up - is about 8_8`() {
        assertEquals(8.8, session(4.5, 60), 0.15)
    }

    @Test fun `zone 1 earns nothing`() {
        assertEquals(0.0, HeatTrainingLoad.ratePerHour(0.9), 0.0)
        assertEquals(0.0, session(0.9, 600), 0.0)
    }

    @Test fun `under HSI 2 the load stops at the low-strain cap however long it runs`() {
        assertEquals(HeatTrainingLoad.LOW_STRAIN_CAP, session(1.9, 600), 1e-9)
    }

    @Test fun `a day never goes past 10`() {
        assertEquals(10.0, session(6.5, 600), 0.0)
    }

    @Test fun `a nonsense index or interval earns nothing`() {
        val day = HeatDay.fresh(0)
        assertEquals(day, day.accumulate(Double.NaN, 1.0))
        assertEquals(day, day.accumulate(4.0, -1.0))
        assertEquals(day, day.accumulate(4.0, Double.NaN))
    }
}
