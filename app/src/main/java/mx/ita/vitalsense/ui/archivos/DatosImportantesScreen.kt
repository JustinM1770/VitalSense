package mx.ita.vitalsense.ui.archivos

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import mx.ita.vitalsense.data.emergency.StorageDoc
import mx.ita.vitalsense.ui.components.BottomNav
import mx.ita.vitalsense.ui.components.BottomNavTab
import mx.ita.vitalsense.ui.theme.DashBg
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope

@Composable
fun DatosImportantesScreen(
    onBack: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onHealthClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onNotifClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser
    val uid = currentUser?.uid.orEmpty()
    val profilePrefs = remember { context.getSharedPreferences("vitalsense_profile", Context.MODE_PRIVATE) }

    val avatarUri = if (uid.isNotEmpty()) profilePrefs.getString("avatar_uri_$uid", null) else null
    val displayName = currentUser?.displayName ?: "Usuario"
    val initials = buildString {
        displayName.split(" ").forEach { word -> word.firstOrNull()?.let { append(it.uppercaseChar()) } }
    }.take(2).ifEmpty { "VS" }

    var driveTreeUri by remember { mutableStateOf(if (uid.isNotEmpty()) profilePrefs.getString("drive_tree_uri_$uid", "") ?: "" else "") }
    var driveFolderUrl by remember { mutableStateOf(if (uid.isNotEmpty()) profilePrefs.getString("drive_folder_url_$uid", "") ?: "" else "") }
    var driveFolderId by remember { mutableStateOf(if (uid.isNotEmpty()) profilePrefs.getString("drive_folder_id_$uid", "") ?: "" else "") }
    val documents = remember { mutableStateListOf<String>() }
    val storageDocuments = remember { mutableStateListOf<StorageDoc>() }
    var isUploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var driveAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    var driveFolders by remember { mutableStateOf<List<DriveFolder>>(emptyList()) }
    var driveFilesCache by remember { mutableStateOf<List<DriveFileItem>>(emptyList()) }
    var isDriveLoading by remember { mutableStateOf(false) }
    var showDriveFolderList by remember { mutableStateOf(false) }
    var pendingAccountForConsent by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    var pendingConsentIntent by remember { mutableStateOf<Intent?>(null) }

    LaunchedEffect(uid) {
        documents.clear()
        if (uid.isNotEmpty()) {
            val saved = profilePrefs.getStringSet("documents_$uid", emptySet()) ?: emptySet()
            documents.addAll(saved.sorted())

            FirebaseDatabase.getInstance().getReference("patients/$uid/profile/documents")
                .get()
                .addOnSuccessListener { snap ->
                    val remoteDocs = snap.children.mapNotNull { it.getValue(String::class.java) }
                    if (remoteDocs.isNotEmpty()) {
                        documents.clear()
                        documents.addAll(remoteDocs.sorted())
                        profilePrefs.edit().putStringSet("documents_$uid", documents.toSet()).apply()
                    }
                }

            if (driveTreeUri.isBlank()) {
                FirebaseDatabase.getInstance().getReference("patients/$uid/profile/driveTreeUri")
                    .get()
                    .addOnSuccessListener { snap ->
                        val remoteTree = snap.getValue(String::class.java).orEmpty()
                        if (remoteTree.isNotBlank()) {
                            driveTreeUri = remoteTree
                            if (driveFolderUrl.isBlank()) {
                                driveFolderUrl = remoteTree
                            }
                            profilePrefs.edit().putString("drive_tree_uri_$uid", remoteTree).apply()
                            // Cargar archivos desde la carpeta de Drive
                            loadFilesFromDriveFolder(context, remoteTree, documents)
                        }
                    }
            } else if (driveTreeUri.isNotBlank()) {
                // Si ya hay una carpeta seleccionada, cargar archivos desde ella
                loadFilesFromDriveFolder(context, driveTreeUri, documents)
            }

            if (driveFolderId.isBlank()) {
                FirebaseDatabase.getInstance().getReference("patients/$uid/profile/driveFolderId")
                    .get()
                    .addOnSuccessListener { snap ->
                        val remoteId = snap.getValue(String::class.java).orEmpty()
                        if (remoteId.isNotBlank()) {
                            driveFolderId = remoteId
                            val url = "https://drive.google.com/drive/folders/$remoteId"
                            driveFolderUrl = url
                            profilePrefs.edit()
                                .putString("drive_folder_id_$uid", remoteId)
                                .putString("drive_folder_url_$uid", url)
                                .apply()
                        }
                    }
            }

            // fallback: recover folder URL from Firebase profile if local is empty
            if (driveFolderUrl.isBlank()) {
                FirebaseDatabase.getInstance().getReference("patients/$uid/profile/driveFolderUrl")
                    .get()
                    .addOnSuccessListener { snap ->
                        val remote = snap.getValue(String::class.java).orEmpty()
                        if (remote.isNotBlank()) {
                            driveFolderUrl = remote
                            profilePrefs.edit().putString("drive_folder_url_$uid", remote).apply()
                        }
                    }
            }

            // Cargar documentos de Firebase Storage
            FirebaseDatabase.getInstance().getReference("patients/$uid/profile/storageDocuments")
                .get()
                .addOnSuccessListener { snap ->
                    val docs = snap.children.mapNotNull { child ->
                        val nombre = child.child("nombre").getValue(String::class.java) ?: return@mapNotNull null
                        val url    = child.child("url").getValue(String::class.java)    ?: return@mapNotNull null
                        val tipo   = child.child("tipo").getValue(String::class.java)   ?: "pdf"
                        StorageDoc(nombre = nombre, url = url, tipo = tipo)
                    }
                    if (docs.isNotEmpty()) {
                        storageDocuments.clear()
                        storageDocuments.addAll(docs)
                    }
                }
        }
    }

    fun startDriveFolderListing(account: GoogleSignInAccount) {
        driveAccount = account
        pendingAccountForConsent = null
        pendingConsentIntent = null
        isDriveLoading = true
        showDriveFolderList = true

        coroutineScope.launch {
            val auth = withContext(Dispatchers.IO) {
                DriveAuthHelper.getAccessToken(context, account)
            }

            when (auth) {
                is DriveAuthResult.Token -> {
                    val folders = try {
                        withContext(Dispatchers.IO) { DriveRestClient.listFolders(auth.accessToken) }
                    } catch (e: Exception) {
                        val msg = when (e) {
                            is DriveRestClient.DriveApiException -> {
                                android.util.Log.e(
                                    "DatosImportantes",
                                    "Drive listFolders failed HTTP ${e.httpCode}. raw=${e.rawBody}",
                                )

                                val lower = e.message.lowercase()
                                when {
                                    lower.contains("accessnotconfigured") ||
                                        lower.contains("has not been used") ||
                                        lower.contains("drive.googleapis.com") -> {
                                        "Drive API bloqueada (HTTP ${e.httpCode}).\n" +
                                            "Activa Google Drive API en Google Cloud Console y vuelve a intentar."
                                    }

                                    lower.contains("insufficientpermissions") ||
                                        lower.contains("insufficient permission") -> {
                                        "Permisos insuficientes (HTTP ${e.httpCode}).\n" +
                                            "Cierra sesión y vuelve a iniciar sesión aceptando permisos de Drive."
                                    }

                                    else -> "No se pudo enlistar carpetas (HTTP ${e.httpCode}).\n${e.message}".take(260)
                                }
                            }

                            else -> {
                                android.util.Log.e("DatosImportantes", "Drive listFolders error: ${e.message}", e)
                                "No se pudo enlistar carpetas de Drive"
                            }
                        }

                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        emptyList()
                    }
                    driveFolders = folders
                }

                is DriveAuthResult.NeedsUserConsent -> {
                    pendingAccountForConsent = account
                    pendingConsentIntent = auth.consentIntent
                }

                is DriveAuthResult.Error -> {
                    Toast.makeText(context, auth.message, Toast.LENGTH_LONG).show()
                }
            }

            isDriveLoading = false
        }
    }

    val driveSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(Exception::class.java)
            if (account != null) {
                startDriveFolderListing(account)
            } else {
                Toast.makeText(context, "No se pudo iniciar sesión con Google", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Inicio de sesión cancelado", Toast.LENGTH_SHORT).show()
        }
    }

    val driveConsentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val account = pendingAccountForConsent
        if (account != null) {
            startDriveFolderListing(account)
        } else {
            Toast.makeText(context, "Permiso de Drive cancelado", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(pendingConsentIntent) {
        val intent = pendingConsentIntent ?: return@LaunchedEffect
        // Lanzar el consentimiento como side-effect controlado.
        driveConsentLauncher.launch(intent)
        pendingConsentIntent = null
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        val uri = result.data?.data
        if (uri == null) {
            android.util.Log.w("DatosImportantes", "folderPicker: URI es null")
            return@rememberLauncherForActivityResult
        }

        android.util.Log.d("DatosImportantes", "URI seleccionado: $uri")
        android.util.Log.d("DatosImportantes", "Authority: ${uri.authority}")

        if (!isGoogleDriveTreeUri(uri)) {
            android.util.Log.w("DatosImportantes", "URI no es de Google Drive válido: $uri")
            android.util.Log.w("DatosImportantes", "Authority encontrada: ${uri.authority}")
            
            // Mostrar mensaje más detallado
            val msg = if (GoogleDriveHelper.isGoogleDriveInstalled(context)) {
                "⚠️ No seleccionaste Google Drive.\n\n" +
                "En el selector, en la parte superior IZQUIERDA donde dice 'Almacenamiento interno' o 'Descargas',\n" +
                "TAP ahí y selecciona 'Google Drive'\n\n" +
                "Intentemos de nuevo."
            } else {
                "⚠️ Google Drive no parece estar instalada.\n\n" +
                "Necesitas tener Google Drive instalada para usar esta función.\n" +
                "Descárgala desde Play Store."
            }
            
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        // Tomar permisos persistentes
        val permissionTaken = GoogleDriveHelper.takePersistableUriPermission(context, uri)
        if (!permissionTaken) {
            android.util.Log.w("DatosImportantes", "No se pudo tomar permiso persistente")
        }

        driveTreeUri = uri.toString()
        driveFolderUrl = uri.toString()
        
        val folderName = GoogleDriveHelper.getFolderNameFromUri(context, uri)
        android.util.Log.d("DatosImportantes", "Carpeta seleccionada: '$folderName'")
        
        if (uid.isNotEmpty()) {
            profilePrefs.edit()
                .putString("drive_tree_uri_$uid", driveTreeUri)
                .putString("drive_folder_url_$uid", driveFolderUrl)
                .putBoolean("drive_folder_locked_$uid", true)
                .apply()
            FirebaseDatabase.getInstance().getReference("patients/$uid/profile")
                .updateChildren(
                    mapOf(
                        "driveTreeUri" to driveTreeUri,
                        "driveFolderUrl" to driveFolderUrl,
                    )
                )
        }
        Toast.makeText(context, "Carpeta de Drive seleccionada correctamente: $folderName", Toast.LENGTH_SHORT).show()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { fileUri ->
        if (fileUri == null) return@rememberLauncherForActivityResult
        if (uid.isEmpty()) {
            Toast.makeText(context, "Debes iniciar sesión para subir documentos", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        val fileName = queryDisplayName(context, fileUri) ?: "documento_${System.currentTimeMillis()}"
        val mime = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
        val tipo = if (mime.startsWith("image/")) "imagen" else "pdf"
        scope.launch {
            isUploading = true
            try {
                val stream = context.contentResolver.openInputStream(fileUri)
                    ?: run {
                        Toast.makeText(context, "No se pudo leer el archivo", Toast.LENGTH_SHORT).show()
                        isUploading = false
                        return@launch
                    }
                val storageRef = FirebaseStorage.getInstance().reference.child("documents/$uid/$fileName")
                storageRef.putStream(stream).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()
                val newDoc = StorageDoc(nombre = fileName, url = downloadUrl, tipo = tipo)
                storageDocuments.add(newDoc)
                val docsToSave = storageDocuments.map {
                    mapOf("nombre" to it.nombre, "url" to it.url, "tipo" to it.tipo)
                }
                FirebaseDatabase.getInstance().getReference("patients/$uid/profile/storageDocuments")
                    .setValue(docsToSave).await()
                Toast.makeText(context, "Documento subido correctamente", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al subir: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isUploading = false
            }
        }
    }

    val qrBitmap = remember(driveFolderUrl) {
        if (driveFolderUrl.isNotBlank()) generateQrBitmap(driveFolderUrl) else null
    }
    val isDriveFolderSelected = remember(driveFolderId, driveTreeUri) {
        driveFolderId.isNotBlank() || (driveTreeUri.isNotBlank() && isGoogleDriveTreeUri(Uri.parse(driveTreeUri)))
    }

    Box(modifier = Modifier.fillMaxSize().background(DashBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp),
        ) {
            Spacer(Modifier.height(52.dp))

            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(DashBlue)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(DashBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!avatarUri.isNullOrBlank()) {
                        AsyncImage(
                            model = Uri.parse(avatarUri),
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = initials,
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color.White)
                    .padding(24.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Datos Importantes",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF1A1A2E),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(20.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = when {
                                isDriveFolderSelected -> "Carpeta Drive conectada"
                                driveTreeUri.isNotBlank() -> "Carpeta no valida (no es Google Drive)"
                                else -> "Sin carpeta de Drive seleccionada"
                            },
                            fontFamily = Manrope,
                            fontSize = 12.sp,
                            color = Color(0xFF4B5563),
                            maxLines = 1,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isDriveFolderSelected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (isDriveFolderSelected) Color(0xFF10B981) else Color(0xFFFF9800),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (isDriveFolderSelected) {
                                "Carpeta lista para subir documentos"
                            } else if (driveTreeUri.isNotBlank()) {
                                "Carpeta local detectada: selecciona una carpeta de Google Drive"
                            } else {
                                "Selecciona una carpeta para continuar"
                            },
                            fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (isDriveFolderSelected) Color(0xFF10B981) else Color(0xFFFF9800),
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (driveTreeUri.isNotBlank() && !isDriveFolderSelected) {
                            "Solo se permite carpeta de Google Drive para subir y sincronizar en la nube."
                        } else {
                            "La ruta solo se define con el selector de Google Drive."
                        },
                        fontFamily = Manrope,
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Button(
                            onClick = {
                                // UX: si el usuario va a usar Drive API, no mostrar advertencia por carpeta local previa
                                driveTreeUri = ""

                                val last = DriveAuthHelper.getLastSignedInAccount(context)
                                if (last == null) {
                                    driveSignInLauncher.launch(DriveAuthHelper.getSignInIntent(context))
                                } else {
                                    startDriveFolderListing(last)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DashBlue),
                        ) {
                            Text("Seleccionar carpeta de Drive", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    if (showDriveFolderList) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (isDriveLoading) "Cargando carpetas de Drive..." else "Elige una carpeta de Drive",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color(0xFF4B5563),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(6.dp))

                        if (!isDriveLoading && driveFolders.isEmpty()) {
                            Text(
                                text = "No se encontraron carpetas o no hay permiso.",
                                fontFamily = Manrope,
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            driveFolders.take(50).forEach { folder ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(10.dp))
                                        .clickable {
                                            driveFolderId = folder.id
                                            val url = "https://drive.google.com/drive/folders/${folder.id}"
                                            driveFolderUrl = url
                                            driveTreeUri = ""
                                            showDriveFolderList = false

                                            if (uid.isNotEmpty()) {
                                                profilePrefs.edit()
                                                    .putString("drive_folder_id_$uid", driveFolderId)
                                                    .putString("drive_folder_url_$uid", driveFolderUrl)
                                                    .putString("drive_tree_uri_$uid", "")
                                                    .putBoolean("drive_folder_locked_$uid", true)
                                                    .apply()
                                                FirebaseDatabase.getInstance().getReference("patients/$uid/profile")
                                                    .updateChildren(
                                                        mapOf(
                                                            "driveFolderId" to driveFolderId,
                                                            "driveFolderUrl" to driveFolderUrl,
                                                            "driveTreeUri" to "",
                                                        )
                                                    )
                                            }

                                            Toast.makeText(context, "Carpeta seleccionada: ${folder.name}", Toast.LENGTH_SHORT).show()

                                            val account = driveAccount ?: DriveAuthHelper.getLastSignedInAccount(context)
                                            if (account != null) {
                                                coroutineScope.launch {
                                                    isDriveLoading = true
                                                    val tokenResult = withContext(Dispatchers.IO) {
                                                        DriveAuthHelper.getAccessToken(context, account)
                                                    }
                                                    if (tokenResult is DriveAuthResult.Token) {
                                                        try {
                                                            val files = withContext(Dispatchers.IO) {
                                                                DriveRestClient.listFilesInFolder(
                                                                    accessToken = tokenResult.accessToken,
                                                                    folderId = driveFolderId,
                                                                )
                                                            }
                                                            driveFilesCache = files
                                                            documents.clear()
                                                            documents.addAll(files.map { it.name })
                                                            if (uid.isNotEmpty()) {
                                                                profilePrefs.edit().putStringSet("documents_$uid", documents.toSet()).apply()
                                                                FirebaseDatabase.getInstance().getReference("patients/$uid/profile/documents")
                                                                    .setValue(documents)
                                                            }
                                                        } catch (_: Exception) {
                                                            // Silencioso: la carpeta quedó guardada aunque no se pueda listar.
                                                        }
                                                    }
                                                    isDriveLoading = false
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = folder.name,
                                        fontFamily = Manrope,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1A1A2E),
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val urlToOpen = when {
                                driveFolderId.isNotBlank() && driveFolderUrl.isNotBlank() -> driveFolderUrl
                                driveTreeUri.isNotBlank() -> driveTreeUri
                                else -> ""
                            }

                            if (urlToOpen.isBlank()) {
                                Toast.makeText(context, "Primero selecciona una carpeta", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            try {
                                val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)).apply {
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                }
                                context.startActivity(openIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "No se pudo abrir la carpeta", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90E2)),
                    ) {
                        Text("Abrir carpeta seleccionada", color = Color.White)
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { filePicker.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DashBlue),
                    ) {
                        Text("Agregar documento", color = Color.White)
                    }

                    Spacer(Modifier.height(18.dp))

                    if (documents.isEmpty()) {
                        Text(
                            text = "No hay documentos subidos",
                            fontFamily = Manrope,
                            fontSize = 13.sp,
                            color = Color(0xFF8A8A8A),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            documents.forEach { name ->
                                DocumentCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    filename = name,
                                    onDelete = { filename ->
                                        documents.remove(filename)
                                        if (uid.isNotEmpty()) {
                                            profilePrefs.edit().putStringSet("documents_$uid", documents.toSet()).apply()
                                            FirebaseDatabase.getInstance().getReference("patients/$uid/profile/documents")
                                                .setValue(documents)

                                            if (driveFolderId.isNotBlank()) {
                                                val account = driveAccount ?: DriveAuthHelper.getLastSignedInAccount(context)
                                                val cached = driveFilesCache.firstOrNull { it.name == filename }
                                                if (account == null || cached == null) {
                                                    Toast.makeText(context, "Eliminado localmente (no se pudo borrar en Drive)", Toast.LENGTH_LONG).show()
                                                } else {
                                                    coroutineScope.launch {
                                                        val tokenResult = withContext(Dispatchers.IO) {
                                                            DriveAuthHelper.getAccessToken(context, account)
                                                        }
                                                        if (tokenResult is DriveAuthResult.Token) {
                                                            try {
                                                                withContext(Dispatchers.IO) {
                                                                    DriveRestClient.deleteFile(tokenResult.accessToken, cached.id)
                                                                }
                                                                driveFilesCache = driveFilesCache.filterNot { it.id == cached.id }
                                                                Toast.makeText(context, "Documento eliminado en Drive", Toast.LENGTH_SHORT).show()
                                                            } catch (_: Exception) {
                                                                Toast.makeText(context, "No se pudo eliminar en Drive", Toast.LENGTH_LONG).show()
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "No se pudo validar permiso de Drive", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            } else {
                                                deleteFileFromDrive(context, driveTreeUri, filename)
                                                Toast.makeText(context, "Documento eliminado", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    // — Documentos en la nube (Firebase Storage) —
                    Text(
                        text = "Documentos en la nube",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color(0xFF1A1A2E),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))

                    if (isUploading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = DashBlue,
                            )
                            Text(
                                text = "Subiendo documento...",
                                fontFamily = Manrope,
                                fontSize = 13.sp,
                                color = Color(0xFF4B5563),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (storageDocuments.isEmpty() && !isUploading) {
                        Text(
                            text = "No hay documentos en la nube",
                            fontFamily = Manrope,
                            fontSize = 13.sp,
                            color = Color(0xFF8A8A8A),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            storageDocuments.forEachIndexed { index, doc ->
                                StorageDocCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    doc = doc,
                                    onDelete = {
                                        storageDocuments.removeAt(index)
                                        if (uid.isNotEmpty()) {
                                            val docsToSave = storageDocuments.map {
                                                mapOf("nombre" to it.nombre, "url" to it.url, "tipo" to it.tipo)
                                            }
                                            FirebaseDatabase.getInstance()
                                                .getReference("patients/$uid/profile/storageDocuments")
                                                .setValue(docsToSave)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    if (qrBitmap != null) {
                        Text(
                            text = "QR Carpeta Drive",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFF1A1A2E),
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Drive",
                            modifier = Modifier.size(170.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                        ) {
                            Text(
                                text = "Selecciona y guarda la ruta de carpeta para generar el QR.",
                                fontFamily = Manrope,
                                fontSize = 12.sp,
                                color = Color(0xFF8A8A8A),
                            )
                        }
                    }

                    Spacer(Modifier.height(26.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) {
                            Text(
                                text = "Cancelar",
                                fontFamily = Manrope,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = DashBlue,
                            )
                        }
                        Button(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DashBlue),
                        ) {
                            Text("Listo", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        BottomNav(
            selected = BottomNavTab.PROFILE,
            onSelect = { tab ->
                when (tab) {
                    BottomNavTab.HOME -> onHomeClick()
                    BottomNavTab.HEALTH -> onHealthClick()
                    BottomNavTab.CHAT -> onChatClick()
                    else -> {}
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DocumentCard(
    modifier: Modifier = Modifier,
    filename: String,
    onDelete: (String) -> Unit = {},
) {
    Box(
        modifier = modifier
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Column {
            Text(
                text = "Nombre",
                fontFamily = Manrope,
                fontSize = 10.sp,
                color = Color(0xFFB0B0B0),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = filename,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1A1A2E),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFFEBEE)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "DOC",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        color = Color(0xFFE53935),
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Button(
                onClick = { onDelete(filename) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE)),
            ) {
                Text(
                    text = "Eliminar",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = Color(0xFFE53935),
                )
            }
        }
    }
}

@Composable
private fun StorageDocCard(
    modifier: Modifier = Modifier,
    doc: StorageDoc,
    onDelete: () -> Unit = {},
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (doc.tipo == "imagen") {
                AsyncImage(
                    model = doc.url,
                    contentDescription = doc.nombre,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (doc.tipo == "imagen") Color(0xFFE3F2FD) else Color(0xFFFFEBEE)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (doc.tipo == "imagen") "IMG" else "PDF",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 7.sp,
                        color = if (doc.tipo == "imagen") Color(0xFF1565C0) else Color(0xFFE53935),
                    )
                }
                Text(
                    text = doc.nombre,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF1A1A2E),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(doc.url))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f).height(32.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1169FF)),
                ) {
                    Text(
                        text = "Ver",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = Color.White,
                    )
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(32.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE)),
                ) {
                    Text(
                        text = "Eliminar",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = Color(0xFFE53935),
                    )
                }
            }
        }
    }
}

private fun loadFilesFromDriveFolder(context: Context, driveUri: String, documentsList: MutableList<String>) {
    try {
        val treeUri = Uri.parse(driveUri)
        val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri) ?: return
        
        // Listar todos los archivos en la carpeta
        tree.listFiles().forEach { file ->
            if (file.isFile && file.canRead()) {
                val fileName = file.name ?: "archivo"
                if (!documentsList.contains(fileName)) {
                    documentsList.add(fileName)
                }
            }
        }
        
        android.util.Log.d("DatosImportantes", "Se encontraron ${documentsList.size} archivos en Drive")
    } catch (e: Exception) {
        android.util.Log.e("DatosImportantes", "Error al listar archivos: ${e.message}", e)
    }
}

private fun launchGoogleDrivePicker(context: Context, folderPicker: androidx.activity.compose.ManagedActivityResultLauncher<Intent, ActivityResult>) {
    try {
        android.util.Log.d("DatosImportantes", "Abriendo selector de carpetas...")

        val driveInstalled = GoogleDriveHelper.isGoogleDriveInstalled(context)
        val driveProviderAvailable = GoogleDriveHelper.isDriveDocumentsProviderAvailable(context)

        if (!driveInstalled) {
            Toast.makeText(
                context,
                "Google Drive no está instalada. Instálala desde Play Store.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        if (!driveProviderAvailable) {
            Toast.makeText(
                context,
                "Tu teléfono no expone Google Drive en el selector de carpetas.\n\n" +
                    "Abre Google Drive, inicia sesión, y vuelve a intentar.\n" +
                    "Si sigue igual, este modelo/ROM solo permite seleccionar carpetas locales.",
                Toast.LENGTH_LONG,
            ).show()
            // Aun así, abrir el selector por si el OEM lo soporta parcialmente.
        } else {
            Toast.makeText(
                context,
                "Se abrirá un selector. Si aparece 'Almacenamiento interno', toca el título de arriba para cambiar ubicación y elige 'Google Drive'.",
                Toast.LENGTH_LONG,
            ).show()
        }

        // Usar el intent corregido (EXTRA_INITIAL_URI válido) y preferir DocumentsUI en Samsung
        val pickIntent = GoogleDriveHelper.createDriveFolderPickerIntentPreferDocumentsUi(context)

        // Diagnóstico: qué app/actividad está resolviendo el intent en este dispositivo
        val resolved = try {
            pickIntent.resolveActivity(context.packageManager)
        } catch (_: Exception) {
            null
        }
        val resolvedPkg = resolved?.packageName.orEmpty()
        val resolvedCls = resolved?.className.orEmpty()
        android.util.Log.d(
            "DatosImportantes",
            "Picker resolved to package='$resolvedPkg' class='$resolvedCls' driveProviderAvailable=$driveProviderAvailable"
        )
        if (resolvedPkg.isNotBlank()) {
            Toast.makeText(
                context,
                "Selector: $resolvedPkg\nDrive provider: ${if (driveProviderAvailable) "sí" else "no"}",
                Toast.LENGTH_LONG,
            ).show()
        }
        folderPicker.launch(pickIntent)
    } catch (e: Exception) {
        android.util.Log.e("DatosImportantes", "Error launching picker: ${e.message}", e)
        Toast.makeText(context, "Error al abrir selector de carpetas", Toast.LENGTH_LONG).show()
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return it.getString(idx)
        }
    }
    return null
}

private fun copyDocumentToTree(context: Context, sourceUri: Uri, treeUri: Uri, fileName: String): Boolean {
    return try {
        android.util.Log.d("DatosImportantes", "Iniciando copia de: $fileName a $treeUri")
        
        val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri) ?: run {
            android.util.Log.e("DatosImportantes", "fromTreeUri retornó null para: $treeUri")
            return false
        }
        
        if (!tree.exists()) {
            android.util.Log.e("DatosImportantes", "La carpeta no existe: $treeUri")
            return false
        }
        
        if (!tree.isDirectory) {
            android.util.Log.e("DatosImportantes", "El URI no es una carpeta")
            return false
        }
        
        if (!tree.canWrite()) {
            android.util.Log.e("DatosImportantes", "No hay permisos de escritura. Permisos del árbol: ${tree.uri}")
            return false
        }

        val contentResolver = context.contentResolver
        val mime = contentResolver.getType(sourceUri) ?: "application/octet-stream"
        android.util.Log.d("DatosImportantes", "MIME type: $mime")

        // Verificar si el archivo ya existe
        val finalName = if (tree.findFile(fileName) == null) {
            fileName
        } else {
            val dot = fileName.lastIndexOf('.')
            val base = if (dot > 0) fileName.substring(0, dot) else fileName
            val ext = if (dot > 0) fileName.substring(dot) else ""
            val newName = "$base-${System.currentTimeMillis()}$ext"
            android.util.Log.d("DatosImportantes", "Archivo existe, renombrando: $fileName -> $newName")
            newName
        }

        val newFile = tree.createFile(mime, finalName) ?: run {
            android.util.Log.e("DatosImportantes", "createFile retornó null para: $finalName")
            return false
        }
        
        if (!newFile.canWrite()) {
            android.util.Log.e("DatosImportantes", "No se puede escribir al archivo creado: ${newFile.uri}")
            return false
        }
        
        android.util.Log.d("DatosImportantes", "Archivo creado: ${newFile.name} (${newFile.uri})")

        // Abrir streams
        val inputStream = contentResolver.openInputStream(sourceUri) ?: run {
            android.util.Log.e("DatosImportantes", "No se pudo abrir InputStream de: $sourceUri")
            return false
        }
        
        val outputStream = contentResolver.openOutputStream(newFile.uri) ?: run {
            android.util.Log.e("DatosImportantes", "No se pudo abrir OutputStream de: ${newFile.uri}")
            inputStream.close()
            return false
        }

        // Copiar contenido
        var bytesCopied = 0L
        inputStream.use { input ->
            outputStream.use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesCopied += read
                }
            }
        }
        
        android.util.Log.d("DatosImportantes", "Copia completada: $bytesCopied bytes")
        true
    } catch (e: SecurityException) {
        android.util.Log.e("DatosImportantes", "SecurityException: Sin permisos para acceder a Drive. ${e.message}")
        false
    } catch (e: Exception) {
        android.util.Log.e("DatosImportantes", "Error al copiar: ${e.message} (${e.javaClass.simpleName})", e)
        false
    }
}

private fun deleteFileFromDrive(context: Context, driveUri: String, fileName: String) {
    try {
        if (driveUri.isBlank()) return
        
        val treeUri = Uri.parse(driveUri)
        val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri) ?: return
        val fileToDelete = tree.findFile(fileName) ?: return
        
        if (fileToDelete.delete()) {
            android.util.Log.d("DatosImportantes", "Archivo eliminado: $fileName")
        } else {
            android.util.Log.e("DatosImportantes", "No se pudo eliminar: $fileName")
        }
    } catch (e: Exception) {
        android.util.Log.e("DatosImportantes", "Error al eliminar archivo: ${e.message}")
    }
}

private fun isGoogleDriveTreeUri(uri: Uri): Boolean {
    return GoogleDriveHelper.isValidTreeUri(uri)
}

private fun generateQrBitmap(data: String, size: Int = 512): Bitmap? {
    return try {
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(data, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

