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

<<<<<<< HEAD
    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _state.value = LoginUiState.Loading
            repo.signInWithEmail(email, password)
                .onSuccess  { _state.value = LoginUiState.Success }
                .onFailure  { _state.value = LoginUiState.Error(it.localizedMessage ?: "Error al iniciar sesión") }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _state.value = LoginUiState.Loading
            repo.signInWithGoogle(context)
                .onSuccess  { _state.value = LoginUiState.Success }
                .onFailure  { e ->
                    // Cancelación voluntaria → volver a Idle sin error
                    if (e.message?.contains("cancel", ignoreCase = true) == true) {
                        _state.value = LoginUiState.Idle
                    } else {
                        _state.value = LoginUiState.Error(e.localizedMessage ?: "Error Google: ${e.message}")
                    }
                }
        }
=======
    fun signInWithEmail(email: String, password: String) {
        _state.value = LoginUiState.Success
    }

    fun signInWithGoogle(context: Context) {
        _state.value = LoginUiState.Success
>>>>>>> 5b14bb15ac5277f3be8467bf84e007c83ca41308
    }

    fun clearError() { _state.value = LoginUiState.Idle }
}
