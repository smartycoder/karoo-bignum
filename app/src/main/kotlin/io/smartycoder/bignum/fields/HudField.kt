package io.smartycoder.bignum.fields

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import io.smartycoder.bignum.Appearance
import io.smartycoder.bignum.BuildConfig
import io.smartycoder.bignum.FontSetting
import io.smartycoder.bignum.R
import io.smartycoder.bignum.Settings
import io.smartycoder.bignum.ZonePillStyle
import io.smartycoder.bignum.render.FieldRenderer
import io.smartycoder.bignum.render.Theme
import io.smartycoder.bignum.render.ZoneBar
import io.smartycoder.bignum.render.renderZonePill
import io.smartycoder.bignum.render.zonePillWidth
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

private const val TAG = "BigNumHud"

/**
 * How long a failed slot stream waits before it is resubscribed. Long enough that a sensor
 * dropping out mid-ride does not turn into a resubscribe loop burning battery, short enough
 * that the half comes back on its own without the rider touching the page.
 */
private const val SLOT_RETRY_DELAY_MS = 2000L

/**
 * How long to hold the collector after drawing, matching the rate ViewEmitter itself allows.
 *
 * ViewEmitter.updateView drops -- does not queue -- any view handed to it within 900ms of the
 * previous one, so a field can never draw faster than this anyway. Pacing here just means the
 * updates that would have been thrown away are never rendered in the first place, and the one
 * that follows carries the newest state instead of being lost. 1000ms rather than 900 so a
 * little scheduling jitter cannot land us back inside the window.
 */
private const val EMITTER_WINDOW_MS = 1000L

/**
 * The composite field: two entries from [FieldCatalog], each drawn as a whole BigNum field in
 * its own half of one tile, under an optional zone bar across the top.
 *
 * Deliberately a [DataTypeImpl] rather than a [BaseNumericField]: it has no stream, no format
 * and no zone of its own, and it composes fields rather than being one. That also means a HUD
 * can never end up nested inside a HUD -- it is not in the catalogue the slots are picked from.
 * It holds no KarooSystemService either: it never streams anything directly, it only collects
 * flows the catalogue's fields already know how to build -- the bar included, which is one more
 * catalogue field read a second way rather than a stream of the tile's own.
 */
