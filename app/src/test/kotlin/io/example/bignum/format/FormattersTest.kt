package io.example.bignum.format

import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import io.hammerhead.karooext.models.UserProfile.PreferredUnit.UnitType
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    private val metric = PreferredUnit(
        distance = UnitType.METRIC,
        elevation = UnitType.METRIC,
        temperature = UnitType.METRIC,
        weight = UnitType.METRIC,
    )
    private val imperial = PreferredUnit(
        distance = UnitType.IMPERIAL,
        elevation = UnitType.IMPERIAL,
        temperature = UnitType.IMPERIAL,
        weight = UnitType.IMPERIAL,
    )

    // ── speed (input m/s) ──────────────────────────────────────────────────
    @Test fun `speed metric — 10 mps is 36 km per h`() {
        assertEquals("36.0" to "km/h", Formatters.speed(10.0, metric))
    }

    @Test fun `speed imperial — 10 mps is about 22_4 mph`() {
        val (v, u) = Formatters.speed(10.0, imperial)
        assertEquals("mph", u)
        assertEquals(22.4, v.toDouble(), 0.1)
    }

    @Test fun `speed null profile defaults to metric`() {
        assertEquals("36.0" to "km/h", Formatters.speed(10.0, null))
    }

    // ── bpm/watts/rpm/percent are unit-agnostic ────────────────────────────
    @Test fun `bpm formats integer with bpm unit`() {
        assertEquals("165" to "bpm", Formatters.bpm(165.4, null))
    }

    @Test fun `watts formats integer with W unit`() {
        assertEquals("237" to "W", Formatters.watts(237.6, metric))
    }

    @Test fun `rpm formats integer with rpm unit`() {
        assertEquals("92" to "rpm", Formatters.rpm(92.0, null))
    }

    @Test fun `percent formats one decimal with percent unit`() {
        assertEquals("4.2" to "%", Formatters.percent(4.234, null))
    }

    // ── distance (input meters) ────────────────────────────────────────────
    @Test fun `distance metric — meters to km`() {
        assertEquals("12.5" to "km", Formatters.distance(12500.0, metric))
    }

    @Test fun `distance imperial — meters to miles`() {
        val (v, u) = Formatters.distance(1609.345, imperial)
        assertEquals("mi", u)
        assertEquals(1.0, v.toDouble(), 0.01)
    }

    // ── elevation (input meters) ───────────────────────────────────────────
    @Test fun `elevation metric — integer meters`() {
        assertEquals("742" to "m", Formatters.elevation(742.7, metric))
    }

    @Test fun `elevation imperial — feet`() {
        val (v, u) = Formatters.elevation(100.0, imperial)
        assertEquals("ft", u)
        assertEquals(328, v.toInt())
    }

    // ── temperature (input celsius) ────────────────────────────────────────
    @Test fun `temperature metric — celsius`() {
        assertEquals("23" to "°C", Formatters.temperature(23.4, metric))
    }

    @Test fun `temperature imperial — fahrenheit`() {
        assertEquals("73" to "°F", Formatters.temperature(23.0, imperial))
    }

    // ── time (input MILLISECONDS) ──────────────────────────────────────────
    @Test fun `time below 1 hour — m colon ss format`() {
        assertEquals("5:30" to "", Formatters.time(330_000.0, null))
    }

    @Test fun `time at exactly 1 hour — h colon mm colon ss`() {
        assertEquals("1:00:00" to "", Formatters.time(3_600_000.0, null))
    }

    @Test fun `time at 59999 ms shows as 0 colon 59`() {
        assertEquals("0:59" to "", Formatters.time(59_999.0, null))
    }

    @Test fun `time at 60000 ms shows as 1 colon 00`() {
        assertEquals("1:00" to "", Formatters.time(60_000.0, null))
    }

    @Test fun `time at 12 hour 34 min 56 sec`() {
        assertEquals("12:34:56" to "", Formatters.time((12*3600 + 34*60 + 56) * 1000.0, null))
    }
}
