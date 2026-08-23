package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class TemperatureField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "temp", karoo) {
    override val upstreamTypeId = DataType.Type.TEMPERATURE
    override val label = "TEMP"
    override val iconRes = R.drawable.ic_temp
    override val zoneKind = null
    override val format = Formatters.temperature
    override val previewValue = 23.0
    override fun formatNeedsProfile() = true
}