class HudField(
    extension: String,
    private val catalog: List<BaseNumericField>,
) : DataTypeImpl(extension, "hud") {

    /** One half of the tile: which field is in the slot, and the update it is drawn from. */
    private data class Slot(
        val field: BaseNumericField,
        val frame: BaseNumericField.Frame,
        val appearance: Appearance,
    )

    /** One pass through the combine below: both halves, and the bar if one is configured. */
    private data class Tick(
        val left: Slot,
        val right: Slot,
        val bar: BaseNumericField.BarFrame?,
        val barIcon: Int?,
        val style: ZonePillStyle,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // REQUIRED, and easy to miss because HudField inherits nothing from BaseNumericField:
        // both slots draw their own header, so without this Karoo stacks its own "HUD" header
        // on top of the two of them and eats the top of the tile.
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        // LOCALS, deliberately, never properties of HudField: startView runs concurrently for
        // every page the tile sits on plus the picker preview, and a property would be one set of
        // cells written by several Dispatchers.IO coroutines at once -- one view's bitmap reuse
        // silently driving another view's render.
        // The HEADER's own height, not a dimen of the pill's own. The pill sits inside the row
        // the two labels are drawn in, and that row is as tall as a header bitmap; a separate
        // resource would be a second spelling of the same number, and the two rounded differently
        // -- 26dp lands at 49px here where the header measures 47.
        val headerPx = FieldRenderer.headerHeight(context)
        // The SAME resource hud_field.xml insets the pill's top margin by, read through
        // getDimensionPixelSize so it rounds exactly the way the inflater does. Subtracted TWICE:
        // the margin buys the gap above, and the shorter bitmap buys the one below.
        val pillInset = context.resources.getDimensionPixelSize(R.dimen.hud_pill_inset)
        val barPx = headerPx - 2 * pillInset
        // The SAME resource hud_field.xml gives the divider, for the same reason: halfConfig
        // subtracts it before splitting the tile, so a literal here and a literal there could
        // disagree and leave every number scaled against a budget wider than the space it lands
        // in.
        val dividerPx = context.resources.getDimensionPixelSize(R.dimen.hud_divider_width)
        // Bitmap reuse key. isNight is part of it because it is read per render and can flip --
        // at sunset, or when the rider changes the theme -- with nothing about the bar changing,
        // and a bar left in the other palette would then stick. The font is in it for the same
        // reason: the rider can change it mid-ride, and the value is drawn INTO the bitmap.
        var lastBar: BaseNumericField.BarFrame? = null
        var lastIcon: Int? = null
        var lastNight = false
        var lastFont: FontSetting? = null
        var lastStyle: ZonePillStyle? = null
        var lastBitmap: Bitmap? = null

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        // Registered before launch, for the same reason BaseNumericField does it: if the
        // emitter were torn down in the gap there would be no way to stop the collector.
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            combine(
                Settings.hudSlotsFlow(context, catalog.ids),
                Settings.hudBarSourceFlow(context, catalog.zoneCapable.ids),
            ) { slots, barId -> slots to barId }
                // Latest, not merge: when the rider changes a slot or the bar's source the old
                // field's stream must be torn down, not left running behind the new one.
                .flatMapLatest { (slots, barId) ->
                    val (left, right) = slots
                    // No `!!` anywhere on this path. hudSlotsFlow already resolves both ids
                    // against catalog.ids, so a miss should be impossible -- but a throw inside
                    // scope.launch is NOT caught by SupervisorJob (that only stops a failing
                    // child from cancelling its siblings); it reaches the default handler and
                    // takes the whole extension process down mid-ride, all 72 fields on every
                    // page with it. The elvis costs nothing.
                    val l = catalog.byId(left) ?: catalog.first()
                    val r = catalog.byId(right) ?: catalog.first()
                    // Null all the way down when the bar is off or its id is gone: unlike a slot
                    // there is no fallback field to substitute, because a bar about something the
                    // rider did not choose is worse than no bar.
                    val barField = barId?.let { catalog.byId(it) }
                    combine(
                        slotFlow(l, context, config),
                        slotFlow(r, context, config),
                        barFlow(barField, context, config),
                        // In the combine rather than read per tick, so changing it repaints at
                        // once -- the same way every other setting reaches this tile.
                        Settings.zonePillStyleFlow(context),
                    ) { a, b, bar, style ->
                        Tick(
                            Slot(l, a.first, a.second),
                            Slot(r, b.first, b.second),
                            bar,
                            barField?.iconRes,
                            style,
                        )
                    }
                }
                // updateView runs inside the collector below, downstream of this operator, so
                // conflate does not save anything from the 900ms emitter window by itself -- two
                // states that arrive back-to-back still render twice. What it saves is the case
                // where a newer state arrives while a render (and the pacing delay under it) is
                // still in flight: conflate bounds the buffer to one, so that intermediate state
                // is dropped instead of queued behind the busy collector and rendered into a view
                // nobody would ever see.
                .conflate()
                .collect { (left, right, bar, barIcon, style) ->
                    // Read once per tick and made part of the reuse key: the theme can flip on
                    // its own with nothing about `bar` changing.
                    val night = Theme.isNight(context)
                    // Either half will do: appearance is one global setting, so both slots carry
                    // the same object. The bar draws a value, and a value follows the rider's
                    // font -- it used to be pinned to Oswald on the reading that the bar is
                    // chrome, which is true of its icon and not of the number beside it.
                    val font = left.appearance.font
                    // ONE decision, used for the pill's bitmap and for both slots' headers. Taken
                    // before either, because the headers only go icon-only to make room for a
                    // pill that is about to be drawn.
                    val layout = bar?.let {
                        pillLayout(
                            context, barPx, it, barIcon != null, font,
                            config.viewSize.first,
                            left.field.label, right.field.label, style,
                        )
                    } ?: PillLayout(iconOnlyHeaders = false, pill = null)
                    // TEMPORARY DIAGNOSTIC, alongside FieldRenderer's -- remove with it. The
                    // narrow-tile cascade cannot be reached on the page this was developed on.
                    if (BuildConfig.DEBUG) {
                        Log.i(
                            TAG,
                            "pillLayout tile=${config.viewSize.first} " +
                                "labels=${left.field.label}/${right.field.label} " +
                                "setting=$style -> iconOnly=${layout.iconOnlyHeaders} " +
                                "pill=${layout.pill}",
                        )
                    }
                    val bitmap: Bitmap? = when {
                        bar == null -> null
                        bar == lastBar && barIcon == lastIcon && night == lastNight &&
                            font == lastFont && style == lastStyle -> lastBitmap
                        else -> {
                            // EVERY line that draws is inside this try, and that is the point of
                            // it: it runs here in the collector, downstream of every retryWhen,
                            // and a throw is NOT caught by SupervisorJob -- it reaches the default
                            // handler and takes the whole extension process down mid-ride, every
                            // field on every page with it. A failed bar must be a missing bar.
                            val drawn = try {
                                layout.pill?.let { drawn ->
                                    renderZonePill(
                                        context = context,
                                        heightPx = barPx,
                                        lit = bar.lit,
                                        segmentCount = bar.segments,
                                        style = drawn,
                                        zoneColor = bar.color,
                                        text = bar.text,
                                        icon = barIcon?.let { context.getDrawable(it) },
                                        isNight = night,
                                        font = font,
                                    )
                                }
                            } catch (t: Throwable) {
                                // Throwable rather than Exception, deliberately:
                                // Bitmap.createBitmap throws OutOfMemoryError, an Error and not
                                // an Exception, and on a Karoo it is the likeliest thing here to
                                // go wrong.
                                Log.w(TAG, "HUD zone bar render failed", t)
                                null
                            }
                            // The key is updated ONLY on success. Caching a failure would mean
                            // an identical next tick reuses the null and the bar stays hidden
                            // until the value happens to change -- and the failure this catch
                            // exists for is OutOfMemoryError, exactly the case where the next
                            // attempt is the one worth making.
                            if (drawn != null) {
                                lastBar = bar
                                lastIcon = barIcon
                                lastNight = night
                                lastFont = font
                                lastStyle = style
                                lastBitmap = drawn
                            }
                            drawn
                        }
                    }
                    // No slot budget to decide any more: the pill lives inside the header row
                    // that both halves reserve whether it is there or not, so a pill that failed
                    // to draw simply leaves that row with two labels in it.
                    emitter.updateView(
                        hudViews(
                            context, config, left, right, bitmap, dividerPx,
                            layout.iconOnlyHeaders,
                        ),
                    )
                    // ViewEmitter.updateView silently DROPS any view sent within 900ms of the
                    // previous one, so pacing the collector past that window is what turns a
                    // dropped update into a delayed one: conflate() above holds only the newest
                    // state while we wait here, so the next thing drawn is current rather than
                    // stale, and nothing is ever lost to the throttle.
                    //
                    // Without this the field is permanently broken, which is how it was found:
                    // the onStart fallback draws "--" at t=0 and opens the window, the first
                    // real frame lands ~400ms later and is discarded, and because frameFlow
                    // ends in distinctUntilChanged an unchanging value never re-emits to
                    // repaint. On the device the tile sat at "--" forever while the log showed
                    // the correct values had been computed and handed to a dropped updateView.
                    // Every JVM test passed throughout.
                    delay(EMITTER_WINDOW_MS)
                }
        }
    }

    /** One slot's updates, with a stream failure turned into "--" instead of a dead half. */
    private fun slotFlow(
        field: BaseNumericField,
        context: Context,
        config: ViewConfig,
    ): Flow<Pair<BaseNumericField.Frame, Appearance>> =
        field.frameFlow(context, config.preview)
            .withSlotRecovery(
                fallback = { field.missingFrame(context) to currentAppearance(context) },
                delayMs = SLOT_RETRY_DELAY_MS,
            )

    /**
     * The bar's updates, or a flow of nothing at all when no source is configured.
     *
     * The recovery wrapper is what makes this safe to put in the combine above. combine waits for
     * every source before it emits anything, so a bar that only spoke once the rider's profile
     * arrived -- which in the picker preview may be never -- would hold BOTH numbers blank behind
     * it. The onStart null inside withSlotRecovery means the bar is simply absent until it has
     * something, and the numbers draw immediately either way.
     */
    private fun barFlow(
        field: BaseNumericField?,
        context: Context,
        config: ViewConfig,
    ): Flow<BaseNumericField.BarFrame?> =
        field?.barFlow(context, config.preview)
            ?.withSlotRecovery(fallback = { null }, delayMs = SLOT_RETRY_DELAY_MS)
            ?: flowOf(null)

    /**
     * A fresh parent RemoteViews per update, never a reused one -- the same reason
     * BaseNumericField rebuilds its view: RemoteViews is an append-only action list with no way
     * to clear it, and [RemoteViews.addView] appends, so a reused instance would grow its action
     * list without bound and re-serialize the whole thing on every send.
     */
    private fun hudViews(
        context: Context,
        config: ViewConfig,
        left: Slot,
        right: Slot,
        bar: Bitmap?,
        dividerPx: Int,
        iconOnlyHeaders: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.hud_field)
        // Visibility is set on BOTH paths, never left to the layout's own `gone`: RemoteViews
        // actions are replayed onto whatever the host already has inflated, so a bar that was
        // VISIBLE last update stays VISIBLE -- showing a stale zone colour -- unless this update
        // says otherwise.
        if (bar != null) {
            views.setImageViewBitmap(R.id.hud_bar, bar)
            views.setViewVisibility(R.id.hud_bar, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.hud_bar, View.GONE)
        }
        // removeAllViews before addView, for each slot: cheap insurance on the reapply path.
        // If the view host replays these actions onto an already-inflated root rather than a
        // fresh one, addView on its own stacks a second slot view on top of the first.
        views.removeAllViews(R.id.slot_left)
        views.addView(
            R.id.slot_left,
            slotViews(context, config, left, true, dividerPx, iconOnlyHeaders),
        )
        views.removeAllViews(R.id.slot_right)
        views.addView(
            R.id.slot_right,
            slotViews(context, config, right, false, dividerPx, iconOnlyHeaders),
        )
        return views
    }

    /** One slot drawn as a whole field, into its own copy of the ordinary field layout. */
    private fun slotViews(
        context: Context,
        config: ViewConfig,
        slot: Slot,
        isLeft: Boolean,
        dividerPx: Int,
        iconOnlyHeaders: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.numeric_field)
        // inSlot = true: the per-field card rounding belongs to the tile, and two rounded cards
        // meeting at the divider would leave four notches down the middle of one tile.
        //
        slot.field.renderInto(
            context, views, halfConfig(config, isLeft, dividerPx), slot.frame, slot.appearance,
            inSlot = true,
            // Each half's label goes to the tile's OWN outer edge, so the two read as one row
            // rather than as two fields that happen to be adjacent -- and so the pill between
            // them has the middle to itself. Not done by handing the slot a different alignment:
            // that value also picks the number's scaleType, and a slot switched to fitStart would
            // pin its digits to the top of its box while its neighbour kept fitEnd.
            headerAlignment = hudHeaderAlignment(isLeft),
            iconOnlyHeader = iconOnlyHeaders,
        )
        return views
    }
}

