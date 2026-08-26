package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService

/**
 * A duration -- the ride clock, a lap -- drawn h:mm:ss, or m:ss while it is under an hour.
 *
 * The budget follows the shape of the value rather than sitting fixed at h:mm:ss; see
 * [Formatters.timeTemplate] for why.
 */
class TimeField(
    extension: String,
    typeId: String,
    karoo: KarooSystemService,
    override val upstreamTypeId: String,
    override val label: String,
    override val previewValue: Double,
    // The ride clock opts out: it is the one value that is real and moving with no sensor
    // paired, and watching it run is how you tell a live field from a frozen one, so a frozen
    // demo time there makes a screenshot look broken rather than staged.
    override val demoInTestMode: Boolean = true,
) : BaseNumericField(extension, typeId, karoo) {
    override val iconRes = R.drawable.ic_clock
    override val zoneKind = null
    override val format = Formatters.time

    override fun widthBudget(text: String) = Formatters.timeTemplate(text)

    // Before the ride starts there is no stream, and "--" says nothing a stopped clock does not
    // say better.
    override val missingValue = 0.0
}
