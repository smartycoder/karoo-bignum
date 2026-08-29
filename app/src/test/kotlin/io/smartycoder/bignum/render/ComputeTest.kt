package io.smartycoder.bignum.render

import io.smartycoder.bignum.ZoneColorMode
import io.smartycoder.bignum.fields.BaseNumericField
import io.smartycoder.bignum.fields.Wedge
import io.smartycoder.bignum.format.Formatters
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises [BaseNumericField.compute] directly. It is pure over its six parameters -- no
 * Context, no android.* call -- so it is the one piece of the field pipeline this Robolectric-
 * free module can drive in a plain JVM test. [BaseNumericField.frameFlow] needs a live Context
 * to build its flows and is left untested here; it stays a thin, hand-checked wrapper around
 * this function plus [io.smartycoder.bignum.Settings].
 */
class ComputeTest {

    private val defaultColor = 0xFF123456.toInt()

    /**
     * Minimal concrete field, just enough to reach [BaseNumericField.compute]. karoo is null:
     * compute() is documented to never touch it, and this module has no Robolectric to hand it a
     * real KarooSystemService, which needs a live Context to construct.
     */
    private class TestField(
        override val missingValue: Double? = null,
        override val demoInTestMode: Boolean = true,
        private val wedgeToReturn: Wedge? = null,
        private val displayOverride: ((Double) -> Double?)? = null,
    ) : BaseNumericField("test", "test_type", null) {
        override val upstreamTypeId = "test.upstream"
        override val label = "TEST"
        override val iconRes = 0
        override val zoneKind: ZoneKind? = null
        override val format = Formatters.watts
        override val previewValue = 42.0

        override fun displayValue(raw: Double, profile: UserProfile?): Double? =
            // Not `displayOverride?.invoke(raw) ?: super...`: that elvis would treat an override
            // that legitimately returns null the same as no override at all, and fall through to
            // the default identity behaviour instead of returning the null this test needs.
            if (displayOverride != null) displayOverride.invoke(raw) else super.displayValue(raw, profile)

        override fun wedge(raw: Double): Wedge? = wedgeToReturn
    }

    private fun streaming(value: Double) =
        StreamState.Streaming(DataPoint("test.upstream", mapOf("value" to value)))

    @Test fun `streaming value renders formatted text with the default colour and no background`() {
        val frame = TestField().compute(
            streaming(123.0), profile = null, preview = false, testMode = false,
            mode = ZoneColorMode.TEXT, defaultColor = defaultColor,
        )
        assertEquals("123", frame.visual.text)
        assertEquals(defaultColor, frame.visual.color)
        assertNull(frame.visual.background)
    }

    @Test fun `test mode shows the preview value even while a stream is arriving`() {
        // demoInTestMode defaults to true, and the field is documented to win here ahead of the
        // stream -- a live 0 and a demo 0 would otherwise look identical.
        val frame = TestField().compute(
            streaming(999.0), profile = null, preview = false, testMode = true,
            mode = ZoneColorMode.OFF, defaultColor = defaultColor,
        )
        assertEquals("42", frame.visual.text)
    }

    @Test fun `preview shows the preview value when no stream is available`() {
        val frame = TestField().compute(
            StreamState.Idle, profile = null, preview = true, testMode = false,
            mode = ZoneColorMode.OFF, defaultColor = defaultColor,
        )
        assertEquals("42", frame.visual.text)
    }

    @Test fun `no value and no missing value falls back to a dash with no wedge`() {
        // wedgeToReturn is non-null on purpose: the raw-null branch must never call wedge() at
        // all, so a field that defines one still shows none.
        val wedge = Wedge(fraction = 0.5f, color = 0xFF00FF00.toInt(), rising = true)
        val frame = TestField(wedgeToReturn = wedge).compute(
            StreamState.Idle, profile = null, preview = false, testMode = false,
            mode = ZoneColorMode.TEXT, defaultColor = defaultColor,
        )
        assertEquals("--", frame.visual.text)
        assertEquals(defaultColor, frame.visual.color)
        assertNull(frame.visual.background)
        assertNull(frame.wedge)
    }

    @Test fun `a missing value is formatted through the field's format rather than shown as a dash`() {
        val frame = TestField(missingValue = 10.0).compute(
            StreamState.Idle, profile = null, preview = false, testMode = false,
            mode = ZoneColorMode.TEXT, defaultColor = defaultColor,
        )
        assertEquals("10", frame.visual.text)
    }

    @Test fun `displayValue returning null renders a dash even though a raw value exists`() {
        // wedgeToReturn is non-null on purpose, same as the missing-value case above: the
        // displayValue-null early return must never call wedge() at all, so a field that defines
        // one still shows none. With wedgeToReturn left null, assertNull(frame.wedge) would pass
        // whether or not the early return actually skipped the wedge computation.
        val wedge = Wedge(fraction = 0.5f, color = 0xFF00FF00.toInt(), rising = true)
        val frame = TestField(wedgeToReturn = wedge, displayOverride = { null }).compute(
            streaming(50.0), profile = null, preview = false, testMode = false,
            mode = ZoneColorMode.TEXT, defaultColor = defaultColor,
        )
        assertEquals("--", frame.visual.text)
        // The early return happens before the wedge is ever computed, same as the missing-value
        // case above.
        assertNull(frame.wedge)
    }

    @Test fun `ZoneColorMode OFF suppresses the wedge even when the field defines one`() {
        val wedge = Wedge(fraction = 0.5f, color = 0xFF00FF00.toInt(), rising = true)
        val field = TestField(wedgeToReturn = wedge)

        val off = field.compute(
            streaming(50.0), profile = null, preview = false, testMode = false,
            mode = ZoneColorMode.OFF, defaultColor = defaultColor,
        )
        assertNull(off.wedge)

        // Confirms the field's wedge is only suppressed, not broken: any other mode still shows
        // it, and Wedge is a data class so this compares what would actually be drawn.
        val text = field.compute(
            streaming(50.0), profile = null, preview = false, testMode = false,
            mode = ZoneColorMode.TEXT, defaultColor = defaultColor,
        )
        assertEquals(wedge, text.wedge)
    }
}
