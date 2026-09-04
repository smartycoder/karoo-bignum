package io.smartycoder.bignum.render

import io.hammerhead.karooext.models.UserProfile.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ZoneBar.litSegments] is the whole pill: everything else about it is drawing. It is also the
 * one place a wrong answer is invisible rather than obviously broken -- a pill stuck one zone low
 * all ride reads as a design choice, not a bug -- so every boundary it has is pinned here.
 */
class ZoneBarTest {

    // Seven zones, as Karoo builds them for a 250W FTP. Uneven widths on purpose: the pill counts
    // zones, so the widths must not matter.
    private val powerZones = listOf(
        Zone(min = 0, max = 137),
        Zone(min = 138, max = 187),
        Zone(min = 188, max = 225),
        Zone(min = 226, max = 262),
        Zone(min = 263, max = 300),
        Zone(min = 301, max = 375),
        Zone(min = 376, max = 500),
    )

    // Five zones whose floor is well above zero, the heart rate shape. A reading below Z1 is a
    // reading, not a zone.
    private val hrZones = listOf(
        Zone(min = 100, max = 129),
        Zone(min = 130, max = 149),
        Zone(min = 150, max = 164),
        Zone(min = 165, max = 177),
        Zone(min = 178, max = 200),
    )

    @Test
    fun `no zones lights nothing`() {
        assertEquals(0, ZoneBar.litSegments(200.0, emptyList()))
    }

    @Test
    fun `a value in the first zone lights one square`() {
        assertEquals(1, ZoneBar.litSegments(100.0, powerZones))
    }

    @Test
    fun `a value in the last zone lights every square`() {
        assertEquals(7, ZoneBar.litSegments(400.0, powerZones))
    }

    @Test
    fun `each zone lights its own index plus one`() {
        assertEquals(2, ZoneBar.litSegments(150.0, powerZones))
        assertEquals(3, ZoneBar.litSegments(200.0, powerZones))
        assertEquals(4, ZoneBar.litSegments(240.0, powerZones))
        assertEquals(5, ZoneBar.litSegments(280.0, powerZones))
        assertEquals(6, ZoneBar.litSegments(350.0, powerZones))
    }

    @Test
    fun `a zone boundary belongs to the zone it opens`() {
        // The lookup is `value >= min`, so 138 is the first watt of Z2 rather than the last of Z1.
        assertEquals(1, ZoneBar.litSegments(137.0, powerZones))
        assertEquals(2, ZoneBar.litSegments(138.0, powerZones))
    }

    @Test
    fun `a gap between two zones falls to the lower one`() {
        // Nothing promises the maxes and mins meet. A reading in the crack belongs to the zone it
        // has already passed the floor of, not to the one it has not reached.
        val gapped = listOf(Zone(min = 0, max = 100), Zone(min = 120, max = 200))
        assertEquals(1, ZoneBar.litSegments(110.0, gapped))
    }

    @Test
    fun `above the last zone stays at the last zone`() {
        // Nothing here promises the top zone's max is a real number, so a value past it must
        // clamp rather than run off the end of the squares.
        assertEquals(7, ZoneBar.litSegments(9999.0, powerZones))
    }

    @Test
    fun `below the first zone's floor lights nothing`() {
        // 85 bpm on a profile whose Z1 starts at 100. zoneIndex returns -1 and the pill is empty,
        // which is the honest answer: it is a reading, not a zone.
        assertEquals(0, ZoneBar.litSegments(85.0, hrZones))
    }

    @Test
    fun `zero lights nothing even where a zone would claim it`() {
        // THE CASE THAT CHANGED WITH THE PILL. Power zones start at 0, so zoneIndex(0) is 0 and a
        // literal reading of it would light Z1 for the whole of every coast and every descent.
        // The bar this replaces drew that case empty, and it stays empty.
        assertEquals(0, ZoneBar.litSegments(0.0, powerZones))
        assertEquals(0, ZoneBar.litSegments(-5.0, powerZones))
    }

    @Test
    fun `NaN lights nothing`() {
        // Every comparison against NaN is false, so it fails the `value <= 0` guard and then
        // finds no zone. Pinned because a NaN that survived would be drawn as a square count.
        assertEquals(0, ZoneBar.litSegments(Double.NaN, powerZones))
    }

    @Test
    fun `infinities land at the ends`() {
        assertEquals(0, ZoneBar.litSegments(Double.NEGATIVE_INFINITY, powerZones))
        assertEquals(7, ZoneBar.litSegments(Double.POSITIVE_INFINITY, powerZones))
    }

    @Test
    fun `the count never exceeds the zones on offer`() {
        // The pill draws exactly `segments` squares and lights `lit` of them; a lit past the end
        // would be a silent no-op today and an array walk off the end after any refactor.
        for (zones in listOf(powerZones, hrZones)) {
            for (v in listOf(0.0, 1.0, 137.0, 250.0, 9999.0)) {
                assert(ZoneBar.litSegments(v, zones) in 0..zones.size)
            }
        }
    }
}
