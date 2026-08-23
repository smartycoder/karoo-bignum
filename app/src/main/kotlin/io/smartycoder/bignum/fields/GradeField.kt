package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class GradeField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "grade", karoo) {
    override val upstreamTypeId = DataType.Type.ELEVATION_GRADE
    override val label = "GRADE"
    override val iconRes = R.drawable.ic_grade
    override val zoneKind = null
    override val format = Formatters.percent
    override val previewValue = 4.2
}
