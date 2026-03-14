package mx.ita.vitalsense.ui.profile

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import mx.ita.vitalsense.data.auth.AuthRepository

class ProfileViewModel : ViewModel() {

    private val repo = AuthRepository()
    private val user get() = FirebaseAuth.getInstance().currentUser

    val displayName: String
        get() = user?.displayName?.takeIf { it.isNotBlank() } ?: "Tutor"

    val email: String
        get() = user?.email ?: "—"

    val initials: String
        get() = displayName
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "T" }

    fun signOut() = repo.signOut()
}
