package io.smartycoder.bignum.format

import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import io.hammerhead.karooext.models.UserProfile.PreferredUnit.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    @Test fun `time below 1 hour still carries its hour digit`() {
        assertEquals("0:05:30" to "", Formatters.time(330_000.0, null))
    }

    @Test fun `time at exactly 1 hour — h colon mm colon ss`() {
        assertEquals("1:00:00" to "", Formatters.time(3_600_000.0, null))
    }

    @Test fun `time at 59999 ms shows as 0 colon 59`() {
        assertEquals("0:00:59" to "", Formatters.time(59_999.0, null))
    }

    @Test fun `time at 60000 ms shows as 1 colon 00`() {
        assertEquals("0:01:00" to "", Formatters.time(60_000.0, null))
    }

    @Test fun `a clock that has not started reads zero rather than dashes`() {
        assertEquals("0:00:00" to "", Formatters.time(0.0, null))
    }

    @Test fun `time at 12 hour 34 min 56 sec`() {
        assertEquals("12:34:56" to "", Formatters.time((12*3600 + 34*60 + 56) * 1000.0, null))
    }

    // Values that outgrow the field's width budget drop their decimal instead of being
    // rendered smaller than every other field.
    @Test
    fun `speed drops the decimal at and above 100`() {
        assertEquals("99.9", Formatters.speed(99.9 / 3.6, null).first)
        assertEquals("100", Formatters.speed(100.0 / 3.6, null).first)
        // 99.96 is under 100 but "%.1f" would print "100.0" -- one glyph too wide, and
        // truncating instead of rounding would show "99" as the rider speeds up.
        assertEquals("100", Formatters.speed(99.96 / 3.6, null).first)
        assertEquals("105", Formatters.speed(105.4 / 3.6, null).first)
    }

    @Test
    fun `distance drops the decimal at and above 100`() {
        assertEquals("99.9", Formatters.distance(99_900.0, null).first)
        assertEquals("120", Formatters.distance(120_000.0, null).first)
    }

    @Test
    fun `grade drops the decimal below minus ten`() {
        assertEquals("-9.5", Formatters.percent(-9.5, null).first)
        assertEquals("-12", Formatters.percent(-12.5, null).first)
        assertEquals("8.4", Formatters.percent(8.4, null).first)
    }

    // W/kg 3s and 5s have no karoo-ext type of their own, so they divide smoothed watts by the
    // rider weight. What has to hold: the arithmetic, and that a missing weight yields nothing to
    // show rather than raw watts dressed up as W/kg.

    @Test
    fun `watts per kilogram divides by rider weight`() {
        assertEquals(3.5, Formatters.perKilogram(245.0, 70f)!!, 0.0001)
        assertEquals(4.0, Formatters.perKilogram(240.0, 60f)!!, 0.0001)
    }

    @Test
    fun `without a usable weight there is nothing to show`() {
        assertNull(Formatters.perKilogram(245.0, null))
        assertNull(Formatters.perKilogram(245.0, 0f))
        assertNull(Formatters.perKilogram(245.0, -70f))
    }

    @Test
    fun `the derived value renders through the same formatter as the plain field`() {
        val perKg = Formatters.perKilogram(241.0, 70f)!!
        assertEquals("3.4", Formatters.wattsPerKg(perKg, null).first)
    }

    @Test
    fun `a non-finite weight yields nothing rather than NaN on screen`() {
        // NaN <= 0f is false, so a bare range check would let this through and render "NaN".
        assertNull(Formatters.perKilogram(245.0, Float.NaN))
        assertNull(Formatters.perKilogram(245.0, Float.POSITIVE_INFINITY))
    }

    @Test
    fun `zero watts is a real reading, not a missing one`() {
        assertEquals(0.0, Formatters.perKilogram(0.0, 70f)!!, 0.0001)
    }
}
