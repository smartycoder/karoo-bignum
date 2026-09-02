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

    // inkTopFor is what decides whether a number ends up under the HUD's zone bar, and so
    // whether it gives up height to it. Getting it wrong is not a crash: it is either a number
    // hidden behind the bar, or every number needlessly smaller than it has to be.

    @Test
    fun `a left-aligned raster starts at the top of its box`() {
        // fitStart, so there is never room above it and a bar always costs it height.
        assertEquals(0, FieldRenderer.inkTopFor(Alignment.LEFT, 200, 100))
        assertEquals(0, FieldRenderer.inkTopFor(Alignment.LEFT, 200, 200))
    }

    @Test
    fun `a centred raster keeps half the slack above it`() {
        assertEquals(50, FieldRenderer.inkTopFor(Alignment.CENTER, 200, 100))
        assertEquals(0, FieldRenderer.inkTopFor(Alignment.CENTER, 200, 200))
    }

    @Test
    fun `a right-aligned raster keeps all the slack above it`() {
        // fitEnd pins it to the bottom, which is why a right-aligned number pays nothing for the
        // bar until it is tall enough to fill the tile on its own.
        assertEquals(100, FieldRenderer.inkTopFor(Alignment.RIGHT, 200, 100))
        assertEquals(0, FieldRenderer.inkTopFor(Alignment.RIGHT, 200, 200))
    }

    @Test
    fun `a raster taller than its box never reports a negative top`() {
        // measure() clamps the text size, so this is a degenerate tile rather than a real case --
        // but a negative top would compare as "clears the bar" and hide the number under it.
        for (alignment in Alignment.entries) {
            assertEquals(0, FieldRenderer.inkTopFor(alignment, 100, 140))
        }
    }
}
