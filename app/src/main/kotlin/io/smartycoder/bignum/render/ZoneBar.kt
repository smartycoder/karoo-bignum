package io.smartycoder.bignum.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import io.hammerhead.karooext.models.UserProfile
import io.smartycoder.bignum.FontSetting
import kotlin.math.roundToInt

/**
 * The zone bar: one row across the whole HUD tile, filled to where the rider's heart rate or
 * power sits on their zone scale and coloured with that zone's colour.
 *
 * The arithmetic is here, [renderZoneBar] draws it. Split so the arithmetic is reachable from a
 * plain JVM test: this project has no Robolectric, so anything touching android.graphics can
 * only be checked on the device.
 */
object ZoneBar {

    /**
     * Where [value] sits on the bar, 0..1.
     *
     * EVERY ZONE GETS AN EQUAL SHARE of the width, and that is deliberate rather than a
     * simplification. The obvious scale -- value over the top of the last zone -- needs that top
     * to be a real number, and nothing here can promise it is: karoo-ext hands over
     * [UserProfile.Zone] pairs with no documented contract for the last one's max, and a
     * sentinel there would pin the bar near empty for the whole ride while looking like a design
     * choice rather than a bug. An equal share per zone needs only each zone's own min and max,
     * so the worst a bogus top max can do is freeze the last of five or seven segments.
     *
     * It also reads better: the top of Z3 is at three fifths on every rider's HR scale, whatever
     * their zones are set to, so the bar means the same thing on two different bikes.
     */
    fun fraction(value: Double, zones: List<UserProfile.Zone>): Float {
        // NaN only, not every non-finite value: -Infinity falls below the first zone and +Infinity
        // above the last, and both of those land correctly on their own. NaN does not -- every
        // comparison against it is false, so it would reach the division and survive both
        // coerce calls as a NaN bar width.
        if (zones.isEmpty() || value.isNaN()) return 0f
        // The same lookup the colour uses, so the fill and the colour can never name different
        // zones. It differs only in what it does BELOW the first zone: the colour clamps up to
        // zone 1, the bar stays empty.
        val index = ZoneColors.zoneIndex(value, zones)
        if (index < 0) return 0f
        val zone = zones[index]
        val span = (zone.max - zone.min).toDouble()
        // A zone with no width at all is full rather than a division by zero -- the shape an
        // unset or sentinel top zone arrives in.
        val within = if (span <= 0) 1.0 else ((value - zone.min) / span).coerceIn(0.0, 1.0)
        return ((index + within) / zones.size).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * How tall the value's INK is drawn, as a share of the bar's height -- the digits themselves, not
 * the text size that produces them.
 *
 * The difference is the whole point. A text size means a different thing in every face: the same
 * 34px produced 28px of digit in Oswald and 23px in Saira, so a bar sized that way changed height
 * when the rider changed the number font. Measuring the ink and scaling to hit this target makes
 * the value the same height in any face, which is what [FieldRenderer.renderHeader] already does
 * with its label.
 */
private const val TEXT_INK_FRACTION = 0.58f

/** The icon's height, as a share of the bar's height. Larger than the text, as in a field header. */
private const val ICON_HEIGHT_FRACTION = 0.76f

/** The empty part of the bar: the card's own colour, darkened or lightened just enough to read. */
private const val TRACK_ON_LIGHT = 0x1A000000
private const val TRACK_ON_DARK = 0x24FFFFFF

/**
 * Draws the bar: track, fill, and the source's icon and value laid over both.
 *
 * The icon and the value are each drawn TWICE, once in the colour that reads on the track and
 * once in the colour that reads on the fill, the second clipped to the fill's own width. That is
 * the only thing that works here: the fill's edge moves across the bar as the value changes, so
 * at any moment either of them can be half on the fill and half off it, and a single colour is
 * necessarily wrong on one side. Clipped, a glyph the edge runs through is simply split.
 *
 * [ZoneColors.onColor] picks each of those two colours, the same function that decides the
 * number's colour on a zone-filled field -- so the bar and a filled field never disagree about
 * what is legible on a given zone colour.
 */
fun renderZoneBar(
    context: Context,
    widthPx: Int,
    heightPx: Int,
    fraction: Float,
    zoneColor: Int,
    text: String,
    icon: Drawable?,
    isNight: Boolean,
    /** Rounds off the bar's top corners, which are the card's own top corners. */
    cornerRadiusPx: Float,
    /**
     * The rider's number font, not the label font. The bar draws a live value, and a value in
     * this app is set in whatever face the rider picked -- the icon beside it is the part that is
     * chrome, and it carries no typeface at all.
     */
    font: FontSetting,
): Bitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val w = widthPx.toFloat()
    val h = heightPx.toFloat()
    // Top corners only: the bar sits on the card's top edge, and its bottom edge meets the two
    // numbers, where a rounded corner would read as a gap rather than as the card's outline.
    val r = cornerRadiusPx.coerceIn(0f, minOf(w, h) / 2f)
    val clip = Path().apply {
        addRoundRect(
            RectF(0f, 0f, w, h),
            floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f),
            Path.Direction.CW,
        )
    }
    canvas.clipPath(clip)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // An OPAQUE ground first, then the translucent track over it. Without this the bar is a
    // window: its track is only 10-14% ink on a transparent bitmap, so everything the tile draws
    // underneath shows through it. That is not hypothetical -- the slot row runs the full height
    // of the tile behind the bar, so the halves' grey divider ran up the middle of the empty
    // track and blinked in and out as the fill passed it, and under ZoneColorMode.FILL each
    // half's zone colour came through too, which would also have made the ink colour below wrong.
    paint.color = Theme.cardColor(isNight)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.color = if (isNight) TRACK_ON_DARK else TRACK_ON_LIGHT
    canvas.drawRect(0f, 0f, w, h, paint)

