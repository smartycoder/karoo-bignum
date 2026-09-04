package io.smartycoder.bignum.fields

import io.hammerhead.karooext.models.ViewConfig
import io.smartycoder.bignum.ZonePillStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [halfConfig] is the only arithmetic in the HUD, and it is the arithmetic the renderer sizes
 * text from: get it wrong by a pixel and every number in the tile is scaled against a budget
 * that does not match the space it is drawn into. Pure and Context-free, so it is testable on
 * the JVM -- unlike startView and frameFlow, which are not.
 */
class HudLayoutTest {

    private fun config(
        width: Int,
        alignment: ViewConfig.Alignment = ViewConfig.Alignment.CENTER,
        preview: Boolean = false,
    ) = ViewConfig(
        gridSize = 2 to 3,
        viewSize = width to 100,
        textSize = 40,
        alignment = alignment,
        boundariesEnabled = true,
        preview = preview,
    )

    // The divider's real width comes from R.dimen.hud_divider_width, which this module cannot
    // resolve without a Context. Passing it in is the point: the arithmetic has to hold for
    // whatever the layout inflated, not just for the 1px it happens to be today.
    private val divider = 1

    @Test
    fun `even tile width splits with nothing lost to the divider`() {
        val parent = config(200)
        val left = halfConfig(parent, left = true, dividerPx = divider).viewSize.first
        val right = halfConfig(parent, left = false, dividerPx = divider).viewSize.first
        assertEquals(200, left + right + divider)
    }

    @Test
    fun `odd tile width splits with nothing lost either`() {
        // The case a second `usable / 2` would get wrong: the remainder pixel has to land in
        // one of the halves, not be floored away on both sides.
        val parent = config(201)
        val left = halfConfig(parent, left = true, dividerPx = divider).viewSize.first
        val right = halfConfig(parent, left = false, dividerPx = divider).viewSize.first
        assertEquals(201, left + right + divider)
    }

    @Test
    fun `a wider divider still leaves the halves adding back up`() {
        // Nothing here may assume the hairline is one pixel: the layout owns that number now.
        for (width in listOf(0, 1, 2, 7)) {
            for (tile in listOf(200, 201)) {
                val parent = config(tile)
                val left = halfConfig(parent, left = true, dividerPx = width).viewSize.first
                val right = halfConfig(parent, left = false, dividerPx = width).viewSize.first
                assertEquals(tile, left + right + width)
            }
        }
    }

    @Test
    fun `both slots inherit whatever alignment the parent has`() {
        // Still true, and now load-bearing rather than incidental: the two labels are moved to
        // opposite edges through renderInto's headerAlignment, NOT by handing a slot a different
        // alignment here. That value also picks the number's scaleType, so a slot given LEFT
        // would switch to fitStart and pin its digits to the top of its box while its neighbour
        // kept fitEnd, leaving the two numbers on visibly different baselines.
        for (parentAlignment in listOf(
            ViewConfig.Alignment.LEFT,
            ViewConfig.Alignment.CENTER,
            ViewConfig.Alignment.RIGHT,
        )) {
            val parent = config(200, alignment = parentAlignment)
            assertEquals(
                parentAlignment,
                halfConfig(parent, left = true, dividerPx = divider).alignment,
            )
            assertEquals(
                parentAlignment,
                halfConfig(parent, left = false, dividerPx = divider).alignment,
            )
        }
    }

    @Test
    fun `each half's label goes to the tile's own outer edge`() {
        // What the HUD passes as headerAlignment, spelled out here because it is the one thing
        // about the two labels a JVM test can see: the rest of it is a Canvas away.
        assertEquals(ViewConfig.Alignment.LEFT, hudHeaderAlignment(isLeft = true))
        assertEquals(ViewConfig.Alignment.RIGHT, hudHeaderAlignment(isLeft = false))
    }

    @Test
    fun `the pill style round-trips through its stored name`() {
        // Mirrors ZoneColorMode's parsing test. The default is SEGMENTS and NOT the first entry
        // by accident: an install that updates into this setting must not change appearance on
        // its own, and an unreadable or absent value has to land there too.
        for (style in ZonePillStyle.entries) {
            assertEquals(style, ZonePillStyle.from(style.name))
        }
        assertEquals(null, ZonePillStyle.from("SPARKLES"))
        assertEquals(null, ZonePillStyle.from(null))
    }

    @Test
    fun `height is untouched and preview is carried through`() {
        val parent = config(200, preview = true)
        for (left in listOf(true, false)) {
            val half = halfConfig(parent, left, dividerPx = divider)
            assertEquals(100, half.viewSize.second)
            assertEquals(true, half.preview)
        }
    }

}
