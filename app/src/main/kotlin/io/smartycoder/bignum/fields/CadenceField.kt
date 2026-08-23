package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class CadenceField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "cadence", karoo) {
    override val upstreamTypeId = DataType.Type.CADENCE
    override val label = "CAD"
    override val iconRes = R.drawable.ic_cadence
    override val zoneKind = null
    override val format = Formatters.rpm
    override val previewValue = 92.0
}
