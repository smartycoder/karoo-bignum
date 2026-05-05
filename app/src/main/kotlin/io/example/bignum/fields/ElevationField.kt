package io.example.bignum.fields

import io.example.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class ElevationField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "elevation", karoo) {
    override val upstreamTypeId = DataType.Type.ELEVATION_GAIN
    override val zoneKind = null
    override val format = Formatters.elevation
    override val previewValue = 742.0
    override fun formatNeedsProfile() = true
}
