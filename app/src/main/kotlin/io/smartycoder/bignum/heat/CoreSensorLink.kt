package io.smartycoder.bignum.heat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** The Karoo's own reading of the CORE, which a connection has to match to be trusted. */
data class KarooBodyTemps(val coreC: Double?, val skinC: Double?)

/** Where the link is, for the settings screen. */
enum class CoreLinkState {
    /** Turned off in the settings. */
    OFF,

    /** No CORE on the Karoo yet: the link only looks for one while the Karoo is reading one. */
    WAITING,
    NO_PERMISSION,
    BLUETOOTH_OFF,
    SEARCHING,

    /** Nothing found, or nothing that would connect; the index is estimated meanwhile. */
    NOT_FOUND,

    /** Connected, checking it is the rider's sensor and not a neighbour's. */
    VERIFYING,

    /** Connected to the rider's sensor, and the index on screen is the sensor's own. */
    SENSOR_HSI,

    /** Connected to the rider's sensor, but its firmware does not send the index. */
    NO_HSI,
}

/**
 * A direct Bluetooth connection to the rider's CORE sensor, for the one thing the Karoo does not
 * pass on: the Heat Strain Index the sensor works out itself.
 *
 * The Karoo keeps its own connection to the sensor, over ANT+ or Bluetooth; a CORE accepts up to
 * three Bluetooth connections at once, so this is an extra listener, not a replacement. Only runs
 * while the Karoo is reading a CORE -- that is when there is a sensor to find, and the Karoo's
 * reading is what proves the right one was found.
 *
 * Every call into the radio is behind [hasPermission]; SecurityException is caught regardless, in
 * case the rider withdraws the permission mid-ride.
 */
@SuppressLint("MissingPermission")
class CoreSensorLink(context: Context) {

    private val appContext = context.applicationContext

    private val _hsi = MutableStateFlow<Double?>(null)

    /** The sensor's own index, once its connection is confirmed; null otherwise. */
    val hsi: StateFlow<Double?> = _hsi.asStateFlow()

    private sealed interface GattEvent {
        data object Connected : GattEvent
        data class Disconnected(val status: Int) : GattEvent
        data class ServicesDiscovered(val ok: Boolean) : GattEvent
        data class DescriptorWritten(val ok: Boolean) : GattEvent
        class Notification(val value: ByteArray) : GattEvent
    }

    private enum class Outcome { LOST, FAILED, WRONG_SENSOR }

    /**
     * Finds the rider's sensor, connects, and keeps the index coming until cancelled, starting
     * over whenever a connection drops. [karoo] is the Karoo's current reading of the same sensor.
     */
    suspend fun run(karoo: StateFlow<KarooBodyTemps>) {
        // A sensor that turned out to be someone else's is not tried again this session.
        val rejected = mutableSetOf<String>()
        var backoff = RETRY_MIN_MS
        try {
            while (true) {
                if (!hasPermission(appContext)) {
                    publish(CoreLinkState.NO_PERMISSION)
                    delay(RECHECK_MS)
                    continue
                }
                val adapter = adapter()
                if (adapter == null || !adapter.isEnabled) {
                    publish(CoreLinkState.BLUETOOTH_OFF)
                    delay(RECHECK_MS)
                    continue
                }

                publish(CoreLinkState.SEARCHING)
                val candidate = scan(adapter, karoo, rejected)
                val outcome = if (candidate == null) {
                    Outcome.FAILED
                } else {
                    connect(adapter.getRemoteDevice(candidate.address), karoo)
                }
                _hsi.value = null
                when (outcome) {
                    Outcome.WRONG_SENSOR -> {
                        rejected += candidate!!.address
                        backoff = RETRY_MIN_MS
                    }
                    Outcome.LOST -> backoff = RETRY_MIN_MS
                    Outcome.FAILED -> {
                        publish(CoreLinkState.NOT_FOUND)
                        backoff = (backoff * 2).coerceAtMost(RETRY_MAX_MS)
                    }
                }
                delay(backoff)
            }
        } finally {
            _hsi.value = null
        }
    }

