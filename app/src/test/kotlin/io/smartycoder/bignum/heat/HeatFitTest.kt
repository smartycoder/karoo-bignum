package io.smartycoder.bignum.heat

import io.hammerhead.karooext.models.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatFitTest {

    private fun state(hsi: Double?, fromSensor: Boolean = false, load: Double = 0.0, adaptation: Double = 0.0) =
        HeatState(hsi = hsi, load = load, adaptation = adaptation, hsiFromSensor = fromSensor)

    private fun List<FieldValue>.valueOf(field: io.hammerhead.karooext.models.DeveloperField) =
        single { it.developerField == field }.value

    @Test fun `field numbers start at 0 and run without gaps, as karoo-ext requires`() {
        assertEquals(HeatFit.ALL.indices.map { it.toShort() }, HeatFit.ALL.map { it.fieldDefinitionNumber })
    }

    @Test fun `the sensor's index goes under CORE's own field name`() {
        assertEquals("heat_strain_index", HeatFit.HEAT_STRAIN_INDEX.fieldName)
        assertEquals("a.u.", HeatFit.HEAT_STRAIN_INDEX.units)
        val record = HeatFit.record(state(4.2, fromSensor = true))
        assertEquals(4.2, record.valueOf(HeatFit.HEAT_STRAIN_INDEX), 0.0)
        assertEquals(3.0, record.valueOf(HeatFit.HEAT_ZONE), 0.0)
        assertTrue(record.none { it.developerField == HeatFit.ESTIMATED_HEAT_STRAIN_INDEX })
    }

    @Test fun `an estimate never goes under CORE's name`() {
        val record = HeatFit.record(state(4.2, fromSensor = false))
        assertEquals(4.2, record.valueOf(HeatFit.ESTIMATED_HEAT_STRAIN_INDEX), 0.0)
        assertTrue(record.none { it.developerField == HeatFit.HEAT_STRAIN_INDEX })
    }

    @Test fun `no index, nothing written`() {
        assertTrue(HeatFit.record(state(null)).isEmpty())
    }

    @Test fun `a ride without a CORE gets no heat summary`() {
        val stats = HeatRideStats()
        repeat(60) { stats.add(state(null, load = 3.0, adaptation = 40.0), 1.0) }
        assertFalse(stats.hasHeat)
        assertTrue(HeatFit.session(stats, state(null, adaptation = 40.0)).isEmpty())
    }

    @Test fun `the summary averages the index over time and counts time in each zone`() {
        val stats = HeatRideStats()
        repeat(60) { stats.add(state(0.5), 1.0) } // zone 1
        repeat(120) { stats.add(state(4.0), 1.0) } // zone 3
        val session = HeatFit.session(stats, state(4.0, adaptation = 55.0))
        assertEquals((0.5 * 60 + 4.0 * 120) / 180, session.valueOf(HeatFit.AVG_HEAT_STRAIN_INDEX), 1e-9)
        assertEquals(4.0, session.valueOf(HeatFit.MAX_HEAT_STRAIN_INDEX), 0.0)
        assertEquals(60.0, session.valueOf(HeatFit.TIME_IN_HEAT_ZONE[0]), 0.0)
        assertEquals(0.0, session.valueOf(HeatFit.TIME_IN_HEAT_ZONE[1]), 0.0)
        assertEquals(120.0, session.valueOf(HeatFit.TIME_IN_HEAT_ZONE[2]), 0.0)
        assertEquals(55.0, session.valueOf(HeatFit.HEAT_ADAPTATION_SCORE), 0.0)
    }

    @Test fun `the ride's load is what it added to the day, not the day's total`() {
        val stats = HeatRideStats()
        // A morning session already left 3.0 on the day.
        stats.add(state(4.0, load = 3.0), 1.0)
        stats.add(state(4.0, load = 5.5), 1.0)
        assertEquals(2.5, stats.load, 1e-9)
    }

    @Test fun `a ride past midnight keeps counting from the new day's total`() {
        val stats = HeatRideStats()
        stats.add(state(4.0, load = 6.0), 1.0)
        stats.add(state(4.0, load = 7.0), 1.0)
        stats.add(state(4.0, load = 0.4), 1.0) // midnight: the day started over
        stats.add(state(4.0, load = 1.0), 1.0)
        assertEquals(1.0 + 0.4 + 0.6, stats.load, 1e-9)
    }
}
