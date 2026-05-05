package io.example.bignum.fields

import io.example.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class TemperatureField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "temp", karoo) {
    override val upstreamTypeId = DataType.Type.TEMPERATURE
    override val zoneKind = null
    override val format = Formatters.temperature
    override val previewValue = 23.0
    override fun formatNeedsProfile() = true
}