    /** Scans for CORE sensors and returns the one most likely to be the rider's. */
    private suspend fun scan(
        adapter: BluetoothAdapter,
        karoo: StateFlow<KarooBodyTemps>,
        rejected: Set<String>,
    ): CoreCandidate? {
        val scanner = adapter.bluetoothLeScanner ?: return null
        val seen = LinkedHashMap<String, CoreCandidate>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val services = record.serviceUuids.orEmpty()
                val manufacturer = record.getManufacturerSpecificData(CoreProtocol.MANUFACTURER_ID)
                // No hardware filter on the service: CORE puts it in the scan response, which
                // not every Bluetooth stack will match a filter against. Any of the three marks
                // will do; the connection itself is what proves it.
                val isCore = ParcelUuid(CoreProtocol.SERVICE) in services ||
                    ParcelUuid(CoreProtocol.LEGACY_SERVICE) in services ||
                    manufacturer != null
                if (!isCore) return
                synchronized(seen) {
                    seen[result.device.address] = CoreCandidate(
                        address = result.device.address,
                        rssi = result.rssi,
                        advertisedCore = CoreProtocol.advertisedCore(manufacturer),
                    )
                }
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, callback)
            // Stops early once the rider's own sensor has shown itself.
            withTimeoutOrNull(SCAN_MS) {
                while (true) {
                    delay(SCAN_POLL_MS)
                    val karooCore = karoo.value.coreC ?: continue
                    val match = synchronized(seen) { seen.values.toList() }.any { c ->
                        c.address !in rejected && c.advertisedCore != null &&
                            kotlin.math.abs(c.advertisedCore - karooCore) <= CoreCandidates.SAME_SENSOR
                    }
                    if (match) break
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "scan refused", e)
            return null
        } finally {
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
                // Bluetooth turned off mid-scan; there is nothing left to stop.
            }
        }
        return CoreCandidates.pick(synchronized(seen) { seen.values.toList() }, karoo.value.coreC, rejected)
    }

    /**
     * Connects to [device] and relays its index until the connection ends. Returns why it ended.
     */
    private suspend fun connect(device: BluetoothDevice, karoo: StateFlow<KarooBodyTemps>): Outcome {
        val events = Channel<GattEvent>(Channel.UNLIMITED)
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                events.trySend(
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        GattEvent.Connected
                    } else {
                        GattEvent.Disconnected(status)
                    },
                )
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                events.trySend(GattEvent.ServicesDiscovered(status == BluetoothGatt.GATT_SUCCESS))
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                events.trySend(GattEvent.DescriptorWritten(status == BluetoothGatt.GATT_SUCCESS))
            }

            // Both overloads: Android 13 added the one carrying the value and calls only that;
            // older versions -- the Karoo 2 runs 8.1 -- call only the deprecated one.
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (characteristic.uuid == CoreProtocol.MEASUREMENT) events.trySend(GattEvent.Notification(value.copyOf()))
            }

            @Deprecated("Deprecated in Android 13; still the only one called before it")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                if (characteristic.uuid == CoreProtocol.MEASUREMENT) events.trySend(GattEvent.Notification(value.copyOf()))
            }
        }

        val gatt = try {
            device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.w(TAG, "connect refused", e)
            null
        } ?: return Outcome.FAILED

        try {
            publish(CoreLinkState.VERIFYING)
            if (events.awaitWithin(CONNECT_MS) { it is GattEvent.Connected } == null) return Outcome.FAILED

            gatt.discoverServices()
            val discovered = events.awaitWithin(STEP_MS) { it is GattEvent.ServicesDiscovered } as? GattEvent.ServicesDiscovered
            if (discovered?.ok != true) return Outcome.FAILED

            // Connected and no Core Temp service: not a CORE at all, or firmware too old for
            // the open profile. Either way this one is not going to give an index.
            val measurement = gatt.getService(CoreProtocol.SERVICE)?.getCharacteristic(CoreProtocol.MEASUREMENT)
                ?: return Outcome.WRONG_SENSOR
            val cccd = measurement.getDescriptor(CoreProtocol.CCCD) ?: return Outcome.WRONG_SENSOR
            gatt.setCharacteristicNotification(measurement, true)
            writeDescriptor(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            val written = events.awaitWithin(STEP_MS) { it is GattEvent.DescriptorWritten } as? GattEvent.DescriptorWritten
            if (written?.ok != true) return Outcome.FAILED

            val match = SensorMatch()
            while (true) {
                // Silence counts as a lost connection: out of range, or a stack that has not
                // noticed yet. The sensor notifies about once a second.
                val event = withTimeoutOrNull(SILENCE_MS) { events.receive() } ?: return Outcome.LOST
                when (event) {
                    is GattEvent.Disconnected -> return Outcome.LOST
                    is GattEvent.Notification -> {
                        val reading = CoreProtocol.parseMeasurement(event.value) ?: continue
                        val temps = karoo.value
                        when (match.offer(temps.coreC, temps.skinC, reading.coreC, reading.skinC)) {
                            SensorMatch.Verdict.REJECTED -> {
                                Log.i(TAG, "${device.address} is not the Karoo's CORE; trying another")
                                return Outcome.WRONG_SENSOR
                            }
                            SensorMatch.Verdict.CONFIRMED -> {
                                _hsi.value = reading.hsi
                                publish(if (reading.hsi != null) CoreLinkState.SENSOR_HSI else CoreLinkState.NO_HSI)
                            }
                            SensorMatch.Verdict.PENDING -> Unit
                        }
                    }
                    else -> Unit
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "connection refused", e)
            return Outcome.FAILED
        } finally {
            _hsi.value = null
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {
                // Already gone with the adapter.
            }
        }
    }

    private fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    /** The next event that satisfies [wanted], or null on a disconnect or after [timeoutMs]. */
    private suspend fun Channel<GattEvent>.awaitWithin(timeoutMs: Long, wanted: (GattEvent) -> Boolean): GattEvent? =
        withTimeoutOrNull(timeoutMs) {
            var found: GattEvent? = null
            while (found == null) {
                val event = receive()
                if (event is GattEvent.Disconnected) break
                if (wanted(event)) found = event
            }
            found
        }

    private fun adapter(): BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun publish(state: CoreLinkState) {
        _state.value = state
    }

    companion object {
        private const val TAG = "BigNum.CORE"

        private const val SCAN_MS = 10_000L
        private const val SCAN_POLL_MS = 500L
        private const val CONNECT_MS = 15_000L
        private const val STEP_MS = 10_000L
        private const val SILENCE_MS = 10_000L
        private const val RECHECK_MS = 30_000L
        private const val RETRY_MIN_MS = 5_000L
        private const val RETRY_MAX_MS = 120_000L

        // Process-wide, because the settings screen that shows it and the extension that sets it
        // are two components of one process with no other channel between them.
        private val _state = MutableStateFlow(CoreLinkState.WAITING)
        val state: StateFlow<CoreLinkState> = _state.asStateFlow()

        internal fun setState(state: CoreLinkState) {
            _state.value = state
        }

        /**
         * What has to be granted before the link can scan and connect. Android 12 split
         * Bluetooth out into permissions of its own; before it -- the Karoo 2 runs 8.1 -- a scan
         * needs location, because a scan can reveal where you are.
         */
        val permissions: Array<String>
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                // Both: Android grants fine location only alongside coarse.
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            }

        fun hasPermission(context: Context): Boolean = permissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
