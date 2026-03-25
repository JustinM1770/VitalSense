package mx.ita.vitalsense.ui.archivos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.data.emergency.StorageDoc
import mx.ita.vitalsense.ui.components.BottomNav
import mx.ita.vitalsense.ui.components.BottomNavTab
import mx.ita.vitalsense.ui.theme.DashBg
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope

/**
 * Pantalla de gestión de documentos médicos.
 *
 * Los documentos se almacenan en Firebase Storage bajo documents/{uid}/{filename}
 * y sus URLs se guardan en patients/{uid}/profile/storageDocuments.
 *
 * Cumple con:
 *  - NOM-024-SSA3-2012 (registros electrónicos de salud)
 *  - Ley Federal de Protección de Datos Personales
 * El almacenamiento se realiza en Firebase (Google Cloud) bajo control exclusivo
 * de la aplicación, sin intermediarios de terceros.
 */
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

    val storageDocuments = remember { mutableStateListOf<StorageDoc>() }
    var isUploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Cargar documentos desde Firebase DB
    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            FirebaseDatabase.getInstance().getReference("patients/$uid/profile/storageDocuments")
                .get()
                .addOnSuccessListener { snap ->
                    val docs = snap.children.mapNotNull { child ->
                        val nombre = child.child("nombre").getValue(String::class.java) ?: return@mapNotNull null
                        val url    = child.child("url").getValue(String::class.java)    ?: return@mapNotNull null
                        val tipo   = child.child("tipo").getValue(String::class.java)   ?: "pdf"
                        StorageDoc(nombre = nombre, url = url, tipo = tipo)
                    }
                    storageDocuments.clear()
                    storageDocuments.addAll(docs)
                }
        }
    }

    // Selector de archivo → sube a Firebase Storage
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
                // Leer todos los bytes antes de subir — evita que el InputStream se cierre
                // antes de que Firebase Storage termine con putStream (causa de "Object not found")
                val bytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                    ?: run {
                        Toast.makeText(context, "No se pudo leer el archivo", Toast.LENGTH_SHORT).show()
                        isUploading = false
                        return@launch
                    }

                val storageRef = FirebaseStorage.getInstance()
                    .reference.child("documents/$uid/$fileName")

                storageRef.putBytes(bytes).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                val newDoc = StorageDoc(nombre = fileName, url = downloadUrl, tipo = tipo)
                storageDocuments.add(newDoc)
                val docsToSave = storageDocuments.map {
                    mapOf("nombre" to it.nombre, "url" to it.url, "tipo" to it.tipo)
                }
                FirebaseDatabase.getInstance()
                    .getReference("patients/$uid/profile/storageDocuments")
                    .setValue(docsToSave).await()
                Toast.makeText(context, "Documento subido correctamente", Toast.LENGTH_SHORT).show()
            } catch (e: com.google.firebase.storage.StorageException) {
                val msg = when (e.errorCode) {
                    com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND ->
                        "Storage no configurado. Actívalo en Firebase Console → Storage."
                    com.google.firebase.storage.StorageException.ERROR_NOT_AUTHORIZED ->
                        "Sin permisos para subir. Revisa las reglas de Firebase Storage."
                    com.google.firebase.storage.StorageException.ERROR_QUOTA_EXCEEDED ->
                        "Cuota de almacenamiento excedida."
                    else -> "Error al subir: ${e.localizedMessage}"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al subir: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isUploading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DashBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp),
        ) {
            Spacer(Modifier.height(52.dp))

            // — Botón regresar —
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

            // — Avatar —
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

            // — Tarjeta principal —
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color.White)
                    .padding(24.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    Text(
                        text = "Documentos Médicos",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF1A1A2E),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(4.dp))

                    // — Aviso normativo —
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEFF6FF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "Almacenamiento seguro en Firebase Storage (Google Cloud) · " +
                                "NOM-024-SSA3-2012 · Ley Federal de Protección de Datos Personales",
                            fontFamily = Manrope,
                            fontSize = 10.sp,
                            color = Color(0xFF1D4ED8),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // — Botón agregar —
                    Button(
                        onClick = { filePicker.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DashBlue),
                        enabled = !isUploading,
                    ) {
                        Text(
                            text = if (isUploading) "Subiendo..." else "Agregar documento",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White,
                        )
                    }

                    // — Indicador de progreso —
                    if (isUploading) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = DashBlue,
                            )
                            Text(
                                text = "Subiendo documento a la nube...",
                                fontFamily = Manrope,
                                fontSize = 13.sp,
                                color = Color(0xFF4B5563),
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // — Lista de documentos —
                    if (storageDocuments.isEmpty() && !isUploading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No hay documentos subidos.\nAgregar PDFs o imágenes médicas.",
                                fontFamily = Manrope,
                                fontSize = 13.sp,
                                color = Color(0xFF8A8A8A),
                            )
                        }
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

                    Spacer(Modifier.height(26.dp))

                    // — Botones Cancelar / Listo —
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
                            Text(
                                text = "Listo",
                                fontFamily = Manrope,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = Color.White,
                            )
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

// ─── Tarjeta de documento en la nube ─────────────────────────────────────────

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
            // Miniatura para imágenes
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
            // Nombre + badge tipo
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
            // Botones Ver / Eliminar
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

// ─── Helpers ──────────────────────────────────────────────────────────────────

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
