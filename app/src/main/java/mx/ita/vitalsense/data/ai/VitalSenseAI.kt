package mx.ita.vitalsense.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ita.vitalsense.BuildConfig
import mx.ita.vitalsense.data.model.VitalsSnapshot
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class VitalSenseAI {

    suspend fun generarRecomendacion(
        historial: List<VitalsSnapshot>,
        perfil: Map<String, Any>
    ): String = withContext(Dispatchers.IO) {

        val promedioGlucosa = historial.map { it.glucose }.average()
        val promedioHR = historial.map { it.heartRate }.average()
        val tendenciaGlucosa = if (historial.size >= 3) {
            val ultimos = historial.takeLast(3).map { it.glucose }
            if (ultimos.last() > ultimos.first()) "en aumento" else "estable"
        } else "sin suficientes datos"

        val prompt = """
            Eres un asistente médico preventivo para adultos mayores.
            Analiza estos datos de salud y da UNA recomendación preventiva concreta en español,
            máximo 2 oraciones, enfocada en prevenir enfermedades crónicas.

            Paciente: ${perfil["nombre"]}, ${perfil["edad"]} años
            Glucosa promedio últimos 7 días: ${"%.0f".format(promedioGlucosa)} mg/dL (tendencia: $tendenciaGlucosa)
            Ritmo cardíaco promedio: ${"%.0f".format(promedioHR)} BPM
            Tipo de sangre: ${perfil["tipoSangre"]}

            Responde solo con la recomendación, sin introducción ni explicación adicional.
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 150)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", BuildConfig.CLAUDE_API_KEY)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = OkHttpClient().newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")
        json.getJSONArray("content").getJSONObject(0).getString("text")
    }
}
