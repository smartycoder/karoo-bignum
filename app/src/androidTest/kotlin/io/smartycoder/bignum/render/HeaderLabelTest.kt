package io.smartycoder.bignum.render

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The header's capitals must come out the same height, and on the same line, whatever glyphs a
 * label happens to hold.
 *
 * On the device rather than on the JVM because the answer comes from real font metrics: the
 * project's unit tests run with `returnDefaultValues`, where every Paint measurement is zero.
 *
 * Labels used to be scaled by their own ink height, so "TIME lap" -- which reaches from the cap
 * line down to the tail of its "p" -- was drawn a fifth smaller than "HR" and sat off the line
 * every other label shared. Run this against that version and the first assertion fails.
 */
@RunWith(AndroidJUnit4::class)
class HeaderLabelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // A transparent drawable in place of the field icon, so every pixel of ink in the bitmap
    // belongs to the label.
    private val noIcon = android.R.color.transparent

    private val labels = listOf(
        "HR",           // capitals only
        "PWR lap",      // a descender
        "TIME lap",     // a descender behind a longer word
        "PWR 3s",       // a digit, which overshoots the cap height
        "W/KG lap",     // a slash, which drops below the baseline
        "AVG %HRR",     // punctuation at cap height
    )

    @Test
    fun everyLabelIsDrawnAtTheSameCapHeightAndBaseline() {
        val inks = labels.associateWith { ink(it) }
        val reference = inks.getValue("HR")
        inks.forEach { (label, ink) ->
            assertClose("cap height of \"$label\"", reference.height, ink.height)
            assertClose("cap top of \"$label\"", reference.top, ink.top)
        }
    }

    /**
     * A pixel of slack, and no more. Type designers draw round and pointed glyphs a touch past
     * the cap line so they do not read as short next to flat ones, and a digit is drawn past it
     * too -- "PWR 3S" rasterises one pixel taller than "HR" here. The bug this test exists for
     * was four pixels, and any regression towards per-label scaling is at least that.
     */
    private fun assertClose(what: String, expected: Int, actual: Int) {
        if (kotlin.math.abs(expected - actual) > 1) {
            assertEquals(what, expected, actual)
        }
    }

    @Test
    fun theHeaderIsTheSameHeightForEveryLabel() {
        // The number's box is what the header leaves behind, so a header that grew with its
        // label would leave fields on one page drawing their numbers at different sizes.
        val heights = labels.map { header(it).height }.distinct()
        assertEquals("header heights: $heights", 1, heights.size)
    }

    private data class Ink(val top: Int, val bottom: Int) {
        val height get() = bottom - top + 1
    }

    private fun header(label: String): Bitmap =
        FieldRenderer.renderHeader(context, label, noIcon, Color.WHITE, Color.WHITE, outline = false)

    /**
     * Rows the label's first glyph puts ink in -- a capital in every label there is.
     *
     * Deliberately not the whole label: scaling each label by its own ink height, which is the
     * bug this guards against, keeps the total ink at the target and pays for it out of the
     * capitals. Measured across the whole label, broken and fixed look identical.
     */
    private fun ink(label: String): Ink {
        val bitmap = header(label)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        fun inked(x: Int, y: Int) = Color.alpha(pixels[y * bitmap.width + x]) > 8

        // The first run of inked columns, ending at the first gap wide enough to be a letter
        // space rather than the join inside a glyph.
        val gapEndingAGlyph = 2
        var first = -1
        var last = -1
        var gap = 0
        for (x in 0 until bitmap.width) {
            val column = (0 until bitmap.height).any { inked(x, it) }
            if (column) {
                if (first < 0) first = x
                last = x
                gap = 0
            } else if (first >= 0) {
                gap++
                if (gap > gapEndingAGlyph) break
            }
        }
        check(first >= 0) { "\"$label\" drew nothing" }

        val rows = (0 until bitmap.height).filter { y -> (first..last).any { inked(it, y) } }
        return Ink(rows.first(), rows.last())
    }
}
