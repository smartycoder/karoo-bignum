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
    // Null reads the stream's singleValue. A duration that ships alongside other fields --
    // time to destination, riding with the route flags -- names the one it wants instead.
    override val valueField: String? = null,
    // A ride clock before the ride starts is a stopped clock, and "--" says nothing 0:00 does
    // not say better. A duration waiting on a route has no such reading: it passes null and
    // sits at "--" beside the nav distances, which do the same.
    override val missingValue: Double? = 0.0,
    // True shows previewValue in test mode. The ride clocks pass false: they are the values
    // that are real and moving with no sensor paired, and watching one run is how you tell a
    // live field from a frozen one, so a frozen demo time makes a screenshot look broken
    // rather than staged.
    override val demoInTestMode: Boolean = true,
) : BaseNumericField(extension, typeId, karoo) {
    override val iconRes = R.drawable.ic_clock
    override val zoneKind = null
    override val format = Formatters.time

    override fun widthBudget(text: String) = Formatters.timeTemplate(text)
}
