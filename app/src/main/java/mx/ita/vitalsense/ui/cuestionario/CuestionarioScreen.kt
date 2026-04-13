package mx.ita.vitalsense.ui.cuestionario

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuestionarioScreen(
    onBack: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser
    val uid = currentUser?.uid ?: ""

    // Initial Split
    val displayName = currentUser?.displayName ?: ""
    val parts = displayName.split(" ")
    val initialNombre = parts.getOrElse(0) { "" }
    val initialApellidos = parts.drop(1).joinToString(" ")
    val initialEmail = currentUser?.email ?: ""

    var nombre by remember { mutableStateOf(initialNombre) }
    var apellidos by remember { mutableStateOf(initialApellidos) }
    var email by remember { mutableStateOf(initialEmail) }
    var nacimiento by remember { mutableStateOf("") }
    var celular by remember { mutableStateOf("") }
    var genero by remember { mutableStateOf("") }
    var frecuencia by remember { mutableStateOf("") }
    var tipoSangre by remember { mutableStateOf("") }
    var useBiometric by remember { mutableStateOf(false) }

    var nombreError by remember { mutableStateOf(false) }
    var apellidosError by remember { mutableStateOf(false) }
    var nacimientoError by remember { mutableStateOf(false) }
    var celularError by remember { mutableStateOf(false) }
    var generoError by remember { mutableStateOf(false) }
    var frecuenciaError by remember { mutableStateOf(false) }
    var tipoSangreError by remember { mutableStateOf(false) }

    val formIsValid = remember(nombre, apellidos, nacimiento, celular, genero, frecuencia, tipoSangre) {
        val birthMillis = parseDateToMillis(nacimiento)
        val birthOk = birthMillis != null && isAtLeastAge(birthMillis, 18)
        nombre.isNotBlank() && apellidos.isNotBlank() && nacimiento.isNotBlank() && birthOk && celular.isNotBlank() && genero.isNotBlank() && frecuencia.isNotBlank() && tipoSangre.isNotBlank()
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    var generoExpanded by remember { mutableStateOf(false) }
    val generos = listOf("Masculino", "Femenino", "Otro")

    var sangreExpanded by remember { mutableStateOf(false) }
    val tiposSangre = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            imageUri = result.uriContent
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(52.dp))

        // ── Header ─────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
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
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Documentos",
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = DashBlue,
            )
        }

        // ── Avatar Icon ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(DashBlue.copy(alpha = 0.1f))
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
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Avatar",
                        tint = DashBlue,
                        modifier = Modifier.size(80.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Title ──────────────────────────────────────────────────────────────
        Text(
            text = "Informacion de tu perfil",
            fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(20.dp))

        // ── Form ───────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CuestionarioField(
                    modifier = Modifier.weight(1f),
                    label = "Nombre",
                    value = nombre,
                    onValueChange = { nombre = it; nombreError = false },
                    isError = nombreError,
                    errorMessage = if (nombreError) "Nombre requerido" else null
                )
                CuestionarioField(
                    modifier = Modifier.weight(1f),
                    label = "Apellidos",
                    value = apellidos,
                    onValueChange = { apellidos = it; apellidosError = false },
                    isError = apellidosError,
                    errorMessage = if (apellidosError) "Apellidos requeridos" else null
                )
            }

            CuestionarioField(
                modifier = Modifier.fillMaxWidth(),
                label = "Email",
                value = email,
                onValueChange = { email = it },
                keyboardType = KeyboardType.Email,
                enabled = false,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Nacimiento with Date Picker
                Box(modifier = Modifier.weight(1f)) {
                    CuestionarioField(
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
                CuestionarioField(
                    modifier = Modifier.weight(1f),
                    label = "Celular",
                    value = celular,
                    onValueChange = { celular = it; celularError = false },
                    keyboardType = KeyboardType.Phone,
                    isError = celularError,
                    errorMessage = if (celularError) "Celular requerido" else null
                )
            }

            // Género con menú desplegable
            Column {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = genero,
                        onValueChange = {},
                        readOnly = true,
                        isError = generoError,
                        label = { Text("Género", fontFamily = Manrope, fontSize = 11.sp, color = Color(0xFFB0B0B0)) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { generoExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DashBlue,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )

                    DropdownMenu(
                        expanded = generoExpanded,
                        onDismissRequest = { generoExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        generos.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    genero = selectionOption
                                    generoError = false
                                    generoExpanded = false
                                }
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CuestionarioField(
                    modifier = Modifier.weight(1f),
                    label = "Frecuencia promedio",
                    value = frecuencia,
                    onValueChange = { frecuencia = it; frecuenciaError = false },
                    keyboardType = KeyboardType.Number,
                    isError = frecuenciaError,
                    errorMessage = if (frecuenciaError) "Frecuencia requerida" else null
                )

                // Tipo de Sangre con menú desplegable
                Column(modifier = Modifier.weight(1f)) {
                    Box {
                        OutlinedTextField(
                            value = tipoSangre,
                            onValueChange = {},
                            readOnly = true,
                            isError = tipoSangreError,
                            label = { Text("Tipo de Sangre", fontFamily = Manrope, fontSize = 11.sp, color = Color(0xFFB0B0B0)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sangreExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DashBlue,
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )

                        DropdownMenu(
                            expanded = sangreExpanded,
                            onDismissRequest = { sangreExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tiposSangre.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        tipoSangre = selectionOption
                                        tipoSangreError = false
                                        sangreExpanded = false
                                    }
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
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Biometric Checkbox ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clickable { useBiometric = !useBiometric },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = useBiometric,
                onCheckedChange = { useBiometric = it },
                colors = CheckboxDefaults.colors(checkedColor = DashBlue)
            )
            Text(
                text = "Verificación de datos biométricos",
                fontFamily = Manrope,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(24.dp))

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

                    // Save to SharedPreferences (local, fast)
                    val profilePrefs = context.getSharedPreferences("vitalsense_profile", Context.MODE_PRIVATE)
                    profilePrefs.edit().apply {
                        putString("nombre_$uid", nombre)
                        putString("apellidos_$uid", apellidos)
                        putString("nacimiento_$uid", nacimiento)
                        putString("celular_$uid", celular)
                        putString("genero_$uid", genero)
                        putString("frecuencia_$uid", frecuencia)
                        putString("tipo_sangre_$uid", tipoSangre)
                        if (!finalAvatar.isNullOrBlank()) putString("avatar_uri_$uid", finalAvatar)
                        putBoolean("cuestionario_completed_$uid", true)
                    }.apply()

                    // Save to Firebase Realtime Database (cloud backup)
                    if (uid.isNotEmpty()) {
                        val profileData = mutableMapOf<String, Any>(
                            "nombre"      to nombre,
                            "apellidos"   to apellidos,
                            "email"       to email,
                            "nacimiento"  to nacimiento,
                            "celular"     to celular,
                            "genero"      to genero,
                            "frecuencia"  to frecuencia,
                            "tipoSangre"  to tipoSangre,
                            "cuestionarioCompleted" to true,
                        )
                        if (!finalAvatar.isNullOrBlank()) profileData["avatarUri"] = finalAvatar
                        FirebaseDatabase.getInstance().getReference("patients/$uid/profile")
                            .updateChildren(profileData)
                            .addOnCompleteListener { task ->
                                if (!task.isSuccessful) {
                                    android.util.Log.e("Cuestionario", "Error saving profile to Firebase", task.exception)
                                    android.widget.Toast.makeText(context, "Error al guardar en la nube: ${task.exception?.message}", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    android.util.Log.d("Cuestionario", "Profile saved to Firebase successfully")
                                }
                            }
                    }

                    if (useBiometric) {
                        val watchPrefs = context.getSharedPreferences("vitalsense_watch_prefs", Context.MODE_PRIVATE)
                        watchPrefs.edit().putBoolean("require_biometric", true).apply()
                    }

                    onNext()
                }
            },
            enabled = formIsValid,

            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(54.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DashBlue),
        ) {
            Text(
                text = "Siguiente",
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color.White,
            )
        }

        Spacer(Modifier.height(32.dp))
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
                            showDatePicker = false
                        } else {
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
private fun CuestionarioField(
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
                disabledContainerColor = Color(0xFFF5F5F5),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                disabledTextColor = Color(0xFF8A8A8A),
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

