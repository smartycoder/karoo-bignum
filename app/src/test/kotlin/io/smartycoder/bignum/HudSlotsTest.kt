package io.smartycoder.bignum

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `resolveSlots` guards the ids a future composite HUD field looks up in the catalogue on a
 * coroutine -- an id it hands back that the catalogue does not know would throw there,
 * uncaught, taking the whole extension process down mid-ride.
 */
class HudSlotsTest {

    private val known = setOf("speed", "hr", "power", "cadence")

    @Test
    fun `both stored ids known survive`() {
        assertEquals("power" to "cadence", Settings.resolveSlots("power", "cadence", known))
    }

    @Test
    fun `left unknown falls back to the default, right survives`() {
        assertEquals("speed" to "power", Settings.resolveSlots("madeUp", "power", known))
    }

    @Test
    fun `both null falls back to DEFAULT_SLOTS`() {
        assertEquals(Settings.DEFAULT_SLOTS, Settings.resolveSlots(null, null, known))
    }

    @Test
    fun `a side can hold the other side's default independently`() {
        // "hr" is the right side's default, but nothing stops the left side from also being "hr".
        assertEquals("hr" to "speed", Settings.resolveSlots("hr", "speed", known))
    }

    @Test
    fun `neither default known falls back to known first for both sides`() {
        val knownWithoutDefaults = setOf("power", "cadence")
        assertEquals(
            knownWithoutDefaults.first() to knownWithoutDefaults.first(),
            Settings.resolveSlots("gone", "alsoGone", knownWithoutDefaults),
        )
    }

    @Test
    fun `empty known returns DEFAULT_SLOTS unchanged rather than throwing`() {
        assertEquals(Settings.DEFAULT_SLOTS, Settings.resolveSlots("speed", "hr", emptySet()))
    }

    @Test
    fun `both DEFAULT_SLOTS ids are real type ids in this build`() {
        // Guards against a rename: if a future change drops "speed" or "hr" from
        // extension_info.xml without updating DEFAULT_SLOTS, this fails the build instead of
        // silently shipping a HUD field that falls back to known.first() on every install.
        val xml = File("src/main/res/xml/extension_info.xml").readText()
        val typeIds = Regex("""typeId="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()

        assertEquals(true, Settings.DEFAULT_SLOTS.first in typeIds)
        assertEquals(true, Settings.DEFAULT_SLOTS.second in typeIds)
    }
}
