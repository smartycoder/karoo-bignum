package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

class ElapsedTimeField(extension: String, karoo: KarooSystemService)
    : BaseNumericField(extension, "elapsed", karoo) {
    override val upstreamTypeId = DataType.Type.ELAPSED_TIME
    override val label = "TIME"
    override val iconRes = R.drawable.ic_clock
    override val zoneKind = null
    override val format = Formatters.time

    // Seconds are always the secondary part: minutes and hours carry the information, and
    // demoting the seconds buys the rest of the value about 20% more height.
    override fun split(text: String) = Formatters.secondsAsSecondary(text)

    // "0" rather than "8": on Saira every digit is the same width, but Oswald's are not and
    // its "0" is the widest, so a template written with "8" would be narrower than values the
    // field really shows for a rider who picked Oswald.
    override val widthTemplate = "0:00:00"
    override val previewValue = 5_073_000.0
}
