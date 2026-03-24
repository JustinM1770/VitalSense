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
        viewModelScope.launch {
            _state.value = LoginUiState.Loading
            repo.signInWithEmail(email, password)
                .onSuccess  { _state.value = LoginUiState.Success }
                .onFailure  { _state.value = LoginUiState.Error(it.localizedMessage ?: "Error al iniciar sesión") }
        }
    }

    // Alias used by HEAD's LoginScreen
    fun loginWithEmail(email: String, password: String) = signInWithEmail(email, password)

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
    }

    fun signInWithFacebook(context: Context) {
        viewModelScope.launch {
            _state.value = LoginUiState.Loading
            repo.signInWithFacebook(context)
                .onSuccess  { _state.value = LoginUiState.Success }
                .onFailure  { e ->
                    if (e.message?.contains("cancel", ignoreCase = true) == true) {
                        _state.value = LoginUiState.Idle
                    } else {
                        _state.value = LoginUiState.Error(e.localizedMessage ?: "Error Facebook: ${e.message}")
                    }
                }
        }
    }

    /** Called after biometric passes — re-uses existing Firebase session if any. */
    fun signInWithBiometric(onSuccess: () -> Unit) {
        val current = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (current != null) {
            onSuccess()
        } else {
            _state.value = LoginUiState.Error("No hay sesión guardada. Inicia sesión primero.")
        }
    }

    fun clearError() { _state.value = LoginUiState.Idle }
}
