package io.smartycoder.bignum.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlin.math.ceil
import java.util.concurrent.ConcurrentHashMap
import io.smartycoder.bignum.FontSetting
import io.smartycoder.bignum.NumberFont
import io.smartycoder.bignum.R
import io.smartycoder.bignum.fields.Wedge
import io.hammerhead.karooext.models.ViewConfig
import io.hammerhead.karooext.models.ViewConfig.Alignment

/**
 * Renders a field -- its icon, short label and primary number -- to a Bitmap in the typeface
 * the rider picked, and pushes it into the RemoteViews via setImageViewBitmap.
 * The unit suffix (km/h, W, ...) is intentionally not drawn.
 *
 * The header is ours rather than Karoo's: the field sends UpdateGraphicConfig(showHeader =
 * false), which buys the whole tile and lets the label be a short form ("PWR 5s") instead of
 * Karoo's uppercased displayName ("POWER 5S AVG"). The label is always Oswald -- see
 * [LABEL_FONT] -- so only the number follows the rider's choice.
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

    // The size the first measurement is taken at, before it is scaled to the view -- see
    // [measure]. Not a drawing size: nothing is drawn at 200 unless a tile happens to want it.
    private const val TEXT_SIZE = 200f

    // Guard rails on the derived size. The floor keeps a nonsense viewSize from producing a
    // one-pixel bitmap; the ceiling bounds the raster on a tile larger than any this screen has.
    private const val MIN_TEXT_SIZE = 12f
    private const val MAX_TEXT_SIZE = 400f

    // The header is drawn at a fixed dp size into its own unscaled ImageView, so it comes out
    // the same on every field size instead of riding along with the number's scale factor.
    private const val LABEL_HEIGHT_DP = 11.07f

    // What LABEL_HEIGHT_DP is measured against. A capital with flat top and bottom: "O" or "S"
    // would carry the overshoot rounded glyphs are drawn with, and any label's own ink carries
    // whatever ascenders, descenders and digits it happens to hold.
    private const val CAP_REFERENCE = "H"

    // The header is always Oswald, whatever the number is set in. It is drawn at 11dp, where
    // the choices that make a face good for a big number stop paying: at that size Saira's
    // narrow widths lose the space between a label's words ("AVG VAM" reads as one), and its
    // light weights thin out. Oswald at one fixed size is the constant the tile is read by.
    private val LABEL_FONT = FontSetting(NumberFont.OSWALD, width = 100, weight = 700)
    private const val ICON_SCALE = 1.4f
    private const val ICON_GAP_DP = 3f

    /**
     * The icon's height when it is the whole header, with no label beside it.
     *
     * Smaller than the 15.5dp an icon gets in a full header, and deliberately so: with a label
     * next to it the icon is half of a caption and is sized to hold its own against the words;
     * alone under the HUD's bar it is a mark saying which half is which, read once and then
     * ignored. At the full size it crowded a wide value -- a three-digit power fills its half
     * edge to edge, and the icon met the first digit.
     */
    private const val ICON_ONLY_HEIGHT_DP = 11f

    /**
     * Inset of that icon from the tile's top and start edges, as a share of [edgePadding] -- a
     * fraction rather than its own dp value, so it cannot drift away from the padding it is
     * defined as half of.
     *
     * Half is what the number and a full header keep: a mark belongs in the corner, and the
     * number's own padding is what keeps the two apart, so the icon can sit closer to the edge
     * than the content it is labelling without touching it.
     */
    private const val ICON_ONLY_PADDING_FRACTION = 0.5f

    /**
     * Breathing room around a field's content, in pixels.
     *
     * Applied as view padding rather than as a margin inside the bitmap: the bitmap is scaled by
     * a factor that differs with field size, so a margin drawn into it comes out a different
     * width in every field. Padding is in view space, so the gap is the same everywhere.
     *
     * From R.dimen.field_edge_padding, which the HUD's layout also insets its divider by, and
     * read with getDimensionPixelSize so code and inflater round identically. It used to be a
     * `5f` here multiplied by density and truncated, with a matching literal `5dp` in the layout
     * and a comment promising the two agreed.
     */
    internal fun edgePadding(context: Context): Int =
        context.resources.getDimensionPixelSize(R.dimen.field_edge_padding)

    private const val ICON_COLOR = 0xFF10B981.toInt()

    /** Matches the corner radius Karoo draws its own field cards with. */
    // internal, not private: the HUD's zone bar sits on the card's top edge and has to round off
    // with it, and two copies of this number would drift apart.
    internal const val CARD_RADIUS_DP = 10f

    // Width of the outline stroke drawn under the number and label when a wedge sits behind
    // them, as a fraction of the paint's text size so it scales the same way shrunk text does
    // rather than looking heavy on a shrunk value. Only ever used when a wedge is present, so
    // every other field's rendering is untouched.
    private const val TEXT_OUTLINE_WIDTH_FRACTION = 0.05f

    // Square canvas the wedge is drawn into before being stretched to fill the tile. The wedge
    // is a linear ramp with straight edges, so an independent x/y stretch under scaleType="fitXY"
    // still leaves it a wedge -- only its angle changes, and the angle carries no information.
    private const val WEDGE_BITMAP_SIZE = 64

    /**
     * Size of the secondary part relative to the primary. Tune by eye on the device: it trades
     * how much height the primary gains against whether the secondary is still readable.
     */
    internal const val SECONDARY_SCALE = 0.5f

    /**
     * The header depends on nothing that changes between samples, but render() runs on every
     * one, so it is drawn once per distinct field and reused. Alignment is not part of the key:
     * the bitmap is content-sized, so the layout does the aligning. Both colours are, because
     * on a zone fill they follow the fill -- without them in the key a field crossing into the
     * next zone would be served the previous zone's header. [outline] is too: the same label
     * gets drawn with and without the wedge outline depending on whether this update carries one.
     */
    private data class HeaderKey(
        val label: String,
        val iconRes: Int,
        val labelColor: Int,
        val iconColor: Int,
        val outline: Boolean,
        val iconOnly: Boolean,
    )

    private val headerCache = ConcurrentHashMap<HeaderKey, Bitmap>()

    // IntArray, so iterating allocates neither a list nor boxed ids.
    private val BITMAP_IDS = intArrayOf(R.id.bitmap_start, R.id.bitmap_center, R.id.bitmap_end)
    private val HEADER_IDS = intArrayOf(R.id.header_start, R.id.header_center, R.id.header_end)

    /**
     * Typeface per setting. Building one is a native call that allocates, and render() runs on
     * every sample of every field, so without this a ride would churn through thousands of
     * identical Typefaces. Bounded by the settings on offer, so it never needs eviction.
     */
    private val typefaceCache = ConcurrentHashMap<FontSetting, Typeface>()

    private data class MetricsKey(
        val font: FontSetting,
        val primary: String,
        val secondary: String,
        val boxWidth: Int,
        val boxHeight: Int,
    )

    /**
     * The bitmap a field draws into, and the numbers needed to place text in it. All of it
     * follows from the font and the width template, neither of which changes between samples,
     * so measuring it on every one shaped twenty glyphs and measured two strings for an answer
     * that was already known.
     *
     * [textSize] is not always [TEXT_SIZE]: see [measure].
     */
    private class Metrics(
        val textSize: Float,
        val width: Int,
        val height: Int,
        val digitTop: Int,
        val templateWidth: Float,
    )

    private val metricsCache = ConcurrentHashMap<MetricsKey, Metrics>()

    /**
     * Null when the font or template is degenerate enough to leave nothing to draw.
     *
     * The raster is sized to the box the number will actually occupy, so the fit* scaleType that
     * puts it on screen scales it by 1.0 and resamples nothing. That is both the cheapest and
     * the sharpest option, and it is the only one that is right for every tile: Karoo hands out
     * views from 238x142 to 478x288, a two-fold range of heights, and a single raster size is
     * necessarily wasteful at one end and blurry at the other. Measured on a Karoo 3, the old
     * fixed size was drawing 1.7x too many pixels on a half tile and stretching 1.6x on the
     * largest one.
     *
     * [TEXT_SIZE] survives only as the size the first measurement is taken at, and as the
     * fallback when [ViewConfig.viewSize] reports nothing usable.
     */
    private fun measure(
        number: Paint,
        secondary: Paint,
        templatePrimary: String,
        templateSecondary: String,
        boxWidth: Int,
        boxHeight: Int,
    ): Metrics? {
        val digits = Rect()
        number.getTextBounds(REFERENCE_GLYPHS, 0, REFERENCE_GLYPHS.length, digits)
        var width = number.measureText(templatePrimary) + secondary.measureText(templateSecondary)
        if (digits.height() <= 0 || width <= 0f) return null

        // What fit* would scale the reference raster by. Pre-applying it leaves nothing for the
        // ImageView to do; whichever of the two bounds is tighter is the one that decides the
        // on-screen size, exactly as before.
        var size = TEXT_SIZE
        if (boxWidth > 0 && boxHeight > 0) {
            val fit = minOf(boxWidth / width, boxHeight / digits.height().toFloat())
            size = (TEXT_SIZE * fit).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
            number.textSize = size
            secondary.textSize = size * SECONDARY_SCALE
            number.getTextBounds(REFERENCE_GLYPHS, 0, REFERENCE_GLYPHS.length, digits)
            width = number.measureText(templatePrimary) + secondary.measureText(templateSecondary)
            if (digits.height() <= 0 || width <= 0f) return null
        }
        // ceil, not truncate: measureText returns an advance, and rounding it down shaves a
        // column off the outermost glyph.
        return Metrics(size, ceil(width).toInt(), digits.height(), digits.top, width)
    }

    private fun typefaceFor(context: Context, font: FontSetting): Typeface =
        typefaceCache.getOrPut(font) {
            val res = when (font.font) {
                NumberFont.OSWALD -> R.font.oswald_bold
                NumberFont.SAIRA -> R.font.saira
            }
            val base = runCatching { context.resources.getFont(res) }
                .getOrDefault(Typeface.DEFAULT_BOLD)
            if (!font.hasAxes) {
                base
            } else {
                // Paint is the only public way to instance a variable font that is already a
                // Typeface: Typeface.Builder can only take a file or an asset, and this one
                // lives in res/font. The derived Typeface is what we keep; the Paint is
                // scaffolding, which is exactly why it must not be built per frame.
                Paint().apply {
                    typeface = base
                    fontVariationSettings = "'wght' ${font.weight}, 'wdth' ${font.width}"
                }.typeface ?: base
            }
        }

    /**
     * How much a value has to shrink to fit the room it has, as a factor of the size the width
     * template was fitted at. 1 leaves it alone.
     *
     * The room is the tile, not the template. The template is what holds a field's size steady
     * as digits come and go, and while width is what limits the fit the two are the same number
     * -- but once height is what limits it, the template is narrower than the tile, and
     * measuring the overflow against it shrank values that had room to spare: a 4-digit power
     * lost a quarter of its height on a tile with 66px of unused width beside it.
     *
     * Falls back to the template where that is the wider of the two, which is the degenerate
     * tile [measure] clamps rather than fits.
     */
    internal fun shrinkFactor(naturalWidth: Float, templateWidth: Float, boxWidth: Int): Float {
        val room = maxOf(templateWidth, boxWidth.toFloat())
        return if (naturalWidth > room && naturalWidth > 0f) room / naturalWidth else 1f
    }

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

    /**
     * Where the top edge of a raster of [inkHeight] lands inside a box of [boxHeight], for the
     * fit* scaleType [alignment] selects.
     *
     * Mirrors what the ImageView does rather than deciding anything: fitStart puts the raster at
     * the top, fitCenter centres it, fitEnd puts it at the bottom. Pure, so the one question that
     * depends on it -- whether a number would end up under the HUD's zone bar -- can be checked
     * without a Canvas.
     */
    internal fun inkTopFor(alignment: Alignment, boxHeight: Int, inkHeight: Int): Int =
        when (alignment) {
            Alignment.LEFT -> 0
            Alignment.CENTER -> (boxHeight - inkHeight) / 2
            Alignment.RIGHT -> boxHeight - inkHeight
        }.coerceAtLeast(0)

    /**
     * Puts the chosen face on [this]. A Paint copy (the secondary number) inherits both the
     * typeface and the feature settings, so the superscript comes out in step with the primary.
     *
     * Oswald ships as a static Bold with no axes and no `tnum` table, so it is left alone and
     * comes out exactly as it did before this setting existed.
     */
    /**
     * Puts the rider's number font on [paint], for a caller outside this object.
     *
     * The zone bar is the only one: the value it draws is a number, so it follows the same
     * setting every other number does. A wrapper rather than making [applyFont] itself internal,
     * because the typeface is only half of it -- the tabular-figures feature is the other half,
     * and a caller that took the Typeface alone would get a value that shifts sideways as its
     * digits change, which is the one thing this font setting exists to prevent.
     */
    internal fun applyNumberFont(paint: Paint, context: Context, font: FontSetting) {
        paint.applyFont(context, font)
    }

    private fun Paint.applyFont(context: Context, font: FontSetting) {
        typeface = typefaceFor(context, font)
        // Equal-width digits: without this the value shifts sideways as digits change, because
        // the bitmap's right edge is pinned and Saira's "1" is far narrower than its "0".
        if (font.hasAxes) fontFeatureSettings = "tnum"
    }

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
        font: FontSetting,
        /** Fill for the whole field, or null to leave Karoo's own background showing. */
        backgroundColor: Int? = null,
        /** Coloured wedge drawn behind the number, or null for every field but Grade. */
        wedge: Wedge? = null,
        /**
         * False when this call draws one slot of a composite tile (the HUD field) rather than a
         * whole Karoo card. Each slot is a full numeric_field.xml, so rounding it here as well as
         * Karoo rounding the card outside would draw two nested rounded corners -- and a Grade
         * wedge, which reaches edge to edge, would get clipped by the inner one.
         */
        roundCorners: Boolean = true,
        /**
         * True when the header is reduced to its icon, with no label beside it. The HUD's slots
         * use it while the zone bar is up: the bar has taken the row the two labels were read
         * from, and a label repeated under it would say twice what the tile already says once.
         */
        iconOnlyHeader: Boolean = false,
        /**
         * Height of something drawn OVER the top of this view that the content has to stay clear
         * of -- the HUD's zone bar, and nothing else so far.
         *
         * It is not subtracted from the view's height, and that is the point: the bar is a
         * sibling laid on top rather than a row above, so the number keeps the whole tile and
         * gives up height only in the cases where it would actually end up underneath the bar.
         * On a right-aligned field it never does, because fitEnd pins the raster to the bottom.
         */
        overlayTopPx: Int = 0,
    ) {
        // The header comes first because the number's box is what it leaves behind. It is cached
        // and depends on nothing the number does, so this is a reorder rather than extra work.
        val onBackground = backgroundColor?.let { ZoneColors.onColor(it) }
        val header = header(
            context, label, iconRes,
            labelColor = onBackground ?: Theme.textColor(context),
            iconColor = onBackground ?: ICON_COLOR,
            outline = wedge != null,
            iconOnly = iconOnlyHeader,
        )
        val pad = edgePadding(context)

        // What the number actually gets on screen, from the view Karoo reports. The layout puts
        // the header above it and pads the other three sides; see numeric_field.xml.
        val (viewWidth, viewHeight) = config.viewSize
        val boxWidth = viewWidth - 2 * pad
        // An icon-only header does not reserve a band up front: it is a mark in the corner, and
        // the number's raster is normally short enough to leave it alone. See headerPad below
        // for the two cases where it is not, which are the ones that buy the band back.
        val fullBox = viewHeight - pad - (if (iconOnlyHeader) 0 else header.height)

        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            applyFont(context, font)
            this.textSize = TEXT_SIZE
            color = primaryColor
            isSubpixelText = true
        }
        val secondaryPaint = Paint(numberPaint).apply { textSize = TEXT_SIZE * SECONDARY_SCALE }

        // Tight box: a fixed template width keeps the aspect ratio -- and so the on-screen text
        // size -- constant no matter how many characters the current value has.
        //
        // A local rather than the inline lookup it replaces, because the icon-only path can need
        // a second measurement against a shorter box. The paints are reset to TEXT_SIZE before
        // each attempt: measure() scales the paints it is handed, so a second call must not start
        // from the size the first one left on them.
        fun measured(boxHeight: Int): Metrics? {
            // Reset BEFORE the cache lookup, not between it and measure(): on a cache hit the
            // paints keep whatever the previous call left on them, and only textSize is written
            // back afterwards. Resetting unconditionally makes both paths start from the same
            // state whatever else measure() may come to touch.
            numberPaint.textSize = TEXT_SIZE
            secondaryPaint.textSize = TEXT_SIZE * SECONDARY_SCALE
            val key = MetricsKey(font, templatePrimary, templateSecondary, boxWidth, boxHeight)
            metricsCache[key]?.let { return it }
            return measure(
                numberPaint, secondaryPaint, templatePrimary, templateSecondary, boxWidth, boxHeight,
            )?.also { metricsCache[key] = it }
        }

        var metrics = measured(fullBox) ?: return
        // How much room comes off the top of the number.
        //
        // A full header always takes its own height, as it always has. An icon-only header takes
        // NOTHING for itself -- the icon is a mark in the corner, drawn over the tile rather than
        // above it -- and gives up height only to an overlay the number would otherwise sit
        // under. Where the raster lands decides that, and it depends on the alignment: fitEnd
        // pins it to the bottom, so a right-aligned number never meets the bar and never pays;
        // fitCenter leaves half the slack above it; fitStart pins it to the top, so it always
        // pays. The re-measure means the number is drawn at the size it ends up shown at rather
        // than being scaled down by the view afterwards.
        // Clamped to half the box: an overlay taller than the room it sits over would leave the
        // number a negative budget, and measure() would hand back nothing at all.
        val overlay = overlayTopPx.coerceIn(0, (fullBox / 2).coerceAtLeast(0))
        val headerPad = when {
            !iconOnlyHeader -> header.height
            inkTopFor(config.alignment, fullBox, metrics.height) >= overlay -> 0
            // Falls back to the metrics already in hand rather than returning: a `return` here
            // draws NOTHING, and an empty half is a worse answer than a number the bar clips a
            // little. The view's fit* then scales what we measured for the taller box, which is
            // the same degradation every other over-tight tile in this renderer gets.
            else -> measured(fullBox - overlay)?.let { metrics = it; overlay } ?: 0
        }
        numberPaint.textSize = metrics.textSize
        secondaryPaint.textSize = metrics.textSize * SECONDARY_SCALE
        val h = metrics.height
        var digitTop = metrics.digitTop
        // Tracked separately from the box height h: a shrunk value has less ink than the box,
        // and baselineFor centres the ink it is given inside the box it is given.
        var digitHeight = metrics.height

        // Measured once and kept: in the common case the shrink below does not fire, and these
        // are the same two numbers naturalWidth is built from.
        var primaryWidth = numberPaint.measureText(primary)
        var secondaryWidth = secondaryPaint.measureText(secondary)

        // Values wider than the room they have (a ride past ten hours, 4-digit power) shrink to
        // fit. Both parts shrink by the same factor so their size relationship is unchanged.
        val naturalWidth = primaryWidth + secondaryWidth
        val factor = shrinkFactor(naturalWidth, metrics.templateWidth, boxWidth)
        if (factor < 1f) {
            numberPaint.textSize = metrics.textSize * factor
            secondaryPaint.textSize = metrics.textSize * SECONDARY_SCALE * factor
            val shrunk = Rect()
            numberPaint.getTextBounds(REFERENCE_GLYPHS, 0, REFERENCE_GLYPHS.length, shrunk)
            digitTop = shrunk.top
            digitHeight = shrunk.height()
            primaryWidth = numberPaint.measureText(primary)
            secondaryWidth = secondaryPaint.measureText(secondary)
        }

        // A wedge cuts diagonally across the tile, so a single contrast threshold that flips the
        // whole number black-on-white cannot work -- only part of the number crosses it. An
        // outline in the contrasting colour survives regardless of where the wedge's edge falls.
        // Wide enough for whatever the value came out as. Narrower values keep the template's
        // width, which is what holds their on-screen size steady as digits come and go; a value
        // that outgrew the template and was left unshrunk needs the room it actually takes, or
        // the alignment would push its leading digits off the bitmap.
        val w = maxOf(metrics.width, ceil(primaryWidth + secondaryWidth).toInt())

        val outlineColor = wedge?.let { ZoneColors.onColor(primaryColor) }

        // Room for that outline. The box is exactly the digits' ink, so a stroke centred on the
        // glyph contour hangs half its width outside it -- and got clipped away at precisely the
        // extremes of each glyph, which is where it was most needed. Only paid when a wedge is
        // actually behind the number, which costs Grade about 6% of its height against a
        // neighbouring tile; measured at 102px against 109px on a half tile.
        val margin = if (outlineColor == null) {
            0
        } else {
            ceil(numberPaint.textSize * TEXT_OUTLINE_WIDTH_FRACTION / 2f).toInt()
        }

        val bitmap = Bitmap.createBitmap(w + 2 * margin, h + 2 * margin, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        fun startX(width: Float) = margin + when (config.alignment) {
            Alignment.LEFT -> 0f
            Alignment.CENTER -> (w - width) / 2f
            Alignment.RIGHT -> w - width
        }

        val baseline = margin + baselineFor(h, digitHeight, digitTop)
        val left = startX(primaryWidth + secondaryWidth)
        drawOutlined(canvas, primary, left, baseline, numberPaint, outlineColor)

        if (secondary.isNotEmpty()) {
            // Superscript: the secondary sits to the right with its ink top on the primary's, so
            // it reads as attached to the value rather than as a second number below it.
            val small = Rect()
            secondaryPaint.getTextBounds(REFERENCE_GLYPHS, 0, REFERENCE_GLYPHS.length, small)
            val secondaryBaseline = baseline + digitTop - small.top
            drawOutlined(canvas, secondary, left + primaryWidth, secondaryBaseline, secondaryPaint, outlineColor)
        }

        if (wedge != null) {
            views.setViewVisibility(R.id.wedge, View.VISIBLE)
            views.setImageViewBitmap(R.id.wedge, wedgeBitmap(wedge))
        } else {
            views.setViewVisibility(R.id.wedge, View.GONE)
        }

        // The bitmap carries the outline margin outside the digits' box, so the view hands that
        // much padding back. Without it the bitmap is larger than the space it is fitted into
        // and fit* shrinks the whole thing to make room, leaving the one field that draws a
        // wedge visibly smaller than the tile beside it -- measured at 102px against 109px.
        // Zero when there is no outline, so every other field keeps the padding it had.
        //
        // Floored at zero: a low-density screen can have less padding than the outline needs,
        // and there the outline goes back to clipping rather than the number leaving the tile.
        val sidePad = (pad - margin).coerceAtLeast(0)
        val topPad = (headerPad - margin).coerceAtLeast(0)
        for (id in BITMAP_IDS) {
            views.setViewPadding(id, sidePad, topPad, sidePad, sidePad)
        }

        val target = when (config.alignment) {
            Alignment.LEFT -> R.id.bitmap_start
            Alignment.CENTER -> R.id.bitmap_center
            Alignment.RIGHT -> R.id.bitmap_end
        }
        for (id in BITMAP_IDS) {
            views.setViewVisibility(id, if (id == target) View.VISIBLE else View.GONE)
        }
        views.setImageViewBitmap(target, bitmap)

        // The header is aligned by the layout, so pick the copy sitting at the right edge. The
        // icon-only header ignores that and always takes the start corner: it is a mark, not a
        // caption, and it belongs in the same corner on both halves of a HUD so the tile reads as
        // one row of two rather than as two fields that happen to be adjacent.
        val headerTarget = when {
            iconOnlyHeader -> R.id.header_start
            config.alignment == Alignment.LEFT -> R.id.header_start
            config.alignment == Alignment.CENTER -> R.id.header_center
            else -> R.id.header_end
        }
        for (id in HEADER_IDS) {
            views.setViewVisibility(id, if (id == headerTarget) View.VISIBLE else View.GONE)
        }
        // The icon is drawn over the tile, so on its own it would land underneath the bar. View
        // padding rather than a taller bitmap: the bitmap is cached across every field that
        // shares a header, and baking an offset into it would key that cache on the overlay too.
        // Zero whenever there is no overlay, which is every field but a HUD slot under its bar.
        //
        // Written to EVERY header id, the way BITMAP_IDS already is, and not only to the target:
        // RemoteViews actions are replayed onto views the host has already inflated, so padding
        // left on a copy that is GONE today comes back with it -- turn the bar off, or change the
        // alignment, and that header reappears pushed a bar's height down the tile.
        for (id in HEADER_IDS) {
            views.setViewPadding(id, 0, if (id == headerTarget) overlay else 0, 0, 0)
        }
        // A fresh RemoteViews per update means this has to repeat even though the bitmap is
        // cached -- only the drawing is saved, not the transfer.
        views.setImageViewBitmap(headerTarget, header)

        views.setInt(R.id.root, "setBackgroundColor", backgroundColor ?: Color.TRANSPARENT)
        // Karoo does NOT clip this view to its rounded card -- measured on a Karoo 3 ride
        // page, where a fill came out with square corners sitting over the rounded card. So
        // the rounding is ours to do, everywhere and not just in the page editor. Except when
        // roundCorners is false: each slot of the HUD field is a whole numeric_field.xml sitting
        // inside Karoo's one card, so rounding it too would draw two nested rounded cards, and a
        // Grade wedge -- which reaches its slot's own edges -- would get clipped at the inner
        // corners it was never meant to have.
        // setViewOutlinePreferredRadius is API 31; minSdk here is 29, Karoo 3 runs 33, so on
        // anything older the fill simply stays square.
        if (roundCorners && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewOutlinePreferredRadius(R.id.root, CARD_RADIUS_DP, TypedValue.COMPLEX_UNIT_DIP)
            views.setBoolean(R.id.root, "setClipToOutline", true)
        }
    }

    /**
     * Draws [text] with a thin outline in [outlineColor] first, then the fill on top, using the
     * same [paint] for both passes -- so the outline always matches the fill's size and position
     * exactly. Null [outlineColor] draws the fill only, unchanged from before wedges existed.
     */
    private fun drawOutlined(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, outlineColor: Int?) {
        if (outlineColor != null) {
            val fillColor = paint.color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = paint.textSize * TEXT_OUTLINE_WIDTH_FRACTION
            paint.color = outlineColor
            canvas.drawText(text, x, y, paint)
            paint.style = Paint.Style.FILL
            paint.color = fillColor
        }
        canvas.drawText(text, x, y, paint)
    }

    /**
     * A right triangle sized so that, once stretched to fill the tile, it reaches [Wedge.fraction]
     * of the tile height at the far edge -- the near edge stays at zero. [Wedge.rising] mirrors
     * which edge is the far one: right for a climb, left for a descent.
     */
    private fun wedgeBitmap(wedge: Wedge): Bitmap {
        val size = WEDGE_BITMAP_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = wedge.color
        }
        val w = size.toFloat()
        val h = size.toFloat()
        val peak = h * wedge.fraction
        val path = Path().apply {
            moveTo(0f, h)
            lineTo(w, h)
            if (wedge.rising) {
                lineTo(w, h - peak)
            } else {
                lineTo(0f, h - peak)
            }
            close()
        }
        canvas.drawPath(path, paint)
        return bitmap
    }

    /** Cached [renderHeader]; see [headerCache]. */
    private fun header(
        context: Context,
        label: String,
        iconRes: Int,
        labelColor: Int,
        iconColor: Int,
        outline: Boolean,
        iconOnly: Boolean = false,
    ): Bitmap = headerCache.getOrPut(
        HeaderKey(label, iconRes, labelColor, iconColor, outline, iconOnly),
    ) {
        renderHeader(context, label, iconRes, labelColor, iconColor, outline, iconOnly)
    }

    /**
     * Icon plus short label, at a fixed dp size. The bitmap is exactly as wide as its content
     * plus the edge padding: it is drawn unscaled into a wrap_content view, and the layout's
     * gravity puts it at the same edge as the number. Sizing it to [ViewConfig.viewSize] instead
     * would inherit that value's inaccuracy as a visible offset or a clipped label.
     */
    internal fun renderHeader(
        context: Context,
        label: String,
        iconRes: Int,
        labelColor: Int,
        iconColor: Int,
        outline: Boolean,
        iconOnly: Boolean = false,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val labelHeight = LABEL_HEIGHT_DP * density
        val iconSize =
            if (iconOnly) (ICON_ONLY_HEIGHT_DP * density).toInt()
            else (labelHeight * ICON_SCALE).toInt()
        val iconGap = ICON_GAP_DP * density
        val full = edgePadding(context).toFloat()
        val padding = if (iconOnly) full * ICON_ONLY_PADDING_FRACTION else full

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            applyFont(context, LABEL_FONT)
            color = labelColor
            isSubpixelText = true
            textSize = labelHeight
        }
        // Measured on a capital rather than on the label, and the label drawn in capitals.
        // Scaling each label's own ink to the target made the size depend on which glyphs it
        // happened to contain: "TIME lap" reaches from the cap line to the "p"'s tail, so its
        // capitals came out a fifth shorter than "HR"'s to keep the whole ink at 11dp, and
        // centring that taller ink pushed them off the line every other label sat on.
        val bounds = Rect()
        paint.getTextBounds(CAP_REFERENCE, 0, CAP_REFERENCE.length, bounds)
        if (bounds.height() > 0) {
            paint.textSize = labelHeight * labelHeight / bounds.height()
            paint.getTextBounds(CAP_REFERENCE, 0, CAP_REFERENCE.length, bounds)
        }

        // The gap goes with the label: an icon on its own is padded like everything else, and
        // carrying a gap for a label that is not drawn would push it off the corner it is meant
        // to sit in.
        val text = if (iconOnly) "" else label.uppercase()
        val labelWidth = if (iconOnly) 0f else paint.measureText(text)
        val contentWidth = if (iconOnly) iconSize.toFloat() else iconSize + iconGap + labelWidth
        val w = (contentWidth + 2 * padding).toInt()
        // The label's own height is out of it when there is no label: keeping maxOf here would
        // hold the bitmap at the 11dp label height and undo the smaller icon entirely.
        val contentHeight = if (iconOnly) iconSize.toFloat() else maxOf(iconSize.toFloat(), labelHeight)
        val h = (contentHeight + 2 * padding).toInt()

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
        // The wedge reaches the tile's top edge above 12%, where the label lives, so the label
        // needs the same outline treatment as the number once a wedge is behind it.
        val outlineColor = if (outline) ZoneColors.onColor(labelColor) else null
        drawOutlined(
            canvas,
            text,
            left + iconSize + iconGap,
            baselineFor(h, bounds.height(), bounds.top),
            paint,
            outlineColor,
        )
        return bitmap
    }
}
