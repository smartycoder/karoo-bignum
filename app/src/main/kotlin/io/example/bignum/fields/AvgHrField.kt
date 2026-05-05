package io.example.bignum.fields

import io.example.bignum.format.Formatters
import io.example.bignum.render.ZoneKind
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class AvgHrField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "avgHr", karoo) {
    override val upstreamTypeId = DataType.Type.AVERAGE_HR
    override val zoneKind = ZoneKind.HR
    override val format = Formatters.bpm
    override val previewValue = 158.0
}
