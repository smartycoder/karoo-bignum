package io.example.bignum.fields

import io.example.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class SpeedField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "speed", karoo) {
    override val upstreamTypeId = DataType.Type.SPEED
    override val zoneKind = null
    override val format = Formatters.speed
    override val previewValue = 10.0
    override fun formatNeedsProfile() = true
}
