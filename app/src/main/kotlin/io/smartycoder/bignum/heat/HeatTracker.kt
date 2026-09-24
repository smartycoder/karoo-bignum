package io.smartycoder.bignum.heat

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.smartycoder.bignum.Settings
import io.smartycoder.bignum.consumerFlow
import io.smartycoder.bignum.streamDataFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the heat fields draw: the live index, and today's load and score. */
data class HeatState(
    /** Heat Strain Index, or null without both of the CORE sensor's temperatures. */
    val hsi: Double?,
    val load: Double,
    val adaptation: Double,
    /** Whether [hsi] is the sensor's own rather than BigNum's estimate. */
    val hsiFromSensor: Boolean = false,
) {
    companion object {
        val EMPTY = HeatState(hsi = null, load = 0.0, adaptation = 0.0)
    }
}

/**
 * Keeps the Heat Strain Index, the day's Heat Training Load and the Heat Adaptation Score that
 * builds on it.
 *
 * The index is the sensor's own whenever [CoreSensorLink] has a confirmed connection to it, and
 * otherwise estimated from the two temperatures the Karoo does pass on; see [HeatStrain].
 *
 * One per extension, started with it, rather than work done inside a field: the load has to
 * accumulate whether or not a heat field is on the page being looked at -- a field's view only
 * runs while it is on screen -- and two copies of a field must not count the same minute twice.
 *
 * Load only accumulates while a ride is recording with a heart rate coming in. That is CORE's
 * own rule -- "without heart rate data, your training session will not contribute" -- and it keeps
 * a sensor worn around the house out of the score.
 */
