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

    // "0" rather than "8": on Saira every digit is the same width, but Oswald's are not and
    // its "0" is the widest, so a template written with "8" would be narrower than values the
    // field really shows for a rider who picked Oswald. The seconds are demoted by the shared
    // raised-tail rule rather than by anything this field does.
    override val widthTemplate = "0:00:00"
    override val previewValue = 5_073_000.0

    // Test mode fakes every other field so a page can be shot without a ride. This one it must
    // not: the clock is real whether or not anything is paired, and watching it run is how you
    // tell a live field from a frozen one.
    override val demoInTestMode = false

    // Before the ride starts there is no stream, and "--" says nothing a stopped clock does not
    // say better.
    override val missingValue = 0.0
}
