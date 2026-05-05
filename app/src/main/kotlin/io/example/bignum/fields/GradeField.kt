package io.example.bignum.fields

import io.example.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class GradeField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "grade", karoo) {
    override val upstreamTypeId = DataType.Type.ELEVATION_GRADE
    override val zoneKind = null
    override val format = Formatters.percent
    override val previewValue = 4.2
}
