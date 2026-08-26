package io.smartycoder.bignum.fields

import io.smartycoder.bignum.render.FieldRenderer
import io.smartycoder.bignum.render.ZoneKind
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile.PreferredUnit

/**
 * A numeric field that needs nothing beyond the values passed here. Fields with behaviour of
 * their own (speed, power, ...) keep their own class; everything else is an instance of this.
 */
class SimpleField(
    extension: String,
    typeId: String,
    karoo: KarooSystemService,
    override val upstreamTypeId: String,
    override val label: String,
    override val iconRes: Int,
    override val format: (Double, PreferredUnit?) -> Pair<String, String>,
    override val zoneKind: ZoneKind? = null,
    override val previewValue: Double = 0.0,
    override val widthTemplate: String = FieldRenderer.DEFAULT_WIDTH_TEMPLATE,
    override val valueField: String? = null,
    override val raisedTailAllowed: Boolean = true,
    private val needsProfile: Boolean = false,
) : BaseNumericField(extension, typeId, karoo) {
    override fun formatNeedsProfile() = needsProfile
}
