package io.smartycoder.bignum.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ink colour for a zone fill. Every colour the palette can produce has to end up with a legible
 * pairing, so the test walks the palette rather than a couple of hand-picked values.
 */
class OnColorTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    // The palette, in the order ZoneColors declares it.
    private val palette = mapOf(
        "Z1 mint" to 0xFF60EEB2.toInt(),
        "Z2 teal" to 0xFF00B988.toInt(),
        "Z3 yellow" to 0xFFFFF500.toInt(),
        "Z4 salmon" to 0xFFFB8C65.toInt(),
        "Z5 orange" to 0xFFFE581F.toInt(),
        "Z6 red" to 0xFFD60404.toInt(),
        "Z7 magenta" to 0xFFB700A2.toInt(),
    )

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `the light zones take black ink`() {
        listOf("Z1 mint", "Z2 teal", "Z3 yellow", "Z4 salmon", "Z5 orange").forEach {
            assertEquals(it, black, ZoneColors.onColor(palette.getValue(it)))
        }
    }

    @Test
    fun `the dark zones take white ink`() {
        listOf("Z6 red", "Z7 magenta").forEach {
            assertEquals(it, white, ZoneColors.onColor(palette.getValue(it)))
        }
    }

    @Test
    fun `every zone fill clears the 4 point 5 to 1 contrast threshold`() {
        palette.forEach { (name, fill) ->
            val ratio = contrast(fill, ZoneColors.onColor(fill))
            assertTrue("$name only reaches ${"%.2f".format(ratio)}:1", ratio >= 4.5)
        }
    }

    @Test
    fun `it always picks the better of black and white`() {
        palette.forEach { (name, fill) ->
            val picked = contrast(fill, ZoneColors.onColor(fill))
            val other = contrast(fill, if (ZoneColors.onColor(fill) == black) white else black)
            assertTrue("$name picked the worse ink", picked >= other)
        }
    }

    @Test
    fun `the extremes are handled`() {
        assertEquals(white, ZoneColors.onColor(black))
        assertEquals(black, ZoneColors.onColor(white))
    }
}
