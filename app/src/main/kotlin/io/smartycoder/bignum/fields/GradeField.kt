package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.smartycoder.bignum.render.ZoneColors
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

    // Karoo's Climber scale, not zone colour: Grade carries no UserProfile zones of its own, so
    // the number itself stays Theme.textColor() and only the wedge behind it is coloured.
    override fun wedge(raw: Double) = Wedge(
        fraction = ZoneColors.wedgeHeightFraction(raw),
        color = ZoneColors.grade(raw),
        // Direction carries the sign the colour band cannot: Karoo's lowest band already covers
        // negative grades, so a -22% descent and a flat road would otherwise look identical.
        rising = raw >= 0,
    )
}
