package io.smartycoder.bignum.fields

import io.smartycoder.bignum.BuildConfig
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
    // 4.2% is what a rider should meet in the page-editor preview: a plausible road. Debug
    // builds get a wall, because the wedge and the top of the colour band only show themselves
    // near the steep end, which is exactly what there is to look at without a ride.
    override val previewValue = if (BuildConfig.DEBUG) 22.0 else 4.2

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
