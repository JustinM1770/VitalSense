package mx.ita.vitalsense.ui.archivos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.database.Cursor
import android.util.Log

/**
 * Helper para manejar la integración con Google Drive
 * Proporciona funciones para seleccionar carpetas y trabajar con URIs de Drive
 */
object GoogleDriveHelper {
    private const val TAG = "GoogleDriveHelper"

    private const val DRIVE_PACKAGE = "com.google.android.apps.docs"
    private const val GOOGLE_DRIVE_STORAGE_AUTHORITY = "com.google.android.apps.docs.storage"
    private const val DOCUMENTS_UI_PACKAGE = "com.android.documentsui"

    private val DRIVE_ROOT_DOCUMENT_URI: Uri = Uri.parse(
        "content://$GOOGLE_DRIVE_STORAGE_AUTHORITY/document/root"
    )

    private fun findDriveRootDocumentUri(context: Context): Uri? {
        return try {
            val rootsUri = DocumentsContract.buildRootsUri(GOOGLE_DRIVE_STORAGE_AUTHORITY)
            val projection = arrayOf(DocumentsContract.Root.COLUMN_DOCUMENT_ID)

            val cursor: Cursor? = context.contentResolver.query(
                rootsUri,
                projection,
                null,
                null,
                null,
            )

            cursor?.use {
                val docIdIdx = it.getColumnIndex(DocumentsContract.Root.COLUMN_DOCUMENT_ID)
                if (docIdIdx < 0) return null
                while (it.moveToNext()) {
                    val docId = it.getString(docIdIdx)?.takeIf { s -> s.isNotBlank() } ?: continue
                    val docUri = DocumentsContract.buildDocumentUri(GOOGLE_DRIVE_STORAGE_AUTHORITY, docId)
                    Log.d(TAG, "Drive root documentId='$docId' uri='$docUri'")
                    return docUri
                }
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not query Drive roots: ${e.message}")
            null
        }
    }
    
    /**
     * Crea el intent para seleccionar una carpeta.
     *
     * Nota importante: Android NO garantiza que se abra Drive directo.
     * En muchos dispositivos, el selector (SAF) decide el proveedor inicial.
     * Este intent solo intenta arrancar en Drive si está disponible.
     */
    fun createDriveFolderPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)

            // Permitir proveedores remotos (Drive). En algunos OEM ayuda.
            putExtra(Intent.EXTRA_LOCAL_ONLY, false)

            // Intentar iniciar dentro de Drive. Algunos pickers ignoran este extra.
            try {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, DRIVE_ROOT_DOCUMENT_URI)
                // Compat: algunos dispositivos leen esta clave en vez de EXTRA_INITIAL_URI
                putExtra("android.provider.extra.INITIAL_URI", DRIVE_ROOT_DOCUMENT_URI)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo establecer EXTRA_INITIAL_URI: ${e.message}")
            }
        }
    }

    /**
     * Intenta forzar el picker estándar (DocumentsUI). En varios Samsung esto hace que aparezca
     * el panel de proveedores (incluyendo Drive) en vez del explorador del teléfono.
     */
    fun createDriveFolderPickerIntentPreferDocumentsUi(context: Context): Intent {
        val intent = createDriveFolderPickerIntent()

        // Si el provider de Drive expone roots, usar un URI inicial real (más compatible que ".../document/root").
        val driveRoot = findDriveRootDocumentUri(context)
        if (driveRoot != null) {
            try {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, driveRoot)
                intent.putExtra("android.provider.extra.INITIAL_URI", driveRoot)
            } catch (e: Exception) {
                Log.w(TAG, "Could not override EXTRA_INITIAL_URI with Drive root: ${e.message}")
            }
        }

        return try {
            val pm = context.packageManager
            val docsUiAvailable = pm.resolveActivity(
                intent.setPackage(DOCUMENTS_UI_PACKAGE),
                0
            ) != null

            if (docsUiAvailable) {
                Log.d(TAG, "Using DocumentsUI picker")
                intent
            } else {
                // Fallback: sin package (deja al sistema elegir el picker)
                intent.setPackage(null)
                Log.d(TAG, "DocumentsUI not available; using default picker")
                intent
            }
        } catch (e: Exception) {
            intent.setPackage(null)
            Log.w(TAG, "Could not prefer DocumentsUI: ${e.message}")
            intent
        }
    }

    /**
     * Indica si el provider de Documentos de Google Drive existe.
     * Si esto da false, el selector de carpetas NO podrá mostrar Drive como opción.
     */
    fun isDriveDocumentsProviderAvailable(context: Context): Boolean {
        return try {
            context.packageManager.resolveContentProvider(GOOGLE_DRIVE_STORAGE_AUTHORITY, 0) != null
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * Verifica si un URI es un árbol de documentos (TREE_URI) válido de Google Drive
     */
    fun isValidTreeUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val authority = uri.authority.orEmpty().lowercase()
        val isTree = uri.toString().contains("tree/")
        val isGoogleDrive = authority.contains(GOOGLE_DRIVE_STORAGE_AUTHORITY) ||
            authority.contains("com.google.android.apps.docs")
        
        Log.d(TAG, "isValidTreeUri - URI: $uri, isTree: $isTree, isGoogleDrive: $isGoogleDrive, authority: $authority")
        
        return isTree && isGoogleDrive
    }
    
    /**
     * Obtiene el nombre de la carpeta desde el URI
     */
    fun getFolderNameFromUri(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (index >= 0) {
                        return it.getString(index)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting folder name: ${e.message}")
        }
        return null
    }
    
    /**
     * Valida y toma permisos persistentes para un URI
     */
    fun takePersistableUriPermission(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo tomar permiso persistente: ${e.message}")
            false
        }
    }
    
    /**
     * Verifica si Google Drive está instalada en el dispositivo
     */
    fun isGoogleDriveInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getApplicationInfo(DRIVE_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Abre Google Drive directamente si está instalada
     */
    fun openGoogleDriveApp(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(DRIVE_PACKAGE)
            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Google Drive: ${e.message}")
            false
        }
    }
}
