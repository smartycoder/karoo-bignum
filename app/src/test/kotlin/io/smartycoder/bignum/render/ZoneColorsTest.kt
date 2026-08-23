package io.smartycoder.bignum.render

import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.UserProfile.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoneColorsTest {

    private fun profileWithZones(hr: List<Zone>, power: List<Zone>): UserProfile {
        return UserProfile(
            weight = 70f,
            preferredUnit = UserProfile.PreferredUnit(
                distance = UserProfile.PreferredUnit.UnitType.METRIC,
                elevation = UserProfile.PreferredUnit.UnitType.METRIC,
                temperature = UserProfile.PreferredUnit.UnitType.METRIC,
                weight = UserProfile.PreferredUnit.UnitType.METRIC,
            ),
            maxHr = 200,
            restingHr = 50,
            heartRateZones = hr,
            ftp = 250,
            powerZones = power,
        )
    }

    // Karoo's own zone colors, sampled from its zone settings screens. The two scales share
    // the first four and diverge at the top: HR ends at Z5, power runs to Z7.
    private val z1 = 0xFF60EEB2.toInt()
    private val z2 = 0xFF00B988.toInt()
    private val z3 = 0xFFFFF500.toInt()
    private val z4 = 0xFFFB8C65.toInt()
    private val hrZ5 = 0xFFD60404.toInt()
    private val pwrZ5 = 0xFFFE581F.toInt()
    private val pwrZ6 = 0xFFD60404.toInt()
    private val pwrZ7 = 0xFFB700A2.toInt()

    private val hrZones = listOf(
        Zone(min = 100, max = 119),
        Zone(min = 120, max = 139),
        Zone(min = 140, max = 159),
        Zone(min = 160, max = 179),
        Zone(min = 180, max = 220),
    )

    // Karoo's power scale has seven zones.
    private val powerZones = listOf(
        Zone(min = 0, max = 124),
        Zone(min = 125, max = 174),
        Zone(min = 175, max = 224),
        Zone(min = 225, max = 274),
        Zone(min = 275, max = 324),
        Zone(min = 325, max = 399),
        Zone(min = 400, max = 9999),
    )

    // Null, not a colour: the field then keeps Theme.textColor, which is black on a light
    // theme. Returning white here made coasting power invisible in day mode.
    @Test fun `null profile has no zone colour`() {
        assertNull(ZoneColors.color(ZoneKind.HR, 150.0, null))
    }

    @Test fun `value at or below zero has no zone colour`() {
        val p = profileWithZones(hrZones, powerZones)
        assertNull(ZoneColors.color(ZoneKind.POWER, 0.0, p))
        assertNull(ZoneColors.color(ZoneKind.POWER, -5.0, p))
    }

    @Test fun `profile without zones has no zone colour`() {
        val p = profileWithZones(emptyList(), emptyList())
        assertNull(ZoneColors.color(ZoneKind.HR, 150.0, p))
        assertNull(ZoneColors.color(ZoneKind.POWER, 200.0, p))
    }

    @Test fun `HR value in zone 1 returns Z1`() {
        val p = profileWithZones(hrZones, powerZones)
        assertEquals(z1, ZoneColors.color(ZoneKind.HR, 110.0, p))
    }

    @Test fun `HR value at zone 2 lower bound returns Z2`() {
        val p = profileWithZones(hrZones, powerZones)
        assertEquals(z2, ZoneColors.color(ZoneKind.HR, 120.0, p))
    }

    @Test fun `HR value at zone 5 upper bound returns Z5`() {
        val p = profileWithZones(hrZones, powerZones)
        assertEquals(hrZ5, ZoneColors.color(ZoneKind.HR, 220.0, p))
    }

    @Test fun `HR value above zone 5 max clamps to Z5`() {
        val p = profileWithZones(hrZones, powerZones)
        assertEquals(hrZ5, ZoneColors.color(ZoneKind.HR, 999.0, p))
    }

    @Test fun `Power value in zone 3 returns Z3`() {
        val p = profileWithZones(hrZones, powerZones)
        assertEquals(z3, ZoneColors.color(ZoneKind.POWER, 200.0, p))
    }

    @Test fun `power zones 5 to 7 each get their own color`() {
        val p = profileWithZones(hrZones, powerZones)
        assertEquals(pwrZ5, ZoneColors.color(ZoneKind.POWER, 300.0, p))
        assertEquals(pwrZ6, ZoneColors.color(ZoneKind.POWER, 350.0, p))
        assertEquals(pwrZ7, ZoneColors.color(ZoneKind.POWER, 500.0, p))
    }

    @Test fun `HR profile with more zones than colors clamps to the last one`() {
        val sevenHr = listOf(
            Zone(0, 99), Zone(100, 119), Zone(120, 139),
            Zone(140, 159), Zone(160, 179), Zone(180, 199), Zone(200, 220),
        )
        val p = profileWithZones(sevenHr, powerZones)
        assertEquals(hrZ5, ZoneColors.color(ZoneKind.HR, 210.0, p))
    }

    @Test fun `power scale is not capped at the HR scale length`() {
        val p = profileWithZones(hrZones, powerZones)
        // Would have been the HR Z5 red before the palettes were split.
        assertEquals(pwrZ7, ZoneColors.color(ZoneKind.POWER, 9000.0, p))
    }
}
