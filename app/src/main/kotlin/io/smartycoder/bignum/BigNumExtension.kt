package io.smartycoder.bignum

import android.util.Log
import io.smartycoder.bignum.fields.FieldCatalog
import io.smartycoder.bignum.fields.HudField
import io.smartycoder.bignum.heat.HeatFitWriter
import io.smartycoder.bignum.heat.HeatTracker
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.FitEffect

class BigNumExtension : KarooExtension("bignum", BuildConfig.VERSION_NAME) {

    private lateinit var karoo: KarooSystemService
    private lateinit var heat: HeatTracker

    override fun onCreate() {
        super.onCreate()
        karoo = KarooSystemService(applicationContext)
        // Started with the extension, not with a heat field: the day's heat training load has
        // to accumulate whether or not one is on screen. Its consumers can be registered before
        // the connect below; karoo-ext holds them and registers them once it is up.
        heat = HeatTracker(applicationContext, karoo).also { it.start() }
        karoo.connect { connected ->
            Log.i(TAG, "Karoo connected=$connected")
        }
    }

    /**
     * Called by the Karoo for each ride it records, because extension_info.xml sets fitFile.
     * Adds the heat data the Karoo does not record itself; see HeatFit.
     */
    override fun startFit(emitter: Emitter<FitEffect>) {
        HeatFitWriter(karoo, heat).start(emitter)
    }

    override fun onDestroy() {
        if (this::heat.isInitialized) heat.stop()
        if (this::karoo.isInitialized) karoo.disconnect()
        super.onDestroy()
    }

    // A property (not a local in `types`) because HudField is handed this same list to pick
    // its two slots out of.
    private val catalog by lazy { FieldCatalog.build(extension, karoo, heat) }

    // Stays `by lazy`: `karoo` is lateinit, assigned in onCreate, so the list must not be
    // built at construction time.
    // HudField is appended rather than built into the catalogue: it composes catalogue entries,
    // so putting it in there would let a HUD be picked as its own slot.
    override val types by lazy { catalog + HudField(extension, catalog) }

    private companion object { const val TAG = "BigNum" }
}
