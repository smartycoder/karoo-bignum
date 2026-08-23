package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.smartycoder.bignum.render.ZoneKind
import io.hammerhead.karooext.KarooSystemService

/**
 * Power in watts, zone-colored.
 *
 * Smoothing is picked when the field is added, by registering one type per interval:
 * karoo-ext gives extensions no per-field settings (DataTypeImpl only offers
 * startStream/startView, and `<DataType>` has no settings attribute), so separate types
 * are the only way to offer the choice where Karoo's own SMOOTHING slider sits.
 */
class PowerField(
    extension: String,
    typeId: String,
    karoo: KarooSystemService,
    override val upstreamTypeId: String,
    override val label: String,
    // Per instance, so a page holding current, smoothed and average power does not show
    // the same number three times over.
    override val previewValue: Double = 237.0,
) : BaseNumericField(extension, typeId, karoo) {
    override val iconRes = R.drawable.ic_bolt
    override val zoneKind = ZoneKind.POWER
    override val format = Formatters.watts

    override val missingValue = 0.0
}
