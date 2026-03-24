package mx.ita.vitalsense.data.sos

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object SosManager {

    fun enviarSOS(patientName: String) {
        val db = FirebaseDatabase.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Escribir en Firebase — el tutor escucha este nodo
        val sosRef = db.getReference("sos/$userId")
        sosRef.setValue(mapOf(
            "activo" to true,
            "timestamp" to System.currentTimeMillis(),
            "paciente" to patientName,
            "mensaje" to "⚠️ SOS activado por $patientName"
        ))

        // También crear notificación en el historial
        db.getReference("patients/$userId/notificaciones").push().setValue(mapOf(
            "titulo" to "🆘 SOS Activado",
            "descripcion" to "$patientName necesita ayuda inmediata",
            "tipo" to "SOS",
            "timestamp" to System.currentTimeMillis(),
            "leida" to false
        ))
    }

    // El tutor escucha este nodo en tiempo real
    fun escucharSOS(userId: String, onSOS: (String) -> Unit) {
        FirebaseDatabase.getInstance()
            .getReference("sos/$userId/activo")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val activo = snapshot.getValue(Boolean::class.java) ?: false
                    if (activo) onSOS("SOS activado")
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}