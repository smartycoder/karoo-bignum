package io.example.bignum

import android.util.Log
import io.example.bignum.fields.AvgHrField
import io.example.bignum.fields.AvgPower10sField
import io.example.bignum.fields.AvgPower30sField
import io.example.bignum.fields.AvgPower3sField
import io.example.bignum.fields.CadenceField
import io.example.bignum.fields.DistanceField
import io.example.bignum.fields.ElapsedTimeField
import io.example.bignum.fields.ElevationField
import io.example.bignum.fields.GradeField
import io.example.bignum.fields.HeartRateField
import io.example.bignum.fields.LapPowerField
import io.example.bignum.fields.PowerField
import io.example.bignum.fields.SpeedField
import io.example.bignum.fields.TemperatureField
import io.hammerhead.karooext.KarooSystemService
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
            SpeedField(extension, karoo),
            HeartRateField(extension, karoo),
            PowerField(extension, karoo),
            CadenceField(extension, karoo),
            DistanceField(extension, karoo),
            ElapsedTimeField(extension, karoo),
            AvgPower3sField(extension, karoo),
            AvgPower10sField(extension, karoo),
            AvgPower30sField(extension, karoo),
            AvgHrField(extension, karoo),
            ElevationField(extension, karoo),
            GradeField(extension, karoo),
            TemperatureField(extension, karoo),
            LapPowerField(extension, karoo),
        )
    }

    private companion object { const val TAG = "BigNum" }
}
