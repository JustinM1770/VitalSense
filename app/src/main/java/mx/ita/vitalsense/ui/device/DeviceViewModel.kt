package mx.ita.vitalsense.ui.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.ita.vitalsense.data.ble.BleConnectionState
import mx.ita.vitalsense.data.ble.BleDevice
import mx.ita.vitalsense.data.ble.BleRepository
import mx.ita.vitalsense.data.ble.BleVitals

class DeviceViewModel(app: Application) : AndroidViewModel(app) {

    val repo = BleRepository(app.applicationContext)

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val connectionState: StateFlow<BleConnectionState> = repo.connectionState
    val vitals: StateFlow<BleVitals> = repo.vitals

    private var scanJob: Job? = null

    fun startScan() {
        if (_isScanning.value) return
        _devices.value = emptyList()
        _isScanning.value = true
        scanJob = viewModelScope.launch {
            repo.scanDevices().collect { list ->
                _devices.value = list
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    fun connect(device: BleDevice) {
        stopScan()
        repo.connect(device)
    }

    fun disconnect() = repo.disconnect()

    override fun onCleared() {
        super.onCleared()
        stopScan()
        repo.disconnect()
    }
}
