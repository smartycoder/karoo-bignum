package io.smartycoder.bignum

import org.junit.Assert.assertEquals
import org.junit.Test

/** The zone colour setting used to be a boolean; an existing install must keep its choice. */
class SettingsMigrationTest {

    @Test
    fun `a stored mode wins over the legacy boolean`() {
        assertEquals(ZoneColorMode.FILL, Settings.resolveMode("FILL", legacyZoneColors = false))
        assertEquals(ZoneColorMode.OFF, Settings.resolveMode("OFF", legacyZoneColors = true))
    }

    @Test
    fun `colours on used to mean colouring the number`() {
        assertEquals(ZoneColorMode.TEXT, Settings.resolveMode(null, legacyZoneColors = true))
    }

    @Test
    fun `colours off stays off`() {
        assertEquals(ZoneColorMode.OFF, Settings.resolveMode(null, legacyZoneColors = false))
    }

    @Test
    fun `an unknown stored value falls back rather than throwing`() {
        // A downgrade, or a hand-edited prefs file.
        assertEquals(ZoneColorMode.TEXT, Settings.resolveMode("SPARKLES", legacyZoneColors = true))
    }
}
