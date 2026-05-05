package io.example.bignum.fields

import android.content.Context
import android.widget.RemoteViews
import io.example.bignum.R
import io.example.bignum.consumerFlow
import io.example.bignum.render.FieldRenderer
import io.example.bignum.render.ZoneColors
import io.example.bignum.render.ZoneKind
import io.example.bignum.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

abstract class BaseNumericField(
    extension: String,
    typeId: String,
    private val karoo: KarooSystemService,
) : DataTypeImpl(extension, typeId) {

    abstract val upstreamTypeId: String
    abstract val zoneKind: ZoneKind?
    abstract val format: (Double, PreferredUnit?) -> Pair<String, String>
    open val previewValue: Double = 0.0
    /** When true, render as the formatter's output for 0.0 instead of "--" while no value is available. */
    open val zeroWhenMissing: Boolean = false
    protected open fun formatNeedsProfile(): Boolean = false

    final override fun startView(
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter,
    ) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val views = RemoteViews(context.packageName, R.layout.numeric_field)
        FieldRenderer.applyConfig(views, config)

        val needsProfile = zoneKind != null || formatNeedsProfile()
        val dataFlow = karoo.streamDataFlow(upstreamTypeId)
        val profileFlow = if (needsProfile) karoo.consumerFlow<UserProfile>() else flowOf<UserProfile?>(null)

        scope.launch {
            combine(dataFlow, profileFlow) { state, profile ->
                compute(state, profile, config.preview)
            }.collect { (text, unit, color) ->
                FieldRenderer.fill(views, text, unit, color)
                emitter.updateView(views)
            }
        }
        emitter.setCancellable { scope.cancel() }
    }

    private fun compute(
        state: StreamState,
        profile: UserProfile?,
        preview: Boolean,
    ): Triple<String, String, Int> {
        val raw: Double? = when {
            preview && state !is StreamState.Streaming -> previewValue
            state is StreamState.Streaming -> state.dataPoint.singleValue
            else -> null
        }
        if (raw == null) {
            return if (zeroWhenMissing) {
                val (text, unit) = format(0.0, profile?.preferredUnit)
                Triple(text, unit, ZoneColors.DEFAULT_TEXT)
            } else {
                Triple("--", "", ZoneColors.DEFAULT_TEXT)
            }
        }
        val (text, unit) = format(raw, profile?.preferredUnit)
        val color = zoneKind?.let { ZoneColors.color(it, raw, profile) } ?: ZoneColors.DEFAULT_TEXT
        return Triple(text, unit, color)
    }
}
