package io.smartycoder.bignum

import android.util.Log
import io.smartycoder.bignum.fields.FieldCatalog
import io.smartycoder.bignum.fields.HudField
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension

class BigNumExtension : KarooExtension("bignum", BuildConfig.VERSION_NAME) {

    private lateinit var karoo: KarooSystemService

    override fun onCreate() {
        super.onCreate()
        karoo = KarooSystemService(applicationContext)
        karoo.connect { connected ->
            Log.i(TAG, "Karoo connected=$connected")
        }
    }

    override fun onDestroy() {
        if (this::karoo.isInitialized) karoo.disconnect()
        super.onDestroy()
    }

    // A property (not a local in `types`) because HudField is handed this same list to pick
    // its two slots out of.
    private val catalog by lazy { FieldCatalog.build(extension, karoo) }

    // Stays `by lazy`: `karoo` is lateinit, assigned in onCreate, so the list must not be
    // built at construction time.
    // HudField is appended rather than built into the catalogue: it composes catalogue entries,
    // so putting it in there would let a HUD be picked as its own slot.
    override val types by lazy { catalog + HudField(extension, catalog) }

    private companion object { const val TAG = "BigNum" }
}
