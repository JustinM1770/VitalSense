package mx.ita.vitalsense.ui.emergency

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.ita.vitalsense.data.emergency.EmergencyTokenRepository
import mx.ita.vitalsense.data.emergency.QrCodeGenerator
import mx.ita.vitalsense.data.emergency.TwilioEmergencyService

sealed interface EmergencyQrState {
    data object Idle    : EmergencyQrState
    data object Loading : EmergencyQrState
    data class Active(
        val qrBitmap: Bitmap,
        val tokenId: String,
        val pin: String,
        val expiresAt: Long,
        val anomalyType: String,
        val heartRate: Int,
    ) : EmergencyQrState
    data class Expired(val anomalyType: String) : EmergencyQrState
    data class Error(val message: String, val anomalyType: String, val heartRate: Int) : EmergencyQrState
}

class EmergencyQrViewModel : ViewModel() {

    private val repository    = EmergencyTokenRepository()
    private val twilioService = TwilioEmergencyService()

    private val _state = MutableStateFlow<EmergencyQrState>(EmergencyQrState.Idle)
    val state: StateFlow<EmergencyQrState> = _state.asStateFlow()

    private val _remainingSecs = MutableStateFlow(0)
    val remainingSecs: StateFlow<Int> = _remainingSecs.asStateFlow()

    private var expiryJob:    Job? = null
    private var countdownJob: Job? = null

    fun onAnomalyDetected(anomalyType: String, heartRate: Int) {
        if (_state.value is EmergencyQrState.Active) return
        viewModelScope.launch {
            _state.value = EmergencyQrState.Loading
            repository.createToken(anomalyType, heartRate)
                .onSuccess { created ->
                    // QR apunta a la web — cualquier navegador la abre, sin app
                    val bitmap    = QrCodeGenerator.generate(created.webUrl)
                    val expiresAt = System.currentTimeMillis() + 30 * 60 * 1000L

                    _state.value = EmergencyQrState.Active(
                        qrBitmap    = bitmap,
                        tokenId     = created.tokenId,
                        pin         = created.pin,
                        expiresAt   = expiresAt,
                        anomalyType = anomalyType,
                        heartRate   = heartRate,
                    )
                    startExpiry(created.tokenId, expiresAt, anomalyType)
                    startCountdown(expiresAt)

                    twilioService.triggerEmergencyCall(
                        tokenId     = created.tokenId,
                        anomalyType = anomalyType,
                        heartRate   = heartRate,
                        pin         = created.pin,
                    )
                }
                .onFailure { e ->
                    _state.value = EmergencyQrState.Error(
                        message     = e.message ?: "Error al generar QR",
                        anomalyType = anomalyType,
                        heartRate   = heartRate,
                    )
                }
        }
    }

    fun resolveEmergency() {
        val current = _state.value as? EmergencyQrState.Active ?: return
        expiryJob?.cancel()
        countdownJob?.cancel()
        viewModelScope.launch {
            repository.revokeToken(current.tokenId)
            _state.value = EmergencyQrState.Idle
        }
    }

    fun retry(anomalyType: String, heartRate: Int) = onAnomalyDetected(anomalyType, heartRate)

    private fun startExpiry(tokenId: String, expiresAt: Long, anomalyType: String) {
        expiryJob?.cancel()
        expiryJob = viewModelScope.launch {
            val remaining = expiresAt - System.currentTimeMillis()
            if (remaining > 0) delay(remaining)
            if (_state.value is EmergencyQrState.Active) {
                repository.revokeToken(tokenId)
                _state.value = EmergencyQrState.Expired(anomalyType)
            }
        }
    }

    private fun startCountdown(expiresAt: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                _remainingSecs.value = repository.remainingSeconds(expiresAt)
                delay(1_000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        expiryJob?.cancel()
        countdownJob?.cancel()
    }
}
