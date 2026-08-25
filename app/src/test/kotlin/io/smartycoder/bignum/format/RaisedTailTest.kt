package io.smartycoder.bignum.format

import io.smartycoder.bignum.render.FieldRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The split is pure string work, so it is testable here even though the drawing is not.
 *
 * Two things have to hold. A value's tail must end up in the secondary part, and the template
 * must be wide enough for every form the formatter can produce -- a field scaled against a
 * budget it does not actually use changes size as its value crosses that budget.
 */
class RaisedTailTest {

    @Test
    fun `a decimal becomes the raised tail and the point goes`() {
        assertEquals("34" to "9", RaisedTail.split("34.9", raised = true))
        assertEquals("105" to "4", RaisedTail.split("105.4", raised = true))
    }

    @Test
    fun `a ride time splits at its last colon`() {
        assertEquals("1:34" to "17", RaisedTail.split("1:34:17", raised = true))
        assertEquals("34" to "56", RaisedTail.split("34:56", raised = true))
    }

    @Test
    fun `a negative value keeps its sign with the digits`() {
        assertEquals("-4" to "2", RaisedTail.split("-4.2", raised = true))
    }

    @Test
    fun `text with no separator is left whole`() {
        assertEquals("226" to "", RaisedTail.split("226", raised = true))
        assertEquals("--" to "", RaisedTail.split("--", raised = true))
        assertEquals("" to "", RaisedTail.split("", raised = true))
    }

    @Test
    fun `a separator with nothing in front of it is not a tail`() {
        assertEquals(".5" to "", RaisedTail.split(".5", raised = true))
    }

    @Test
    fun `switched off, nothing is raised`() {
        assertEquals("34.9" to "", RaisedTail.split("34.9", raised = false))
        assertEquals("1:34:17" to "", RaisedTail.split("1:34:17", raised = false))
    }

    @Test
    fun `a duration template splits the same way its values do`() {
        assertEquals("0:00" to "00", RaisedTail.template("0:00:00", raised = true))
        assertEquals("0:00:00" to "", RaisedTail.template("0:00:00", raised = false))
    }

    /**
     * The reason template() is not just split(). Formatters.compact drops the decimal once the
     * value grows, so a distance field draws "99.9" and then "105"; budgeting "99" plus a raised
     * digit would leave three full-size digits over budget and shrink the number at 100km.
     */
    @Test
    fun `a decimal template budgets for the form that has no decimal at all`() {
        assertEquals("000" to "", RaisedTail.template("00.0", raised = true))
        assertEquals("00.0" to "", RaisedTail.template("00.0", raised = false))
    }

    @Test
    fun `every value a compact field can produce fits its raised template`() {
        val (primary, secondary) = RaisedTail.template("00.0", raised = true)
        // The renderer's own scale, not a copy of it: at any other value this test would pass
        // while the field really was over budget, which is the one thing it exists to catch.
        val scale = FieldRenderer.SECONDARY_SCALE
        val budget = primary.length + secondary.length * scale
        // 99.9 keeps its decimal, 105 has lost it; both must fit the one budget.
        for (text in listOf("99.9", "0.0", "105", "999", "-12")) {
            val (p, s) = RaisedTail.split(text, raised = true)
            assertTrue("$text over budget", p.length + s.length * scale <= budget)
        }
    }

    @Test
    fun `every duration the formatter produces splits its seconds off`() {
        val durations = listOf(59_999.0, 60_000.0, 330_000.0, 3_600_000.0, 5_073_000.0, 45_296_000.0)
        for (ms in durations) {
            val text = Formatters.time(ms, null).first
            val i = text.lastIndexOf(':')
            val (primary, secondary) = RaisedTail.split(text, raised = true)
            assertEquals("seconds of $text", text.substring(i + 1), secondary)
            // Rejoined with the separator the text actually carried, so this stays a statement
            // about the split rather than about what the formatter happens to emit.
            assertEquals("round trip of $text", text, primary + text[i] + secondary)
        }
    }
}
