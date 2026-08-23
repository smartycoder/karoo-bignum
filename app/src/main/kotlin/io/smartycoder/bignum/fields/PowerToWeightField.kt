package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class PowerToWeightField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "powerToWeight", karoo) {
    override val upstreamTypeId = DataType.Type.POWER_TO_WEIGHT
    override val label = "W/KG"
    override val iconRes = R.drawable.ic_bolt
    override val zoneKind = null
    override val format = Formatters.wattsPerKg
    override val previewValue = 3.4
}
