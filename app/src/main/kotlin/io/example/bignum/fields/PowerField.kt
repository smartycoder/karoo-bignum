package io.example.bignum.fields

import io.example.bignum.format.Formatters
import io.example.bignum.render.ZoneKind
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class PowerField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "power", karoo) {
    override val upstreamTypeId = DataType.Type.POWER
    override val zoneKind = ZoneKind.POWER
    override val format = Formatters.watts
    override val previewValue = 237.0
    override val zeroWhenMissing = true
}
