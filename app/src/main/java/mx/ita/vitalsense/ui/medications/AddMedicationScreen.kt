package mx.ita.vitalsense.ui.medications

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import mx.ita.vitalsense.data.model.Medication
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val frequencyOptions = listOf(
    "Cada 4 horas",
    "Cada 6 horas",
    "Cada 8 horas",
    "Cada 12 horas",
    "Cada 24 horas",
    "Personalizado",
)

private val durationOptions = listOf(
    "2 dias",
    "7 dias",
    "2 semanas",
    "1 mes",
    "Indeterminado",
)

@Composable
fun AddMedicationScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    var nombre by remember { mutableStateOf("") }
    var frecuencia by remember { mutableStateOf("") }
    var frecuenciaCustom by remember { mutableStateOf("") }
    var duracion by remember { mutableStateOf("") }
    var recordatorioHora by remember { mutableStateOf("") }

    var freqExpanded by remember { mutableStateOf(false) }
    var durExpanded by remember { mutableStateOf(false) }

    val frecuenciaFinal = if (frecuencia == "Personalizado") frecuenciaCustom.trim() else frecuencia
    val horaFinal = recordatorioHora.trim()
    val horaError = !horaFinal.matches(Regex("^([01]\\d|2[0-3]):([0-5]\\d)$"))

    val nombreError = nombre.trim().isBlank()
    val frecuenciaError = frecuenciaFinal.isBlank()
    val duracionError = duracion.isBlank()
    val formValid = !nombreError && !frecuenciaError && !duracionError && !horaError && uid.isNotBlank()

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(DashBlue.copy(alpha = 0.12f), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Regresar",
                        tint = DashBlue,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Agregar medicamento",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = DashBlue,
                )
            }

            Spacer(Modifier.height(24.dp))

            Text("Nombre del medicamento", fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                isError = nombreError,
                placeholder = { Text("Ej. Metformina", color = Color(0xFF9CA3AF)) },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = DashBlue,
                    unfocusedIndicatorColor = Color(0xFFE5E7EB),
                ),
            )
            if (nombreError) {
                Text("El nombre es obligatorio", color = Color(0xFFD32F2F), fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))

            Text("Cada cuanto tomar", fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Box {
                OutlinedTextField(
                    value = frecuencia,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = frecuenciaError,
                    placeholder = { Text("Selecciona una frecuencia", color = Color(0xFF9CA3AF)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = DashBlue,
                        unfocusedIndicatorColor = Color(0xFFE5E7EB),
                    ),
                )
                // Overlay transparente que captura el toque sin que el TextField lo bloquee
                Box(modifier = Modifier.matchParentSize().clickable { freqExpanded = true })
                DropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                    frequencyOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                frecuencia = option
                                freqExpanded = false
                            },
                        )
                    }
                }
            }

            if (frecuencia == "Personalizado") {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = frecuenciaCustom,
                    onValueChange = { frecuenciaCustom = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = frecuenciaError,
                    placeholder = { Text("Ej. Cada 10 horas", color = Color(0xFF9CA3AF)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = DashBlue,
                        unfocusedIndicatorColor = Color(0xFFE5E7EB),
                    ),
                )
            }

            if (frecuenciaError) {
                Text("La frecuencia es obligatoria", color = Color(0xFFD32F2F), fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))

            Text("Duracion del tratamiento", fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Box {
                OutlinedTextField(
                    value = duracion,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = duracionError,
                    placeholder = { Text("Selecciona duracion", color = Color(0xFF9CA3AF)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = DashBlue,
                        unfocusedIndicatorColor = Color(0xFFE5E7EB),
                    ),
                )
                // Overlay transparente que captura el toque sin que el TextField lo bloquee
                Box(modifier = Modifier.matchParentSize().clickable { durExpanded = true })
                DropdownMenu(expanded = durExpanded, onDismissRequest = { durExpanded = false }) {
                    durationOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                duracion = option
                                durExpanded = false
                            },
                        )
                    }
                }
            }
            if (duracionError) {
                Text("La duracion es obligatoria", color = Color(0xFFD32F2F), fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))

            Text("Hora del primer recordatorio", fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = recordatorioHora,
                onValueChange = { recordatorioHora = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = horaError,
                placeholder = { Text("Ej. 08:00", color = Color(0xFF9CA3AF)) },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = DashBlue,
                    unfocusedIndicatorColor = Color(0xFFE5E7EB),
                ),
            )
            if (horaError) {
                Text("Usa formato 24 h HH:mm", color = Color(0xFFD32F2F), fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Extra: este medicamento se mostrara en dashboard y perfil de emergencia.",
                color = Color(0xFF6B7280),
                fontFamily = Manrope,
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (!formValid) return@Button
                    val id = FirebaseDatabase.getInstance().getReference("medications/$uid").push().key
                        ?: System.currentTimeMillis().toString()

                    val med = Medication(
                        id = id,
                        nombre = nombre.trim(),
                        cadaCuanto = frecuenciaFinal,
                        duracion = duracion,
                        dosis = frecuenciaFinal,
                        horario = duracion,
                        recordatorioHora = horaFinal,
                        reminderEnabled = true,
                        nextReminderAt = resolveFirstReminderAt(horaFinal),
                        activo = true,
                        createdAt = System.currentTimeMillis(),
                    )

                    FirebaseDatabase.getInstance()
                        .getReference("medications/$uid/$id")
                        .setValue(med)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Medicamento guardado", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "No se pudo guardar el medicamento", Toast.LENGTH_LONG).show()
                        }
                },
                enabled = formValid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DashBlue,
                    disabledContainerColor = DashBlue.copy(alpha = 0.35f),
                ),
            ) {
                Text("Guardar medicamento", color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun resolveFirstReminderAt(hhmm: String): Long {
    val parts = hhmm.split(":")
    if (parts.size != 2) return 0L
    val hour = parts[0].toIntOrNull() ?: return 0L
    val minute = parts[1].toIntOrNull() ?: return 0L
    val localDateTime = LocalDate.now(ZoneId.systemDefault()).atTime(LocalTime.of(hour, minute))
    val candidate = localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val current = System.currentTimeMillis()
    return if (candidate >= current) candidate else candidate + 24L * 60L * 60L * 1000L
}
