package io.example.bignum.render

import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.UserProfile.Zone
import org.junit.Assert.assertEquals
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

    private val whiteARGB = 0xFFFFFFFF.toInt()
    private val z1 = 0xFF6B7280.toInt()
    private val z2 = 0xFF3B82F6.toInt()
    private val z3 = 0xFF10B981.toInt()
    private val z4 = 0xFFF59E0B.toInt()
    private val z5 = 0xFFEF4444.toInt()

    private val hrZones = listOf(
        Zone(min = 100, max = 119),
        Zone(min = 120, max = 139),
        Zone(min = 140, max = 159),
        Zone(min = 160, max = 179),
        Zone(min = 180, max = 220),
    )

    private val powerZones = listOf(
        Zone(min = 0, max = 124),
        Zone(min = 125, max = 174),
        Zone(min = 175, max = 224),
        Zone(min = 225, max = 274),
        Zone(min = 275, max = 9999),
    )

    @Test fun `null profile returns default white`() {
        assertEquals(whiteARGB, ZoneColors.color(ZoneKind.HR, 150.0, null))
    }

    @Test fun `value at or below zero returns default white`() {
        val p = profileWithZones(hrZones, powerZones)
        assertEquals(whiteARGB, ZoneColors.color(ZoneKind.POWER, 0.0, p))
        assertEquals(whiteARGB, ZoneColors.color(ZoneKind.POWER, -5.0, p))
    }

    @Test fun `HR value in zone 1 returns Z1 grey`() {
        val p = profileWithZones(hrZones, powerZones)
        assertEquals(z1, ZoneColors.color(ZoneKind.HR, 110.0, p))
    }

    @Test fun `HR value at zone 2 lower bound returns Z2`() {
        val p = profileWithZones(hrZones, powerZones)
        assertEquals(z2, ZoneColors.color(ZoneKind.HR, 120.0, p))
    }

    @Test fun `HR value at zone 5 upper bound returns Z5`() {
        val p = profileWithZones(hrZones, powerZones)
        assertEquals(z5, ZoneColors.color(ZoneKind.HR, 220.0, p))
    }

    @Test fun `HR value above zone 5 max clamps to Z5`() {
        val p = profileWithZones(hrZones, powerZones)
        assertEquals(z5, ZoneColors.color(ZoneKind.HR, 999.0, p))
    }

    @Test fun `Power value in zone 3 returns Z3`() {
        val p = profileWithZones(hrZones, powerZones)
        assertEquals(z3, ZoneColors.color(ZoneKind.POWER, 200.0, p))
    }

    @Test fun `user with seven zones — only first five colors used, rest clamp to Z5`() {
        val sevenHr = listOf(
            Zone(0, 99), Zone(100, 119), Zone(120, 139),
            Zone(140, 159), Zone(160, 179), Zone(180, 199), Zone(200, 220),
        )
        val p = profileWithZones(sevenHr, powerZones)
        assertEquals(z5, ZoneColors.color(ZoneKind.HR, 210.0, p))
    }
}
