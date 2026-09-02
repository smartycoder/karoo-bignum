package io.smartycoder.bignum.fields

import android.content.Context
import android.widget.RemoteViews
import io.smartycoder.bignum.R
import io.smartycoder.bignum.Settings
import io.smartycoder.bignum.Appearance
import io.smartycoder.bignum.format.RaisedTail
import io.smartycoder.bignum.ZoneColorMode
import io.smartycoder.bignum.consumerFlow
import io.smartycoder.bignum.render.FieldRenderer
import io.smartycoder.bignum.render.Theme
import io.smartycoder.bignum.render.ZoneBar
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    // Nullable only so ComputeTest can build a throwaway subclass in a plain JVM test: this
    // project has no Robolectric, so there is no live Context to hand a real KarooSystemService,
    // and compute() -- the one thing that test drives -- never touches this field anyway. Every
    // real field always constructs with a non-null instance; see the `!!` uses in frameFlow.
    private val karoo: KarooSystemService?,
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

    /**
     * The budget for one particular value, for a field whose values come in more than one shape.
     * A duration is the case that needs it: below an hour it is drawn as m:ss, and scaling that
     * against a h:mm:ss budget would give back the height dropping the hour just bought.
     */
    open fun widthBudget(text: String): String = widthTemplate

    /**
     * Which field of the data point carries the value, for a stream that ships more than one.
     * Null takes whatever [io.hammerhead.karooext.models.DataPoint.singleValue] returns.
     *
     * The navigation types need it: each ships its distance alongside NAVIGATION_STATE, ON_ROUTE
     * and REROUTING_ENABLED, and singleValue is `values.values.firstOrNull()` -- whichever of the
     * four the map happens to hold first. Named, a missing value renders "--" instead of a route
     * flag drawn as a distance.
     */
    open val valueField: String? = null

    /**
     * Whether the raised-tail setting applies to this field. Off for a wall clock: "14:35" would
     * split into a big "14" and a small "35", which is how a stopwatch reads, not a time of day.
     */
    open val raisedTailAllowed: Boolean = true
    abstract val zoneKind: ZoneKind?
    abstract val format: (Double, PreferredUnit?) -> Pair<String, String>
    open val previewValue: Double = 0.0

    /**
     * Whether test mode swaps in [previewValue]. Elapsed time opts out: the ride clock is the
     * one value that is real and moving with no sensor paired, so a frozen demo time there makes
     * a screenshot look broken rather than staged.
     */
    open val demoInTestMode: Boolean = true
    /** Rendered through [format] instead of "--" while no value is available. */
    open val missingValue: Double? = null
    protected open fun formatNeedsProfile(): Boolean = false

    /**
     * The value rendered, when it differs from the value the stream carries. Null means there is
     * nothing to show -- a field deriving W/kg from watts has no answer without a rider weight.
     */
    open fun displayValue(raw: Double, profile: UserProfile?): Double? = raw

    /** Wedge behind the number for this field's [raw] value. Null for every field but Grade. */
    open fun wedge(raw: Double): Wedge? = null

    final override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // We draw the icon and label ourselves, so Karoo's header would only duplicate them
        // and eat the top of the tile.
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        // Registered before launch: if setCancellable ran after and the emitter were torn down
        // in the gap, there would be no way to stop the collector this scope is about to start.
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            frameFlow(context, config.preview).collect { (frame, appearance) ->
                // A fresh RemoteViews per update, never a reused one: RemoteViews is an
                // append-only list of actions with no way to clear it, so reusing the instance
                // would retain every bitmap ever set and re-serialize the whole growing list on
                // each send -- ending in FAILED BINDER TRANSACTION or OOM after a long ride.
                val views = RemoteViews(context.packageName, R.layout.numeric_field)
                renderInto(context, views, config, frame, appearance)
                emitter.updateView(views)
            }
        }
    }

    /**
     * The value half of [startView]: today's sample plus the settings that decide how it is
     * drawn, collapsed into one (frame, appearance) update. Split out from the drawing half so
     * the HUD field can drive several of these at once, one per slot, without duplicating the
     * combine/compute wiring.
     */
    internal fun frameFlow(context: Context, preview: Boolean): Flow<Pair<Frame, Appearance>> {
        val needsProfile = zoneKind != null || formatNeedsProfile()
        val dataFlow = karoo!!.streamDataFlow(upstreamTypeId)
        val profileFlow = if (needsProfile) karoo.consumerFlow<UserProfile>() else flowOf<UserProfile?>(null)

        return combine(
            dataFlow,
            profileFlow,
            Settings.zoneColorModeFlow(context),
            Settings.testModeFlow(context),
            Settings.appearanceFlow(context),
        ) { state, profile, mode, testMode, appearance ->
            // Carried alongside the frame rather than inside it: appearance decides how the
            // value is drawn, not what the value is, and compute() returns from a dozen
            // places that have no business knowing about typefaces.
            compute(state, profile, preview, testMode, mode, Theme.textColor(context)) to appearance
        }
            // Karoo sends a sample whether or not the value moved, and most fields sit still
            // for long stretches -- an average, a maximum, a total, a temperature. Without
            // this each of those samples draws a bitmap identical to the one already on
            // screen and ships it across a process boundary to change nothing. Frame, Visual,
            // Wedge and Appearance are all data classes, so equality compares what is drawn.
            .distinctUntilChanged()
    }

    /**
     * The drawing half of [startView]: turns one (frame, appearance) update into the actions on
     * [views]. [inSlot] is true when this field is one tile among several sharing a single
     * `numeric_field.xml`, as the HUD field does -- see [FieldRenderer.render]'s `roundCorners`.
     */
    internal fun renderInto(
        context: Context,
        views: RemoteViews,
        config: ViewConfig,
        frame: Frame,
        appearance: Appearance,
        inSlot: Boolean = false,
        /** See [FieldRenderer.render]'s `iconOnlyHeader`; set by the HUD while its bar is up. */
        iconOnlyHeader: Boolean = false,
        /** See [FieldRenderer.render]'s `overlayTopPx`; the HUD's bar height while it is up. */
        overlayTopPx: Int = 0,
    ) {
        val visual = frame.visual
        val raised = appearance.raisedTail && raisedTailAllowed
        val (primary, secondary) = RaisedTail.split(visual.text, raised)
        val (tPrimary, tSecondary) = RaisedTail.template(widthBudget(visual.text), raised)
        FieldRenderer.render(
            context, views, config, label, iconRes,
            tPrimary, tSecondary, primary, secondary, visual.color, appearance.font,
            visual.background, frame.wedge,
            roundCorners = !inSlot,
            iconOnlyHeader = iconOnlyHeader,
            overlayTopPx = overlayTopPx,
        )
    }

    /**
     * What one update puts on screen. [background] is null unless the field is filled with its
     * zone colour, in which case [color] is the contrasting ink for that fill.
     */
    internal data class Visual(val text: String, val color: Int, val background: Int?)

    /** One update's worth of drawing: the number/fill and the wedge behind it, if any. */
    internal data class Frame(val visual: Visual, val wedge: Wedge?)

    /**
     * The number this field's stream is carrying, before [displayValue] and [format] have had
     * anything to say about it, or null when there is none.
     *
     * Its own function only so the zone bar can read it: the bar needs the raw value to place
     * itself on the zone scale, and it must resolve preview and test mode exactly the way the
     * number beside it does, or a screenshot would show a demo number over a live bar.
     */
    internal fun rawValue(state: StreamState, preview: Boolean, testMode: Boolean): Double? = when {
        // Ahead of the stream, unlike preview: with a Karoo sitting idle a live 0 and a
        // demo 0 look the same, so test mode has to win even while data is arriving.
        // Page editing keeps deferring to real data when there is any.
        testMode && demoInTestMode -> previewValue
        preview && state !is StreamState.Streaming -> previewValue
        state is StreamState.Streaming -> state.dataPoint.let { point ->
            valueField?.let { point.values[it] } ?: point.singleValue
        }
        else -> null
    }

    /**
     * [raw] as this field would print it, or null when the field has nothing to print.
     *
     * Both the number and the bar go through here so they cannot disagree about what an
     * unrenderable reading means. Null is a real answer and not an error: a field deriving W/kg
     * from watts has none without a rider weight, and the two callers then do the same thing for
     * their own medium -- the number draws "--", the bar hides.
     */
    internal fun formattedDisplay(raw: Double, profile: UserProfile?): String? =
        displayValue(raw, profile)?.let { format(it, profile?.preferredUnit).first }

    internal fun compute(
        state: StreamState,
        profile: UserProfile?,
        preview: Boolean,
        testMode: Boolean,
        mode: ZoneColorMode,
        defaultColor: Int,
    ): Frame {
        val raw = rawValue(state, preview, testMode)
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
        val text = formattedDisplay(raw, profile)
            ?: return Frame(Visual("--", defaultColor, null), null)
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

    /** One update of the HUD's zone bar: how full it is, its colour, and the value on it. */
    internal data class BarFrame(val fraction: Float, val color: Int, val text: String)

    /**
     * This field seen as the HUD's zone bar, or null whenever there is nothing honest to draw:
     * the field has no zones at all, the rider's profile carries none, or no value has arrived.
     *
     * Null hides the bar rather than drawing an empty track, and the tile gives the height back
     * to the two numbers. An empty track would be a promise of information that is not coming --
     * a rider with no FTP set would see a bar that never moves for the whole ride.
     *
     * Deliberately NOT built on [frameFlow]: that one carries a formatted string and a colour
     * chosen for a number, and it is shared with the two slots, where an extra field on [Frame]
     * would make them redraw on value changes their own text does not show. This reads the same
     * two streams for its own three numbers instead.
     */
    internal fun barFlow(context: Context, preview: Boolean): Flow<BarFrame?> {
        val kind = zoneKind ?: return flowOf(null)
        // Elvis, not `!!`, and for the reason HudField spells out at length: this flow is built
        // eagerly inside HudField's flatMapLatest, so a throw here lands in scope.launch, where
        // SupervisorJob does NOT catch it -- it reaches the default handler and takes the whole
        // extension down mid-ride. Every real field has a KarooSystemService; the one that does
        // not is ComputeTest's stand-in, and it should get no bar rather than a crash.
        val karoo = karoo ?: return flowOf(null)
        return combine(
            karoo.streamDataFlow(upstreamTypeId),
            karoo.consumerFlow<UserProfile>(),
            Settings.testModeFlow(context),
            Settings.zoneColorModeFlow(context),
        ) { state, profile, testMode, mode ->
            barFrame(rawValue(state, preview, testMode), profile, mode)
        }.distinctUntilChanged()
    }

    /**
     * One bar update from a raw reading, or null when there is no bar to draw.
     *
     * Pure over its three arguments so the decision below can be tested; [barFlow] is the thin
     * wrapper that feeds it.
     *
     * WHAT DECIDES NULL IS THE ZONE LIST, NOT THE ZONE COLOUR, and the difference is the whole
     * point. [ZoneColors.color] returns null for any non-positive value and for anything under
     * the first zone's floor -- both of which are ordinary readings: power is 0 at every stop
     * light, on every coast and most of every descent. Keying the bar's existence on the colour
     * made all of those look like missing data, and because a hidden bar also gives the two
     * halves their labels back and re-measures both numbers, the tile visibly reflowed several
     * times a minute. A zero belongs at the left end of the bar, not off it.
     *
     * [ZoneColorMode.OFF] hides the bar outright: a rider who turned zone colours off is not
     * asking for the one thing on the tile that is nothing but a zone colour.
     */
    internal fun barFrame(raw: Double?, profile: UserProfile?, mode: ZoneColorMode): BarFrame? {
        val kind = zoneKind ?: return null
        if (raw == null || mode == ZoneColorMode.OFF) return null
        // raw, not the displayed value, on both counts: zones are defined on what the stream
        // carries, exactly as compute() has it, and a field showing W/kg is still placed on
        // the bar by the watts behind it.
        val zones = ZoneColors.zones(kind, profile)
        // No zones is the case the bar genuinely cannot draw: an empty track that never moves
        // for a whole ride promises information that is not coming.
        if (zones.isEmpty()) return null
        return BarFrame(
            fraction = ZoneBar.fraction(raw, zones),
            // Below the first zone there is no colour to look up, so the bar takes zone 1's --
            // the fill is zero-width there anyway, and the colour is what the icon and the value
            // are contrasted against.
            color = ZoneColors.color(kind, raw, profile) ?: ZoneColors.baseColor(kind),
            // The value is drawn the way the field itself would draw it, so a bar over a
            // slot showing the same source never disagrees with it by a digit -- including when
            // there is nothing to draw. Falling back to the raw reading here instead would put
            // watts on a bar wearing a W/kg icon while the slot beside it said "--".
            text = formattedDisplay(raw, profile) ?: return null,
        )
    }

    /**
     * What this field draws with no value at all -- the same path compute() takes for absent
     * data. A composite field like HudField needs to draw a slot before that slot's stream has
     * produced anything, and it must draw what the field itself would draw (a formatted
     * missingValue such as "0.0" for speed/power/time, or "--" where no missingValue is
     * defined), not a stand-in that only some fields would actually show.
     */
    internal fun missingFrame(context: Context): Frame = compute(
        StreamState.Idle,
        profile = null,
        preview = false,
        testMode = false,
        mode = ZoneColorMode.OFF,
        defaultColor = Theme.textColor(context),
    )
}
