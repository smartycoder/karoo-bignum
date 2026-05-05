package io.example.bignum.fields

import io.example.bignum.format.Formatters
import io.example.bignum.render.ZoneKind
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class HeartRateField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "hr", karoo) {
    override val upstreamTypeId = DataType.Type.HEART_RATE
    override val zoneKind = ZoneKind.HR
    override val format = Formatters.bpm
    override val previewValue = 145.0
}
