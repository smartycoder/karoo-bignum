package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class DistanceField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "distance", karoo) {
    override val upstreamTypeId = DataType.Type.DISTANCE
    override val label = "DIST"
    override val iconRes = R.drawable.ic_distance
    override val zoneKind = null
    override val format = Formatters.distance
    override val previewValue = 34_200.0
    override fun formatNeedsProfile() = true
}
