package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService

class SpeedField(
    extension: String,
    typeId: String,
    karoo: KarooSystemService,
    override val upstreamTypeId: String,
    override val label: String,
    // Per instance: current, average and max speed on one page should not agree exactly.
    // In m/s, as the stream delivers it.
    override val previewValue: Double = 10.0,
) : BaseNumericField(extension, typeId, karoo) {
    override val iconRes = R.drawable.ic_speed
    override val zoneKind = null
    override val format = Formatters.speed
    override val missingValue = 0.0
    override fun formatNeedsProfile() = true
}
