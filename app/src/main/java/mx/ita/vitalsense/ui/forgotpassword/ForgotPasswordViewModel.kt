package mx.ita.vitalsense.ui.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.ita.vitalsense.data.auth.AuthRepository

sealed interface ForgotPasswordUiState {
    object Idle      : ForgotPasswordUiState
    object Loading   : ForgotPasswordUiState
    object EmailSent : ForgotPasswordUiState
    data class Error(val message: String) : ForgotPasswordUiState
}

class ForgotPasswordViewModel : ViewModel() {

    private val repo = AuthRepository()

    private val _state = MutableStateFlow<ForgotPasswordUiState>(ForgotPasswordUiState.Idle)
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun sendResetEmail(email: String) {
        if (email.isBlank()) {
            _state.value = ForgotPasswordUiState.Error("Ingresa tu correo electrónico")
            return
        }
        viewModelScope.launch {
            _state.value = ForgotPasswordUiState.Loading
            repo.sendPasswordResetEmail(email)
                .onSuccess  { _state.value = ForgotPasswordUiState.EmailSent }
                .onFailure  { _state.value = ForgotPasswordUiState.Error(
                    it.localizedMessage ?: "Error al enviar el correo"
                ) }
        }
    }

    fun resetState() { _state.value = ForgotPasswordUiState.Idle }
}
