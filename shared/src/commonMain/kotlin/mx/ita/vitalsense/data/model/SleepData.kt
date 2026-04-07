package mx.ita.vitalsense.data.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class SleepData(
    val score: Int = 0,
    val minutosTotales: Int = 0,
    val horasCompletas: Int = 0,
    val sleepStartMillis: Long = 0L,
    val sleepEndMillis: Long = 0L,
    val horas: Float = 0f,
    val estado: String = "",
)
{
    val totalMinutes: Int
        get() = when {
            minutosTotales > 0 -> minutosTotales
            horasCompletas > 0 -> horasCompletas * 60
            horas > 0f -> (horas * 60f).roundToInt()
            else -> 0
        }

    val completeHours: Int
        get() = when {
            horasCompletas > 0 -> horasCompletas
            totalMinutes > 0 -> totalMinutes / 60
            else -> 0
        }

    val remainingMinutes: Int
        get() = totalMinutes % 60

    val hasSleep: Boolean
        get() = totalMinutes > 0

    val hasSleepWindow: Boolean
        get() = sleepStartMillis > 0L && sleepEndMillis > 0L && sleepEndMillis >= sleepStartMillis

    fun durationLabel(): String = when {
        totalMinutes <= 0 -> "Sin datos"
        completeHours > 0 && remainingMinutes > 0 -> "${completeHours} h ${remainingMinutes} min"
        completeHours > 0 -> "${completeHours} h"
        else -> "${totalMinutes} min"
    }
}