/**
 * The [ViewConfig] one slot renders against: the tile's config, narrowed to that slot's half.
 *
 * The height is NOT narrowed by the zone bar; see [FieldRenderer.render]'s `overlayTopPx` for
 * why. [dividerPx] is the hairline between the halves, which does come off the width -- passed in
 * rather than read here so this stays a pure function a JVM test can drive, and so the value is
 * the one the layout actually inflated.
 *
 * [config].preview is deliberately carried through unchanged, so a slot still knows it is being
 * previewed and shows its demo value in the page editor.
 */
internal fun halfConfig(config: ViewConfig, left: Boolean, dividerPx: Int): ViewConfig {
    val (w, h) = config.viewSize
    val usable = w - dividerPx
    return config.copy(
        // `usable - usable / 2` for the right slot rather than a second `usable / 2`: a
        // weight=1 LinearLayout hands the odd remainder pixel to one child, and flooring both
        // would leave the renderer budgeting for a tile a pixel narrower than the one it gets.
        viewSize = (if (left) usable / 2 else usable - usable / 2) to h,
        // alignment is not overridden here: it carries through from the parent, same as
        // preview, so both halves line up their label and number the way every other field
        // on the page does.
    )
}

/**
 * Emit [fallback] on subscribe, and again on every upstream failure before resubscribing.
 *
 * `.catch` is WRONG here and must not be used: catch emits and then COMPLETES the flow, so one
 * transient stream failure would pin that half at "--" for the rest of the ride while the other
 * half kept updating. retryWhen emits and then resubscribes, which is the behaviour a slot needs.
 *
 * The [onStart] emission exists so that when the rider changes a slot, the tile does not keep
 * showing the previous field's value under the new configuration until the new stream produces
 * something of its own -- and so that combine, which waits for every source, is never held up by
 * a source that has not spoken yet.
 */
