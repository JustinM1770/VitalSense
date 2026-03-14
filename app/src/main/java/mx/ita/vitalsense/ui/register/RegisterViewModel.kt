package mx.ita.vitalsense.ui.register

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.ita.vitalsense.data.auth.AuthRepository
import mx.ita.vitalsense.data.test.TestDataSeeder

sealed interface RegisterUiState {
    object Idle    : RegisterUiState
    object Loading : RegisterUiState
    object Success : RegisterUiState
    data class Error(val message: String) : RegisterUiState
}

class RegisterViewModel : ViewModel() {

    private val repo = AuthRepository()

    private val _state = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun registerWithEmail(email: String, password: String) {
        _state.value = RegisterUiState.Success
    }

    fun signInWithGoogle(context: Context) {
        _state.value = RegisterUiState.Success
    }

    fun enterDemoMode() {
        _state.value = RegisterUiState.Success
    }

    fun clearError() { _state.value = RegisterUiState.Idle }
}
