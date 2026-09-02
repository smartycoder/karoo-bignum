package io.smartycoder.bignum.fields

import io.smartycoder.bignum.ZoneColorMode
import io.smartycoder.bignum.format.Formatters
import io.smartycoder.bignum.render.ZoneKind
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.UserProfile.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises [BaseNumericField.barFrame], which decides whether the HUD's zone bar exists at all
 * for a given reading. That decision is worth pinning because getting it wrong is not a crash:
 * a bar that hides itself also gives the two halves their labels back and re-measures both
 * numbers, so the whole tile visibly reflows. It shipped that way once, on every reading of
 * zero -- which for power is every stop light, coast and descent.
 *
 * Pure over its three arguments, so it runs here rather than needing a device; [barFlow] is the
 * thin wrapper that feeds it, and needs a live Context.
 */
class BarFrameTest {

    /**
     * Minimal concrete field, the same shape ComputeTest uses. karoo is null: barFrame() never
     * touches it, and this module has no Robolectric to build a real KarooSystemService.
     */
    private class TestField(override val zoneKind: ZoneKind?) :
        BaseNumericField("test", "test_type", null) {
        override val upstreamTypeId = "test.upstream"
        override val label = "TEST"
        override val iconRes = 0
        override val format = Formatters.watts
    }

    private val powerZones = listOf(
        Zone(min = 0, max = 137),
        Zone(min = 138, max = 187),
        Zone(min = 188, max = 225),
        Zone(min = 226, max = 262),
        Zone(min = 263, max = 300),
        Zone(min = 301, max = 375),
        Zone(min = 376, max = 500),
    )

    private fun profile(power: List<Zone> = powerZones, hr: List<Zone> = emptyList()) = UserProfile(
        weight = 70f,
        preferredUnit = UserProfile.PreferredUnit(
            distance = UserProfile.PreferredUnit.UnitType.METRIC,
            elevation = UserProfile.PreferredUnit.UnitType.METRIC,
            temperature = UserProfile.PreferredUnit.UnitType.METRIC,
            weight = UserProfile.PreferredUnit.UnitType.METRIC,
        ),
        maxHr = 200,
        restingHr = 50,
        heartRateZones = hr,
        ftp = 250,
        powerZones = power,
    )

    private val field = TestField(ZoneKind.POWER)

    @Test
    fun `zero watts still draws a bar, empty rather than absent`() {
        // THE REGRESSION THIS FILE EXISTS FOR. ZoneColors.color returns null for any non-positive
        // value, and the first version keyed the bar's existence on that colour -- so coasting
        // hid the bar, brought both labels back and resized both numbers, several times a minute.
        val frame = field.barFrame(0.0, profile(), ZoneColorMode.TEXT)
        assertNotNull(frame)
        assertEquals(0f, frame!!.fraction, 0.0001f)
        assertEquals("0", frame.text)
    }

    @Test
    fun `a value under the first zone's floor draws an empty bar too`() {
        // The heart rate shape of the same bug: a profile whose Z1 starts at 100 has no colour
        // for 85, but 85 bpm is a reading, not missing data.
        val hrField = TestField(ZoneKind.HR)
        val hrZones = listOf(Zone(min = 100, max = 139), Zone(min = 140, max = 200))
        val frame = hrField.barFrame(85.0, profile(hr = hrZones), ZoneColorMode.TEXT)
        assertNotNull(frame)
        assertEquals(0f, frame!!.fraction, 0.0001f)
    }

    @Test
    fun `no reading means no bar`() {
        assertNull(field.barFrame(null, profile(), ZoneColorMode.TEXT))
    }

    @Test
    fun `a profile with no zones means no bar`() {
        // The case the bar genuinely cannot draw: without zones an empty track would never move
        // for the whole ride, which promises information that is not coming.
        assertNull(field.barFrame(240.0, profile(power = emptyList()), ZoneColorMode.TEXT))
        assertNull(field.barFrame(240.0, null, ZoneColorMode.TEXT))
    }

    @Test
    fun `zone colours off means no bar`() {
        // A rider who turned zone colours off is not asking for the one thing on the tile that is
        // nothing but a zone colour.
        assertNull(field.barFrame(240.0, profile(), ZoneColorMode.OFF))
    }

    @Test
    fun `a field with no zone of its own can never drive the bar`() {
        assertNull(TestField(null).barFrame(240.0, profile(), ZoneColorMode.TEXT))
    }

    @Test
    fun `a live reading fills and colours the bar`() {
        val frame = field.barFrame(263.0, profile(), ZoneColorMode.FILL)
        assertNotNull(frame)
        // Bottom of zone 5 of 7.
        assertEquals(4f / 7f, frame!!.fraction, 0.0001f)
        assertEquals("263", frame.text)
    }
}
