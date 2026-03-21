package mx.ita.vitalsense.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

// ─── UUIDs del dispositivo VitalSense (ESP32) ────────────────────────────────
//  Para cambiarlos: edita estas constantes para que coincidan con el firmware.
//
//  Si usas otro wearable estándar (Polar, Garmin, etc.), usa los UUIDs
//  estándar de BLE Health:
//    Heart Rate Service:   0000180d-0000-1000-8000-00805f9b34fb
//    HR Measurement Char:  00002a37-0000-1000-8000-00805f9b34fb

object BleUUIDs {
    val VITALSENSE_SERVICE  = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    val HEART_RATE_CHAR     = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    val GLUCOSE_CHAR        = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9")
    val SPO2_CHAR           = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26aa")
    val CCCD                = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Estándar Heart Rate (compatible con Polar, Wahoo, etc.)
    val STD_HR_SERVICE      = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val STD_HR_CHAR         = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
}

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)

data class BleVitals(
    val heartRate: Int? = null,
    val glucose: Double? = null,
    val spo2: Int? = null,
    val timestamp: Long? = null,
)

sealed interface BleConnectionState {
    object Disconnected : BleConnectionState
    object Connecting   : BleConnectionState
    data class Connected(val deviceName: String) : BleConnectionState
    data class Error(val message: String) : BleConnectionState
}

@SuppressLint("MissingPermission")
class BleRepository(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _vitals = MutableStateFlow(BleVitals())
    val vitals: StateFlow<BleVitals> = _vitals.asStateFlow()

    private var gatt: BluetoothGatt? = null

    // ── Escaneo ───────────────────────────────────────────────────────────────

    fun scanDevices(): Flow<List<BleDevice>> = callbackFlow {
        val found = mutableMapOf<String, BleDevice>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name?.takeIf { it.isNotBlank() } ?: return
                found[result.device.address] = BleDevice(
                    name    = name,
                    address = result.device.address,
                    rssi    = result.rssi,
                )
                trySend(found.values.sortedByDescending { it.rssi })
            }
        }

        bluetoothAdapter?.bluetoothLeScanner?.startScan(callback)

        awaitClose {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
        }
    }

    // ── Conexión ──────────────────────────────────────────────────────────────

    fun connect(device: BleDevice) {
        _connectionState.value = BleConnectionState.Connecting
        val btDevice = bluetoothAdapter?.getRemoteDevice(device.address) ?: run {
            _connectionState.value = BleConnectionState.Error("Dispositivo no encontrado")
            return
        }

        gatt = btDevice.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = BleConnectionState.Disconnected
        _vitals.value = BleVitals()
    }

    fun setConnecting() {
        _connectionState.value = BleConnectionState.Connecting
    }

    fun setDisconnected() {
        _connectionState.value = BleConnectionState.Disconnected
    }

    fun setConnected(deviceName: String) {
        _connectionState.value = BleConnectionState.Connected(deviceName)
    }

    fun connectWithCode(code: String, deviceName: String) {
        // Mock connection associated with pairing code
        _connectionState.value = BleConnectionState.Connected(deviceName)
    }

    fun updateVitals(vitals: BleVitals) {
        _vitals.value = vitals
    }

    // ── GATT Callbacks ────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BleConnectionState.Disconnected
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val deviceName = gatt.device.name ?: "VitalSense"
            _connectionState.value = BleConnectionState.Connected(deviceName)

            // Habilitar notificaciones en todos los characteristics del servicio
            gatt.getService(BleUUIDs.VITALSENSE_SERVICE)?.let { service ->
                listOf(BleUUIDs.HEART_RATE_CHAR, BleUUIDs.GLUCOSE_CHAR, BleUUIDs.SPO2_CHAR)
                    .mapNotNull { service.getCharacteristic(it) }
                    .forEach { char -> enableNotification(gatt, char) }
            }

            // También intentar servicio estándar Heart Rate
            gatt.getService(BleUUIDs.STD_HR_SERVICE)
                ?.getCharacteristic(BleUUIDs.STD_HR_CHAR)
                ?.let { enableNotification(gatt, it) }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                characteristic.value ?: return
            } else {
                characteristic.value ?: return
            }
            parseCharacteristic(characteristic.uuid, bytes)
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            parseCharacteristic(characteristic.uuid, value)
        }
    }

    // ── Parseo ────────────────────────────────────────────────────────────────

    private fun parseCharacteristic(uuid: UUID, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        when (uuid) {
            BleUUIDs.HEART_RATE_CHAR, BleUUIDs.STD_HR_CHAR -> {
                // Estándar BLE HR: byte0 = flags, si bit0=0 HR en byte1, si bit0=1 HR en bytes 1-2
                val hr = if (bytes[0].toInt() and 0x01 == 0) {
                    bytes[1].toInt() and 0xFF
                } else {
                    ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                }
                _vitals.value = _vitals.value.copy(heartRate = hr)
            }
            BleUUIDs.GLUCOSE_CHAR -> {
                // Valor flotante IEEE 754 en 4 bytes little-endian
                if (bytes.size >= 4) {
                    val bits = (bytes[3].toInt() and 0xFF shl 24) or
                               (bytes[2].toInt() and 0xFF shl 16) or
                               (bytes[1].toInt() and 0xFF shl 8)  or
                               (bytes[0].toInt() and 0xFF)
                    _vitals.value = _vitals.value.copy(glucose = java.lang.Float.intBitsToFloat(bits).toDouble())
                } else {
                    _vitals.value = _vitals.value.copy(glucose = (bytes[0].toInt() and 0xFF).toDouble())
                }
            }
            BleUUIDs.SPO2_CHAR -> {
                _vitals.value = _vitals.value.copy(spo2 = bytes[0].toInt() and 0xFF)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotification(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        char.getDescriptor(BleUUIDs.CCCD)?.let { descriptor ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }
}
