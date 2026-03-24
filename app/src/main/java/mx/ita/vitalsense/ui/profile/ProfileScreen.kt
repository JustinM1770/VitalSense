package mx.ita.vitalsense.ui.profile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import mx.ita.vitalsense.ui.components.BottomNav
import mx.ita.vitalsense.ui.components.BottomNavTab
import mx.ita.vitalsense.ui.theme.DashBg
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope
import coil.compose.AsyncImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileScreen(
    onDeviceClick: () -> Unit = {},
    onBack: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onDatosImportantes: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onHealthClick: () -> Unit = {},
    onNotifClick: () -> Unit = {},
    vm: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefsShared = remember { context.getSharedPreferences("vitalsense_watch_prefs", Context.MODE_PRIVATE) }
    val isWatchPaired = remember { mutableStateOf(prefsShared.getBoolean("code_paired", false)) }
    val pairedDeviceName = remember { mutableStateOf(prefsShared.getString("paired_device_name", "Wearable") ?: "Wearable") }

    val currentUser = FirebaseAuth.getInstance().currentUser
    val uid = currentUser?.uid ?: ""
    val profilePrefs = remember { context.getSharedPreferences("vitalsense_profile", Context.MODE_PRIVATE) }

    val displayName = currentUser?.displayName ?: ""
    val parts = displayName.split(" ")
    var nombre by remember { mutableStateOf(profilePrefs.getString("nombre_$uid", null) ?: parts.getOrElse(0) { "" }) }
    var apellidos by remember { mutableStateOf(profilePrefs.getString("apellidos_$uid", null) ?: parts.drop(1).joinToString(" ")) }
    var email by remember { mutableStateOf(currentUser?.email ?: "") }
    var password by remember { mutableStateOf("•••••") }
    var nacimiento by remember { mutableStateOf(profilePrefs.getString("nacimiento_$uid", null) ?: "**/**/2000") }
    var celular by remember { mutableStateOf(profilePrefs.getString("celular_$uid", null) ?: "") }
    var genero by remember { mutableStateOf(profilePrefs.getString("genero_$uid", null) ?: "Hombre") }
    var frecuencia by remember { mutableStateOf(profilePrefs.getString("frecuencia_$uid", null) ?: "72") }
    var tipoSangre by remember { mutableStateOf(profilePrefs.getString("tipo_sangre_$uid", null) ?: "") }

    val avatarUriString = profilePrefs.getString("avatar_uri_$uid", null)
    var imageUri by remember { mutableStateOf<Uri?>(if (avatarUriString != null) Uri.parse(avatarUriString) else null) }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            imageUri = result.uriContent
            if (result.uriContent != null) {
                profilePrefs.edit().putString("avatar_uri_$uid", result.uriContent.toString()).apply()
                // Also update avatar in Firebase
                if (uid.isNotEmpty()) {
                    FirebaseDatabase.getInstance().getReference("patients/$uid/profile")
                        .updateChildren(mapOf("avatarUri" to result.uriContent.toString()))
                }
            }
        }
    }

    // Restore from Firebase if local data is missing (e.g. fresh install after uninstall)
    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        val hasLocalData = profilePrefs.contains("nombre_$uid")
        if (!hasLocalData) {
            try {
                val snapshot = FirebaseDatabase.getInstance()
                    .getReference("patients/$uid/profile").get().await()
                if (snapshot.exists()) {
                    profilePrefs.edit().apply {
                        snapshot.child("nombre").getValue(String::class.java)?.let     { putString("nombre_$uid",      it); nombre      = it }
                        snapshot.child("apellidos").getValue(String::class.java)?.let  { putString("apellidos_$uid",   it); apellidos   = it }
                        snapshot.child("nacimiento").getValue(String::class.java)?.let { putString("nacimiento_$uid",  it); nacimiento  = it }
                        snapshot.child("celular").getValue(String::class.java)?.let    { putString("celular_$uid",     it); celular     = it }
                        snapshot.child("genero").getValue(String::class.java)?.let     { putString("genero_$uid",      it); genero      = it }
                        snapshot.child("frecuencia").getValue(String::class.java)?.let { putString("frecuencia_$uid",  it); frecuencia  = it }
                        snapshot.child("tipoSangre").getValue(String::class.java)?.let { putString("tipo_sangre_$uid", it); tipoSangre  = it }
                        snapshot.child("avatarUri").getValue(String::class.java)?.let  { s ->
                            putString("avatar_uri_$uid", s)
                            imageUri = Uri.parse(s)
                        }
                        putBoolean("cuestionario_completed_$uid", true)
                    }.apply()
                }

                // Also attempt to restore the watch pairing status
                val watchSnapshot = FirebaseDatabase.getInstance()
                    .getReference("patients/$uid/watch").get().await()
                if (watchSnapshot.exists()) {
                    val isPaired = watchSnapshot.child("paired").getValue(Boolean::class.java) ?: false
                    if (isPaired) {
                        val pairedCode = watchSnapshot.child("code").getValue(String::class.java) ?: ""
                        val deviceName = watchSnapshot.child("deviceName").getValue(String::class.java) ?: "Wearable"
                        
                        val watchPrefs = context.getSharedPreferences("vitalsense_watch_prefs", Context.MODE_PRIVATE)
                        watchPrefs.edit()
                            .putBoolean("code_paired", true)
                            .putString("paired_code", pairedCode)
                            .putString("paired_device_name", deviceName)
                            .apply()
                        
                        // Because ProfileScreen uses profilePrefs locally, no need to update its own UI for watch here 
                    }
                }

            } catch (_: Exception) { /* No internet or data doesn't exist — keep current state */ }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DashBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp),
        ) {
            Spacer(Modifier.height(52.dp))

            // Back button
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

            // Avatar
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(DashBlue)
                            .clickable {
                                cropLauncher.launch(
                                    CropImageContractOptions(
                                        uri = null,
                                        cropImageOptions = CropImageOptions(
                                            imageSourceIncludeGallery = true,
                                            imageSourceIncludeCamera = true,
                                            guidelines = CropImageView.Guidelines.ON,
                                            aspectRatioX = 1,
                                            aspectRatioY = 1,
                                            fixAspectRatio = true,
                                        )
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (imageUri != null) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = "Avatar",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val initials = buildString {
                                nombre.firstOrNull()?.let { append(it.uppercaseChar()) }
                                apellidos.firstOrNull()?.let { append(it.uppercaseChar()) }
                            }.ifEmpty { "VS" }
                            Text(
                                text = initials,
                                fontFamily = Manrope,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                color = Color.White,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Editar foto",
                            tint = DashBlue,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // White card with form
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color.White)
                    .padding(24.dp),
            ) {
                Column {
                    Text(
                        text = "Editar datos personales",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF1A1A2E),
                    )
                    Spacer(Modifier.height(24.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileField(modifier = Modifier.weight(1f), label = "Nombre", value = nombre, onValueChange = { nombre = it })
                        ProfileField(modifier = Modifier.weight(1f), label = "Apellidos", value = apellidos, onValueChange = { apellidos = it })
                    }
                    Spacer(Modifier.height(14.dp))
                    ProfileField(modifier = Modifier.fillMaxWidth(), label = "Email", value = email, onValueChange = { email = it }, keyboardType = KeyboardType.Email, enabled = false)
                    Spacer(Modifier.height(14.dp))
                    ProfileField(modifier = Modifier.fillMaxWidth(), label = "Contraseña", value = password, onValueChange = { password = it }, keyboardType = KeyboardType.Password, isPassword = true)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileField(modifier = Modifier.weight(1f), label = "Nacimiento", value = nacimiento, onValueChange = { nacimiento = it })
                        ProfileField(modifier = Modifier.weight(1f), label = "Celular", value = celular, onValueChange = { celular = it }, keyboardType = KeyboardType.Phone)
                    }
                    Spacer(Modifier.height(14.dp))
                    ProfileField(modifier = Modifier.fillMaxWidth(), label = "Genero", value = genero, onValueChange = { genero = it })
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileField(modifier = Modifier.weight(1f), label = "Frecuencia promedio", value = "❤️ $frecuencia", onValueChange = { frecuencia = it.removePrefix("❤️ ") }, keyboardType = KeyboardType.Number)
                        ProfileField(modifier = Modifier.weight(1f), label = "Tipo de Sangre", value = tipoSangre, onValueChange = { tipoSangre = it })
                    }

                    Spacer(Modifier.height(30.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                profilePrefs.edit().apply {
                                    putString("nombre_$uid", nombre)
                                    putString("apellidos_$uid", apellidos)
                                    putString("nacimiento_$uid", nacimiento)
                                    putString("celular_$uid", celular)
                                    putString("genero_$uid", genero)
                                    putString("frecuencia_$uid", frecuencia)
                                    putString("tipo_sangre_$uid", tipoSangre)
                                }.apply()
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DashBlue),
                        ) {
                            Text("Guardar", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                        }
                        Button(
                            onClick = onDatosImportantes,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DashBlue),
                        ) {
                            Text("Datos Importantes", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Cerrar sesión",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color(0xFFE53935),
                        modifier = Modifier.clickable {
                            FirebaseAuth.getInstance().signOut()
                            onSignOut()
                        }.padding(vertical = 8.dp),
                    )
                }
            }
        }

        BottomNav(
            selected = BottomNavTab.PROFILE,
            onSelect = { tab ->
                when (tab) {
                    BottomNavTab.HOME   -> onHomeClick()
                    BottomNavTab.HEALTH -> onHealthClick()
                    BottomNavTab.CHAT   -> onNotifClick()
                    else -> {}
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ProfileField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, fontFamily = Manrope, fontSize = 11.sp, color = Color(0xFFB0B0B0)) },
        enabled = enabled,
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DashBlue,
            unfocusedBorderColor = Color(0xFFE0E0E0),
            disabledBorderColor = Color(0xFFE0E0E0),
            focusedTextColor = Color(0xFF1A1A2E),
            unfocusedTextColor = Color(0xFF1A1A2E),
            disabledTextColor = Color(0xFF8A8A8A),
            disabledContainerColor = Color(0xFFF5F5F5),
        ),
    )
}
