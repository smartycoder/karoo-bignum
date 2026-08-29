package io.smartycoder.bignum.fields

import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import io.smartycoder.bignum.Appearance
import io.smartycoder.bignum.R
import io.smartycoder.bignum.Settings
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

private const val TAG = "BigNumHud"

/** Width of the divider in hud_field.xml; the slots share what is left of the tile. */
// Not private only so HudLayoutTest can assert the halves plus the divider add back up to the
// tile width, rather than hard-coding the same 1 in a second place that could drift.
internal const val DIVIDER_PX = 1

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
 * its own half of one tile.
 *
 * Deliberately a [DataTypeImpl] rather than a [BaseNumericField]: it has no stream, no format
 * and no zone of its own, and it composes fields rather than being one. That also means a HUD
 * can never end up nested inside a HUD -- it is not in the catalogue the slots are picked from.
 * It holds no KarooSystemService either: it never streams anything directly, it only collects
 * flows the catalogue's fields already know how to build.
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // REQUIRED, and easy to miss because HudField inherits nothing from BaseNumericField:
        // both slots draw their own header, so without this Karoo stacks its own "HUD" header
        // on top of the two of them and eats the top of the tile.
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        // Registered before launch, for the same reason BaseNumericField does it: if the
        // emitter were torn down in the gap there would be no way to stop the collector.
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            Settings.hudSlotsFlow(context, catalog.ids)
                // Latest, not merge: when the rider changes a slot the old field's stream must
                // be torn down, not left running behind the new one.
                .flatMapLatest { (left, right) ->
                    // No `!!` anywhere on this path. hudSlotsFlow already resolves both ids
                    // against catalog.ids, so a miss should be impossible -- but a throw inside
                    // scope.launch is NOT caught by SupervisorJob (that only stops a failing
                    // child from cancelling its siblings); it reaches the default handler and
                    // takes the whole extension process down mid-ride, all 58 fields on every
                    // page with it. The elvis costs nothing.
                    val l = catalog.byId(left) ?: catalog.first()
                    val r = catalog.byId(right) ?: catalog.first()
                    combine(slotFlow(l, context, config), slotFlow(r, context, config)) { a, b ->
                        Slot(l, a.first, a.second) to Slot(r, b.first, b.second)
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
                .collect { (left, right) ->
                    emitter.updateView(hudViews(context, config, left, right))
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
     * A fresh parent RemoteViews per update, never a reused one -- the same reason
     * BaseNumericField rebuilds its view: RemoteViews is an append-only action list with no way
     * to clear it, and [RemoteViews.addView] appends, so a reused instance would grow its action
     * list without bound and re-serialize the whole thing on every send.
     */
    private fun hudViews(context: Context, config: ViewConfig, left: Slot, right: Slot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.hud_field)
        // removeAllViews before addView, for each slot: cheap insurance on the reapply path.
        // If the view host replays these actions onto an already-inflated root rather than a
        // fresh one, addView on its own stacks a second slot view on top of the first.
        views.removeAllViews(R.id.slot_left)
        views.addView(R.id.slot_left, slotViews(context, config, left, isLeft = true))
        views.removeAllViews(R.id.slot_right)
        views.addView(R.id.slot_right, slotViews(context, config, right, isLeft = false))
        return views
    }

    /** One slot drawn as a whole field, into its own copy of the ordinary field layout. */
    private fun slotViews(
        context: Context,
        config: ViewConfig,
        slot: Slot,
        isLeft: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.numeric_field)
        // inSlot = true: the per-field card rounding belongs to the tile, and two rounded cards
        // meeting at the divider would leave four notches down the middle of one tile.
        slot.field.renderInto(
            context, views, halfConfig(config, isLeft), slot.frame, slot.appearance, inSlot = true,
        )
        return views
    }
}

/**
 * The [ViewConfig] one slot renders against: the tile's config, narrowed to that slot's half.
 *
 * [config].preview is deliberately carried through unchanged, so a slot still knows it is being
 * previewed and shows its demo value in the page editor.
 */
internal fun halfConfig(config: ViewConfig, left: Boolean): ViewConfig {
    val (w, h) = config.viewSize
    val usable = w - DIVIDER_PX
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
 * something of its own.
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
