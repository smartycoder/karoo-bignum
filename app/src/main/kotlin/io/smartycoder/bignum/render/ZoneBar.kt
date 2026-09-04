package io.smartycoder.bignum.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import io.hammerhead.karooext.models.UserProfile
import io.smartycoder.bignum.FontSetting
import io.smartycoder.bignum.ZonePillStyle
import kotlin.math.roundToInt

/**
 * The zone pill: a rounded lozenge centred in the HUD's header row, showing the rider's source
 * icon, one square per zone with everything up to the current one lit, and the value.
 *
 * It replaces a bar that ran the whole width of the tile. The bar cost both halves their labels
 * -- there was nowhere else for them to go -- and it never said which zone it was in except by
 * colour, which is a thing to remember rather than a thing to read. Discrete squares are counted
 * at a glance on a moving bike, and being only as wide as its own content leaves the two labels
 * their corners.
 *
 * The count of lit squares is [ZoneColors.zoneIndex] plus one, so the fill and the colour can
 * never name different zones. There is no fraction WITHIN a zone any more: the old bar's
 * position was an equal share per zone rather than a true scale, so what it actually resolved to
 * was the zone number and a wobble inside it, and the wobble is what made it hard to read.
 */
object ZoneBar {

    /**
     * How many squares are lit for [value], out of [zones]`.size`.
     *
     * Zero has two meanings and both are correct here. Below the first zone's floor
     * [ZoneColors.zoneIndex] returns -1 -- a heart rate under Z1 is a reading, not a zone. And a
     * NON-POSITIVE value is forced to zero even where a zone would claim it: power zones start at
     * 0, so coasting would otherwise light Z1 for the whole of every descent, and the bar this
     * replaces drew that case empty. NaN falls out as zero on its own, because every comparison
     * against it is false.
     */
    fun litSegments(value: Double, zones: List<UserProfile.Zone>): Int {
        if (zones.isEmpty() || value <= 0.0) return 0
        return (ZoneColors.zoneIndex(value, zones) + 1).coerceIn(0, zones.size)
    }
}

/**
 * How tall the value's INK is drawn, as a share of the pill's height -- the digits themselves,
 * not the text size that produces them.
 *
 * The difference is the whole point. A text size means a different thing in every face: the same
 * 34px produced 28px of digit in Oswald and 23px in Saira, so a pill sized that way changed
 * height when the rider changed the number font. Measuring the ink and scaling to hit this target
 * makes the value the same height in any face, which is what [FieldRenderer.renderHeader] already
 * does with its label.
 *
 * Tuned by eye on the device rather than derived, and retuned twice. 0.52 put 24px of digit in
 * the Karoo 3's 46px pill and read small next to the two labels beside it; 0.565 fixed that. Then
 * the header row was shortened to give the numbers below it more room, and since the pill is
 * sized from that row it lost 5px of height -- taking the value down with it, from 24px of ink to
 * 21px, undoing the earlier fix without anything about the pill changing. 0.605 puts it at 23px in the 38px
 * pill that row now leaves -- two more than before, which is what was asked for.
 *
 * A fraction and not a dp value so the bump scales with the row on a screen of another density --
 * which is also why it had to be retuned rather than left alone: it scales with a row that moved.
 */
private const val TEXT_INK_FRACTION = 0.605f

/** The icon's height, as a share of the pill's height. Larger than the text, as in a header. */
private const val ICON_HEIGHT_FRACTION = 0.62f

/** A zone square's side, as a share of the pill's height. */
private const val SEGMENT_FRACTION = 0.30f

/** Gap between two squares, as a share of the pill's height. */
private const val SEGMENT_GAP_FRACTION = 0.09f

/** Gap between the pill's three groups -- icon, squares, value -- as a share of its height. */
private const val GROUP_GAP_FRACTION = 0.17f

/** The pill's ground, and the colour of a square that is not lit. */
private const val TRACK_ON_LIGHT = 0x1A000000
private const val TRACK_ON_DARK = 0x24FFFFFF

