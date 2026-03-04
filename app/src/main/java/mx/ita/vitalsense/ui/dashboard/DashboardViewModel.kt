package mx.ita.vitalsense.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.repository.VitalsRepository

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(val vitals: VitalsData) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel : ViewModel() {

    private val repository = VitalsRepository()

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeVitals()
    }

    private fun observeVitals() {
        viewModelScope.launch {
            repository.observeVitals().collect { result ->
                _uiState.value = result.fold(
                    onSuccess = { DashboardUiState.Success(it) },
                    onFailure = { DashboardUiState.Error(it.message ?: "Error al conectar con Firebase") },
                )
            }
        }
    }
}