internal fun <T> Flow<T>.withSlotRecovery(fallback: () -> T, delayMs: Long): Flow<T> =
    retryWhen { cause, attempt ->
        // Logged rather than swallowed: a broad silent catch here would hide the one diagnostic
        // that says which slot's stream is failing and why.
        Log.w(TAG, "HUD slot stream failed (attempt $attempt), retrying in ${delayMs}ms", cause)
        emit(fallback())
        delay(delayMs)
        true
    }.onStart { emit(fallback()) }

/**
 * The same pair [Settings.appearanceFlow] emits, read once. A fallback frame still has to be
 * drawn in the rider's chosen font, and there is no upstream value to carry one alongside.
 */
private fun currentAppearance(context: Context): Appearance =
    Appearance(Settings.fontSetting(context), Settings.raisedTail(context))

/**
 * Which edge one half's LABEL is pinned to: the tile's own outer edge, so the two halves read as
 * one row rather than as two fields that happen to be adjacent, and so the zone pill between them
 * has the middle to itself.
 *
 * Its own function rather than an inline conditional so a JVM test can pin it. Everything else
 * about the two labels needs a Canvas.
 */
internal fun hudHeaderAlignment(isLeft: Boolean): ViewConfig.Alignment =
    if (isLeft) ViewConfig.Alignment.LEFT else ViewConfig.Alignment.RIGHT

