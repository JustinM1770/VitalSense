package mx.ita.vitalsense.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SleepData(
    val score: Int = 0,
    val horas: Float = 0f,
    val estado: String = "",
)
