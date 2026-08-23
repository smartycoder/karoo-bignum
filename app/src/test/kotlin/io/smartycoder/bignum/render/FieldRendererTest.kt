package io.smartycoder.bignum.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Geometry only. The drawing itself needs android.graphics and so belongs on a device, but the
 * bug this covers was arithmetic: the baseline was pinned to the top of the box rather than
 * centred, which only shows once a wide value shrinks the text.
 */
class FieldRendererTest {

    /** Ink bounds come back with a negative top, measured from the baseline upwards. */
    private fun gaps(boxHeight: Int, inkHeight: Int): Pair<Float, Float> {
        val inkTop = -inkHeight
        val baseline = FieldRenderer.baselineFor(boxHeight, inkHeight, inkTop)
        val above = baseline + inkTop
        val below = boxHeight - baseline
        return above to below
    }

    @Test
    fun `full-size value fills the box exactly`() {
        // Nothing shrank, so the ink spans the whole box: baseline sits on the bottom edge.
        assertEquals(144f, FieldRenderer.baselineFor(144, 144, -144), 0.001f)
        val (above, below) = gaps(144, 144)
        assertEquals(0f, above, 0.001f)
        assertEquals(0f, below, 0.001f)
    }

    @Test
    fun `shrunk value gets the same room above and below`() {
        val (above, below) = gaps(144, 100)
        assertEquals(22f, above, 0.001f)
        assertEquals(above, below, 0.001f)
    }

    @Test
    fun `odd leftover splits evenly rather than landing all on one side`() {
        val (above, below) = gaps(145, 100)
        assertEquals(22.5f, above, 0.001f)
        assertEquals(above, below, 0.001f)
    }

    @Test
    fun `heavily shrunk value stays centred`() {
        // A long elapsed time can lose a third of its height to the width budget.
        val (above, below) = gaps(144, 96)
        assertEquals(24f, above, 0.001f)
        assertEquals(above, below, 0.001f)
    }
}
