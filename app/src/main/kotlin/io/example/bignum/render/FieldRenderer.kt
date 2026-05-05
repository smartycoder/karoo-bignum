package io.example.bignum.render

import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews
import io.example.bignum.R
import io.hammerhead.karooext.models.ViewConfig

object FieldRenderer {

    fun applyConfig(views: RemoteViews, config: ViewConfig) {
        val gravity = when (config.alignment) {
            ViewConfig.Alignment.LEFT   -> Gravity.START or Gravity.CENTER_VERTICAL
            ViewConfig.Alignment.CENTER -> Gravity.CENTER
            ViewConfig.Alignment.RIGHT  -> Gravity.END or Gravity.CENTER_VERTICAL
        }
        views.setInt(R.id.primary, "setGravity", gravity)

        val baseSp = (config.textSize * 1.05f).coerceAtLeast(24f)
        views.setTextViewTextSize(R.id.primary, TypedValue.COMPLEX_UNIT_SP, baseSp)
        views.setTextViewTextSize(R.id.unit, TypedValue.COMPLEX_UNIT_SP, baseSp * 0.30f)
    }

    fun fill(views: RemoteViews, primary: String, unit: String, primaryColor: Int) {
        views.setTextViewText(R.id.primary, primary)
        views.setTextViewText(R.id.unit, unit)
        views.setTextColor(R.id.primary, primaryColor)
    }
}
