package io.example.bignum.fields

import io.example.bignum.format.Formatters
import io.example.bignum.render.ZoneKind
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class AvgPower10sField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "avgPower10s", karoo) {
    override val upstreamTypeId = DataType.Type.SMOOTHED_10S_AVERAGE_POWER
    override val zoneKind = ZoneKind.POWER
    override val format = Formatters.watts
    override val previewValue = 238.0
}
