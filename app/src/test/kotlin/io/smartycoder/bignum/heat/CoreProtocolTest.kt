package io.smartycoder.bignum.heat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreProtocolTest {

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    /**
     * CORE's own worked example from its Connectivity Implementation Notes. The document prints
     * the bytes three ways that do not quite agree; this is the one it gives as Python renders it,
     * b"7\x19\x0f\xa4\r/\x00\x11\x00'", which is the only one of the right length.
     */
    private val example = bytes(0x37, 0x19, 0x0F, 0xA4, 0x0D, 0x2F, 0x00, 0x11, 0x00, 0x27)

    @Test fun `CORE's example payload decodes field by field`() {
        val m = CoreProtocol.parseMeasurement(example)!!
        assertEquals(38.65, m.coreC!!, 1e-9)
        assertEquals(34.92, m.skinC!!, 1e-9)
        assertEquals(3.9, m.hsi!!, 1e-9)
        assertEquals(1, m.quality) // poor
        assertNull(m.heartRate) // supported, but 0: none coming in
    }

    @Test fun `an index of zero is a reading, not a missing one`() {
        val m = CoreProtocol.parseMeasurement(bytes(0x21, 0x19, 0x0F, 0xA4, 0x0D, 0x00))!!
        assertEquals(0.0, m.hsi!!, 0.0)
    }

    @Test fun `a sensor without the index flag has no index`() {
        // Firmware before 0.8.7: skin and quality, no bit 5.
        val m = CoreProtocol.parseMeasurement(bytes(0x05, 0x19, 0x0F, 0xA4, 0x0D, 0x13))!!
        assertEquals(38.65, m.coreC!!, 1e-9)
        assertNull(m.hsi)
        assertEquals(3, m.quality)
    }

    @Test fun `no core reading means no index either`() {
        val m = CoreProtocol.parseMeasurement(bytes(0x20, 0xFF, 0x7F, 0x27))!!
        assertNull(m.coreC)
        assertNull(m.hsi)
    }

    @Test fun `the 0xFF index marker is not 25_5`() {
        val m = CoreProtocol.parseMeasurement(bytes(0x20, 0x19, 0x0F, 0xFF))!!
        assertNull(m.hsi)
    }

    @Test fun `Fahrenheit readings are converted to Celsius`() {
        // Flag bit 3; 101.57 °F is 38.65 °C.
        val m = CoreProtocol.parseMeasurement(bytes(0x09, 0xAD, 0x27, 0xA4, 0x0D))!!
        assertEquals(38.65, m.coreC!!, 0.001)
        assertEquals((34.92 - 32) * 5 / 9, m.skinC!!, 0.001)
    }

    @Test fun `a payload cut short leaves the missing fields null instead of throwing`() {
        val m = CoreProtocol.parseMeasurement(bytes(0x37, 0x19, 0x0F, 0xA4))!!
        assertEquals(38.65, m.coreC!!, 1e-9)
        assertNull(m.skinC)
        assertNull(m.quality)
        assertNull(m.hsi)
        assertNull(CoreProtocol.parseMeasurement(bytes(0x37, 0x19)))
    }

    @Test fun `the advertised core temperature is read from the manufacturer data`() {
        // CORE's example advertisement: version 0, state 4 (measuring), 0x91B3 = 37.299 °C.
        assertEquals(37.299, CoreProtocol.advertisedCore(bytes(0x00, 0x04, 0xB3, 0x91))!!, 1e-9)
    }

    @Test fun `an advertisement from a sensor that is not measuring carries no temperature`() {
        assertNull(CoreProtocol.advertisedCore(bytes(0x00, 0x02, 0xB3, 0x91)))
        assertNull(CoreProtocol.advertisedCore(bytes(0x01, 0x04, 0xB3, 0x91)))
        assertNull(CoreProtocol.advertisedCore(bytes(0x00, 0x04)))
        assertNull(CoreProtocol.advertisedCore(null))
    }
}

class CoreCandidatesTest {

    private val mine = CoreCandidate("AA:AA:AA:AA:AA:01", rssi = -70, advertisedCore = 38.21)
    private val theirs = CoreCandidate("BB:BB:BB:BB:BB:02", rssi = -50, advertisedCore = 37.80)
    private val silent = CoreCandidate("CC:CC:CC:CC:CC:03", rssi = -60, advertisedCore = null)

    @Test fun `the sensor advertising the Karoo's core wins over a stronger signal`() {
        assertEquals(mine, CoreCandidates.pick(listOf(theirs, mine, silent), karooCore = 38.23, rejected = emptySet()))
    }

    @Test fun `a sensor advertising a clearly different core is never picked`() {
        assertEquals(silent, CoreCandidates.pick(listOf(theirs, silent), karooCore = 38.23, rejected = emptySet()))
    }

    @Test fun `without a match the strongest signal is tried`() {
        assertEquals(theirs, CoreCandidates.pick(listOf(theirs, mine), karooCore = null, rejected = emptySet()))
    }

    @Test fun `a sensor rejected this session is not tried again`() {
        assertNull(CoreCandidates.pick(listOf(mine), karooCore = 38.23, rejected = setOf(mine.address)))
    }
}
