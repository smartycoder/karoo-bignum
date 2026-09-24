package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.heat.HeatState
import io.smartycoder.bignum.heat.HeatTracker
import io.smartycoder.bignum.render.FieldRenderer
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A field whose value BigNum works out itself from the CORE sensor, rather than one the Karoo
 * already streams: Heat Strain Index, Heat Zone, Heat Training Load and Heat Adaptation Score.
 *
 * Each one is also a proper karoo-ext data type: [startStream] publishes the value under this
 * extension's own type id, so anything on the Karoo that asks for the stream gets it. The field's
 * own view reads the same value in-process through [sourceFlow] rather than asking the Karoo for
 * that stream back -- one hop instead of two, and nothing that depends on the Karoo routing an
 * extension's stream back to the extension that makes it.
 */
class HeatField(
    extension: String,
    typeId: String,
    karoo: KarooSystemService,
    private val heat: HeatTracker,
    override val label: String,
    override val format: (Double, PreferredUnit?) -> Pair<String, String>,
    /** The number out of the tracker's state, or null when there is none to show. */
    private val read: (HeatState) -> Double?,
    /** Colour for a value; see BaseNumericField.bandColor. */
    private val bands: ((Double) -> Int?)? = null,
    override val previewValue: Double = 0.0,
    override val widthTemplate: String = FieldRenderer.DEFAULT_WIDTH_TEMPLATE,
) : BaseNumericField(extension, typeId, karoo) {
    override val upstreamTypeId = DataType.dataTypeId(extension, typeId)
    override val iconRes = R.drawable.ic_temp
    override val zoneKind = null

    override fun bandColor(raw: Double): Int? = bands?.invoke(raw)

    override fun sourceFlow(): Flow<StreamState> = heat.state
        .map { read(it) }
        .distinctUntilChanged()
        .map { value ->
            // NotAvailable, not Idle: a missing index means no CORE sensor, the same answer the
            // Karoo gives for any sensor field with nothing paired.
            value?.let { StreamState.Streaming(DataPoint(upstreamTypeId, mapOf(DataType.Field.SINGLE to it))) }
                ?: StreamState.NotAvailable
        }

    override fun startStream(emitter: Emitter<StreamState>) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        emitter.setCancellable { scope.cancel() }
        scope.launch { sourceFlow().collect { emitter.onNext(it) } }
    }
}
