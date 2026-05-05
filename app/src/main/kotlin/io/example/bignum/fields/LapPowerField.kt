package io.example.bignum.fields

import io.example.bignum.format.Formatters
import io.example.bignum.render.ZoneKind
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class LapPowerField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "lapPower", karoo) {
    override val upstreamTypeId = DataType.Type.POWER_LAP
    override val zoneKind = ZoneKind.POWER
    override val format = Formatters.watts
    override val previewValue = 245.0
}
