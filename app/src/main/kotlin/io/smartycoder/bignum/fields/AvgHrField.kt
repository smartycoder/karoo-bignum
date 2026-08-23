package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.smartycoder.bignum.render.ZoneKind
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class AvgHrField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "avgHr", karoo) {
    override val upstreamTypeId = DataType.Type.AVERAGE_HR
    override val label = "HR avg"
    override val iconRes = R.drawable.ic_heart
    override val zoneKind = ZoneKind.HR
    override val format = Formatters.bpm
    override val previewValue = 158.0
}
