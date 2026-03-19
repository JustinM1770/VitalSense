package mx.ita.vitalsense.data.test

import com.google.firebase.database.FirebaseDatabase
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.model.VitalsSnapshot

/**
 * Siembra datos de prueba en Firebase para demo/testing.
 * Llama a [seed] una sola vez al entrar en Modo Demo.
 */
object TestDataSeeder {

    private val db = FirebaseDatabase.getInstance()

    private val patients = listOf(
        VitalsData(
            patientId   = "demo_patient_1",
            patientName = "Ana García",
            heartRate   = 72,
            glucose     = 98.0,
            spo2        = 98,
            timestamp   = System.currentTimeMillis(),
        ),
        VitalsData(
            patientId   = "demo_patient_2",
            patientName = "Carlos López",
            heartRate   = 108,   // alerta: > 100
            glucose     = 165.0, // alerta: > 150
            spo2        = 94,
            timestamp   = System.currentTimeMillis(),
        ),
        VitalsData(
            patientId   = "demo_patient_3",
            patientName = "María Hernández",
            heartRate   = 65,
            glucose     = 88.0,
            spo2        = 99,
            timestamp   = System.currentTimeMillis(),
        ),
    )

    fun seed() {
        val patientsRef = db.getReference("patients")

        patients.forEach { patient ->
            // Escribe vitales actuales del paciente
            patientsRef.child(patient.patientId).setValue(patient)

            // Genera 15 lecturas históricas (últimas 2 horas)
            val historyRef = patientsRef.child(patient.patientId).child("history")
            val now = System.currentTimeMillis()
            val intervalMs = 8 * 60 * 1000L // cada 8 minutos

            repeat(15) { i ->
                val ts = now - (14 - i) * intervalMs
                val noise = (i % 5) - 2  // variación realista ±2
                val snapshot = VitalsSnapshot(
                    heartRate = (patient.heartRate + noise * 2).coerceIn(50, 150),
                    glucose   = (patient.glucose + noise * 3).coerceIn(60.0, 250.0),
                    spo2      = (patient.spo2 + if (i % 4 == 0) -1 else 0).coerceIn(85, 100),
                    timestamp = ts,
                )
                historyRef.push().setValue(snapshot)
            }
        }
    }

    /** Elimina los pacientes de demo de Firebase (limpieza post-prueba). */
    fun clear() {
        patients.forEach { patient ->
            db.getReference("patients").child(patient.patientId).removeValue()
        }
    }

    // ── Datos mock locales (sin Firebase) ────────────────────────────────────
    // Usados por VitalsRepository como fallback cuando Firebase no responde.

    val mockPatients: List<VitalsData> = patients

    fun mockHistory(patientId: String): List<VitalsSnapshot> {
        val base = patients.find { it.patientId == patientId } ?: return emptyList()
        val now = System.currentTimeMillis()
        return (0 until 15).map { i ->
            val noise = (i % 5) - 2
            VitalsSnapshot(
                heartRate = (base.heartRate + noise * 2).coerceIn(50, 150),
                glucose   = (base.glucose + noise * 3).coerceIn(60.0, 250.0),
                spo2      = (base.spo2 + if (i % 4 == 0) -1 else 0).coerceIn(85, 100),
                timestamp = now - (14 - i) * 8 * 60 * 1000L,
            )
        }
    }
}
