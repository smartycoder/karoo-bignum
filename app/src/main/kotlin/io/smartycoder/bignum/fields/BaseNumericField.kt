package io.smartycoder.bignum.fields

import android.content.Context
import android.widget.RemoteViews
import io.smartycoder.bignum.R
import io.smartycoder.bignum.Settings
import io.smartycoder.bignum.ZoneColorMode
import io.smartycoder.bignum.consumerFlow
import io.smartycoder.bignum.render.FieldRenderer
import io.smartycoder.bignum.render.Theme
import io.smartycoder.bignum.render.ZoneColors
import io.smartycoder.bignum.render.ZoneKind
import io.smartycoder.bignum.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
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

/**
 * A wedge behind the number: how far up the tile it reaches, its colour, and which way it runs --
 * rising for a climb, falling for a descent.
 */
data class Wedge(val fraction: Float, val color: Int, val rising: Boolean)

abstract class BaseNumericField(
    extension: String,
    typeId: String,
    private val karoo: KarooSystemService,
) : DataTypeImpl(extension, typeId) {

    abstract val upstreamTypeId: String

    /** Short header text, drawn by us -- Karoo's own header shows the uppercased displayName. */
    abstract val label: String

    /** Header icon, drawn by us and tinted; see [io.smartycoder.bignum.render.FieldRenderer]. */
    abstract val iconRes: Int

    /**
     * Widest value this field renders at full size. Text size is derived from it rather than
     * from the current value, so the number does not resize as digits come and go; anything
     * wider still shrinks to fit.
     */
    open val widthTemplate: String = FieldRenderer.DEFAULT_WIDTH_TEMPLATE
    abstract val zoneKind: ZoneKind?
    abstract val format: (Double, PreferredUnit?) -> Pair<String, String>
    open val previewValue: Double = 0.0
    /** Rendered through [format] instead of "--" while no value is available. */
    open val missingValue: Double? = null
    protected open fun formatNeedsProfile(): Boolean = false

    /**
     * Splits formatted text into the part drawn at full size and a trailing part drawn
     * smaller and raised, the way a Wahoo shows the seconds of a ride time. Default: no
     * split.
     *
     * The same rule is applied to [widthTemplate], so the scale a field renders at follows
     * from one definition instead of a second template kept in step by hand.
     */
    open fun split(text: String): Pair<String, String> = text to ""

    /**
     * The value rendered, when it differs from the value the stream carries. Null means there is
     * nothing to show -- a field deriving W/kg from watts has no answer without a rider weight.
     */
    open fun displayValue(raw: Double, profile: UserProfile?): Double? = raw

    /** Wedge behind the number for this field's [raw] value. Null for every field but Grade. */
    open fun wedge(raw: Double): Wedge? = null

    final override fun startView(
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter,
    ) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // We draw the icon and label ourselves, so Karoo's header would only duplicate them
        // and eat the top of the tile.
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        val needsProfile = zoneKind != null || formatNeedsProfile()
        val dataFlow = karoo.streamDataFlow(upstreamTypeId)
        val profileFlow = if (needsProfile) karoo.consumerFlow<UserProfile>() else flowOf<UserProfile?>(null)

        scope.launch {
            combine(
                dataFlow,
                profileFlow,
                Settings.zoneColorModeFlow(context),
                Settings.testModeFlow(context),
            ) { state, profile, mode, testMode ->
                compute(state, profile, config.preview, testMode, mode, Theme.textColor(context))
            }.collect { frame ->
                // A fresh RemoteViews per update, never a reused one: RemoteViews is an
                // append-only list of actions with no way to clear it, so reusing the instance
                // would retain every bitmap ever set and re-serialize the whole growing list on
                // each send -- ending in FAILED BINDER TRANSACTION or OOM after a long ride.
                val views = RemoteViews(context.packageName, R.layout.numeric_field)
                val visual = frame.visual
                val (primary, secondary) = split(visual.text)
                val (tPrimary, tSecondary) = split(widthTemplate)
                FieldRenderer.render(
                    context, views, config, label, iconRes,
                    tPrimary, tSecondary, primary, secondary, visual.color, visual.background,
                    frame.wedge,
                )
                emitter.updateView(views)
            }
        }
        emitter.setCancellable { scope.cancel() }
    }

    /**
     * What one update puts on screen. [background] is null unless the field is filled with its
     * zone colour, in which case [color] is the contrasting ink for that fill.
     */
    private data class Visual(val text: String, val color: Int, val background: Int?)

    /** One update's worth of drawing: the number/fill and the wedge behind it, if any. */
    private data class Frame(val visual: Visual, val wedge: Wedge?)

    private fun compute(
        state: StreamState,
        profile: UserProfile?,
        preview: Boolean,
        testMode: Boolean,
        mode: ZoneColorMode,
        defaultColor: Int,
    ): Frame {
        val raw: Double? = when {
            // Ahead of the stream, unlike preview: with a Karoo sitting idle a live 0 and a
            // demo 0 look the same, so test mode has to win even while data is arriving.
            // Page editing keeps deferring to real data when there is any.
            testMode -> previewValue
            preview && state !is StreamState.Streaming -> previewValue
            state is StreamState.Streaming -> state.dataPoint.singleValue
            else -> null
        }
        if (raw == null) {
            // No zone applies to a missing value, so no fill either -- an empty field should not
            // sit there in a colour that says something about data it does not have. The fallback
            // still goes through displayValue: a field deriving W/kg from watts must not print it
            // as raw watts just because this path is shorter. No wedge either: there is no raw
            // value for it to be drawn from.
            val fallback = missingValue?.let { displayValue(it, profile) }
                ?: return Frame(Visual("--", defaultColor, null), null)
            return Frame(Visual(format(fallback, profile?.preferredUnit).first, defaultColor, null), null)
        }
        val display = displayValue(raw, profile) ?: return Frame(Visual("--", defaultColor, null), null)
        val text = format(display, profile?.preferredUnit).first
        // A rider who turned zone colours off probably means everywhere, including the wedge.
        val wedgeValue = if (mode != ZoneColorMode.OFF) wedge(raw) else null
        val zone = zoneKind
            ?.takeIf { mode != ZoneColorMode.OFF }
            // raw, not display: zones are defined on the value the stream carries. A field that
            // shows W/kg derived from watts still has its zone decided by those watts.
            ?.let { ZoneColors.color(it, raw, profile) }
            ?: return Frame(Visual(text, defaultColor, null), wedgeValue)
        return when (mode) {
            ZoneColorMode.FILL -> Frame(Visual(text, ZoneColors.onColor(zone), zone), wedgeValue)
            else -> Frame(Visual(text, zone, null), wedgeValue)
        }
    }
}