/** An unlit square, which has to read against the pill's own ground rather than the card's. */
private const val EMPTY_SEGMENT_ON_LIGHT = 0x24000000
private const val EMPTY_SEGMENT_ON_DARK = 0x33FFFFFF

/**
 * How wide [renderZonePill] will draw, so the caller can decide whether it fits before asking for
 * it. Pure, so the decision is testable without a Canvas.
 *
 * [segmentCount] of zero drops the squares entirely -- see [renderZonePill]'s note on the narrow
 * tile.
 */
fun zonePillWidth(
    context: Context,
    heightPx: Int,
    text: String,
    segmentCount: Int,
    hasIcon: Boolean,
    font: FontSetting,
): Int {
    if (heightPx <= 0) return 0
    val h = heightPx.toFloat()
    val pad = FieldRenderer.edgePadding(context).toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        FieldRenderer.applyNumberFont(this, context, font)
        textSize = valueTextSize(this, h)
    }
    var w = 2 * pad + paint.measureText(text)
    if (hasIcon) w += h * ICON_HEIGHT_FRACTION + h * GROUP_GAP_FRACTION
    if (segmentCount > 0) {
        w += segmentCount * h * SEGMENT_FRACTION +
            (segmentCount - 1) * h * SEGMENT_GAP_FRACTION +
            h * GROUP_GAP_FRACTION
    }
    return kotlin.math.ceil(w).toInt()
}

/**
 * Text size that makes [paint]'s digit ink [TEXT_INK_FRACTION] of [h], whatever the face.
 *
 * Measured on a "0" rather than on the value's own glyphs, for the same reason FieldRenderer
 * measures its label on a capital: digits and their absence would otherwise move the baseline as
 * the value changes.
 */
private fun valueTextSize(paint: Paint, h: Float): Float {
    val target = h * TEXT_INK_FRACTION
    val ink = Rect()
    var size = target
    // Corrected repeatedly rather than once. A single ratio assumes ink height scales
    // continuously with text size; it does not -- getTextBounds returns whole pixels and hinting
    // steps them -- so one pass lands close and not on. Measured on a Karoo 3: aiming at 23.5px
    // of digit in a 38px pill, one pass produced 22, which is most of a retune thrown away.
    // Three passes because the second is already within a pixel and the third costs nothing that
    // matters at this call rate.
    repeat(3) {
        paint.textSize = size
        paint.getTextBounds("0", 0, 1, ink)
        // Guarded, because a face that measures nothing would turn this into a division by zero
        // and put a NaN text size on the paint.
        if (ink.height() <= 0) return size
        size *= target / ink.height()
    }
    paint.textSize = size
    return size
}

/**
 * Draws the pill: rounded ground, the source's icon, [segmentCount] squares of which [lit] are
 * filled with [zoneColor], and the value.
 *
 * [ZonePillStyle.SOLID] drops the squares and takes the zone colour as its ground instead. It is
 * both a style the rider can choose and what the segmented style degrades into on a narrow tile:
 * a Karoo cell can be half width, where a seven-zone pill does not fit beside two labels, and the
 * squares are the part that degrades best -- the colour still names the zone, which is all the bar
 * this replaces ever said. Whether it fits at all is the caller's question; see the HUD's pillFit.
 *
 * Unlike the bar, nothing here is drawn twice and clipped. The bar had a fill edge that moved
 * across it, so a glyph the edge ran through was necessarily the wrong colour on one side; the
 * pill's ground is one flat colour, and the squares never straddle anything.
 */
