package mx.ita.vitalsense.ui.archivos

data class DriveFolder(
    val id: String,
    val name: String,
)

data class DriveFileItem(
    val id: String,
    val name: String,
    val mimeType: String? = null,
)

sealed class DriveAuthResult {
    data class Token(val accessToken: String) : DriveAuthResult()
    data class NeedsUserConsent(val consentIntent: android.content.Intent) : DriveAuthResult()
    data class Error(val message: String, val cause: Throwable? = null) : DriveAuthResult()
}