class HeatTracker(
    context: Context,
    private val karoo: KarooSystemService,
) {
    // Lazy, and the context is the application's: the settings screen builds one of these only
    // to list the fields' labels, and should not touch the disk to do it.
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _state = MutableStateFlow(HeatState.EMPTY)
    val state: StateFlow<HeatState> = _state.asStateFlow()

    private var scope: CoroutineScope? = null

    /** Written from the tick loop, read by [stop] for a last save. */
    @Volatile private var day: HeatDay? = null

    // Lazy for the same reason as prefs: built only once the tracker runs.
    private val link by lazy { CoreSensorLink(appContext) }

    /** The Karoo's readings this tracker works from. */
    private data class Inputs(val core: Double?, val skin: Double?, val heartRate: Boolean, val recording: Boolean) {
        companion object {
            val NONE = Inputs(core = null, skin = null, heartRate = false, recording = false)
        }
    }

    fun start() {
        if (scope != null) return
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        this.scope = scope
        scope.launch { track() }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        day?.let { save(it) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun track() = coroutineScope {
        // Each stream starts from "nothing" so the combine does not wait on a sensor that is not
        // paired, and a missing heart rate or ride state reads as "does not count".
        val inputs = combine(
            karoo.streamDataFlow(DataType.Type.CORE_TEMP).map { it.field(DataType.Field.CORE_TEMP) }.onStart { emit(null) },
            karoo.streamDataFlow(DataType.Type.SKIN_TEMP).map { it.field(DataType.Field.SKIN_TEMP) }.onStart { emit(null) },
            karoo.streamDataFlow(DataType.Type.HEART_RATE).map { (it.single() ?: 0.0) > 0 }.onStart { emit(false) },
            karoo.consumerFlow<RideState>().map { it is RideState.Recording }.onStart { emit(false) },
        ) { core, skin, heartRate, recording ->
            Inputs(core, skin, heartRate, recording)
        }.stateIn(this, SharingStarted.Eagerly, Inputs.NONE)

        // The sensor's own index when the link has one, the estimate otherwise -- including in
        // the seconds a dropped connection takes to come back.
        // Paired with where it came from, which the FIT file keeps apart; see HeatFit.
        val hsi = combine(inputs, link.hsi) { input, sensor ->
            sensor?.let { it to true } ?: (HeatStrain.index(input.core, input.skin) to false)
        }.stateIn(this, SharingStarted.Eagerly, null to false)

        var current = load().rolledTo(today())
        day = current
        save(current)
        publish(current)

        // The index follows every sample; only the load waits for the tick below.
        launch {
            hsi.collect { (value, fromSensor) -> _state.update { it.copy(hsi = value, hsiFromSensor = fromSensor) } }
        }

        // The link runs while the Karoo is reading a CORE -- there is a sensor to find, and the
        // Karoo's reading of it is how the right one is recognised -- and holds on through a
        // short gap in that reading rather than dropping a good connection over it.
        launch {
            val karooTemps = inputs.map { KarooBodyTemps(it.core, it.skin) }
                .stateIn(this, SharingStarted.Eagerly, KarooBodyTemps(null, null))
            val karooHasCore = inputs.map { it.core != null }
                .distinctUntilChanged()
                .mapLatest { present ->
                    if (!present) delay(LINK_LINGER_MS)
                    present
                }
                .distinctUntilChanged()
            combine(karooHasCore, Settings.coreSensorLinkFlow(appContext)) { present, enabled -> present to enabled }
                .distinctUntilChanged()
                .collectLatest { (present, enabled) ->
                    when {
                        !enabled -> CoreSensorLink.setState(CoreLinkState.OFF)
                        !present -> CoreSensorLink.setState(CoreLinkState.WAITING)
                        else -> link.run(karooTemps)
                    }
                }
        }

        var last = SystemClock.elapsedRealtime()
        var lastSaved = last
        var dirty = false
        while (isActive) {
            delay(TICK_MS)
            val now = SystemClock.elapsedRealtime()
            // Capped, so a stretch where this process was not running -- or the Karoo slept --
            // is not credited as time spent at whatever the index was before it.
            val hours = (now - last).coerceAtMost(MAX_GAP_MS) / 3_600_000.0
            last = now

            val rolled = current.rolledTo(today())
            if (rolled != current) {
                current = rolled
                dirty = true
            }
            val input = inputs.value
            // Recording with a heart rate only; see the class comment.
            val index = hsi.value.first
            if (index != null && input.heartRate && input.recording) {
                current = current.accumulate(index, hours)
                dirty = true
            }
            day = current
            // Saved every so often while it moves rather than every tick, and straight away once
            // the ride stops, so a finished ride is on disk before the rider switches off.
            if (dirty && (now - lastSaved >= SAVE_MS || !input.recording)) {
                save(current)
                lastSaved = now
                dirty = false
            }
            publish(current)
        }
    }

    private fun publish(day: HeatDay) {
        _state.update { it.copy(load = day.load, adaptation = day.adaptation) }
    }

    private fun today(): Long = LocalDate.now().toEpochDay()

    private fun load(): HeatDay {
        if (!prefs.contains(KEY_DAY)) return HeatDay.fresh(today())
        return HeatDay(
            epochDay = prefs.getLong(KEY_DAY, today()),
            lowLoad = prefs.getFloat(KEY_LOW, 0f).toDouble(),
            highLoad = prefs.getFloat(KEY_HIGH, 0f).toDouble(),
            before = HeatAdaptation.Day(
                score = prefs.getFloat(KEY_SCORE, 0f).toDouble(),
                restStreak = prefs.getInt(KEY_STREAK, 0),
            ),
        )
    }

    private fun save(day: HeatDay) {
        prefs.edit()
            .putLong(KEY_DAY, day.epochDay)
            .putFloat(KEY_LOW, day.lowLoad.toFloat())
            .putFloat(KEY_HIGH, day.highLoad.toFloat())
            .putFloat(KEY_SCORE, day.before.score.toFloat())
            .putInt(KEY_STREAK, day.before.restStreak)
            .apply()
    }

    private companion object {
        // Its own file, not the settings one: Settings listens for changes to its file, and a
        // save here every half minute of a ride would wake every field for nothing.
        const val PREFS = "bignum_heat"
        const val KEY_DAY = "day"
        const val KEY_LOW = "load_low"
        const val KEY_HIGH = "load_high"
        const val KEY_SCORE = "score_before"
        const val KEY_STREAK = "rest_streak_before"

        const val LINK_LINGER_MS = 60_000L
        const val TICK_MS = 5_000L
        const val MAX_GAP_MS = 15_000L
        const val SAVE_MS = 30_000L
    }
}

/** The named field of a streaming data point, or null. */
private fun StreamState.field(name: String): Double? =
    (this as? StreamState.Streaming)?.dataPoint?.values?.get(name)

private fun StreamState.single(): Double? =
    (this as? StreamState.Streaming)?.dataPoint?.singleValue
