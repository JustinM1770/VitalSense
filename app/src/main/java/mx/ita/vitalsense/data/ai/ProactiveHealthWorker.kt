package mx.ita.vitalsense.data.ai

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import mx.ita.vitalsense.BuildConfig
import mx.ita.vitalsense.MainActivity
import mx.ita.vitalsense.R
import mx.ita.vitalsense.data.model.Medication
import mx.ita.vitalsense.data.model.SleepData
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.HealthSensorApp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Worker periódico que analiza los datos de salud del paciente con Gemini
 * y envía una notificación proactiva si detecta patrones de riesgo para
 * enfermedades crónicas.
 *
 * Se ejecuta cada 12 horas cuando hay conexión a internet.
 * La IA decide si hay algo relevante que notificar — si no hay hallazgos,
 * no se envía ninguna notificación para no generar ruido innecesario.
 *
 * Datos analizados:
 * - Historial de vitales de los últimos 7 días (HR, glucosa, SpO₂)
 * - Calidad y duración del sueño de la última semana
 * - Medicamentos activos del paciente
 * - Perfil médico (edad, padecimientos, tipo de sangre)
 */
class ProactiveHealthWorker(
    private val ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseDatabase.getInstance()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext Result.success()

        runCatching {
            val data    = collectHealthData(userId)
            val insight = analyzeWithGemini(data) ?: return@runCatching
            sendNotification(insight)
        }

        Result.success()
    }

    // ── Recolección de datos ──────────────────────────────────────────────────

    private suspend fun collectHealthData(userId: String): HealthSummary {
        // Perfil médico
        val profileSnap = runCatching {
            db.getReference("users/$userId/datosMedicos").get().await()
        }.getOrNull()
        val nombre    = profileSnap?.child("nombre")?.getValue(String::class.java).orEmpty()
        val apellidos = profileSnap?.child("apellidos")?.getValue(String::class.java).orEmpty()
        val edad      = profileSnap?.child("edad")?.getValue(Int::class.java) ?: 0
        val padecimientos = profileSnap?.child("padecimientos")?.getValue(String::class.java).orEmpty()

        // Historial de vitales — últimas 50 lecturas (aprox. 7 días)
        val historySnap = runCatching {
            db.getReference("patients/$userId/history")
                .orderByChild("timestamp")
                .limitToLast(50)
                .get().await()
        }.getOrNull()

        val vitals = historySnap?.children?.mapNotNull { snap ->
            snap.getValue(VitalsData::class.java)
        }?.filter { it.heartRate > 0 } ?: emptyList()

        // Sueño — últimos 7 días
        val sleepList = mutableListOf<SleepData>()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        for (i in 0..6) {
            val dateKey = LocalDate.now().minusDays(i.toLong()).format(formatter)
            runCatching {
                db.getReference("sleep/$userId/$dateKey").get().await()
                    .getValue(SleepData::class.java)
            }.getOrNull()?.let { sleepList.add(it) }
        }

        // Medicamentos activos
        val medsSnap = runCatching {
            db.getReference("medications/$userId").get().await()
        }.getOrNull()
        val meds = medsSnap?.children?.mapNotNull { snap ->
            snap.getValue(Medication::class.java)
        }?.filter { it.activo } ?: emptyList()

        return HealthSummary(
            nombre        = "$nombre $apellidos".trim().ifBlank { "Paciente" },
            edad          = edad,
            padecimientos = padecimientos,
            vitals        = vitals,
            sleep         = sleepList,
            medications   = meds,
        )
    }

    // ── Análisis con Gemini ───────────────────────────────────────────────────

    /**
     * Envía el resumen clínico a Gemini 2.0 Flash y solicita un análisis preventivo.
     *
     * El prompt instruye al modelo a responder **solo** con JSON estructurado.
     * Si no hay hallazgos relevantes, devuelve `{ "notificar": false }` y no
     * se genera ninguna notificación.
     *
     * @return [GeminiInsight] si hay algo que notificar, `null` en caso contrario.
     */
    private suspend fun analyzeWithGemini(data: HealthSummary): GeminiInsight? {
        if (data.vitals.isEmpty()) return null

        val avgHr      = data.vitals.map { it.heartRate }.average()
        val avgGlucose = data.vitals.filter { it.glucose > 0 }.map { it.glucose }.average()
        val avgSpo2    = data.vitals.filter { it.spo2 > 0 }.map { it.spo2 }.average()
        val avgSleep   = data.sleep.filter { it.horas > 0 }.map { it.horas }.average()
        val avgSleepScore = data.sleep.filter { it.score > 0 }.map { it.score }.average()

        // Tendencia de glucosa: compara primera mitad vs segunda mitad del historial
        val glucoseReadings = data.vitals.filter { it.glucose > 0 }.map { it.glucose }
        val glucoseTrend = if (glucoseReadings.size >= 6) {
            val firstHalf  = glucoseReadings.take(glucoseReadings.size / 2).average()
            val secondHalf = glucoseReadings.drop(glucoseReadings.size / 2).average()
            val delta = secondHalf - firstHalf
            when {
                delta >  10 -> "subiendo (${"%.0f".format(delta)} mg/dL en el período)"
                delta < -10 -> "bajando (${"%.0f".format(-delta)} mg/dL en el período)"
                else        -> "estable"
            }
        } else "insuficientes datos"

        val hrReadings = data.vitals.map { it.heartRate }
        val hrTrend = if (hrReadings.size >= 6) {
            val firstHalf  = hrReadings.take(hrReadings.size / 2).average()
            val secondHalf = hrReadings.drop(hrReadings.size / 2).average()
            val delta = secondHalf - firstHalf
            when {
                delta >  8 -> "subiendo (+${"%.0f".format(delta)} BPM)"
                delta < -8 -> "bajando (${"%.0f".format(delta)} BPM)"
                else       -> "estable"
            }
        } else "insuficientes datos"

        val medsText = if (data.medications.isEmpty()) "ninguno"
        else data.medications.joinToString(", ") { it.nombre }

        val systemPrompt = """
            Eres un sistema de análisis preventivo de salud para adultos mayores.
            Tu misión es detectar patrones de riesgo para enfermedades crónicas
            (diabetes tipo 2, hipertensión, enfermedad cardiovascular, EPOC)
            antes de que se vuelvan urgencias médicas.

            Responde ÚNICAMENTE con JSON válido, sin texto adicional, sin markdown.
            Formato requerido:
            {
              "notificar": true | false,
              "titulo": "Título corto (máx 60 caracteres)",
              "mensaje": "Observación y recomendación preventiva (máx 120 caracteres, español)",
              "categoria": "glucosa" | "corazon" | "oxigenacion" | "sueno" | "medicacion" | "general"
            }

            Si los datos son normales y no hay patrón preocupante, responde:
            { "notificar": false }

            Reglas:
            - Solo notifica si hay un patrón real, no por valores aislados.
            - No alarmes si todo está dentro del rango normal.
            - Prioriza tendencias sobre valores puntuales.
            - Mensajes en español, sin tecnicismos innecesarios.
            - El mensaje debe sugerir una acción preventiva concreta.
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("Paciente: ${data.nombre}, ${data.edad} años")
            if (data.padecimientos.isNotBlank()) appendLine("Padecimientos: ${data.padecimientos}")
            appendLine("Medicamentos activos: $medsText")
            appendLine()
            appendLine("Promedios últimos 7 días (${data.vitals.size} lecturas):")
            if (!avgHr.isNaN())      appendLine("- FC promedio: ${"%.0f".format(avgHr)} BPM — tendencia: $hrTrend")
            if (!avgGlucose.isNaN()) appendLine("- Glucosa promedio: ${"%.0f".format(avgGlucose)} mg/dL — tendencia: $glucoseTrend")
            if (!avgSpo2.isNaN())    appendLine("- SpO₂ promedio: ${"%.0f".format(avgSpo2)}%")
            if (!avgSleep.isNaN())   appendLine("- Sueño: ${"%.1f".format(avgSleep)} h/noche, score ${"%.0f".format(avgSleepScore)}/100 (${data.sleep.size} noches registradas)")
            appendLine()
            appendLine("¿Hay algún patrón de riesgo para enfermedades crónicas que justifique una notificación preventiva?")
        }

        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature",     0.2)   // muy bajo para respuestas consistentes
                put("maxOutputTokens", 200)
            })
        }

        val apiKey  = BuildConfig.GEMINI_API_KEY
        val url     = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val rawText = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val raw = response.body?.string().orEmpty()
            JSONObject(raw)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text", "")
                .orEmpty()
                .trim()
        }

        if (rawText.isBlank()) return null

        return runCatching {
            // Gemini a veces envuelve el JSON en ```json ... ```, limpiar si ocurre
            val clean = rawText
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val json = JSONObject(clean)
            if (!json.optBoolean("notificar", false)) return null
            GeminiInsight(
                titulo    = json.optString("titulo",   "Análisis preventivo"),
                mensaje   = json.optString("mensaje",  ""),
                categoria = json.optString("categoria","general"),
            )
        }.getOrNull()
    }

    // ── Notificación ─────────────────────────────────────────────────────────

    private fun sendNotification(insight: GeminiInsight) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    ctx, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) return
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_chat", true)
            putExtra("chat_context", insight.mensaje)
        }
        val pending = PendingIntent.getActivity(
            ctx, insight.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val icon = categoryIcon(insight.categoria)

        val notification = NotificationCompat.Builder(ctx, HealthSensorApp.CHANNEL_AI_INSIGHTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$icon ${insight.titulo}")
            .setContentText(insight.mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(insight.mensaje))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification)
    }

    private fun categoryIcon(categoria: String) = when (categoria) {
        "glucosa"     -> "\u2665"   // ♥ (glucosa/diabetes)
        "corazon"     -> "\uD83D\uDC93" // 💓
        "oxigenacion" -> "\uD83D\uDCA8" // 💨
        "sueno"       -> "\uD83C\uDF19" // 🌙
        "medicacion"  -> "\uD83D\uDC8A" // 💊
        else          -> "\uD83D\uDCCA" // 📊
    }

    // ── Modelos internos ──────────────────────────────────────────────────────

    private data class HealthSummary(
        val nombre: String,
        val edad: Int,
        val padecimientos: String,
        val vitals: List<VitalsData>,
        val sleep: List<SleepData>,
        val medications: List<Medication>,
    )

    private data class GeminiInsight(
        val titulo: String,
        val mensaje: String,
        val categoria: String,
    )

    // ── Companion: scheduling ─────────────────────────────────────────────────

    companion object {
        private const val WORK_NAME = "vitalsense_proactive_health_analysis"
        private const val NOTIF_ID  = 9001

        /**
         * Programa el análisis preventivo cada 12 horas.
         * Usa [ExistingPeriodicWorkPolicy.KEEP] para no reiniciar si ya está programado.
         * Requiere conexión a internet para llamar a Gemini.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ProactiveHealthWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
