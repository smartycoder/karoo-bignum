package io.smartycoder.bignum.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.util.concurrent.ConcurrentHashMap
import io.smartycoder.bignum.R
import io.hammerhead.karooext.models.ViewConfig
import io.hammerhead.karooext.models.ViewConfig.Alignment

/**
 * Renders a field -- its icon, short label and primary number -- to a Bitmap using the
 * bundled Oswald Bold typeface, and pushes it into the RemoteViews via setImageViewBitmap.
 * The unit suffix (km/h, W, ...) is intentionally not drawn.
 *
 * The header is ours rather than Karoo's: the field sends UpdateGraphicConfig(showHeader =
 * false), which buys the whole tile and lets the label be a short form ("PWR 5s") in the
 * same typeface as the number, instead of Karoo's uppercased displayName ("POWER 5S AVG").
 *
 * Neither bitmap is sized to [ViewConfig.viewSize]: on the Karoo the reported view size does
 * not match the actual ImageView, so anything measured against it gets rescaled away or
 * clipped. The number is a tight, fixed-aspect box that a fit* scaleType blows up to fill the
 * real view; the header is drawn at its natural size into a wrap_content view. Both are
 * pinned to the edge [ViewConfig.alignment] asks for by the layout, not by padding baked
 * into the bitmap.
 */
object FieldRenderer {

    // Ink height of the digits, measured on the digits rather than on fontMetrics:
    // ascent/descent reserve room for accents and descenders that digits never use.
    private const val REFERENCE_GLYPHS = "0123456789"

    // Default width budget. Keeping it fixed keeps the bitmap's aspect ratio constant, so a
    // field ends up at the same on-screen text size regardless of how many characters its
    // current value has. Wider values shrink to fit; a field that is routinely wider declares
    // its own via BaseNumericField.widthTemplate.
    const val DEFAULT_WIDTH_TEMPLATE = "00.0"

    // Arbitrary; only the bitmap's aspect ratio reaches the screen. Big enough that the
    // upscale to the real view stays sharp.
    private const val TEXT_SIZE = 200f

    // The header is drawn at a fixed dp size into its own unscaled ImageView, so it comes out
    // the same on every field size instead of riding along with the number's scale factor.
    private const val LABEL_HEIGHT_DP = 11.07f
    private const val ICON_SCALE = 1.4f
    private const val ICON_GAP_DP = 3f

    // Breathing room around the number. Applied as view padding rather than as margin inside
    // the bitmap: the bitmap is scaled by a factor that differs with field size, so a margin
    // drawn into it comes out a different width in every field. Padding is in view space, so
    // the gap is the same everywhere and lines up with the header's own padding.
    private const val EDGE_PADDING_DP = 5f

    private const val ICON_COLOR = 0xFF10B981.toInt()

    /** Matches the corner radius Karoo draws its own field cards with. */
    private const val CARD_RADIUS_DP = 10f

    /**
     * Size of the secondary part relative to the primary. Tune by eye on the device: it trades
     * how much height the primary gains against whether the secondary is still readable.
     */
    private const val SECONDARY_SCALE = 0.5f

    /**
     * The header depends on nothing that changes between samples, but render() runs on every
     * one, so it is drawn once per distinct field and reused. Alignment is not part of the key:
     * the bitmap is content-sized, so the layout does the aligning. Both colours are, because
     * on a zone fill they follow the fill -- without them in the key a field crossing into the
     * next zone would be served the previous zone's header.
     */
    private data class HeaderKey(
        val label: String,
        val iconRes: Int,
        val labelColor: Int,
        val iconColor: Int,
    )

    private val headerCache = ConcurrentHashMap<HeaderKey, Bitmap>()

    /**
     * Baseline that centres ink of [inkHeight] (whose bounds start at [inkTop], negative above
     * the baseline) inside a box of [boxHeight].
     *
     * The box keeps the full-size height even when a wide value shrinks the text, so the two
     * are not the same number. Returning -inkTop, which is right only when they match, left the
     * whole difference below the glyphs and made shrunk values ride high in the tile.
     */
    internal fun baselineFor(boxHeight: Int, inkHeight: Int, inkTop: Int): Float =
        (boxHeight - inkHeight) / 2f - inkTop

