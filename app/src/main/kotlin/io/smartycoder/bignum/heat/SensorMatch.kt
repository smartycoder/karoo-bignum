package io.smartycoder.bignum.heat

import kotlin.math.abs

/**
 * Whether the CORE BigNum connected to is the one the Karoo is reading.
 *
 * On a group ride there can be several CORE sensors in range, and a Heat Strain Index read from
 * the wrong one would look entirely plausible. So nothing from a connection is trusted until its
 * core and skin temperatures have agreed with the Karoo's own, from the same sensor, several
 * notifications running. Core temperature moves slowly enough that two riders can share it for a
 * while; skin temperature is the one that tells them apart.
 *
 * Fed one comparison per notification: the sensor's reading and the Karoo's latest. The Karoo's
 * can be a second behind, which the tolerances allow for.
 */
class SensorMatch {

    enum class Verdict { PENDING, CONFIRMED, REJECTED }

    var verdict: Verdict = Verdict.PENDING
        private set

    private var agreedInARow = 0
    private var disagreedInARow = 0

    fun offer(karooCore: Double?, karooSkin: Double?, sensorCore: Double?, sensorSkin: Double?): Verdict {
        if (verdict == Verdict.REJECTED) return verdict
        // Nothing to compare: a sensor still settling, or the Karoo not reading it yet.
        if (karooCore == null || sensorCore == null) return verdict

        val core = abs(karooCore - sensorCore)
        val skin = if (karooSkin != null && sensorSkin != null) abs(karooSkin - sensorSkin) else null
        val agrees = core <= CORE_AGREES && (skin == null || skin <= SKIN_AGREES)
        val disagrees = core > CORE_DISAGREES || (skin != null && skin > SKIN_DISAGREES)

        // In between counts as neither: a skin reading caught mid-change on one side.
        if (agrees) {
            agreedInARow++
            disagreedInARow = 0
        } else if (disagrees) {
            disagreedInARow++
            agreedInARow = 0
        }

        verdict = when (verdict) {
            Verdict.PENDING -> when {
                agreedInARow >= CONFIRM_AFTER -> Verdict.CONFIRMED
                disagreedInARow >= REJECT_AFTER -> Verdict.REJECTED
                else -> Verdict.PENDING
            }
            // Once confirmed it takes a long run to let go: a sensor that is right does not stop
            // being right because the Karoo's reading stalled for a few seconds.
            Verdict.CONFIRMED -> if (disagreedInARow >= DROP_AFTER) Verdict.REJECTED else Verdict.CONFIRMED
            Verdict.REJECTED -> Verdict.REJECTED
        }
        return verdict
    }

    companion object {
        // Both sides come from the same sensor at 0.01 °C (ANT+ skin at 0.05 °C), so a real
        // match is a few hundredths off at most; the margins cover a Karoo reading a second old.
        const val CORE_AGREES = 0.06
        const val CORE_DISAGREES = 0.2
        const val SKIN_AGREES = 0.2
        const val SKIN_DISAGREES = 0.6

        const val CONFIRM_AFTER = 5
        const val REJECT_AFTER = 5
        const val DROP_AFTER = 15
    }
}
