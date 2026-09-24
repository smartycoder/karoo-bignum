package io.smartycoder.bignum.heat

import java.util.UUID
import kotlin.math.abs

/** One notification of CORE's Core Body Temperature characteristic. Temperatures in °C. */
data class CoreMeasurement(
    /** Null while the sensor has no reading: it sends 0x7FFF, "data not available". */
    val coreC: Double?,
    val skinC: Double?,
    /** The sensor's own Heat Strain Index. Null before firmware 0.8.7, or while core is invalid. */
    val hsi: Double?,
    /** 0 invalid, 1 poor, 2 fair, 3 good, 4 excellent; null when the sensor does not say. */
    val quality: Int?,
    /** The heart rate the sensor itself is receiving; null when none. */
    val heartRate: Int?,
)

/**
 * CORE's open Bluetooth profile, as published in its "CoreTemp BLE Service Specification" (v2.2)
 * and "CORE Connectivity Implementation Notes" (v3.1), github.com/CoreBodyTemp/CoreBodyTemp.
 *
 * The Karoo talks to a CORE itself and hands extensions its core and skin temperature, but not the
 * Heat Strain Index the sensor has broadcast since firmware 0.8.7. [CoreSensorLink] reads it
 * straight from the sensor; this is the part of that which needs no radio, so it can be tested.
 */
object CoreProtocol {

    /** The Core Temp service, advertised in the sensor's scan response. */
    val SERVICE: UUID = UUID.fromString("00002100-5b1e-4347-b07c-97b514dae121")

    /** Core Body Temperature: notified about once a second. */
    val MEASUREMENT: UUID = UUID.fromString("00002101-5b1e-4347-b07c-97b514dae121")

    /** What older firmware advertised instead of [SERVICE]; CORE says to accept either. */
    val LEGACY_SERVICE: UUID = UUID.fromString("00004200-f366-40b2-ac37-70cce0aa83b1")

    /** The standard Client Characteristic Configuration descriptor, to turn notifications on. */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** CORE's Bluetooth company ID, on the manufacturer data in every advertisement. */
    const val MANUFACTURER_ID = 0xF60B

    private const val FLAG_SKIN = 1 shl 0
    private const val FLAG_RESERVED = 1 shl 1
    private const val FLAG_QUALITY = 1 shl 2
    private const val FLAG_FAHRENHEIT = 1 shl 3
    private const val FLAG_HEART_RATE = 1 shl 4
    private const val FLAG_HSI = 1 shl 5

    private const val TEMP_NOT_AVAILABLE = 0x7FFF
    private const val HSI_INVALID = 0xFF
    private const val QUALITY_NOT_AVAILABLE = 0b111

    /**
     * A Core Body Temperature notification, or null for one too short to carry even the core
     * temperature. Fields are present in a fixed order, each only when its flag is set. A payload
     * cut short leaves the field it ends in, and every one after it, null: past a missing field
     * there is no telling which bytes belong to which.
     */
    fun parseMeasurement(payload: ByteArray): CoreMeasurement? {
        if (payload.size < 3) return null
        val flags = payload[0].toInt() and 0xFF
        var at = 1
        var short = false

        fun sint16(): Int? {
            if (short || at + 2 > payload.size) {
                short = true
                return null
            }
            // The high byte is sign-extended by toInt(), which is what makes this a SINT16.
            val v = (payload[at].toInt() and 0xFF) or (payload[at + 1].toInt() shl 8)
            at += 2
            return v
        }

        fun uint8(): Int? {
            if (short || at + 1 > payload.size) {
                short = true
                return null
            }
            return (payload[at++].toInt() and 0xFF)
        }

        fun celsius(raw: Int?): Double? {
            if (raw == null || raw == TEMP_NOT_AVAILABLE) return null
            val value = raw / 100.0
            return if (flags and FLAG_FAHRENHEIT != 0) (value - 32) * 5 / 9 else value
        }

        val core = celsius(sint16())
        val skin = if (flags and FLAG_SKIN != 0) celsius(sint16()) else null
        if (flags and FLAG_RESERVED != 0) sint16()
        val quality = if (flags and FLAG_QUALITY != 0) {
            uint8()?.let { it and 0b111 }?.takeIf { it != QUALITY_NOT_AVAILABLE }
        } else {
            null
        }
        val heartRate = if (flags and FLAG_HEART_RATE != 0) uint8()?.takeIf { it > 0 } else null
        // 0.0 is a real index -- no strain at all -- and CORE asks for it to be shown as such.
        // Only the flag and the 0xFF marker mean there is none.
        val hsi = if (flags and FLAG_HSI != 0) uint8()?.takeIf { it != HSI_INVALID }?.let { it / 10.0 } else null

        return CoreMeasurement(core, skin, hsi = if (core == null) null else hsi, quality, heartRate)
    }

    /**
     * The core temperature a CORE advertises, from its manufacturer data -- what follows the
     * company ID: a version byte, a status byte and the temperature in thousandths of a degree.
     * Null unless the sensor says it is measuring.
     *
     * This is what lets [CoreSensorLink] tell the rider's own sensor from the one on the wheel
     * in front before connecting to either: the Karoo's core temperature is the same number.
     */
    fun advertisedCore(manufacturerData: ByteArray?): Double? {
        if (manufacturerData == null || manufacturerData.size < 4) return null
        if (manufacturerData[0].toInt() != 0) return null
        val state = manufacturerData[1].toInt() and 0x0F
        if (state != STATE_MEASURING) return null
        val raw = (manufacturerData[2].toInt() and 0xFF) or ((manufacturerData[3].toInt() and 0xFF) shl 8)
        if (raw == 0xFFFF || raw == 0) return null
        return raw / 1000.0
    }

    private const val STATE_MEASURING = 4
}

/** A CORE seen in a scan. */
data class CoreCandidate(val address: String, val rssi: Int, val advertisedCore: Double?)

/** Picking which scanned CORE is the rider's. Pure, for the tests. */
object CoreCandidates {

    /** An advertised core this close to the Karoo's is taken as the same sensor. */
    const val SAME_SENSOR = 0.1

    /** One this far off is someone else's, however strong its signal. */
    const val OTHER_SENSOR = 0.3

    /**
     * The candidate most likely to be the rider's own CORE, or null when none qualifies.
     *
     * One whose advertised core matches the Karoo's wins outright; after that the strongest
     * signal, since the rider's sensor is a few centimetres from the Karoo. Candidates already
     * [rejected] this session, and ones advertising a core clearly not the Karoo's, are never
     * picked. Either way the choice is confirmed after connecting; see [SensorMatch].
     */
    fun pick(candidates: Collection<CoreCandidate>, karooCore: Double?, rejected: Set<String>): CoreCandidate? {
        val open = candidates.filter { it.address !in rejected }
            .filterNot { c ->
                karooCore != null && c.advertisedCore != null && abs(c.advertisedCore - karooCore) > OTHER_SENSOR
            }
        if (karooCore != null) {
            open.filter { it.advertisedCore != null && abs(it.advertisedCore - karooCore) <= SAME_SENSOR }
                .maxByOrNull { it.rssi }
                ?.let { return it }
        }
        return open.maxByOrNull { it.rssi }
    }
}
