package io.example.bignum.fields

import io.example.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class ElapsedTimeField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "elapsed", karoo) {
    override val upstreamTypeId = DataType.Type.ELAPSED_TIME
    override val zoneKind = null
    override val format = Formatters.time
    override val previewValue = 5_073_000.0
}
