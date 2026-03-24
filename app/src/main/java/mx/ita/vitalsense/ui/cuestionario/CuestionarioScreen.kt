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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult

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

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    var generoExpanded by remember { mutableStateOf(false) }
    val generos = listOf("Masculino", "Femenino", "Otro")

    var sangreExpanded by remember { mutableStateOf(false) }
    val tiposSangre = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            imageUri = result.uriContent
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
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
            color = Color(0xFF1A1A2E),
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(20.dp))

        // ── Form ───────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CuestionarioField(modifier = Modifier.weight(1f), label = "Nombre", value = nombre, onValueChange = { nombre = it })
                CuestionarioField(modifier = Modifier.weight(1f), label = "Apellidos", value = apellidos, onValueChange = { apellidos = it })
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
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true }
                    )
                }
                CuestionarioField(modifier = Modifier.weight(1f), label = "Celular", value = celular, onValueChange = { celular = it }, keyboardType = KeyboardType.Phone)
            }

            // Genero with Dropdown
            ExposedDropdownMenuBox(
                expanded = generoExpanded,
                onExpandedChange = { generoExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = genero,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Género", fontFamily = Manrope, fontSize = 11.sp, color = Color(0xFFB0B0B0)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
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
                    onDismissRequest = { generoExpanded = false }
                ) {
                    generos.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                genero = selectionOption
                                generoExpanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CuestionarioField(modifier = Modifier.weight(1f), label = "Frecuencia promedio", value = frecuencia, onValueChange = { frecuencia = it }, keyboardType = KeyboardType.Number)
                
                // Tipo de Sangre with Dropdown
                ExposedDropdownMenuBox(
                    expanded = sangreExpanded,
                    onExpandedChange = { sangreExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = tipoSangre,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de Sangre", fontFamily = Manrope, fontSize = 11.sp, color = Color(0xFFB0B0B0)) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
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
                        onDismissRequest = { sangreExpanded = false }
                    ) {
                        tiposSangre.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    tipoSangre = selectionOption
                                    sangreExpanded = false
                                }
                            )
                        }
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
                color = Color(0xFF1A1A2E),
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
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
                    if (imageUri != null) putString("avatar_uri_$uid", imageUri.toString())
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
                    if (imageUri != null) profileData["avatarUri"] = imageUri.toString()
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
            },
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
                    datePickerState.selectedDateMillis?.let {
                        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        nacimiento = formatter.format(Date(it))
                    }
                    showDatePicker = false
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

@Composable
private fun CuestionarioField(
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
            disabledContainerColor = Color(0xFFF5F5F5),
            focusedTextColor = Color(0xFF1A1A2E),
            unfocusedTextColor = Color(0xFF1A1A2E),
            disabledTextColor = Color(0xFF8A8A8A),
        ),
    )
}
