package io.example.bignum.fields

import io.example.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class CadenceField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "cadence", karoo) {
    override val upstreamTypeId = DataType.Type.CADENCE
    override val zoneKind = null
    override val format = Formatters.rpm
    override val previewValue = 92.0
}
