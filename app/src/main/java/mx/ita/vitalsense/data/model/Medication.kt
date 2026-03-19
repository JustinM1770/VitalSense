package mx.ita.vitalsense.data.model

data class Medication(
    val nombre: String = "",
    val dosis: String = "",
    val horario: String = "",
    val activo: Boolean = false
)
