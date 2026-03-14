package mx.ita.vitalsense.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.ita.vitalsense.data.auth.AuthRepository

sealed interface LoginUiState {
    object Idle    : LoginUiState
    object Loading : LoginUiState
    object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}

class LoginViewModel : ViewModel() {

    private val repo = AuthRepository()

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun signInWithEmail(email: String, password: String) {
        _state.value = LoginUiState.Success
    }

    fun signInWithGoogle(context: Context) {
        _state.value = LoginUiState.Success
    }

    fun clearError() { _state.value = LoginUiState.Idle }
}
