package io.smartycoder.bignum.render

import io.hammerhead.karooext.models.UserProfile.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ZoneBar.fraction] is the whole bar: everything else about it is drawing. It is also the one
 * place a wrong answer is invisible rather than obviously broken -- a bar that sits at 14% all
 * ride looks like a design choice, not a bug -- so every boundary it has is pinned here.
 */
class ZoneBarTest {

    // Seven zones, as Karoo builds them for a 250W FTP. Uneven widths on purpose: the point of
    // giving each zone an equal share of the bar is that the widths do not matter.
    private val powerZones = listOf(
        Zone(min = 0, max = 137),
        Zone(min = 138, max = 187),
        Zone(min = 188, max = 225),
        Zone(min = 226, max = 262),
        Zone(min = 263, max = 300),
        Zone(min = 301, max = 375),
        Zone(min = 376, max = 500),
    )

    private fun assertFraction(expected: Double, actual: Float) =
        assertEquals(expected, actual.toDouble(), 0.001)

    @Test
    fun `no zones leaves the bar empty`() {
        assertFraction(0.0, ZoneBar.fraction(240.0, emptyList()))
    }

    @Test
    fun `a value below the first zone leaves the bar empty`() {
        assertFraction(0.0, ZoneBar.fraction(-5.0, powerZones))
    }

    @Test
    fun `the bottom of the first zone is an empty bar`() {
        assertFraction(0.0, ZoneBar.fraction(0.0, powerZones))
    }

    @Test
    fun `the top of the last zone is a full bar`() {
        assertFraction(1.0, ZoneBar.fraction(500.0, powerZones))
    }

    @Test
    fun `past the top of the last zone stays full rather than overflowing`() {
        assertFraction(1.0, ZoneBar.fraction(900.0, powerZones))
    }

    @Test
    fun `each zone gets an equal share whatever its width`() {
        // Bottom of zone 4 (index 3) is exactly three sevenths along, even though zones 1-3 are
        // far wider than zones 4-5. That equal share is the reason this does not need the
        // scale's top value to be trustworthy.
        assertFraction(3.0 / 7.0, ZoneBar.fraction(226.0, powerZones))
        assertFraction(4.0 / 7.0, ZoneBar.fraction(263.0, powerZones))
    }

    @Test
    fun `inside a zone the bar moves proportionally through that zone's share`() {
        // Zone 3 runs 188..225; 206.5 is its midpoint, so the bar is half way through the third
        // seventh of its width.
        assertFraction((2.0 + 0.5) / 7.0, ZoneBar.fraction(206.5, powerZones))
    }

    @Test
    fun `a gap between zones counts as the top of the lower one`() {
        // Karoo's zones are contiguous by construction, but nothing here guarantees it: 262 is
        // zone 4's max and 263 is zone 5's min, so a hypothetical 262.5 falls in neither. It
        // must clamp to the top of the zone it is past, not run over into the next one's share.
        assertFraction(4.0 / 7.0, ZoneBar.fraction(262.5, powerZones))
    }

    @Test
    fun `a zero-width first zone fills only its own share, not the whole bar`() {
        // The guard returns within = 1.0 for a degenerate zone, which is right for the LAST one
        // and must not be mistaken for a full bar when it is the first: the value is at the top
        // of zone 1 of 2, so half.
        val zones = listOf(Zone(min = 100, max = 100), Zone(min = 101, max = 180))
        assertFraction(0.5, ZoneBar.fraction(100.0, zones))
    }

    @Test
    fun `a zone with no width at all is treated as full rather than dividing by zero`() {
        // The shape a sentinel or an unset top zone can arrive in. Without the guard this is
        // (v - min) / 0 -- Infinity or NaN -- and NaN would survive every coerce below it.
        val zones = listOf(Zone(min = 0, max = 100), Zone(min = 101, max = 101))
        assertFraction(1.0, ZoneBar.fraction(101.0, zones))
    }

    @Test
    fun `a nonsense value leaves the bar empty instead of drawing a NaN width`() {
        assertFraction(0.0, ZoneBar.fraction(Double.NaN, powerZones))
        assertFraction(0.0, ZoneBar.fraction(Double.NEGATIVE_INFINITY, powerZones))
        assertFraction(1.0, ZoneBar.fraction(Double.POSITIVE_INFINITY, powerZones))
    }

    @Test
    fun `five zones divide the bar in fifths, not sevenths`() {
        // The HR scale. Nothing in fraction() may assume the seven-zone power scale.
        val hrZones = listOf(
            Zone(min = 100, max = 119),
            Zone(min = 120, max = 139),
            Zone(min = 140, max = 159),
            Zone(min = 160, max = 179),
            Zone(min = 180, max = 200),
        )
        assertFraction(2.0 / 5.0, ZoneBar.fraction(140.0, hrZones))
        assertFraction(1.0, ZoneBar.fraction(200.0, hrZones))
    }
}
