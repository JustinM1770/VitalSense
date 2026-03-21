package mx.ita.vitalsense.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mx.ita.vitalsense.data.model.VitalsSnapshot

@Serializable
private data class AnthropicRequest(
    val model: String,
    val max_tokens: Int,
    val messages: List<Message>
)

@Serializable
private data class Message(
    val role: String,
    val content: String
)

@Serializable
private data class AnthropicResponse(
    val content: List<Content>
)

@Serializable
private data class Content(
    val text: String,
    val type: String
)

class VitalSenseAI(private val apiKey: String) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    suspend fun generarRecomendacion(
        historial: List<VitalsSnapshot>,
        perfil: Map<String, String>
    ): String {

        val promedioGlucosa = historial.map { it.glucose }.average()
        val promedioHR = historial.map { it.heartRate }.average()
        
        val prompt = """
            Eres un asistente médico preventivo para adultos mayores.
            Analiza estos datos de salud y da UNA recomendación preventiva concreta en español,
            máximo 2 oraciones, enfocada en prevenir enfermedades crónicas.

            Paciente: ${perfil["nombre"]}, ${perfil["edad"]} años
            Glucosa promedio: ${promedioGlucosa.toInt()} mg/dL
            Ritmo cardíaco promedio: ${promedioHR.toInt()} BPM
            Tipo de sangre: ${perfil["tipoSangre"]}

            Responde solo con la recomendación, sin introducción ni explicación adicional.
        """.trimIndent()

        val response: AnthropicResponse = client.post("https://api.anthropic.com/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(AnthropicRequest(
                model = "claude-3-5-sonnet-20240620",
                max_tokens = 150,
                messages = listOf(Message(role = "user", content = prompt))
            ))
        }.body()

        return response.content.firstOrNull()?.text ?: "No se pudo generar una recomendación."
    }
}
