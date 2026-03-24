package mx.ita.vitalsense.ui.register

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.ita.vitalsense.data.auth.AuthRepository

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

    fun registerWithEmail(name: String, email: String, password: String) {
        viewModelScope.launch {
            _state.value = RegisterUiState.Loading
            repo.registerWithEmail(name, email, password)
                .onSuccess  { _state.value = RegisterUiState.Success }
                .onFailure  { _state.value = RegisterUiState.Error(it.localizedMessage ?: "Error") }
        }
    }

    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _state.value = RegisterUiState.Loading
            repo.signInWithEmail(email, password)
                .onSuccess  { _state.value = RegisterUiState.Success }
                .onFailure  { _state.value = RegisterUiState.Error(it.localizedMessage ?: "Error") }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _state.value = RegisterUiState.Loading
            repo.signInWithGoogle(context)
                .onSuccess  { _state.value = RegisterUiState.Success }
                .onFailure  { e ->
                    if (e.message?.contains("cancel", ignoreCase = true) == true) {
                        _state.value = RegisterUiState.Idle
                    } else {
                        _state.value = RegisterUiState.Error(e.localizedMessage ?: "Error Google: ${e.message}")
                    }
                }
        }
    }

    fun signInWithFacebook(context: Context) {
        viewModelScope.launch {
            _state.value = RegisterUiState.Loading
            repo.signInWithFacebook(context)
                .onSuccess  { _state.value = RegisterUiState.Success }
                .onFailure  { e ->
                    if (e.message?.contains("cancel", ignoreCase = true) == true) {
                        _state.value = RegisterUiState.Idle
                    } else {
                        _state.value = RegisterUiState.Error(e.localizedMessage ?: "Error Facebook: ${e.message}")
                    }
                }
        }
    }

    fun clearError() { _state.value = RegisterUiState.Idle }
}
