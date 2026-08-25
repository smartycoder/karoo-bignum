package io.smartycoder.bignum

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The font setting is read back from three loose preference values, any of which can predate
 * this build or have been written by hand. None of them may take a field down.
 */
class FontSettingTest {

    @Test
    fun `an install that predates the setting gets Saira at its narrowest`() {
        val setting = Settings.resolveFont(font = null, width = 50, weight = 900)
        assertEquals(NumberFont.SAIRA, setting.font)
        assertEquals(50, setting.width)
        assertEquals(900, setting.weight)
    }

    @Test
    fun `an unknown font name falls back rather than throwing`() {
        assertEquals(NumberFont.SAIRA, Settings.resolveFont("COMIC_SANS", 50, 900).font)
    }

    @Test
    fun `axis values are clamped into what Saira can actually draw`() {
        val tooLow = Settings.resolveFont("SAIRA", width = 10, weight = 0)
        assertEquals(50, tooLow.width)
        assertEquals(100, tooLow.weight)

        val tooHigh = Settings.resolveFont("SAIRA", width = 400, weight = 1200)
        assertEquals(125, tooHigh.width)
        assertEquals(900, tooHigh.weight)
    }

    @Test
    fun `only Saira has axes`() {
        assertEquals(true, Settings.resolveFont("SAIRA", 50, 900).hasAxes)
        assertEquals(false, Settings.resolveFont("OSWALD", 50, 900).hasAxes)
    }

    @Test
    fun `the width steps start at the narrowest the font can draw and stay even`() {
        assertEquals(50, Settings.WIDTHS.first())
        assertEquals(124, Settings.WIDTHS.last())
        assertEquals(38, Settings.WIDTHS.size)
        assertEquals(listOf(50, 52, 54), Settings.WIDTHS.take(3))
    }

    @Test
    fun `every offered step survives the clamp`() {
        for (w in Settings.WIDTHS) assertEquals(w, Settings.resolveFont("SAIRA", w, 900).width)
        for (w in Settings.WEIGHTS) assertEquals(w, Settings.resolveFont("SAIRA", 50, w).weight)
    }
}
