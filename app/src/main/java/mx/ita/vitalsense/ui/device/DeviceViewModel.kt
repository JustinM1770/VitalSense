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
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.repository.VitalsRepository

class DeviceViewModel(app: Application) : AndroidViewModel(app) {

    val repo = BleRepository(app.applicationContext)
    private val vitalsRepo = VitalsRepository()

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val connectionState: StateFlow<BleConnectionState> = repo.connectionState
    val vitals: StateFlow<BleVitals> = repo.vitals

    // ID del paciente al que se asocian las lecturas BLE
    private val _selectedPatientId = MutableStateFlow("")
    val selectedPatientId: StateFlow<String> = _selectedPatientId.asStateFlow()

    private var scanJob: Job? = null
    private var snapshotJob: Job? = null

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
        startSnapshotSaving()
    }

    fun disconnect() {
        snapshotJob?.cancel()
        repo.disconnect()
    }

    fun setPatientId(patientId: String) {
        _selectedPatientId.value = patientId
    }

    /** Guarda cada lectura BLE como snapshot histórico en Firebase. */
    private fun startSnapshotSaving() {
        snapshotJob?.cancel()
        snapshotJob = viewModelScope.launch {
            vitals.collect { ble ->
                val patientId = _selectedPatientId.value
                if (patientId.isNotEmpty() && (ble.heartRate != null || ble.glucose != null || ble.spo2 != null)) {
                    val vitalsData = VitalsData(
                        patientId = patientId,
                        heartRate = ble.heartRate ?: 0,
                        glucose   = ble.glucose   ?: 0.0,
                        spo2      = ble.spo2       ?: 0,
                        timestamp = System.currentTimeMillis(),
                    )
                    vitalsRepo.saveSnapshot(patientId, vitalsData)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        snapshotJob?.cancel()
        repo.disconnect()
    }
}
