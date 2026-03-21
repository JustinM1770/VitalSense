package mx.ita.vitalsense.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.R

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    // ── Email / Password ──────────────────────────────────────────────────────

    suspend fun registerWithEmail(name: String, email: String, password: String): Result<FirebaseUser> =
        runCatching {
            val user = auth.createUserWithEmailAndPassword(email, password).await().user!!
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            user.updateProfile(profileUpdates).await()
            user
        }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            auth.signInWithEmailAndPassword(email, password).await().user!!
        }

    // ── Google Sign-In (Credential Manager API) ───────────────────────────────

    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> {
        val activity = activityContext.findActivity()
            ?: throw IllegalStateException("Contexto no es una Activity")

        val credentialManager = CredentialManager.create(activity)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activity.getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return runCatching {
            val result = credentialManager.getCredential(activity, request)
            val googleIdToken = GoogleIdTokenCredential
                .createFrom(result.credential.data)
                .idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            auth.signInWithCredential(firebaseCredential).await().user!!
        }.recoverCatching { e ->
            if (e is GetCredentialCancellationException) throw e
            throw e
        }
    }

    // ── Facebook Sign-In ──────────────────────────────────────────────────────
    suspend fun signInWithFacebook(activityContext: Context): Result<FirebaseUser> {
        val activity = activityContext.findActivity()
            ?: return Result.failure(IllegalStateException("Contexto no es una Activity"))

        return kotlin.coroutines.suspendCoroutine { cont ->
            val callbackManager = com.facebook.CallbackManager.Factory.create()
            val loginManager = com.facebook.login.LoginManager.getInstance()

            loginManager.registerCallback(
                callbackManager,
                object : com.facebook.FacebookCallback<com.facebook.login.LoginResult> {
                    override fun onSuccess(result: com.facebook.login.LoginResult) {
                        val token = result.accessToken.token
                        val credential = com.google.firebase.auth.FacebookAuthProvider.getCredential(token)
                        auth.signInWithCredential(credential)
                            .addOnSuccessListener { authResult ->
                                cont.resumeWith(Result.success(Result.success(authResult.user!!)))
                            }
                            .addOnFailureListener { e ->
                                cont.resumeWith(Result.success(Result.failure(e)))
                            }
                    }

                    override fun onCancel() {
                        cont.resumeWith(Result.success(Result.failure(Exception("Inicio de sesión cancelado"))))
                    }

                    override fun onError(error: com.facebook.FacebookException) {
                        cont.resumeWith(Result.success(Result.failure(error)))
                    }
                }
            )

            loginManager.logInWithReadPermissions(
                activity as android.app.Activity,
                listOf("public_profile")
            )
        }
    }

    // ── Modo Demo: acceso anónimo sin registro ────────────────────────────────
    suspend fun signInAnonymously(): Result<FirebaseUser> =
        runCatching { auth.signInAnonymously().await().user!! }

    val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous == true

    // ── Recuperar contraseña ─────────────────────────────────────────────────
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        runCatching { auth.sendPasswordResetEmail(email).await() }

    fun signOut() = auth.signOut()
}
