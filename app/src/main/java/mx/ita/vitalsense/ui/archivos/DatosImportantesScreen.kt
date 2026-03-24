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
    val documents = remember { mutableStateListOf<String>() }

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
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        val uri = result.data?.data
        if (uri == null) return@rememberLauncherForActivityResult

        if (!isGoogleDriveTreeUri(uri)) {
            Toast.makeText(
                context,
                "Selecciona una carpeta de Google Drive (no almacenamiento local).",
                Toast.LENGTH_LONG,
            ).show()
            return@rememberLauncherForActivityResult
        }

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }

        driveTreeUri = uri.toString()
        driveFolderUrl = uri.toString()
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
        Toast.makeText(context, "Carpeta de Drive seleccionada correctamente", Toast.LENGTH_SHORT).show()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { fileUri ->
        if (fileUri == null) return@rememberLauncherForActivityResult

        if (driveTreeUri.isBlank()) {
            Toast.makeText(context, "Selecciona primero una carpeta", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        if (!isGoogleDriveTreeUri(Uri.parse(driveTreeUri))) {
            Toast.makeText(
                context,
                "La carpeta actual no es de Google Drive. Vuelve a seleccionar una carpeta de Drive.",
                Toast.LENGTH_LONG,
            ).show()
            return@rememberLauncherForActivityResult
        }

        val targetName = queryDisplayName(context, fileUri) ?: "documento_${System.currentTimeMillis()}.pdf"
        try {
            // Intentar copiar a DocumentFile primero
            val success = copyDocumentToTree(context, fileUri, Uri.parse(driveTreeUri), targetName)
            
            if (success) {
                if (!documents.contains(targetName)) documents.add(targetName)
                if (uid.isNotEmpty()) {
                    profilePrefs.edit().putStringSet("documents_$uid", documents.toSet()).apply()
                    FirebaseDatabase.getInstance().getReference("patients/$uid/profile/documents")
                        .setValue(documents)
                }
                Toast.makeText(context, "Documento subido a la carpeta", Toast.LENGTH_SHORT).show()
            } else {
                // Si falla con DocumentFile, intentar con intent directo
                Toast.makeText(context, "Integrando documento...", Toast.LENGTH_SHORT).show()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(driveTreeUri)).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                try {
                    context.startActivity(intent)
                    Toast.makeText(context, "Abre la carpeta en Drive y copia el archivo manualmente", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al subir: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    val qrBitmap = remember(driveFolderUrl) {
        if (driveFolderUrl.isNotBlank()) generateQrBitmap(driveFolderUrl) else null
    }
    val isDriveFolderSelected = remember(driveTreeUri) {
        driveTreeUri.isNotBlank() && isGoogleDriveTreeUri(Uri.parse(driveTreeUri))
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
                                val initialUri = when {
                                    isDriveFolderSelected -> Uri.parse(driveTreeUri)
                                    else -> Uri.parse("content://com.google.android.apps.docs.storage/document/root")
                                }
                                val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                    addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                                    putExtra("android.content.extra.SHOW_ADVANCED", true)
                                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                                }
                                folderPicker.launch(pickIntent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DashBlue),
                        ) {
                            Text("Seleccionar carpeta de Drive", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (driveTreeUri.isBlank()) {
                                Toast.makeText(context, "Primero selecciona una carpeta", Toast.LENGTH_LONG).show()
                            } else {
                                try {
                                    val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(driveTreeUri)).apply {
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                    }
                                    context.startActivity(openIntent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "No se pudo abrir la carpeta", Toast.LENGTH_LONG).show()
                                }
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
                                            deleteFileFromDrive(context, driveTreeUri, filename)
                                            Toast.makeText(context, "Documento eliminado", Toast.LENGTH_SHORT).show()
                                        }
                                    }
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
    val authority = uri.authority.orEmpty().lowercase()
    return authority.contains("com.google.android.apps.docs")
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

