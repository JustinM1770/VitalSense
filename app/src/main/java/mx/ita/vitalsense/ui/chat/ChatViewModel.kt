package mx.ita.vitalsense.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean
)

class ChatViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val userName = auth.currentUser?.displayName?.split(" ")?.firstOrNull() ?: "Jonathan"

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                id = "1",
                text = "Hola $userName, Tienes alguna duda?",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        val userMsg = ChatMessage(id = System.currentTimeMillis().toString(), text = text, isUser = true)
        _messages.value = _messages.value + userMsg
        
        // Simular IA pensando y respondiendo
        viewModelScope.launch {
            _isTyping.value = true
            delay(1500)
            
            val aiResponse = ChatMessage(
                id = (System.currentTimeMillis() + 1).toString(),
                text = "Entiendo tu inquietud sobre los signos vitales. Según el último escaneo, la frecuencia cardíaca se mantuvo estable. Si experimentas taquicardias te sugiero consultar a un médico.",
                isUser = false
            )
            _messages.value = _messages.value + aiResponse
            _isTyping.value = false
        }
    }
}
