package io.example.bignum.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.RemoteViews
import io.example.bignum.R
import io.hammerhead.karooext.models.ViewConfig

// Renders the field's primary number to a Bitmap using the bundled Oswald Bold
// typeface and pushes it into the RemoteViews via setImageViewBitmap. The unit
// suffix (km/h, W, ...) is intentionally not drawn.
object FieldRenderer {

    private const val RIGHT_PADDING_PX = 8f
    private const val HEIGHT_SAFETY = 0.98f

    fun render(
        context: Context,
        views: RemoteViews,
        config: ViewConfig,
        primary: String,
        @Suppress("UNUSED_PARAMETER") unit: String,
        primaryColor: Int,
    ) {
        val w = config.viewSize.first
        val h = config.viewSize.second
        if (w <= 0 || h <= 0) return

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val typeface = runCatching { context.resources.getFont(R.font.oswald_bold) }
            .getOrDefault(Typeface.DEFAULT_BOLD)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            color = primaryColor
            isSubpixelText = true
        }

        // Maximum textSize that fits the available height.
        paint.textSize = 100f
        val ratio = paint.fontMetrics.let { (it.descent - it.ascent) / 100f }
        var size = (h.toFloat() / ratio) * HEIGHT_SAFETY
        paint.textSize = size

        // Shrink to fit width.
        while (size > 12f) {
            if (paint.measureText(primary) <= w - RIGHT_PADDING_PX) break
            size *= 0.95f
            paint.textSize = size
        }

        val fm = paint.fontMetrics
        val baseline = (h - (fm.descent - fm.ascent)) / 2f - fm.ascent
        val textWidth = paint.measureText(primary)
        val startX = w - RIGHT_PADDING_PX - textWidth

        canvas.drawText(primary, startX, baseline, paint)
        views.setImageViewBitmap(R.id.bitmap, bitmap)
    }
}