fun renderZonePill(
    context: Context,
    heightPx: Int,
    lit: Int,
    segmentCount: Int,
    style: ZonePillStyle,
    zoneColor: Int,
    text: String,
    icon: Drawable?,
    isNight: Boolean,
    /**
     * The rider's number font, not the label font. The pill draws a live value, and a value in
     * this app is set in whatever face the rider picked -- the icon beside it is the part that is
     * chrome, and it carries no typeface at all.
     */
    font: FontSetting,
): Bitmap? {
    if (heightPx <= 0) return null
    // ONE source of truth for the square count, used to measure and to draw. A solid pill has no
    // squares, and a width measured with them would leave a lozenge padded out with empty space.
    val solid = style == ZonePillStyle.SOLID
    val squares = if (solid) 0 else segmentCount
    val widthPx = zonePillWidth(context, heightPx, text, squares, icon != null, font)
    if (widthPx <= 0) return null

    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val w = widthPx.toFloat()
    val h = heightPx.toFloat()
    val pad = FieldRenderer.edgePadding(context).toFloat()

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // An OPAQUE ground first, then the pill's own colour over it. Without this the pill is a
    // window: the track is only 10-14% ink on a transparent bitmap, so everything the tile draws
    // underneath shows through. That is not hypothetical -- the HUD's slot row runs the full
    // height of the tile behind this, so the halves' grey divider ran up the middle of the empty
    // track, and under ZoneColorMode.FILL each half's zone colour came through too. The solid
    // style is opaque on its own, but it is painted the same way rather than specially: one path
    // cannot drift out of step with the other.
    paint.color = Theme.cardColor(isNight)
    canvas.drawRoundRect(RectF(0f, 0f, w, h), h / 2f, h / 2f, paint)
    paint.color = if (solid) zoneColor else if (isNight) TRACK_ON_DARK else TRACK_ON_LIGHT
    canvas.drawRoundRect(RectF(0f, 0f, w, h), h / 2f, h / 2f, paint)

    // On the solid style the ground IS the zone colour, so the ink is whatever reads on it --
    // the same function that decides a zone-filled field's number colour, so the two can never
    // disagree about what is legible on a given zone.
    val ink = if (solid) ZoneColors.onColor(zoneColor) else Theme.textColor(isNight)
    var x = pad

    icon?.mutate()?.apply {
        // Whole pixels, rounded, and a SQUARE box derived by adding the size to the origin rather
        // than truncating each edge on its own: truncating four independent floats produced a
        // 37x38 box at this density, and the square vector came out stretched.
        val size = (h * ICON_HEIGHT_FRACTION).roundToInt()
        val left = x.roundToInt()
        val top = ((h - size) / 2f).roundToInt()
        setTint(ink)
        setBounds(left, top, left + size, top + size)
        draw(canvas)
        x += size + h * GROUP_GAP_FRACTION
    }

    if (squares > 0) {
        val side = h * SEGMENT_FRACTION
        val gap = h * SEGMENT_GAP_FRACTION
        val top = (h - side) / 2f
        // A square's own corner radius, not the pill's: rounded enough to belong to the same
        // drawing, square enough to still be counted.
        val r = side * 0.25f
        val emptyColor = if (isNight) EMPTY_SEGMENT_ON_DARK else EMPTY_SEGMENT_ON_LIGHT
        for (i in 0 until squares) {
            // Every lit square takes the CURRENT zone's colour rather than its own zone's. The
            // pill answers "which zone am I in", and a rainbow of the zones below would say
            // something about zones the rider is not in, in colours that compete with the one
            // that matters.
            paint.color = if (i < lit) zoneColor else emptyColor
            val left = x + i * (side + gap)
            canvas.drawRoundRect(RectF(left, top, left + side, top + side), r, r, paint)
        }
        x += squares * side + (squares - 1) * gap + h * GROUP_GAP_FRACTION
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Through FieldRenderer rather than resolving the face here: it also turns on tabular
        // figures for a variable font, without which the value shifts sideways as its digits
        // change.
        FieldRenderer.applyNumberFont(this, context, font)
        isSubpixelText = true
        color = ink
    }
    textPaint.textSize = valueTextSize(textPaint, h)
    val digits = Rect()
    textPaint.getTextBounds("0", 0, 1, digits)
    // FieldRenderer's, not a third copy: it is the one piece of this arithmetic that a
    // font-metric correction would have to reach in both places at once.
    canvas.drawText(text, x, FieldRenderer.baselineFor(heightPx, digits.height(), digits.top), textPaint)

    return bitmap
}