    fun render(
        context: Context,
        views: RemoteViews,
        config: ViewConfig,
        label: String,
        iconRes: Int,
        templatePrimary: String,
        templateSecondary: String,
        primary: String,
        secondary: String,
        primaryColor: Int,
        /** Fill for the whole field, or null to leave Karoo's own background showing. */
        backgroundColor: Int? = null,
    ) {
        val typeface = runCatching { context.resources.getFont(R.font.oswald_bold) }
            .getOrDefault(Typeface.DEFAULT_BOLD)

        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = TEXT_SIZE
            color = primaryColor
            isSubpixelText = true
        }
        val secondaryPaint = Paint(numberPaint).apply { textSize = TEXT_SIZE * SECONDARY_SCALE }

        val digits = Rect()
        numberPaint.getTextBounds(REFERENCE_GLYPHS, 0, REFERENCE_GLYPHS.length, digits)
        val digitHeight = digits.height().toFloat()
        val templateWidth = numberPaint.measureText(templatePrimary) +
            secondaryPaint.measureText(templateSecondary)
        if (digitHeight <= 0f || templateWidth <= 0f) return

        // Tight box: a fixed template width keeps the aspect ratio -- and so the on-screen text
        // size -- constant no matter how many characters the current value has.
        val w = templateWidth.toInt()
        val h = digitHeight.toInt()

