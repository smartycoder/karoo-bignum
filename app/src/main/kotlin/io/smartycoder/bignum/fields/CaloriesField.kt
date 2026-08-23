package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class CaloriesField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "calories", karoo) {
    override val upstreamTypeId = DataType.Type.CALORIES
    override val label = "KCAL"
    override val iconRes = R.drawable.ic_bolt
    override val zoneKind = null
    override val format = Formatters.count
    override val previewValue = 0.0

    // A long ride runs past 1000 kcal, so the field is sized for four digits.
    override val widthTemplate = "0000"
}
