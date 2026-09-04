package io.smartycoder.bignum.render

import io.hammerhead.karooext.models.ViewConfig.Alignment
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
    // ── shrinkFactor ───────────────────────────────────────────────────────
    // The template holds a field's size steady as digits come and go, but it is narrower than
    // the tile whenever height is what limited the fit. Measuring overflow against it shrank
    // values that had room to spare.
    @Test
    fun `a value inside its template is left alone`() {
        assertEquals(1f, FieldRenderer.shrinkFactor(140f, 157f, 220), 0.0001f)
    }

    @Test
    fun `a value past its template but inside the tile is left alone`() {
        // 4-digit power on a tile where height is what limits the number: 209px of value,
        // a 157px template, and 220px of room.
        assertEquals(1f, FieldRenderer.shrinkFactor(209f, 157f, 220), 0.0001f)
    }

    @Test
    fun `a value past the tile shrinks to the tile`() {
        assertEquals(220f / 293f, FieldRenderer.shrinkFactor(293f, 220f, 220), 0.0001f)
    }

    @Test
    fun `a template wider than the tile is what the value is held to`() {
        // measure() clamps rather than fits when a tile is degenerate, and then the template is
        // the wider of the two; shrinking to the tile there would be a second, harsher clamp.
        assertEquals(240f / 300f, FieldRenderer.shrinkFactor(300f, 240f, 100), 0.0001f)
    }

    @Test
    fun `a value with no width asks for no shrinking`() {
        assertEquals(1f, FieldRenderer.shrinkFactor(0f, 157f, 220), 0.0001f)
    }

}
