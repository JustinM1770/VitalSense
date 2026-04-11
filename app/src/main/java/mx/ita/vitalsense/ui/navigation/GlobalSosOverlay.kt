package mx.ita.vitalsense.ui.navigation

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.core.app.NotificationCompat
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import mx.ita.vitalsense.HealthSensorApp
import mx.ita.vitalsense.MainActivity
import mx.ita.vitalsense.R
import mx.ita.vitalsense.ui.theme.DashBlue

private const val DB_URL = "https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com"

@Composable
fun GlobalSosOverlay() {
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid ?: return
    val database = remember { FirebaseDatabase.getInstance(DB_URL) }
    val context = LocalContext.current

    var activeSosId by rememberSaveable { mutableStateOf<String?>(null) }
    var sosLat by remember { mutableStateOf(0.0) }
    var sosLng by remember { mutableStateOf(0.0) }
    var lastNotifiedSosId by rememberSaveable { mutableStateOf<String?>(null) }
    var dismissedSosId by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(userId) {
        val ref = database.getReference("alerts").child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val previousActiveId = activeSosId
                val latestUnreadActiveSos = snapshot.children
                    .mapNotNull { child ->
                        val id = child.key ?: return@mapNotNull null
                        val status = child.child("status").getValue(String::class.java) ?: ""
                        val type = child.child("type").getValue(String::class.java) ?: ""
                        val read = child.child("read").getValue(Boolean::class.java) ?: false
                        if (type != "SOS" || status != "active" || read) return@mapNotNull null
                        Triple(
                            id,
                            child.child("timestamp").getValue(Long::class.java) ?: 0L,
                            Pair(
                                child.child("lat").getValue(Double::class.java) ?: 0.0,
                                child.child("lng").getValue(Double::class.java) ?: 0.0,
                            ),
                        )
                    }
                    .maxByOrNull { it.second }

                val foundId = latestUnreadActiveSos?.first
                val foundLat = latestUnreadActiveSos?.third?.first ?: 0.0
                val foundLng = latestUnreadActiveSos?.third?.second ?: 0.0

                activeSosId = foundId
                sosLat = foundLat
                sosLng = foundLng

                if (foundId != previousActiveId) {
                    dismissedSosId = null
                }

                if (!foundId.isNullOrBlank() && foundId != lastNotifiedSosId) {
                    showLocalSosNotification(
                        context = context,
                        alertId = foundId,
                        lat = foundLat,
                        lng = foundLng,
                    )
                    lastNotifiedSosId = foundId
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    val currentId = activeSosId ?: return
    if (currentId == dismissedSosId) return

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
                text = "Se ha activado una alerta SOS desde el reloj por internet (sin Bluetooth).",
                fontSize = 15.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    dismissedSosId = currentId
                    activeSosId = null
                    database.getReference("alerts")
                        .child(userId)
                        .child(currentId)
                        .updateChildren(
                            mapOf(
                                "read" to true,
                                "status" to "resolved",
                                "resolvedAt" to System.currentTimeMillis(),
                                "resolvedBy" to "phone_overlay",
                            ),
                        )
                },
                colors = ButtonDefaults.buttonColors(containerColor = DashBlue)
            ) {
                Text("Finalizar SOS", color = Color.White)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sosLat != 0.0 && sosLng != 0.0) {
                    TextButton(
                        onClick = {
                            val uri = "geo:$sosLat,$sosLng?q=$sosLat,$sosLng(SOS)"
                            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            context.startActivity(mapIntent)
                        }
                    ) {
                        Text("Abrir en Maps", color = DashBlue)
                    }
                }

                TextButton(
                    onClick = {
                        dismissedSosId = currentId
                        database.getReference("alerts")
                            .child(userId)
                            .child(currentId)
                            .child("read")
                            .setValue(true)
                    }
                ) {
                    Text("Cerrar", color = DashBlue)
                }
            }
        },
        containerColor = Color.White
    )
}

private fun showLocalSosNotification(
    context: android.content.Context,
    alertId: String,
    lat: Double,
    lng: Double,
) {
    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("open_notifications", true)
        putExtra("alert_id", alertId)
        putExtra("alert_lat", lat)
        putExtra("alert_lng", lng)
    }
    val pending = PendingIntent.getActivity(
        context,
        alertId.hashCode(),
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val body = if (lat != 0.0 && lng != 0.0) {
        "SOS detectado. Toca para ver detalles y abrir ubicacion en Google Maps."
    } else {
        "SOS detectado. Toca para abrir los detalles de la alerta."
    }

    val notification = NotificationCompat.Builder(context, HealthSensorApp.CHANNEL_ALERTS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("SOS desde el reloj")
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setContentIntent(pending)
        .setAutoCancel(true)
        .build()

    val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(alertId.hashCode(), notification)
}
