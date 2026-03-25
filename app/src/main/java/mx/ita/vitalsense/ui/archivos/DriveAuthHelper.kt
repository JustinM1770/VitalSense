package mx.ita.vitalsense.ui.archivos

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

object DriveAuthHelper {
    private const val TAG = "DriveAuthHelper"

    // Least-privilege-ish set that still allows listing folders + creating files.
    private const val SCOPE_METADATA_READONLY = "https://www.googleapis.com/auth/drive.metadata.readonly"
    private const val SCOPE_FILE = "https://www.googleapis.com/auth/drive.file"

    private val scopes = arrayOf(
        Scope(SCOPE_METADATA_READONLY),
        Scope(SCOPE_FILE),
    )

    fun buildGoogleSignInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(scopes[0], *scopes.drop(1).toTypedArray())
            .build()

        return GoogleSignIn.getClient(context, options)
    }

    fun getLastSignedInAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun getSignInIntent(context: Context): Intent {
        return buildGoogleSignInClient(context).signInIntent
    }

    /**
     * Returns an OAuth2 access token for Drive API calls.
     *
     * Can return NeedsUserConsent if Google requires user approval for the requested scopes.
     */
    fun getAccessToken(context: Context, account: GoogleSignInAccount): DriveAuthResult {
        val androidAccount = account.account
            ?: return DriveAuthResult.Error("Cuenta de Google inválida (account=null)")

        val scopeString = "oauth2:${SCOPE_METADATA_READONLY} ${SCOPE_FILE}"

        return try {
            val token = GoogleAuthUtil.getToken(context, androidAccount, scopeString)
            DriveAuthResult.Token(token)
        } catch (e: UserRecoverableAuthException) {
            Log.w(TAG, "User consent required for Drive scopes")
            val intent = e.intent
            if (intent != null) {
                DriveAuthResult.NeedsUserConsent(intent)
            } else {
                DriveAuthResult.Error("Se requiere consentimiento de Drive, pero no se pudo abrir la pantalla de permisos", e)
            }
        } catch (e: GoogleAuthException) {
            DriveAuthResult.Error("No se pudo autenticar con Google", e)
        } catch (e: Exception) {
            DriveAuthResult.Error("Error al obtener token de Drive", e)
        }
    }

    fun signOut(context: Context) {
        try {
            buildGoogleSignInClient(context).signOut()
        } catch (e: Exception) {
            Log.w(TAG, "signOut failed: ${e.message}")
        }
    }
}
