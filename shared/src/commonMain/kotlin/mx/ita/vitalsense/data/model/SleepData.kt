package mx.ita.vitalsense.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SleepData(
    val score: Int = 0,
    val horas: Double = 0.0,
    val estado: String = "",
)
