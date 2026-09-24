package io.smartycoder.bignum.heat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HeatStrain] against what CORE publishes: the zone boundaries on its Heat Zones chart and the
 * one point its text gives, HSI 10 at a 40 °C core on 37 °C skin.
 */
class HeatStrainTest {

    private fun hsi(core: Double, skin: Double) = HeatStrain.index(core, skin)!!

    @Test fun `the chart's boundaries read as HSI 1, 3 and 7`() {
        // One row from the cool end, one from the middle, one from the hot end.
        assertEquals(1.0, hsi(38.80, 28.0), 0.01)
        assertEquals(3.0, hsi(39.62, 28.0), 0.01)
        assertEquals(7.0, hsi(40.00, 28.0), 0.01)
        assertEquals(1.0, hsi(38.10, 33.0), 0.01)
        assertEquals(3.0, hsi(38.77, 33.0), 0.01)
        assertEquals(7.0, hsi(39.82, 33.0), 0.01)
        assertEquals(1.0, hsi(36.88, 38.0), 0.01)
        assertEquals(3.0, hsi(37.49, 38.0), 0.01)
        assertEquals(7.0, hsi(38.98, 38.0), 0.01)
    }

    @Test fun `a 40 degree core on 37 degree skin is HSI 10`() {
        assertEquals(10.0, hsi(40.0, 37.0), 0.01)
    }

    @Test fun `between two chart rows the boundary is interpolated`() {
        // Halfway between the 33.0 row (38.10) and the 33.5 row (38.01).
        assertEquals(1.0, hsi(38.055, 33.25), 0.01)
    }

    @Test fun `hotter skin means more strain at the same core temperature`() {
        assertTrue(hsi(38.5, 36.0) > hsi(38.5, 32.0))
    }

    @Test fun `a resting body has no heat strain`() {
        assertEquals(0.0, hsi(37.0, 32.0), 0.0)
    }

    @Test fun `the index is clamped to 10`() {
        assertEquals(10.0, hsi(42.0, 38.0), 0.0)
    }

    @Test fun `skin beyond the chart is read on its nearest edge`() {
        assertEquals(hsi(38.5, 28.0), hsi(38.5, 22.0), 0.0)
        assertEquals(hsi(38.5, 38.0), hsi(38.5, 40.0), 0.0)
    }

    @Test fun `no index without both temperatures, or from a sensor that is not on a body`() {
        assertNull(HeatStrain.index(null, 34.0))
        assertNull(HeatStrain.index(38.0, null))
        assertNull(HeatStrain.index(Double.NaN, 34.0))
        assertNull(HeatStrain.index(22.0, 21.0))
        assertNull(HeatStrain.index(0.0, 0.0))
    }

    @Test fun `zones follow CORE's bands on the printed tenth`() {
        assertEquals(1, HeatStrain.zone(0.0))
        assertEquals(1, HeatStrain.zone(0.94))
        // 0.96 prints as "1.0", so it is zone 2 along with the number.
        assertEquals(2, HeatStrain.zone(0.96))
        assertEquals(2, HeatStrain.zone(2.9))
        assertEquals(3, HeatStrain.zone(3.0))
        assertEquals(3, HeatStrain.zone(6.9))
        assertEquals(4, HeatStrain.zone(7.0))
        assertEquals(4, HeatStrain.zone(10.0))
    }

    @Test fun `each zone has CORE's colour and nothing else has one`() {
        assertEquals(0xFF37CA94.toInt(), HeatStrain.colorOfZone(1))
        assertEquals(0xFFFFC655.toInt(), HeatStrain.colorOfZone(2))
        assertEquals(0xFFFFA06A.toInt(), HeatStrain.colorOfZone(3))
        assertEquals(0xFFF35264.toInt(), HeatStrain.colorOfZone(4))
        assertNull(HeatStrain.colorOfZone(0))
        assertNull(HeatStrain.colorOfZone(5))
        assertEquals(HeatStrain.colorOfZone(3), HeatStrain.color(4.5))
        assertNull(HeatStrain.color(Double.NaN))
    }
}
