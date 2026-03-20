package mx.ita.vitalsense.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.model.VitalsSnapshot

class VitalsRepository {

    private val db = FirebaseDatabase.getInstance("https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com")
    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

    private fun getVitalsRef() = db.getReference("vitals/current/${auth.currentUser?.uid ?: "global"}")

    // ── Multiusuario: todos los pacientes bajo "patients/" ──────────────────
    fun observePatients(): Flow<Result<List<VitalsData>>> = callbackFlow {
        val ref = db.getReference("patients")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = snapshot.children.mapNotNull { child ->
                        child.getValue(VitalsData::class.java)
                            ?.copy(patientId = child.key ?: "")
                    }
                    trySend(Result.success(list))
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Guardar snapshot histórico ───────────────────────────────────────────
    fun saveSnapshot(patientId: String, vitals: VitalsData) {
        val snapshot = VitalsSnapshot(
            heartRate = vitals.heartRate,
            glucose   = vitals.glucose,
            spo2      = vitals.spo2,
            timestamp = if (vitals.timestamp > 0) vitals.timestamp else System.currentTimeMillis(),
        )
        db.getReference("patients/$patientId/history").push().setValue(snapshot)
    }

    // ── Historial de un paciente (últimas N lecturas) ─────────────────────────
    fun observeHistory(patientId: String, limit: Int = 24): Flow<List<VitalsSnapshot>> = callbackFlow {
        val ref = db.getReference("patients/$patientId/history")
            .orderByChild("timestamp")
            .limitToLast(limit)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children
                    .mapNotNull { it.getValue(VitalsSnapshot::class.java) }
                    .sortedBy { it.timestamp }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { trySend(emptyList()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Un paciente por ID (usado por PatientDetailScreen) ──────────────────
    fun observePatient(patientId: String): Flow<Result<VitalsData>> = callbackFlow {
        val ref = db.getReference("patients/$patientId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val vitals = snapshot.getValue(VitalsData::class.java)
                    trySend(Result.success(vitals?.copy(patientId = patientId) ?: VitalsData(patientId = patientId)))
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                }
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Un solo paciente (usado por DeviceScan / BLE) ────────────────────────
    fun observeVitals(): Flow<Result<VitalsData>> = callbackFlow {
        val ref = getVitalsRef()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val vitals = snapshot.getValue(VitalsData::class.java)
                    trySend(Result.success(vitals ?: VitalsData()))
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
