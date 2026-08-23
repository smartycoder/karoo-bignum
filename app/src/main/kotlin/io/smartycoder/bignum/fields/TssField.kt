package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class TssField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "tss", karoo) {
    override val upstreamTypeId = DataType.Type.TRAINING_STRESS_SCORE
    override val label = "TSS"
    override val iconRes = R.drawable.ic_bolt
    override val zoneKind = null
    override val format = Formatters.count
    override val previewValue = 0.0
}
