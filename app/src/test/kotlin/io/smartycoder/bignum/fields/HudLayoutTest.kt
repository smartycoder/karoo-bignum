package io.smartycoder.bignum.fields

import io.hammerhead.karooext.models.ViewConfig
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

    @Test
    fun `even tile width splits with nothing lost to the divider`() {
        val parent = config(200)
        val left = halfConfig(parent, left = true).viewSize.first
        val right = halfConfig(parent, left = false).viewSize.first
        assertEquals(200, left + right + DIVIDER_PX)
    }

    @Test
    fun `odd tile width splits with nothing lost either`() {
        // The case a second `usable / 2` would get wrong: the remainder pixel has to land in
        // one of the halves, not be floored away on both sides.
        val parent = config(201)
        val left = halfConfig(parent, left = true).viewSize.first
        val right = halfConfig(parent, left = false).viewSize.first
        assertEquals(201, left + right + DIVIDER_PX)
    }

    @Test
    fun `both slots inherit whatever alignment the parent has`() {
        for (parentAlignment in listOf(
            ViewConfig.Alignment.LEFT,
            ViewConfig.Alignment.CENTER,
            ViewConfig.Alignment.RIGHT,
        )) {
            val parent = config(200, alignment = parentAlignment)
            assertEquals(
                parentAlignment,
                halfConfig(parent, left = true).alignment,
            )
            assertEquals(
                parentAlignment,
                halfConfig(parent, left = false).alignment,
            )
        }
    }

    @Test
    fun `height is untouched and preview is carried through`() {
        val parent = config(200, preview = true)
        for (left in listOf(true, false)) {
            val half = halfConfig(parent, left)
            assertEquals(100, half.viewSize.second)
            assertEquals(true, half.preview)
        }
    }
}
