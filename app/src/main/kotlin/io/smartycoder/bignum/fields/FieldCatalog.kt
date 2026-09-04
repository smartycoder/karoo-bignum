package io.smartycoder.bignum.fields

import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.smartycoder.bignum.render.ZoneKind
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType

/**
 * The full list of data fields BigNum offers, in the order the Karoo picker shows them.
 *
 * This used to live inline in `BigNumExtension.types`; it is pulled out here so a later field
 * (a composite that picks two entries out of this same list) can be built from it too.
 */
object FieldCatalog {

    /**
     * The sunrise and sunset previews, as seconds into the day -- 6:42 and 20:18.
     *
     * [Formatters.clock] takes a value this small for seconds into the day rather than an
     * instant, so the demo reads the same wherever the Karoo is; a fixed epoch would drift a
     * screenshot by the rider's offset. Named so the test that pins them to those two times
     * reads the same numbers the fields do.
     */
    internal const val SUNRISE_PREVIEW = 24_120.0
    internal const val SUNSET_PREVIEW = 73_080.0

    /** The wall clock's preview, on the same footing as the two above it: 14:35. */
    internal const val CLOCK_PREVIEW = 52_500.0

    /**
     * The arrival time's preview: 15:14, which is [CLOCK_PREVIEW] plus the 39 minutes the time
     * to destination previews, so the four clocks in a screenshot agree with each other.
     *
     * Seconds into the day like the rest of them. It used to be a fixed epoch, which rendered
     * in the device's zone and so read 14:35 only in UTC -- a demo that moved with the rider.
     */
    internal const val ETA_PREVIEW = 54_840.0

