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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import mx.ita.vitalsense.R
import mx.ita.vitalsense.data.model.Medication
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope

@Composable
fun MedicationListScreen(
    onBack: () -> Unit,
    onAddMedication: () -> Unit,
) {
    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val auth = remember { FirebaseAuth.getInstance() }
    val database = remember { FirebaseDatabase.getInstance() }
    val userId = auth.currentUser?.uid.orEmpty()

    var medications by remember { mutableStateOf<List<Medication>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<Medication?>(null) }

    DisposableEffect(userId) {
        if (userId.isBlank()) {
            medications = emptyList()
            loading = false
            onDispose { }
        } else {
            val ref = database.getReference("medications").child(userId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    medications = snapshot.children.mapNotNull { child ->
                        child.getValue(Medication::class.java)?.copy(id = child.key ?: "")
                    }.sortedByDescending { it.createdAt }
                    loading = false
                }

                override fun onCancelled(error: DatabaseError) {
                    loading = false
                }
            }
            ref.addValueEventListener(listener)
            onDispose { ref.removeEventListener(listener) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DashBlue.copy(alpha = 0.12f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = DashBlue)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_medications), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = colorScheme.onSurface)
                    Text(stringResource(R.string.medication_manage_subtitle), fontFamily = Manrope, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onAddMedication) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.medication_add), tint = DashBlue)
                }
            }

            Spacer(Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(stringResource(R.string.medication_total_registered), fontFamily = Manrope, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        Text("${medications.size}", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = colorScheme.onSurface)
                    }
                    TextButton(onClick = onAddMedication, colors = ButtonDefaults.textButtonColors(contentColor = DashBlue)) {
                        Text(stringResource(R.string.medication_add_another), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DashBlue)
                }
            } else if (medications.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.surface,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.medication_empty_title), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.medication_empty_body), fontFamily = Manrope, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onAddMedication, colors = ButtonDefaults.buttonColors(containerColor = DashBlue)) {
                            Text(stringResource(R.string.medication_add), color = Color.White)
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    medications.forEach { medication ->
                        MedicationCard(
                            medication = medication,
                            onDelete = { pendingDelete = medication },
                            onOpen = onAddMedication,
                        )
                    }
                }
            }
        }

        if (pendingDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.medication_delete_title)) },
                text = { Text(stringResource(R.string.medication_delete_message, pendingDelete?.nombre ?: stringResource(R.string.medication_this_item))) },
                confirmButton = {
                    TextButton(onClick = {
                        val med = pendingDelete ?: return@TextButton
                        val ref = database.getReference("medications").child(userId).child(med.id)
                        ref.removeValue()
                        pendingDelete = null
                    }) {
                        Text(stringResource(R.string.medication_delete_confirm), color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(R.string.common_cancel), color = DashBlue)
                    }
                },
            )
        }
    }
}

@Composable
private fun MedicationCard(
    medication: Medication,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(DashBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(medication.nombre.firstOrNull()?.uppercaseChar()?.toString() ?: "M", color = DashBlue, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(medication.nombre, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(medication.cadaCuanto.ifBlank { stringResource(R.string.medication_no_frequency) }, fontFamily = Manrope, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.medication_delete_confirm), tint = Color(0xFFDC2626))
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(medication.duracion.ifBlank { stringResource(R.string.medication_no_duration) })
                Pill(medication.recordatorioHora.ifBlank { stringResource(R.string.medication_no_time) })
                Pill(if (medication.activo) stringResource(R.string.medication_active) else stringResource(R.string.medication_inactive))
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onOpen, colors = ButtonDefaults.textButtonColors(contentColor = DashBlue)) {
                Text(stringResource(R.string.medication_add_another), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = RoundedCornerShape(999.dp), color = DashBlue.copy(alpha = 0.08f)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontFamily = Manrope,
            fontSize = 11.sp,
            color = DashBlue,
        )
    }
}