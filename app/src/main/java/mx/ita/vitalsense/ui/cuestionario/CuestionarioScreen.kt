package mx.ita.vitalsense.ui.cuestionario

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope

@Composable
fun CuestionarioScreen(
    onBack: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    var nombre by remember { mutableStateOf("") }
    var apellidos by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nacimiento by remember { mutableStateOf("") }
    var celular by remember { mutableStateOf("") }
    var genero by remember { mutableStateOf("") }
    var frecuencia by remember { mutableStateOf("") }
    var tipoSangre by remember { mutableStateOf("") }

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
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = DashBlue, fontWeight = FontWeight.Bold)) { append("Do") }
                    withStyle(SpanStyle(color = DashBlue)) { append("cume") }
                    withStyle(SpanStyle(color = DashBlue, fontWeight = FontWeight.Bold)) { append("ntes") }
                },
                fontFamily = Manrope,
                fontSize = 22.sp,
            )
        }

        // ── Face ID icon ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center,
            ) {
                // Face ID lines
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FaceIdCorner()
                        Spacer(Modifier.width(20.dp))
                        FaceIdCorner(flipH = true)
                    }
                    Spacer(Modifier.height(10.dp))
                    // Eyes
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(Modifier.size(width = 8.dp, height = 14.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF00FF00)))
                        Box(Modifier.size(width = 8.dp, height = 14.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF00FF00)))
                    }
                    Spacer(Modifier.height(8.dp))
                    // Nose
                    Box(Modifier.size(width = 4.dp, height = 8.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF00FF00)))
                    Spacer(Modifier.height(6.dp))
                    // Smile
                    Box(Modifier.size(width = 24.dp, height = 10.dp).clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(Color(0xFF00FF00)))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FaceIdCorner(flipV = true)
                        Spacer(Modifier.width(20.dp))
                        FaceIdCorner(flipH = true, flipV = true)
                    }
                }
            }
            // Blue circle behind icon
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(DashBlue.copy(alpha = 0.3f)),
            )
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

            CuestionarioField(
                modifier = Modifier.fillMaxWidth(),
                label = "Contraseña",
                value = password,
                onValueChange = { password = it },
                keyboardType = KeyboardType.Password,
                isPassword = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CuestionarioField(modifier = Modifier.weight(1f), label = "Nacimiento", value = nacimiento, onValueChange = { nacimiento = it })
                CuestionarioField(modifier = Modifier.weight(1f), label = "Celular", value = celular, onValueChange = { celular = it }, keyboardType = KeyboardType.Phone)
            }

            CuestionarioField(modifier = Modifier.fillMaxWidth(), label = "Genero", value = genero, onValueChange = { genero = it })

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CuestionarioField(modifier = Modifier.weight(1f), label = "Frecuencia promedio", value = frecuencia, onValueChange = { frecuencia = it }, keyboardType = KeyboardType.Number)
                CuestionarioField(modifier = Modifier.weight(1f), label = "Tipo de Sangre", value = tipoSangre, onValueChange = { tipoSangre = it })
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Da Click En La Casilla Si Aceptas Verificación\nPor Face ID",
            fontFamily = Manrope,
            fontSize = 13.sp,
            color = DashBlue,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onNext,
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
}

@Composable
private fun FaceIdCorner(flipH: Boolean = false, flipV: Boolean = false) {
    val w = 14.dp; val h = 14.dp; val stroke = 3.dp
    Box(modifier = Modifier.size(w, h)) {
        val corner = Color(0xFF00FF00)
        Box(
            modifier = Modifier
                .size(stroke, h)
                .background(corner)
                .align(if (flipH) Alignment.TopEnd else Alignment.TopStart),
        )
        Box(
            modifier = Modifier
                .size(w, stroke)
                .background(corner)
                .align(if (flipV) Alignment.BottomStart else Alignment.TopStart),
        )
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
        ),
    )
}