    fun build(extension: String, karoo: KarooSystemService): List<BaseNumericField> {
        return listOf(
            // Speed
            SpeedField(extension, "speed", karoo, DataType.Type.SPEED, "SPEED", previewValue = 9.7),
            SpeedField(extension, "speed3s", karoo, DataType.Type.SMOOTHED_3S_AVERAGE_SPEED, "SPEED 3s", previewValue = 9.4),
            SpeedField(extension, "speed5s", karoo, DataType.Type.SMOOTHED_5S_AVERAGE_SPEED, "SPEED 5s", previewValue = 9.2),
            SpeedField(extension, "speed10s", karoo, DataType.Type.SMOOTHED_10S_AVERAGE_SPEED, "SPEED 10s", previewValue = 9.0),
            SpeedField(extension, "avgSpeed", karoo, DataType.Type.AVERAGE_SPEED, "AVG SPEED", previewValue = 7.9),
            SpeedField(extension, "maxSpeed", karoo, DataType.Type.MAX_SPEED, "MAX SPEED", previewValue = 17.4),

            // Distance
            DistanceField(extension, karoo),

            // Heart rate
            HeartRateField(extension, karoo),
            SimpleField(extension, "hrZone", karoo, DataType.Type.HR_ZONE, "HR Z", R.drawable.ic_heart, Formatters.count, previewValue = 3.0),
            AvgHrField(extension, karoo),
            SimpleField(extension, "maxHr", karoo, DataType.Type.MAX_HR, "MAX HR", R.drawable.ic_heart, Formatters.bpm, zoneKind = ZoneKind.HR, previewValue = 178.0),
            SimpleField(extension, "percentMaxHr", karoo, DataType.Type.PERCENT_MAX_HR, "%HR", R.drawable.ic_heart, Formatters.percent, previewValue = 82.0),
            SimpleField(extension, "percentHrr", karoo, DataType.Type.PERCENT_HRR, "%HRR", R.drawable.ic_heart, Formatters.percent, previewValue = 74.0),
            SimpleField(extension, "avgPercentHrr", karoo, DataType.Type.AVERAGE_PERCENT_HRR, "AVG %HRR", R.drawable.ic_heart, Formatters.percent, previewValue = 68.0),

            // Cadence
            CadenceField(extension, karoo),
            SimpleField(extension, "cadence3s", karoo, DataType.Type.SMOOTHED_3S_AVERAGE_CADENCE, "CAD 3s", R.drawable.ic_cadence, Formatters.rpm, previewValue = 90.0),
            SimpleField(extension, "cadence5s", karoo, DataType.Type.SMOOTHED_5S_AVERAGE_CADENCE, "CAD 5s", R.drawable.ic_cadence, Formatters.rpm, previewValue = 89.0),
            SimpleField(extension, "cadence10s", karoo, DataType.Type.SMOOTHED_10S_AVERAGE_CADENCE, "CAD 10s", R.drawable.ic_cadence, Formatters.rpm, previewValue = 87.0),
            SimpleField(extension, "avgCadence", karoo, DataType.Type.AVERAGE_CADENCE, "AVG CAD", R.drawable.ic_cadence, Formatters.rpm, previewValue = 84.0),
            SimpleField(extension, "maxCadence", karoo, DataType.Type.MAX_CADENCE, "MAX CAD", R.drawable.ic_cadence, Formatters.rpm, previewValue = 112.0),

            // Power
            PowerField(extension, "power", karoo, DataType.Type.POWER, "PWR", previewValue = 237.0),
            SimpleField(extension, "powerZone", karoo, DataType.Type.POWER_ZONE, "PWR Z", R.drawable.ic_bolt, Formatters.count, previewValue = 4.0),
            PowerField(extension, "power3s", karoo, DataType.Type.SMOOTHED_3S_AVERAGE_POWER, "PWR 3s", previewValue = 541.0),
            PowerField(extension, "power5s", karoo, DataType.Type.SMOOTHED_5S_AVERAGE_POWER, "PWR 5s", previewValue = 244.0),
            PowerField(extension, "power10s", karoo, DataType.Type.SMOOTHED_10S_AVERAGE_POWER, "PWR 10s", previewValue = 249.0),
            PowerField(extension, "power30s", karoo, DataType.Type.SMOOTHED_30S_AVERAGE_POWER, "PWR 30s", previewValue = 252.0),
            PowerField(extension, "power20m", karoo, DataType.Type.SMOOTHED_20M_AVERAGE_POWER, "PWR 20m", previewValue = 263.0),
            PowerField(extension, "power1hr", karoo, DataType.Type.SMOOTHED_1HR_AVERAGE_POWER, "PWR 1hr", previewValue = 228.0),
            PowerField(extension, "avgPower", karoo, DataType.Type.AVERAGE_POWER, "AVG PWR", previewValue = 214.0),
            PowerField(extension, "maxPower", karoo, DataType.Type.MAX_POWER, "MAX PWR", previewValue = 812.0),
            SimpleField(extension, "percentMaxFtp", karoo, DataType.Type.PERCENT_MAX_FTP, "%FTP", R.drawable.ic_bolt, Formatters.percent, previewValue = 78.0),
            PowerField(extension, "np", karoo, DataType.Type.NORMALIZED_POWER, "NP", previewValue = 231.0),
            PowerToWeightField(extension, karoo),
            // previewValue is in watts; at the demo profile's 70 kg these render 3.4 and 3.5.
            SmoothedPowerToWeightField(extension, "powerToWeight3s", karoo, DataType.Type.SMOOTHED_3S_AVERAGE_POWER, "W/KG 3s", previewValue = 241.0),
            SmoothedPowerToWeightField(extension, "powerToWeight5s", karoo, DataType.Type.SMOOTHED_5S_AVERAGE_POWER, "W/KG 5s", previewValue = 244.0),
            TssField(extension, karoo),
            CaloriesField(extension, karoo),

            // Climbing
            SimpleField(extension, "altitude", karoo, DataType.Type.PRESSURE_ELEVATION_CORRECTION, "ALT", R.drawable.ic_elevation, Formatters.elevation, needsProfile = true, previewValue = 642.0),
            ElevationField(extension, karoo),
            SimpleField(extension, "descent", karoo, DataType.Type.ELEVATION_LOSS, "DESCENT", R.drawable.ic_elevation, Formatters.elevation, needsProfile = true, previewValue = 873.0),
            GradeField(extension, karoo),
            SimpleField(extension, "vam", karoo, DataType.Type.VERTICAL_SPEED, "VAM", R.drawable.ic_elevation, Formatters.count, widthTemplate = "0000", previewValue = 720.0),
            SimpleField(extension, "avgVam", karoo, DataType.Type.AVERAGE_VERTICAL_SPEED, "AVG VAM", R.drawable.ic_elevation, Formatters.count, widthTemplate = "0000", previewValue = 610.0),
            SimpleField(extension, "distanceToTop", karoo, DataType.Type.DISTANCE_TO_TOP, "TO TOP", R.drawable.ic_distance, Formatters.distance, needsProfile = true, previewValue = 2400.0),
            SimpleField(extension, "elevationToTop", karoo, DataType.Type.ELEVATION_TO_TOP, "ELEV TOP", R.drawable.ic_elevation, Formatters.elevation, needsProfile = true, previewValue = 185.0),

            // Lap
            SimpleField(extension, "lapNumber", karoo, DataType.Type.LAP_NUMBER, "LAP", R.drawable.ic_info, Formatters.count, widthTemplate = "00", previewValue = 3.0),
            TimeField(extension, "lapTime", karoo, DataType.Type.ELAPSED_TIME_LAP, "TIME lap", previewValue = 743_000.0),
            SimpleField(extension, "lapDistance", karoo, DataType.Type.DISTANCE_LAP, "DIST lap", R.drawable.ic_distance, Formatters.distance, needsProfile = true, previewValue = 12_400.0),
            SpeedField(extension, "lapSpeed", karoo, DataType.Type.AVERAGE_SPEED_LAP, "SPEED lap", previewValue = 8.3),
            SpeedField(extension, "lapMaxSpeed", karoo, DataType.Type.MAX_SPEED_LAP, "MAX SPEED lap", previewValue = 15.2),
            SimpleField(extension, "lapHr", karoo, DataType.Type.AVERAGE_LAP_HR, "HR lap", R.drawable.ic_heart, Formatters.bpm, zoneKind = ZoneKind.HR, previewValue = 152.0),
            SimpleField(extension, "lapMaxHr", karoo, DataType.Type.MAX_HR_LAP, "MAX HR lap", R.drawable.ic_heart, Formatters.bpm, zoneKind = ZoneKind.HR, previewValue = 171.0),
            SimpleField(extension, "lapCadence", karoo, DataType.Type.CADENCE_LAP, "CAD lap", R.drawable.ic_cadence, Formatters.rpm, previewValue = 87.0),
            SimpleField(extension, "lapMaxCadence", karoo, DataType.Type.MAX_CADENCE_LAP, "MAX CAD lap", R.drawable.ic_cadence, Formatters.rpm, previewValue = 104.0),
            PowerField(extension, "lapPower", karoo, DataType.Type.POWER_LAP, "PWR lap", previewValue = 226.0),
            PowerField(extension, "lapNp", karoo, DataType.Type.NORMALIZED_POWER_LAP, "NP lap", previewValue = 249.0),
            PowerField(extension, "lapMaxPower", karoo, DataType.Type.MAX_POWER_LAP, "MAX PWR lap", previewValue = 734.0),
            // Karoo delivers this one already in W/kg, so it is a plain field rather than a
            // SmoothedPowerToWeightField dividing watts by the rider weight.
            SimpleField(extension, "lapPowerToWeight", karoo, DataType.Type.POWER_TO_WEIGHT_LAP, "W/KG lap", R.drawable.ic_bolt, Formatters.wattsPerKg, previewValue = 3.2),
            SimpleField(extension, "lapVam", karoo, DataType.Type.AVERAGE_VERTICAL_SPEED_LAP, "VAM lap", R.drawable.ic_elevation, Formatters.count, widthTemplate = "0000", previewValue = 680.0),
            SimpleField(extension, "lapAscent", karoo, DataType.Type.ELEVATION_GAIN_LAP, "ASCENT lap", R.drawable.ic_elevation, Formatters.elevation, needsProfile = true, previewValue = 214.0),
            SimpleField(extension, "lapDescent", karoo, DataType.Type.ELEVATION_LOSS_LAP, "DESCENT lap", R.drawable.ic_elevation, Formatters.elevation, needsProfile = true, previewValue = 168.0),

            // Navigation
            // Both distances ship alongside the route flags, so the field they read is named
            // rather than left to singleValue; see BaseNumericField.valueField.
            SimpleField(extension, "distanceToDestination", karoo, DataType.Type.DISTANCE_TO_DESTINATION, "TO DEST", R.drawable.ic_distance, Formatters.distance, needsProfile = true, valueField = DataType.Field.DISTANCE_TO_DESTINATION, previewValue = 18_600.0),
            SimpleField(extension, "distanceToNextTurn", karoo, DataType.Type.DISTANCE_TO_NEXT_TURN, "TO TURN", R.drawable.ic_distance, Formatters.distance, needsProfile = true, valueField = DataType.Field.DISTANCE_TO_NEXT_TURN, previewValue = 450.0),
            // 39 minutes: the 18.6 km the field above previews, at the demo average speed, so
            // the three navigation fields tell one story in a screenshot.
            TimeField(extension, "timeToDestination", karoo, DataType.Type.TIME_TO_DESTINATION, "TIME TO DEST", previewValue = 2_360_000.0, valueField = DataType.Field.TIME_TO_DESTINATION, missingValue = null),
            SimpleField(extension, "timeOfArrival", karoo, DataType.Type.TIME_OF_ARRIVAL, "ETA", R.drawable.ic_clock, Formatters.clock, widthTemplate = "00:00", previewValue = ETA_PREVIEW),

            // Time and environment
            // Labelled for what karoo-ext documents ELAPSED_TIME as -- "Ride Time, time spent
            // recording" -- not for the constant, which reads like the opposite. The typeId
            // stays "elapsed": it is what saved pages and HUD slots are keyed on.
            TimeField(extension, "elapsed", karoo, DataType.Type.ELAPSED_TIME, "RIDE TIME", previewValue = 5_073_000.0, demoInTestMode = false),
            // Longer than the field above it by the length of a coffee stop. The SDK's names for
            // the two are the other way round from their meanings: RIDE_TIME is documented as
            // "Total Time -- time since this ride began, including paused time", while
            // ELAPSED_TIME is "Ride Time -- time spent recording". The labels follow the
            // documented meaning, not the constant.
            TimeField(extension, "totalTime", karoo, DataType.Type.RIDE_TIME, "TOTAL TIME", previewValue = 5_680_000.0, demoInTestMode = false),
            // The wall clock opts out of the demo value for the reason the ride clock does: it
            // runs with nothing paired, so a frozen one reads as a broken field.
            SimpleField(extension, "clockTime", karoo, DataType.Type.CLOCK_TIME, "CLOCK", R.drawable.ic_clock, Formatters.clock, widthTemplate = "00:00", previewValue = CLOCK_PREVIEW, demoInTestMode = false),
            // Both keep the ETA's treatment: a 24-hour wall clock whose minutes raise like any
            // other tail, so every field that looks like a clock shrinks its least significant
            // unit and nothing on a page is the odd one out.
            SimpleField(extension, "sunrise", karoo, DataType.Type.SUNRISE, "SUNRISE", R.drawable.ic_clock, Formatters.clock, widthTemplate = "00:00", previewValue = SUNRISE_PREVIEW),
            SimpleField(extension, "sunset", karoo, DataType.Type.SUNSET, "SUNSET", R.drawable.ic_clock, Formatters.clock, widthTemplate = "00:00", previewValue = SUNSET_PREVIEW),
            TemperatureField(extension, karoo),
            SimpleField(extension, "battery", karoo, DataType.Type.BATTERY_PERCENT, "BATTERY", R.drawable.ic_battery, Formatters.percent, previewValue = 64.0),
        )
    }
}

/** Null for an id this build no longer has; see Settings.resolveSlots. */
fun List<BaseNumericField>.byId(typeId: String): BaseNumericField? =
    firstOrNull { it.typeId == typeId }

val List<BaseNumericField>.ids: Set<String> get() = mapTo(mutableSetOf()) { it.typeId }

/**
 * The fields the HUD's zone bar can be driven by: the ones that carry a heart rate or power
 * zone. Everything else has no scale to be placed on -- a bar of speed or elapsed time would
 * have no meaning to fill towards.
 */
val List<BaseNumericField>.zoneCapable: List<BaseNumericField> get() = filter { it.zoneKind != null }
