package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile

/**
 * Watts per kilogram over a smoothing interval.
 *
 * karoo-ext has no smoothed power-to-weight type -- only the live POWER_TO_WEIGHT and its lap
 * variants -- so the value is derived here: stream smoothed watts and divide by the rider weight
 * from the profile.
 *
 * That makes the stream value watts and the rendered value W/kg, which is what [displayValue] is
 * for. The plain W/kg field keeps using Karoo's own type and needs none of this.
 */
class SmoothedPowerToWeightField(
    extension: String,
    typeId: String,
    karoo: KarooSystemService,
    override val upstreamTypeId: String,
    override val label: String,
    // In watts, as the stream delivers it; the field renders previewValue / weight.
    override val previewValue: Double,
) : BaseNumericField(extension, typeId, karoo) {
    override val iconRes = R.drawable.ic_bolt
    override val zoneKind = null
    override val format = Formatters.wattsPerKg

    // Not the units: the rider weight is what the displayed value is divided by. Without this the
    // profile flow never starts and the field would sit at "--" forever.
    override fun formatNeedsProfile() = true

    override fun displayValue(raw: Double, profile: UserProfile?): Double? =
        Formatters.perKilogram(raw, profile?.weight)
}