        // Values wider than the template (a ride past ten hours, 4-digit power) shrink to fit.
        // Both parts shrink by the same factor so their size relationship is unchanged.
        val naturalWidth = numberPaint.measureText(primary) + secondaryPaint.measureText(secondary)
        if (naturalWidth > templateWidth) {
            val factor = templateWidth / naturalWidth
            numberPaint.textSize = TEXT_SIZE * factor
            secondaryPaint.textSize = TEXT_SIZE * SECONDARY_SCALE * factor
            numberPaint.getTextBounds(REFERENCE_GLYPHS, 0, REFERENCE_GLYPHS.length, digits)
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        fun startX(width: Float) = when (config.alignment) {
            Alignment.LEFT -> 0f
            Alignment.CENTER -> (w - width) / 2f
            Alignment.RIGHT -> w - width
        }

        val primaryWidth = numberPaint.measureText(primary)
        val secondaryWidth = secondaryPaint.measureText(secondary)
        val baseline = baselineFor(h, digits.height(), digits.top)
        val left = startX(primaryWidth + secondaryWidth)
        canvas.drawText(primary, left, baseline, numberPaint)

        if (secondary.isNotEmpty()) {
            // Superscript: the secondary sits to the right with its ink top on the primary's, so
            // it reads as attached to the value rather than as a second number below it.
            val small = Rect()
            secondaryPaint.getTextBounds(REFERENCE_GLYPHS, 0, REFERENCE_GLYPHS.length, small)
            val secondaryBaseline = baseline + digits.top - small.top
            canvas.drawText(secondary, left + primaryWidth, secondaryBaseline, secondaryPaint)
        }

        val onBackground = backgroundColor?.let { ZoneColors.onColor(it) }
        val header = header(
            context, typeface, label, iconRes,
            labelColor = onBackground ?: Theme.textColor(context),
            iconColor = onBackground ?: ICON_COLOR,
        )
        val pad = (EDGE_PADDING_DP * context.resources.displayMetrics.density).toInt()
        for (id in listOf(R.id.bitmap_start, R.id.bitmap_center, R.id.bitmap_end)) {
            views.setViewPadding(id, pad, header.height, pad, pad)
        }

        val target = when (config.alignment) {
            Alignment.LEFT -> R.id.bitmap_start
            Alignment.CENTER -> R.id.bitmap_center
            Alignment.RIGHT -> R.id.bitmap_end
        }
        for (id in listOf(R.id.bitmap_start, R.id.bitmap_center, R.id.bitmap_end)) {
            views.setViewVisibility(id, if (id == target) View.VISIBLE else View.GONE)
        }
        views.setImageViewBitmap(target, bitmap)

        // The header is aligned by the layout, so pick the copy sitting at the right edge.
        val headerTarget = when (config.alignment) {
            Alignment.LEFT -> R.id.header_start
            Alignment.CENTER -> R.id.header_center
            Alignment.RIGHT -> R.id.header_end
        }
        for (id in listOf(R.id.header_start, R.id.header_center, R.id.header_end)) {
            views.setViewVisibility(id, if (id == headerTarget) View.VISIBLE else View.GONE)
        }
        // A fresh RemoteViews per update means this has to repeat even though the bitmap is
        // cached -- only the drawing is saved, not the transfer.
        views.setImageViewBitmap(headerTarget, header)

        views.setInt(R.id.root, "setBackgroundColor", backgroundColor ?: Color.TRANSPARENT)
        // Karoo does NOT clip this view to its rounded card -- measured on a Karoo 3 ride
        // page, where a fill came out with square corners sitting over the rounded card. So
        // the rounding is ours to do, everywhere and not just in the page editor.
        // setViewOutlinePreferredRadius is API 31; minSdk here is 29, Karoo 3 runs 33, so on
        // anything older the fill simply stays square.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewOutlinePreferredRadius(R.id.root, CARD_RADIUS_DP, TypedValue.COMPLEX_UNIT_DIP)
            views.setBoolean(R.id.root, "setClipToOutline", true)
        }
    }

    /** Cached [renderHeader]; see [headerCache]. */
    private fun header(
        context: Context,
        typeface: Typeface,
        label: String,
        iconRes: Int,
        labelColor: Int,
        iconColor: Int,
    ): Bitmap = headerCache.getOrPut(HeaderKey(label, iconRes, labelColor, iconColor)) {
        renderHeader(context, typeface, label, iconRes, labelColor, iconColor)
    }

    /**
     * Icon plus short label, at a fixed dp size. The bitmap is exactly as wide as its content
     * plus the edge padding: it is drawn unscaled into a wrap_content view, and the layout's
     * gravity puts it at the same edge as the number. Sizing it to [ViewConfig.viewSize] instead
     * would inherit that value's inaccuracy as a visible offset or a clipped label.
     */
    private fun renderHeader(
        context: Context,
        typeface: Typeface,
        label: String,
        iconRes: Int,
        labelColor: Int,
        iconColor: Int,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val labelHeight = LABEL_HEIGHT_DP * density
        val iconSize = (labelHeight * ICON_SCALE).toInt()
        val iconGap = ICON_GAP_DP * density
        val padding = EDGE_PADDING_DP * density

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            color = labelColor
            isSubpixelText = true
            textSize = labelHeight
        }
        val bounds = Rect()
        paint.getTextBounds(label, 0, label.length, bounds)
        if (bounds.height() > 0) {
            // Scale so the label's ink height, not its nominal text size, matches the target.
            paint.textSize = labelHeight * labelHeight / bounds.height()
            paint.getTextBounds(label, 0, label.length, bounds)
        }

        val labelWidth = paint.measureText(label)
        val contentWidth = iconSize + iconGap + labelWidth
        val w = (contentWidth + 2 * padding).toInt()
        val h = (maxOf(iconSize.toFloat(), labelHeight) + 2 * padding).toInt()

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // The bitmap is content-sized, so there is only one place the content can go.
        val left = padding
        val iconTop = ((h - iconSize) / 2f).toInt()
        context.getDrawable(iconRes)?.mutate()?.apply {
            setTint(iconColor)
            setBounds(left.toInt(), iconTop, left.toInt() + iconSize, iconTop + iconSize)
            draw(canvas)
        }
        canvas.drawText(
            label,
            left + iconSize + iconGap,
            (h - bounds.height()) / 2f - bounds.top,
            paint,
        )
        return bitmap
    }
}
