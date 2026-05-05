package io.example.bignum.render

import io.hammerhead.karooext.models.UserProfile

object ZoneColors {
    private val palette = intArrayOf(
        0xFF6B7280.toInt(),  // Z1 grey
        0xFF3B82F6.toInt(),  // Z2 blue
        0xFF10B981.toInt(),  // Z3 green
        0xFFF59E0B.toInt(),  // Z4 orange
        0xFFEF4444.toInt(),  // Z5 red
    )
    const val DEFAULT_TEXT: Int = 0xFFFFFFFF.toInt()

    fun color(kind: ZoneKind, value: Double, profile: UserProfile?): Int {
        if (profile == null || value <= 0) return DEFAULT_TEXT
        val zones = when (kind) {
            ZoneKind.HR -> profile.heartRateZones
            ZoneKind.POWER -> profile.powerZones
        }
        if (zones.isEmpty()) return DEFAULT_TEXT
        val v = value.toInt()
        val idx = zones.indexOfLast { v >= it.min }
            .let { if (it < 0) 0 else it }
            .coerceAtMost(palette.lastIndex)
        return palette[idx]
    }
}
