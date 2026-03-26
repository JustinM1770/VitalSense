package mx.ita.vitalsense.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mx.ita.vitalsense.data.model.VitalsSnapshot

// ── Modelos de request/response para Gemini Generative Language API v1beta ───

@Serializable
private data class GeminiRequest(
    val system_instruction: SystemInstruction,
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig,
)

@Serializable
private data class SystemInstruction(val parts: List<Part>)

@Serializable
private data class GeminiContent(val role: String, val parts: List<Part>)

@Serializable
private data class Part(val text: String)

@Serializable
private data class GenerationConfig(
    val temperature: Double,
    val maxOutputTokens: Int,
)

@Serializable
private data class GeminiResponse(val candidates: List<Candidate> = emptyList())

@Serializable
private data class Candidate(val content: GeminiContent)

// ── Cliente IA ────────────────────────────────────────────────────────────────

/**
 * Genera recomendaciones preventivas personalizadas usando Gemini 2.0 Flash.
 *
 * Endpoint: POST /v1beta/models/gemini-2.0-flash:generateContent?key={apiKey}
 */
class VitalSenseAI(private val apiKey: String) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues  = true
            })
        }
    }

    /**
     * Analiza el historial de vitales del paciente y genera una recomendación
     * preventiva breve (máximo 2 oraciones) enfocada en adultos mayores.
     *
     * @param historial Últimas lecturas del dispositivo.
     * @param perfil    Datos del perfil: nombre, edad, tipoSangre.
     * @return Recomendación en español o mensaje de error por defecto.
     */
    suspend fun generarRecomendacion(
        historial: List<VitalsSnapshot>,
        perfil: Map<String, String>,
    ): String {
        val promedioGlucosa = historial.map { it.glucose }.average()
        val promedioHR      = historial.map { it.heartRate }.average()

        val systemPrompt = """
            Eres un asistente médico preventivo especializado en adultos mayores.
            Responde siempre en español. Sé breve, empático y concreto.
            No inventes datos; si faltan, omítelos sin mencionarlo.
        """.trimIndent()

        val userPrompt = """
            Analiza estos datos de salud y da UNA recomendación preventiva concreta,
            máximo 2 oraciones, enfocada en prevenir enfermedades crónicas.

            Paciente: ${perfil["nombre"] ?: "Paciente"}, ${perfil["edad"] ?: "?"} años
            Glucosa promedio: ${promedioGlucosa.toInt()} mg/dL
            Ritmo cardíaco promedio: ${promedioHR.toInt()} BPM
            Tipo de sangre: ${perfil["tipoSangre"] ?: "No disponible"}

            Responde solo con la recomendación, sin introducción ni explicación adicional.
        """.trimIndent()

        return runCatching {
            val response: GeminiResponse = client.post {
                url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                contentType(ContentType.Application.Json)
                setBody(GeminiRequest(
                    system_instruction = SystemInstruction(listOf(Part(systemPrompt))),
                    contents           = listOf(GeminiContent("user", listOf(Part(userPrompt)))),
                    generationConfig   = GenerationConfig(temperature = 0.3, maxOutputTokens = 150),
                ))
            }.body()

            response.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?.trim()
                ?: "Mantén una dieta balanceada, hidratación adecuada y actividad física regular."
        }.getOrDefault("Mantén una dieta balanceada, hidratación adecuada y actividad física regular.")
    }
}
