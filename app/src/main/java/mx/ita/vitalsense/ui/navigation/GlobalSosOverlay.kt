package mx.ita.vitalsense.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import mx.ita.vitalsense.ui.theme.DashBlue

private const val DB_URL = "https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com"

@Composable
fun GlobalSosOverlay() {
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid ?: return
    val database = remember { FirebaseDatabase.getInstance(DB_URL) }
    val context = LocalContext.current

    var activeSosId by remember { mutableStateOf<String?>(null) }
    var sosLat by remember { mutableStateOf(0.0) }
    var sosLng by remember { mutableStateOf(0.0) }

    DisposableEffect(userId) {
        val ref = database.getReference("alerts").child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var foundId: String? = null
                var foundLat = 0.0
                var foundLng = 0.0
                for (child in snapshot.children) {
                    val read = child.child("read").getValue(Boolean::class.java) ?: false
                    val status = child.child("status").getValue(String::class.java) ?: ""
                    val type = child.child("type").getValue(String::class.java) ?: ""
                    if (type == "SOS" && !read && status == "active") {
                        foundId = child.key
                        foundLat = child.child("lat").getValue(Double::class.java) ?: 0.0
                        foundLng = child.child("lng").getValue(Double::class.java) ?: 0.0
                        break
                    }
                }
                activeSosId = foundId
                sosLat = foundLat
                sosLng = foundLng
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    val currentId = activeSosId ?: return

    AlertDialog(
        onDismissRequest = { /* require explicit action */ },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Emergencia",
                    tint = Color.Red
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "¡EMERGENCIA SOS!",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Text(
                text = "Se ha activado una alerta SOS desde el reloj.",
                fontSize = 15.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    database.getReference("alerts")
                        .child(userId)
                        .child(currentId)
                        .child("read")
                        .setValue(true)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DashBlue)
            ) {
                Text("Aceptar", color = Color.White)
            }
        },
        dismissButton = {
            if (sosLat != 0.0 && sosLng != 0.0) {
                TextButton(
                    onClick = {
                        database.getReference("alerts")
                            .child(userId)
                            .child(currentId)
                            .child("read")
                            .setValue(true)
                        val uri = "geo:$sosLat,$sosLng?q=$sosLat,$sosLng(SOS)"
                        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        context.startActivity(mapIntent)
                    }
                ) {
                    Text("Ver en Mapa", color = DashBlue)
                }
            }
        },
        containerColor = Color.White
    )
}
