package io.example.bignum.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.RemoteViews
import io.example.bignum.R
import io.hammerhead.karooext.models.ViewConfig

// Renders the field's text to a Bitmap using the bundled Oswald Bold typeface and
// pushes it into the RemoteViews via setImageViewBitmap. Sidesteps the unreliable
// cross-process loading of font resources by RemoteViews TextViews.
object FieldRenderer {

    private const val UNIT_COLOR = 0xFFAAAAAA.toInt()
    private const val UNIT_RATIO = 0.30f
    private const val GAP_PX = 12f
    private const val MAX_HEIGHT_FRACTION = 0.85f

    fun render(
        context: Context,
        views: RemoteViews,
        config: ViewConfig,
        primary: String,
        unit: String,
        primaryColor: Int,
    ) {
        val w = config.viewSize.first
        val h = config.viewSize.second
        if (w <= 0 || h <= 0) return

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val typeface = runCatching { context.resources.getFont(R.font.oswald_bold) }
            .getOrDefault(Typeface.DEFAULT_BOLD)

        val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            color = primaryColor
            isSubpixelText = true
        }
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            color = UNIT_COLOR
            isSubpixelText = true
        }

        var primarySize = h * MAX_HEIGHT_FRACTION
        primaryPaint.textSize = primarySize
        unitPaint.textSize = primarySize * UNIT_RATIO

        while (primarySize > 12f) {
            val totalWidth = primaryPaint.measureText(primary) +
                if (unit.isEmpty()) 0f else unitPaint.measureText(unit) + GAP_PX
            if (totalWidth <= w.toFloat()) break
            primarySize *= 0.95f
            primaryPaint.textSize = primarySize
            unitPaint.textSize = primarySize * UNIT_RATIO
        }

        val pFm = primaryPaint.fontMetrics
        val pTextHeight = pFm.descent - pFm.ascent
        val baseline = (h - pTextHeight) / 2f - pFm.ascent

        val pWidth = primaryPaint.measureText(primary)
        val uWidth = if (unit.isEmpty()) 0f else unitPaint.measureText(unit) + GAP_PX
        val totalW = pWidth + uWidth

        val startX: Float = when (config.alignment) {
            ViewConfig.Alignment.LEFT -> 0f
            ViewConfig.Alignment.CENTER -> (w - totalW) / 2f
            ViewConfig.Alignment.RIGHT -> w - totalW
        }

        canvas.drawText(primary, startX, baseline, primaryPaint)
        if (unit.isNotEmpty()) {
            canvas.drawText(unit, startX + pWidth + GAP_PX, baseline, unitPaint)
        }

        views.setImageViewBitmap(R.id.bitmap, bitmap)
    }
}
