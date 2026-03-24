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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    var genero by remember { mutableStateOf(profilePrefs.getString("genero_$uid", null) ?: "Masculino") }
    var frecuencia by remember { mutableStateOf(profilePrefs.getString("frecuencia_$uid", null) ?: "72") }
    var tipoSangre by remember { mutableStateOf(profilePrefs.getString("tipo_sangre_$uid", null) ?: "") }

    var nombreError by remember { mutableStateOf(false) }
    var apellidosError by remember { mutableStateOf(false) }
    var nacimientoError by remember { mutableStateOf(false) }
    var celularError by remember { mutableStateOf(false) }
    var generoError by remember { mutableStateOf(false) }
    var frecuenciaError by remember { mutableStateOf(false) }
    var tipoSangreError by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    var generoExpanded by remember { mutableStateOf(false) }
    val generos = listOf("Masculino", "Femenino", "Otro")

    val formIsValid = remember(nombre, apellidos, nacimiento, celular, genero, frecuencia, tipoSangre) {
        val nacimientoVal = parseDateToMillis(nacimiento)
        val birthOk = nacimientoVal != null && isAtLeastAge(nacimientoVal, 18)
        nombre.isNotBlank() && apellidos.isNotBlank() && nacimiento.isNotBlank() && birthOk && celular.isNotBlank() && genero.isNotBlank() && frecuencia.isNotBlank() && tipoSangre.isNotBlank()
    }

    var sangreExpanded by remember { mutableStateOf(false) }
    val tiposSangre = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    val avatarUriString = profilePrefs.getString("avatar_uri_$uid", null)
    var imageUri by remember { mutableStateOf<Uri?>(if (avatarUriString != null) Uri.parse(avatarUriString) else null) }
    val scope = rememberCoroutineScope()

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            imageUri = result.uriContent
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
                        ProfileField(
                            modifier = Modifier.weight(1f),
                            label = "Nombre",
                            value = nombre,
                            onValueChange = { nombre = it; nombreError = false },
                            isError = nombreError,
                            errorMessage = if (nombreError) "Nombre requerido" else null
                        )
                        ProfileField(
                            modifier = Modifier.weight(1f),
                            label = "Apellidos",
                            value = apellidos,
                            onValueChange = { apellidos = it; apellidosError = false },
                            isError = apellidosError,
                            errorMessage = if (apellidosError) "Apellidos requeridos" else null
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    ProfileField(modifier = Modifier.fillMaxWidth(), label = "Email", value = email, onValueChange = { email = it }, keyboardType = KeyboardType.Email, enabled = false)
                    Spacer(Modifier.height(14.dp))
                    ProfileField(modifier = Modifier.fillMaxWidth(), label = "Contraseña", value = password, onValueChange = { password = it }, keyboardType = KeyboardType.Password, isPassword = true)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileField(
                                modifier = Modifier.fillMaxWidth(),
                                label = "Nacimiento",
                                value = nacimiento,
                                onValueChange = {},
                                enabled = false,
                                isError = nacimientoError,
                                errorMessage = if (nacimientoError) "Nacimiento invalido o menor de 18" else null
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showDatePicker = true }
                            )
                        }
                        ProfileField(
                            modifier = Modifier.weight(1f),
                            label = "Celular",
                            value = celular,
                            onValueChange = { celular = it; celularError = false },
                            keyboardType = KeyboardType.Phone,
                            isError = celularError,
                            errorMessage = if (celularError) "Celular requerido" else null
                        )
                    }
                    Spacer(Modifier.height(14.dp))

                    Column {
                        ExposedDropdownMenuBox(
                            expanded = generoExpanded,
                            onExpandedChange = { generoExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = genero,
                                onValueChange = {},
                                readOnly = true,
                                isError = generoError,
                                label = { Text("Género", fontFamily = Manrope, fontSize = 11.sp, color = Color(0xFFB0B0B0)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = generoExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DashBlue,
                                    unfocusedBorderColor = Color(0xFFE0E0E0),
                                    focusedTextColor = Color(0xFF1A1A2E),
                                    unfocusedTextColor = Color(0xFF1A1A2E),
                                ),
                            )

                            ExposedDropdownMenu(
                                expanded = generoExpanded,
                                onDismissRequest = { generoExpanded = false },
                            ) {
                                generos.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption) },
                                        onClick = {
                                            genero = selectionOption
                                            generoError = false
                                            generoExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        if (generoError) {
                            Text(
                                text = "Género requerido",
                                fontFamily = Manrope,
                                fontSize = 10.sp,
                                color = Color(0xFFE53935),
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileField(
                            modifier = Modifier.weight(1f),
                            label = "Frecuencia promedio",
                            value = frecuencia,
                            onValueChange = { frecuencia = it.filter { ch -> ch.isDigit() }; frecuenciaError = false },
                            keyboardType = KeyboardType.Number,
                            isError = frecuenciaError,
                            errorMessage = if (frecuenciaError) "Frecuencia requerida" else null
                        )
                        ExposedDropdownMenuBox(
                            expanded = sangreExpanded,
                            onExpandedChange = { sangreExpanded = it },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = tipoSangre,
                                onValueChange = {},
                                readOnly = true,
                                isError = tipoSangreError,
                                label = { Text("Tipo de Sangre", fontFamily = Manrope, fontSize = 11.sp, color = Color(0xFFB0B0B0)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sangreExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DashBlue,
                                    unfocusedBorderColor = Color(0xFFE0E0E0),
                                    focusedTextColor = Color(0xFF1A1A2E),
                                    unfocusedTextColor = Color(0xFF1A1A2E),
                                ),
                            )

                            ExposedDropdownMenu(
                                expanded = sangreExpanded,
                                onDismissRequest = { sangreExpanded = false },
                            ) {
                                tiposSangre.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption) },
                                        onClick = {
                                            tipoSangre = selectionOption
                                            tipoSangreError = false
                                            sangreExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        if (tipoSangreError) {
                            Text(
                                text = "Tipo de sangre requerido",
                                fontFamily = Manrope,
                                fontSize = 10.sp,
                                color = Color(0xFFE53935),
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(30.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                nombreError = nombre.isBlank()
                                apellidosError = apellidos.isBlank()
                                celularError = celular.isBlank()
                                generoError = genero.isBlank()
                                frecuenciaError = frecuencia.isBlank()
                                tipoSangreError = tipoSangre.isBlank()

                                val birthMillis = parseDateToMillis(nacimiento)
                                val birthOk = birthMillis != null && isAtLeastAge(birthMillis, 18)
                                nacimientoError = !birthOk

                                if (nombreError || apellidosError || nacimientoError || celularError || generoError || frecuenciaError || tipoSangreError) {
                                    return@Button
                                }

                                scope.launch {
                                    var finalAvatar = imageUri?.toString()

                                    if (uid.isNotEmpty() && imageUri != null && (imageUri?.scheme == "content" || imageUri?.scheme == "file")) {
                                        finalAvatar = uploadAvatarToFirebaseStorage(uid = uid, uri = imageUri!!)
                                            ?: imageUri?.toString()
                                    }

                                    profilePrefs.edit().apply {
                                        putString("nombre_$uid", nombre)
                                        putString("apellidos_$uid", apellidos)
                                        putString("nacimiento_$uid", nacimiento)
                                        putString("celular_$uid", celular)
                                        putString("genero_$uid", genero)
                                        putString("frecuencia_$uid", frecuencia)
                                        putString("tipo_sangre_$uid", tipoSangre)
                                        if (!finalAvatar.isNullOrBlank()) putString("avatar_uri_$uid", finalAvatar)
                                    }.apply()

                                    if (uid.isNotEmpty()) {
                                        val profileData = mutableMapOf<String, Any>(
                                            "nombre" to nombre,
                                            "apellidos" to apellidos,
                                            "nacimiento" to nacimiento,
                                            "celular" to celular,
                                            "genero" to genero,
                                            "frecuencia" to frecuencia,
                                            "tipoSangre" to tipoSangre,
                                        )
                                        if (!finalAvatar.isNullOrBlank()) profileData["avatarUri"] = finalAvatar

                                        FirebaseDatabase.getInstance().getReference("patients/$uid/profile")
                                            .updateChildren(profileData)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    if (!finalAvatar.isNullOrBlank()) {
                                                        imageUri = Uri.parse(finalAvatar)
                                                    }
                                                    Toast.makeText(context, "Datos guardados y respaldados", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Error guardando: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                    } else {
                                        Toast.makeText(context, "Datos guardados localmente", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = formIsValid,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DashBlue),
                        ) {
                            Text("Guardar", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                        }
                        Button(
                            onClick = onDatosImportantes,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DashBlue),
                        ) {
                            Text(
                                "Datos importantes",
                                fontFamily = Manrope,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
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

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            if (isAtLeastAge(millis, 18)) {
                                val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                nacimiento = formatter.format(Date(millis))
                                nacimientoError = false
                                showDatePicker = false
                            } else {
                                nacimientoError = true
                                Toast.makeText(context, "Debes tener al menos 18 años", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) {
                        Text("OK", color = DashBlue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancelar", color = DashBlue)
                    }
                }
            ) {
                DatePicker(state = datePickerState)
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

private suspend fun uploadAvatarToFirebaseStorage(uid: String, uri: Uri): String? {
    return try {
        val ref = FirebaseStorage.getInstance()
            .reference
            .child("avatars/$uid/profile.jpg")
        ref.putFile(uri).await()
        ref.downloadUrl.await().toString()
    } catch (_: Exception) {
        null
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
    isError: Boolean = false,
    errorMessage: String? = null,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label, fontFamily = Manrope, fontSize = 11.sp, color = Color(0xFFB0B0B0)) },
            enabled = enabled,
            isError = isError,
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
        if (isError && !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                fontFamily = Manrope,
                fontSize = 10.sp,
                color = Color(0xFFE53935),
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    }
}

private fun parseDateToMillis(dateStr: String): Long? {
    return try {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        formatter.parse(dateStr)?.time
    } catch (_: Exception) {
        null
    }
}

private fun isAtLeastAge(dateMillis: Long, yearThreshold: Int = 18): Boolean {
    val birth = Calendar.getInstance().apply { timeInMillis = dateMillis }
    val now = Calendar.getInstance()
    var age = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
    if (now.get(Calendar.MONTH) < birth.get(Calendar.MONTH) ||
        (now.get(Calendar.MONTH) == birth.get(Calendar.MONTH) && now.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH))
    ) {
        age -= 1
    }
    return age >= yearThreshold
}
