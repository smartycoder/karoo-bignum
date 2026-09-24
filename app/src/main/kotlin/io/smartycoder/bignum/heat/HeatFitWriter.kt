package io.smartycoder.bignum.heat

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.models.WriteToSessionMesg
import io.smartycoder.bignum.consumerFlow
import io.smartycoder.bignum.streamDataFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * Feeds [HeatFit]'s fields into the ride's FIT file, through karoo-ext's FIT effects.
 *
 * The Karoo calls [start] for each ride it records. Driven by the ride clock, as karoo-ext's own
 * sample is: the Karoo writes a record a second, and the clock ticks once for each.
 */
class HeatFitWriter(
    private val karoo: KarooSystemService,
    private val heat: HeatTracker,
) {
    fun start(emitter: Emitter<FitEffect>) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            val stats = HeatRideStats()
            var lastSeconds: Double? = null
            var lastSessionAt = 0.0

            fun writeSession() {
                val values = HeatFit.session(stats, heat.state.value)
                if (values.isNotEmpty()) emitter.onNext(WriteToSessionMesg(values))
            }

            combine(
                karoo.streamDataFlow(DataType.Type.ELAPSED_TIME)
                    .mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.singleValue?.div(1000) },
                karoo.consumerFlow<RideState>(),
            ) { seconds, ride -> seconds to ride }
                .collect { (seconds, ride) ->
                    when (ride) {
                        is RideState.Recording -> {
                            // The ride clock stands still through a pause, so the step is only
                            // ever the time recorded -- capped in case a sample went missing.
                            val step = lastSeconds?.let { (seconds - it).coerceIn(0.0, MAX_STEP_S) } ?: 0.0
                            lastSeconds = seconds
                            val state = heat.state.value
                            stats.add(state, step)
                            val record = HeatFit.record(state)
                            if (record.isNotEmpty()) emitter.onNext(WriteToRecordMesg(record))
                            // The session is written as it goes as well as at a pause: the Karoo
                            // keeps the last one it was given, and a ride can be ended without
                            // stopping on a pause long enough to write one.
                            if (seconds - lastSessionAt >= SESSION_EVERY_S) {
                                writeSession()
                                lastSessionAt = seconds
                            }
                        }
                        is RideState.Paused -> writeSession()
                        RideState.Idle -> Unit
                    }
                }
        }
    }

    private companion object {
        const val MAX_STEP_S = 5.0
        const val SESSION_EVERY_S = 60.0
    }
}
