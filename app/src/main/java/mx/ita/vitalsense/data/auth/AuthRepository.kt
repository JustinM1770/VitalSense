package mx.ita.vitalsense.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.R

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    // ── Email / Password ──────────────────────────────────────────────────────

    suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            auth.createUserWithEmailAndPassword(email, password).await().user!!
        }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            auth.signInWithEmailAndPassword(email, password).await().user!!
        }

    // ── Google Sign-In (Credential Manager API) ───────────────────────────────
    //
    // REQUISITO:
    //   Firebase Console → Authentication → Sign-in method → Habilitar Google
    //   Luego actualiza R.string.default_web_client_id con el Web Client ID real.

    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> {
        val credentialManager = CredentialManager.create(activityContext)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)          // muestra todas las cuentas
            .setServerClientId(activityContext.getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return runCatching {
            val result = credentialManager.getCredential(activityContext, request)
            val googleIdToken = GoogleIdTokenCredential
                .createFrom(result.credential.data)
                .idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            auth.signInWithCredential(firebaseCredential).await().user!!
        }.recoverCatching { e ->
            // El usuario canceló el selector → relanzamos con mensaje claro
            if (e is GetCredentialCancellationException) throw e
            throw e
        }
    }

    // ── Modo Demo: acceso anónimo sin registro ────────────────────────────────
    suspend fun signInAnonymously(): Result<FirebaseUser> =
        runCatching { auth.signInAnonymously().await().user!! }

    val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous == true

    fun signOut() = auth.signOut()
}
