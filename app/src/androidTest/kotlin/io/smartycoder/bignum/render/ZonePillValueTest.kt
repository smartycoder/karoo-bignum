package io.smartycoder.bignum.render

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.smartycoder.bignum.FontSetting
import io.smartycoder.bignum.NumberFont
import io.smartycoder.bignum.ZonePillStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pill's value is sized by its INK, not by a text size, so that it comes out the same height
 * in either number font. This measures the rendered bitmap and holds that promise.
 *
 * On the device because the answer comes from real font metrics: the JVM tests run with
 * `returnDefaultValues`, where every Paint measurement is zero.
 *
 * Rendered directly rather than read off a screenshot, which is what this test exists to replace:
 * the Karoo keeps the RemoteViews it was last handed, so a pill whose code changed can sit
 * unchanged on screen and look like the change did not take.
 */
@RunWith(AndroidJUnit4::class)
class ZonePillValueTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The pill the Karoo 3's header row leaves: headerHeight, less the inset at each end. */
    private val pillHeight = FieldRenderer.headerHeight(context) -
        2 * context.resources.getDimensionPixelSize(io.smartycoder.bignum.R.dimen.hud_pill_inset)

    /**
     * Measured on a value of "0", which is the glyph the renderer sizes against. A rounded digit
     * is drawn past the cap line at both ends, so a flat-sided value like "141" rasterises about
     * two pixels shorter -- sizing on one and measuring the other is a two-pixel error that looks
     * exactly like a change that did not take, and cost an hour of exactly that.
     */
    @Test
    fun theValueInkIsTheIntendedShareOfThePill() {
        for (face in NumberFont.entries) {
            val ink = inkHeight(FontSetting(face, width = 100, weight = 700))
            // Within a pixel and a half of the fraction the renderer aims at. Digit ink is a
            // whole number of pixels that a font's hinting steps rather than scales, so the
            // aim can only ever be met to the nearest reachable step -- the probe that found
            // this measured 25px of text size giving 22px of ink and 26px giving 23, with
            // nothing in between.
            val wanted = pillHeight * 0.605
            assertTrue(
                "$face: ink $ink of $pillHeight, wanted about ${"%.1f".format(wanted)}",
                kotlin.math.abs(ink - wanted) <= 1.5,
            )
        }
    }

    private fun inkHeight(font: FontSetting): Int {
        val bitmap = renderZonePill(
            context, pillHeight, lit = 3, segmentCount = 5, style = ZonePillStyle.SEGMENTS,
            zoneColor = Color.RED, text = "0", icon = null, isNight = true, font = font,
        ) ?: error("the pill drew nothing")
        // The value is the rightmost group, so the last fifth of the width is digits alone.
        val from = bitmap.width * 4 / 5
        // Halfway between the pill's ground and the value's own colour. Alpha alone counts the
        // lozenge's antialiased rounded ends as ink and reports the whole pill; a threshold near
        // white drops the softest row at each end of the digits.
        fun luma(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        val ground = luma(bitmap.getPixel(bitmap.width - 2, 1))
        val cut = ground + (255 - ground) / 2
        val rows = (0 until bitmap.height).filter { y ->
            (from until bitmap.width).any { x -> luma(bitmap.getPixel(x, y)) > cut }
        }
        check(rows.isNotEmpty()) { "no value ink found" }
        return rows.last() - rows.first() + 1
    }
}
