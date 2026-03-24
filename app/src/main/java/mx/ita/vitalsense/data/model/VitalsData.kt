package mx.ita.vitalsense.data.model

data class VitalsData(
    val patientId: String = "",
    val heartRate: Int = 0,
    val glucose: Double = 0.0,
    val spo2: Int = 0,
    val timestamp: Long = 0L,
    val patientName: String = "Paciente",
)

// ── Umbrales y alertas IA ───────────────────────────────────────────────────

data class VitalAlert(
    val title: String,
    val advice: String,
)

fun VitalsData.computeAlerts(): List<VitalAlert> = buildList {
    if (glucose > 150)
        add(VitalAlert("Pico de glucosa detectado", "Sugiere hidratacion y reposo ligero"))
    if (heartRate > 100)
        add(VitalAlert("Frecuencia cardiaca elevada", "Sugiere reposo y respiracion profunda"))
    if (heartRate in 1..49)
        add(VitalAlert("Frecuencia cardiaca baja", "Contactar medico de inmediato"))
    if (spo2 in 1..89)
        add(VitalAlert("Oxigenacion critica", "Buscar atencion medica urgente"))
}

fun VitalsData.overallStatus(): OverallStatus =
    if (computeAlerts().isEmpty()) OverallStatus.STABLE else OverallStatus.ALERT

enum class OverallStatus { STABLE, ALERT }
