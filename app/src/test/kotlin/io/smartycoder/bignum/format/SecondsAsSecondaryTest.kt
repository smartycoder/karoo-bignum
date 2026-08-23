package io.smartycoder.bignum.format

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The split is pure string work, so it is testable here even though the drawing is not.
 *
 * What has to hold: the seconds always end up in the secondary part, and the same rule applied to
 * the width template yields the template the renderer measures against. If those two disagree the
 * field is scaled against a budget it does not actually use.
 */
class SecondsAsSecondaryTest {

    @Test
    fun `hours and minutes stay full size, seconds become secondary`() {
        assertEquals("1:34" to ":17", Formatters.secondsAsSecondary("1:34:17"))
        assertEquals("10:34" to ":56", Formatters.secondsAsSecondary("10:34:56"))
    }

    @Test
    fun `minutes and seconds split the same way below an hour`() {
        assertEquals("34" to ":56", Formatters.secondsAsSecondary("34:56"))
        assertEquals("0" to ":59", Formatters.secondsAsSecondary("0:59"))
    }

    @Test
    fun `the width template splits by the same rule as a value`() {
        // ElapsedTimeField declares "0:00:00", so the renderer measures "0:00" at full size plus
        // ":00" at the secondary size.
        assertEquals("0:00" to ":00", Formatters.secondsAsSecondary("0:00:00"))
    }

    @Test
    fun `text without a separator is left whole`() {
        assertEquals("42" to "", Formatters.secondsAsSecondary("42"))
        assertEquals("" to "", Formatters.secondsAsSecondary(""))
    }

    @Test
    fun `every duration the formatter produces splits its seconds off`() {
        val durations = listOf(59_999.0, 60_000.0, 330_000.0, 3_600_000.0, 5_073_000.0, 45_296_000.0)
        durations.forEach { ms ->
            val text = Formatters.time(ms, null).first
            val (primary, secondary) = Formatters.secondsAsSecondary(text)
            assertEquals("seconds of $text", text.substring(text.lastIndexOf(':')), secondary)
            assertEquals("primary of $text", text.dropLast(secondary.length), primary)
            assertEquals("round trip of $text", text, primary + secondary)
        }
    }
}
