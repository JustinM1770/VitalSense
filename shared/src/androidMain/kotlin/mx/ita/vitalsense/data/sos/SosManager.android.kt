package mx.ita.vitalsense.data.sos

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

actual class SosManager actual constructor() {

    private val db = FirebaseDatabase.getInstance()

    actual fun enviarSOS(userId: String, patientName: String) {
        val sosRef = db.getReference("sos/$userId")
        sosRef.setValue(mapOf(
            "activo" to true,
            "timestamp" to System.currentTimeMillis(),
            "paciente" to patientName,
            "mensaje" to "SOS activado por $patientName"
        ))

        db.getReference("users/$userId/notificaciones").push().setValue(mapOf(
            "titulo" to "SOS Activado",
            "descripcion" to "$patientName necesita ayuda inmediata",
            "tipo" to "SOS",
            "timestamp" to System.currentTimeMillis(),
            "leida" to false
        ))
    }

    actual fun escucharSOS(userId: String, onAlerta: (String) -> Unit) {
        db.getReference("sos/$userId/activo")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val activo = snapshot.getValue(Boolean::class.java) ?: false
                    if (activo) onAlerta("SOS activado")
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
