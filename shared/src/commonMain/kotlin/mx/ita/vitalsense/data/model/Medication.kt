package mx.ita.vitalsense.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Medication(
    val id: String = "",
    val nombre: String = "",
    val cadaCuanto: String = "",
    val duracion: String = "",
    val dosis: String = "",
    val horario: String = "",
    val recordatorioHora: String = "",
    val reminderEnabled: Boolean = true,
    val nextReminderAt: Long = 0L,
    val lastReminderAt: Long = 0L,
    val activo: Boolean = true,
    val createdAt: Long = 0L,
)