/**
 * What this tile has room for: whether the two headers must drop their labels, and which pill
 * style survives -- or none at all.
 */
internal data class PillLayout(val iconOnlyHeaders: Boolean, val pill: ZonePillStyle?)

/**
 * Fit the pill and the two labels into [tileWidthPx], degrading in the order the rider would
 * choose themselves.
 *
 * The cascade, in order: full labels with the chosen style; icon-only labels with the chosen
 * style; icon-only labels with the solid pill; icon-only labels and no pill. The rider's chosen
 * style is honoured for as long as anything fits, and the labels are what gives way first -- the
 * style was picked deliberately, the labels are automatic, and on a half-width cell the rider
 * chose which two fields are in it anyway.
 *
 * Dropping the labels buys about 145px here: a full header is roughly 110px wide and an
 * icon-only one 38, and it costs nothing vertically because both are the same height.
 *
 * Never a partial square count. A pill showing four of a rider's seven zones would be read as
 * four zones, which is worse than showing none.
 *
 * [tileWidthPx] is the tile's own width, not a half's: the pill straddles the divider.
 */
internal fun pillLayout(
    context: Context,
    heightPx: Int,
    bar: BaseNumericField.BarFrame,
    hasIcon: Boolean,
    font: FontSetting,
    tileWidthPx: Int,
    leftLabel: String,
    rightLabel: String,
    style: ZonePillStyle,
): PillLayout {
    // The two labels actually on the tile, not a worst case: reserving room for the widest label
    // this app has would drop the squares on a tile showing "HR" and "SPD" purely because some
    // other field is called "TIME lap". Both halves are re-rendered whenever a slot changes, so
    // this is re-decided at the same moment the labels themselves do.
    fun room(iconOnly: Boolean) = tileWidthPx -
        FieldRenderer.headerWidth(context, leftLabel, iconOnly) -
        FieldRenderer.headerWidth(context, rightLabel, iconOnly)
    fun fits(segments: Int, iconOnly: Boolean) =
        zonePillWidth(context, heightPx, bar.text, segments, hasIcon, font) <= room(iconOnly)

    val squares = if (style == ZonePillStyle.SEGMENTS) bar.segments else 0
    return when {
        fits(squares, false) -> PillLayout(false, style)
        fits(squares, true) -> PillLayout(true, style)
        // Falls back to the style the rider could have chosen anyway rather than to a third look
        // of its own, so there are exactly two pills in this app and not two and a half.
        fits(0, true) -> PillLayout(true, ZonePillStyle.SOLID)
        // FULL labels here, not icon-only. The labels are dropped to make room for a pill; when
        // there is no pill to make room for, dropping them buys nothing and costs the two words
        // that say what the numbers are.
        else -> PillLayout(false, null)
    }
}