    val fillWidth = w * fraction.coerceIn(0f, 1f)
    paint.color = zoneColor
    canvas.drawRect(0f, 0f, fillWidth, h, paint)

    val pad = FieldRenderer.edgePadding(context).toFloat()
    // Whole pixels, rounded, and a SQUARE box derived by adding the size to the origin rather
    // than truncating each edge on its own. The obvious spelling --
    // `setBounds(pad.toInt(), iconTop.toInt(), (pad + iconSize).toInt(), (iconTop + iconSize).toInt())`
    // -- truncates four independent floats, which at this density produced a 37x38 box: the
    // square vector came out stretched, and the icon sat a pixel high before fitXY squashed the
    // bitmap into a view a pixel shorter still. Measured on a Karoo 3, the icon had 7px of air
    // above and 10 below while the value beside it sat at a true 10 and 10.
    val iconPx = (h * ICON_HEIGHT_FRACTION).roundToInt()
    val iconLeft = pad.roundToInt()
    val iconTop = ((h - iconPx) / 2f).roundToInt()

    val target = h * TEXT_INK_FRACTION
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Through FieldRenderer rather than resolving the face here: it also turns on tabular
        // figures for a variable font, without which the value shifts sideways as its digits
        // change -- and it is pinned to the bar's right edge, so that shift would be visible.
        FieldRenderer.applyNumberFont(this, context, font)
        isSubpixelText = true
        textSize = target
    }
    // Measured on a "0" rather than on the value's own glyphs, for the same reason
    // FieldRenderer measures its label on a capital: digits and their absence would otherwise
    // move the baseline as the value changes.
    val ink = Rect()
    textPaint.getTextBounds("0", 0, 1, ink)
    // Then scaled so the ink actually IS the target height, whatever face this is. Guarded,
    // because a face that measures nothing would turn this into a division by zero and put a NaN
    // text size on the paint.
    if (ink.height() > 0) {
        textPaint.textSize = target * target / ink.height()
        textPaint.getTextBounds("0", 0, 1, ink)
    }
    // FieldRenderer's, not a fourth copy: three renderers were each spelling out
    // (box - ink) / 2 - inkTop, and it is the one piece of this arithmetic that a font-metric
    // correction would have to reach in all of them at once.
    val baseline = FieldRenderer.baselineFor(heightPx, ink.height(), ink.top)
    val textWidth = textPaint.measureText(text)
    val textLeft = w - pad - textWidth

    /** One full pass of icon and value in [ink], drawn only where [clipTo] allows. */
    fun pass(inkColor: Int, clipTo: RectF) {
        canvas.save()
        canvas.clipRect(clipTo)
        icon?.mutate()?.apply {
            setTint(inkColor)
            setBounds(iconLeft, iconTop, iconLeft + iconPx, iconTop + iconPx)
            draw(canvas)
        }
        textPaint.color = inkColor
        canvas.drawText(text, textLeft, baseline, textPaint)
        canvas.restore()
    }

    // Track first, then the fill's own pass over it. Not the other way round: the fill pass is
    // the narrower clip, and drawing it first would let the wider one paint straight over it.
    //
    // isNight, not a second read of the theme through the Context: isNight is part of the caller's
    // bitmap reuse key, and a colour resolved outside that key could disagree with the bitmap it
    // was drawn into.
    pass(Theme.textColor(isNight), RectF(fillWidth, 0f, w, h))
    pass(ZoneColors.onColor(zoneColor), RectF(0f, 0f, fillWidth, h))

    return bitmap
}
