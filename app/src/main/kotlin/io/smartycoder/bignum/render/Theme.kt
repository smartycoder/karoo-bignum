package io.smartycoder.bignum.render

import android.content.Context
import android.content.res.Configuration

/**
 * Karoo's light/dark setting is the Android night mode, so the field can read it from its own
 * configuration -- karoo-ext exposes nothing about the theme. Read per render rather than
 * cached: a theme switch then shows up on the next update without restarting anything.
 */
object Theme {

    private const val ON_DARK = 0xFFFFFFFF.toInt()
    private const val ON_LIGHT = 0xFF000000.toInt()

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** Colour for text that carries no zone colour of its own. */
    fun textColor(context: Context): Int = if (isNight(context)) ON_DARK else ON_LIGHT
}
