package io.smartycoder.bignum.heat

import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import kotlin.math.max
import kotlin.math.min

/**
 * The heat data BigNum adds to the ride's FIT file: what the Karoo does not write itself.
 *
 * The Karoo already records a CORE's core and skin temperature, so those are left alone. What it
 * cannot record is anything it never receives: the Heat Strain Index, and everything BigNum
 * builds on it.
 *
 * CORE's connectivity notes name the record field for the index -- `heat_strain_index`, a float
 * in "a.u." -- and ask for exactly that name, so analysis tools that know CORE find it. That
 * field only ever carries the sensor's own value. When the index on screen is BigNum's estimate
 * it goes in a field of its own, so no tool mistakes it for CORE's.
 */
object HeatFit {

    // FIT base type ids, from the FIT SDK.
    private const val UINT8: Short = 2
    private const val UINT32: Short = 134
    private const val FLOAT32: Short = 136

    // Record fields, written each second while recording.
    val HEAT_STRAIN_INDEX = DeveloperField(0, FLOAT32, "heat_strain_index", "a.u.")
    val ESTIMATED_HEAT_STRAIN_INDEX = DeveloperField(1, FLOAT32, "estimated_heat_strain_index", "a.u.")
    val HEAT_ZONE = DeveloperField(2, UINT8, "heat_zone", "zone")

    // Session fields: the ride's summary.
    val HEAT_TRAINING_LOAD = DeveloperField(3, FLOAT32, "heat_training_load", "a.u.")
    val HEAT_ADAPTATION_SCORE = DeveloperField(4, FLOAT32, "heat_adaptation_score", "%")
    val AVG_HEAT_STRAIN_INDEX = DeveloperField(5, FLOAT32, "avg_heat_strain_index", "a.u.")
    val MAX_HEAT_STRAIN_INDEX = DeveloperField(6, FLOAT32, "max_heat_strain_index", "a.u.")
    val TIME_IN_HEAT_ZONE = List(4) { i ->
        DeveloperField((7 + i).toShort(), UINT32, "time_in_heat_zone_${i + 1}", "s")
    }

    /** Every field, for the test that holds their numbers sequential and unique, as FIT needs. */
    internal val ALL = listOf(
        HEAT_STRAIN_INDEX, ESTIMATED_HEAT_STRAIN_INDEX, HEAT_ZONE,
        HEAT_TRAINING_LOAD, HEAT_ADAPTATION_SCORE, AVG_HEAT_STRAIN_INDEX, MAX_HEAT_STRAIN_INDEX,
    ) + TIME_IN_HEAT_ZONE

    /** This second's record values; empty with no index, so nothing is written for it. */
    fun record(state: HeatState): List<FieldValue> {
        val hsi = state.hsi ?: return emptyList()
        val index = if (state.hsiFromSensor) HEAT_STRAIN_INDEX else ESTIMATED_HEAT_STRAIN_INDEX
        return listOf(
            FieldValue(index, hsi),
            FieldValue(HEAT_ZONE, HeatStrain.zone(hsi).toDouble()),
        )
    }

    /** The session summary so far; empty for a ride with no index at all -- one without a CORE. */
    fun session(stats: HeatRideStats, state: HeatState): List<FieldValue> {
        if (!stats.hasHeat) return emptyList()
        return listOf(
            FieldValue(HEAT_TRAINING_LOAD, stats.load),
            FieldValue(HEAT_ADAPTATION_SCORE, state.adaptation),
            FieldValue(AVG_HEAT_STRAIN_INDEX, stats.averageHsi),
            FieldValue(MAX_HEAT_STRAIN_INDEX, stats.maxHsi),
        ) + TIME_IN_HEAT_ZONE.mapIndexed { i, field -> FieldValue(field, stats.zoneSeconds[i]) }
    }
}

/**
 * One ride's heat summary, built a second at a time while it records.
 *
 * The load is the ride's own: the tracker keeps the day's total, which may already hold a
 * morning session, so this adds up only what the total gained during the ride -- and starts
 * over from the new day's total if the ride runs past midnight.
 */
class HeatRideStats {

    private var hsiSeconds = 0.0
    private var weightedHsi = 0.0
    private var lastDayLoad: Double? = null

    var maxHsi = 0.0
        private set
    var load = 0.0
        private set
    val zoneSeconds = DoubleArray(4)

    /** Whether the ride has had an index at all. */
    val hasHeat: Boolean get() = hsiSeconds > 0

    val averageHsi: Double get() = if (hsiSeconds > 0) weightedHsi / hsiSeconds else 0.0

    /** [seconds] more of the ride, spent in [state]. */
    fun add(state: HeatState, seconds: Double) {
        val previous = lastDayLoad
        if (previous != null) {
            val gained = if (state.load >= previous) state.load - previous else state.load
            load = min(HeatTrainingLoad.MAX, load + gained)
        }
        lastDayLoad = state.load

        val hsi = state.hsi ?: return
        if (!(seconds > 0)) return
        hsiSeconds += seconds
        weightedHsi += hsi * seconds
        maxHsi = max(maxHsi, hsi)
        zoneSeconds[HeatStrain.zone(hsi) - 1] += seconds
    }
}
