package mx.ita.vitalsense.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Medication(
    val id: String = "",
    val nombre: String = "",
    val dosis: String = "",
    val horario: String = "",
    val activo: Boolean = true,
)
