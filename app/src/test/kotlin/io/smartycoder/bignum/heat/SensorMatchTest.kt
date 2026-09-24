package io.smartycoder.bignum.heat

import io.smartycoder.bignum.heat.SensorMatch.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class SensorMatchTest {

    private fun SensorMatch.offerTimes(n: Int, karoo: Pair<Double?, Double?>, sensor: Pair<Double?, Double?>): Verdict {
        var v = verdict
        repeat(n) { v = offer(karoo.first, karoo.second, sensor.first, sensor.second) }
        return v
    }

    @Test fun `the rider's own sensor is confirmed after five matching readings`() {
        val match = SensorMatch()
        assertEquals(Verdict.PENDING, match.offerTimes(4, 38.20 to 34.10, 38.21 to 34.15))
        assertEquals(Verdict.CONFIRMED, match.offerTimes(1, 38.20 to 34.10, 38.21 to 34.15))
    }

    @Test fun `someone else's sensor is rejected`() {
        val match = SensorMatch()
        assertEquals(Verdict.REJECTED, match.offerTimes(5, 38.20 to 34.10, 37.60 to 33.00))
    }

    @Test fun `the same core on different skin is someone else's`() {
        // Core temperature moves slowly enough for two riders to share it for a while.
        val match = SensorMatch()
        assertEquals(Verdict.REJECTED, match.offerTimes(5, 38.20 to 34.10, 38.21 to 35.40))
    }

    @Test fun `agreement has to be unbroken to confirm`() {
        val match = SensorMatch()
        repeat(4) {
            match.offer(38.20, 34.10, 38.21, 34.12)
            match.offer(38.20, 34.10, 37.50, 33.00)
        }
        assertEquals(Verdict.PENDING, match.verdict)
    }

    @Test fun `readings with nothing to compare leave the verdict alone`() {
        val match = SensorMatch()
        assertEquals(Verdict.PENDING, match.offerTimes(20, null to 34.10, 38.21 to 34.15))
        assertEquals(Verdict.PENDING, match.offerTimes(20, 38.20 to 34.10, null to 34.15))
    }

    @Test fun `without skin on one side, core alone decides`() {
        val match = SensorMatch()
        assertEquals(Verdict.CONFIRMED, match.offerTimes(5, 38.20 to null, 38.21 to 34.15))
    }

    @Test fun `a confirmed sensor rides out a short disagreement but not a long one`() {
        val match = SensorMatch()
        match.offerTimes(5, 38.20 to 34.10, 38.20 to 34.10)
        assertEquals(Verdict.CONFIRMED, match.offerTimes(14, 38.20 to 34.10, 37.50 to 33.00))
        assertEquals(Verdict.REJECTED, match.offerTimes(1, 38.20 to 34.10, 37.50 to 33.00))
    }
}
