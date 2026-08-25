package io.smartycoder.bignum

import android.util.Log
import io.smartycoder.bignum.fields.AvgHrField
import io.smartycoder.bignum.fields.CadenceField
import io.smartycoder.bignum.fields.DistanceField
import io.smartycoder.bignum.fields.ElapsedTimeField
import io.smartycoder.bignum.fields.ElevationField
import io.smartycoder.bignum.fields.GradeField
import io.smartycoder.bignum.fields.HeartRateField
import io.smartycoder.bignum.fields.PowerField
import io.smartycoder.bignum.fields.PowerToWeightField
import io.smartycoder.bignum.fields.SmoothedPowerToWeightField
import io.smartycoder.bignum.R
import io.smartycoder.bignum.fields.SimpleField
import io.smartycoder.bignum.format.Formatters
import io.smartycoder.bignum.render.ZoneKind
import io.smartycoder.bignum.fields.SpeedField
import io.smartycoder.bignum.fields.TssField
import io.smartycoder.bignum.fields.CaloriesField
import io.smartycoder.bignum.fields.TemperatureField
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.extension.KarooExtension

class BigNumExtension : KarooExtension("bignum", BuildConfig.VERSION_NAME) {

    private lateinit var karoo: KarooSystemService

    override fun onCreate() {
        super.onCreate()
        karoo = KarooSystemService(applicationContext)
        karoo.connect { connected ->
            Log.i(TAG, "Karoo connected=$connected")
        }
    }

    override fun onDestroy() {
        if (this::karoo.isInitialized) karoo.disconnect()
        super.onDestroy()
    }

    override val types by lazy {
        listOf(
            SpeedField(extension, "speed", karoo, DataType.Type.SPEED, "SPEED", previewValue = 9.7),
            SpeedField(extension, "avgSpeed", karoo, DataType.Type.AVERAGE_SPEED, "AVG SPEED", previewValue = 7.9),
            SpeedField(extension, "maxSpeed", karoo, DataType.Type.MAX_SPEED, "MAX SPEED", previewValue = 17.4),
            HeartRateField(extension, karoo),
            CadenceField(extension, karoo),
            DistanceField(extension, karoo),
            ElapsedTimeField(extension, karoo),
            AvgHrField(extension, karoo),
            ElevationField(extension, karoo),
            GradeField(extension, karoo),
            TemperatureField(extension, karoo),
            PowerField(extension, "power", karoo, DataType.Type.POWER, "PWR", previewValue = 237.0),
            PowerField(extension, "power3s", karoo, DataType.Type.SMOOTHED_3S_AVERAGE_POWER, "PWR 3s", previewValue = 541.0),
            PowerField(extension, "power5s", karoo, DataType.Type.SMOOTHED_5S_AVERAGE_POWER, "PWR 5s", previewValue = 244.0),
            PowerField(extension, "power10s", karoo, DataType.Type.SMOOTHED_10S_AVERAGE_POWER, "PWR 10s", previewValue = 249.0),
            PowerField(extension, "power30s", karoo, DataType.Type.SMOOTHED_30S_AVERAGE_POWER, "PWR 30s", previewValue = 252.0),
            PowerField(extension, "power20m", karoo, DataType.Type.SMOOTHED_20M_AVERAGE_POWER, "PWR 20m", previewValue = 263.0),
            PowerField(extension, "power1hr", karoo, DataType.Type.SMOOTHED_1HR_AVERAGE_POWER, "PWR 1hr", previewValue = 228.0),
            PowerField(extension, "avgPower", karoo, DataType.Type.AVERAGE_POWER, "AVG PWR", previewValue = 214.0),
            PowerField(extension, "maxPower", karoo, DataType.Type.MAX_POWER, "MAX PWR", previewValue = 812.0),
            PowerField(extension, "np", karoo, DataType.Type.NORMALIZED_POWER, "NP", previewValue = 231.0),
            PowerField(extension, "lapPower", karoo, DataType.Type.POWER_LAP, "PWR lap", previewValue = 226.0),
            PowerToWeightField(extension, karoo),
            // previewValue is in watts; at the demo profile's 70 kg these render 3.4 and 3.5.
            SmoothedPowerToWeightField(extension, "powerToWeight3s", karoo, DataType.Type.SMOOTHED_3S_AVERAGE_POWER, "W/KG 3s", previewValue = 241.0),
            SmoothedPowerToWeightField(extension, "powerToWeight5s", karoo, DataType.Type.SMOOTHED_5S_AVERAGE_POWER, "W/KG 5s", previewValue = 244.0),
            TssField(extension, karoo),
            CaloriesField(extension, karoo),
            SimpleField(extension, "hrZone", karoo, DataType.Type.HR_ZONE, "HR Z", R.drawable.ic_heart, Formatters.count, previewValue = 3.0),
            SimpleField(extension, "maxHr", karoo, DataType.Type.MAX_HR, "MAX HR", R.drawable.ic_heart, Formatters.bpm, zoneKind = ZoneKind.HR, previewValue = 178.0),
            SimpleField(extension, "percentMaxHr", karoo, DataType.Type.PERCENT_MAX_HR, "%HR", R.drawable.ic_heart, Formatters.percent, previewValue = 82.0),
            SimpleField(extension, "percentHrr", karoo, DataType.Type.PERCENT_HRR, "%HRR", R.drawable.ic_heart, Formatters.percent, previewValue = 74.0),
            SimpleField(extension, "avgPercentHrr", karoo, DataType.Type.AVERAGE_PERCENT_HRR, "AVG %HRR", R.drawable.ic_heart, Formatters.percent, previewValue = 68.0),
            SimpleField(extension, "avgCadence", karoo, DataType.Type.AVERAGE_CADENCE, "AVG CAD", R.drawable.ic_cadence, Formatters.rpm, previewValue = 84.0),
            SimpleField(extension, "maxCadence", karoo, DataType.Type.MAX_CADENCE, "MAX CAD", R.drawable.ic_cadence, Formatters.rpm, previewValue = 112.0),
            SimpleField(extension, "powerZone", karoo, DataType.Type.POWER_ZONE, "PWR Z", R.drawable.ic_bolt, Formatters.count, previewValue = 4.0),
            SimpleField(extension, "altitude", karoo, DataType.Type.PRESSURE_ELEVATION_CORRECTION, "ALT", R.drawable.ic_elevation, Formatters.elevation, needsProfile = true, previewValue = 642.0),
            SimpleField(extension, "descent", karoo, DataType.Type.ELEVATION_LOSS, "DESCENT", R.drawable.ic_elevation, Formatters.elevation, needsProfile = true, previewValue = 873.0),
            SimpleField(extension, "vam", karoo, DataType.Type.VERTICAL_SPEED, "VAM", R.drawable.ic_elevation, Formatters.count, widthTemplate = "0000", previewValue = 720.0),
            SimpleField(extension, "avgVam", karoo, DataType.Type.AVERAGE_VERTICAL_SPEED, "AVG VAM", R.drawable.ic_elevation, Formatters.count, widthTemplate = "0000", previewValue = 610.0),
            SimpleField(extension, "distanceToTop", karoo, DataType.Type.DISTANCE_TO_TOP, "TO TOP", R.drawable.ic_distance, Formatters.distance, needsProfile = true, previewValue = 2400.0),
            SimpleField(extension, "elevationToTop", karoo, DataType.Type.ELEVATION_TO_TOP, "ELEV TOP", R.drawable.ic_elevation, Formatters.elevation, needsProfile = true, previewValue = 185.0),

        )
    }

    private companion object { const val TAG = "BigNum" }
}
