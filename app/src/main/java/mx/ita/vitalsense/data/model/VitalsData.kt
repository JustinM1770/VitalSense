package mx.ita.vitalsense.data.model

data class VitalsData(
    val heartRate: Int = 0,
    val glucose: Double = 0.0,
    val spo2: Int = 0,
    val timestamp: Long = 0L,
    val patientName: String = "Paciente",
)
