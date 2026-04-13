package mx.ita.vitalsense.ui.chat

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import mx.ita.vitalsense.BuildConfig
import mx.ita.vitalsense.data.model.SleepData
import mx.ita.vitalsense.data.model.VitalsData
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ChatMessageType {
    TEXT,
    IMAGE,
    AUDIO,
}

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val type: ChatMessageType = ChatMessageType.TEXT,
    val mediaUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()
    private val userName = auth.currentUser?.displayName?.split(" ")?.firstOrNull() ?: if (currentLanguage() == "en") "User" else "Usuario"
    private val uid = auth.currentUser?.uid ?: "guest"
    private val prefs = application.getSharedPreferences("chatbot_history", Context.MODE_PRIVATE)
    private val historyKey = "messages_$uid"
    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    init {
        loadHistory()
    }

    private fun currentLanguage(): String {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        return when (lang) {
            "pt" -> "pt"
            "en" -> "en"
            else -> "es"
        }
    }

    private fun welcomeText(): String {
        return when (currentLanguage()) {
            "en" -> "Hi $userName, do you have any questions?"
            "pt" -> "Olá $userName, você tem alguma dúvida?"
            else -> "Hola $userName, tienes alguna duda?"
        }
    }

    fun sendMessage(text: String) {
        val cleanText = sanitizeText(text)
        if (cleanText.isBlank()) return

        appendMessage(
            ChatMessage(
                id = System.currentTimeMillis().toString(),
                text = cleanText,
                isUser = true,
                type = ChatMessageType.TEXT,
            ),
        )

        viewModelScope.launch {
            _isTyping.value = true
            delay(400)

            val patientContext = fetchPatientContext()
            val response = runCatching {
                if (apiKey.isBlank()) {
                    buildMedicalFallbackResponse(cleanText, patientContext)
                } else {
                    callGeminiMedicalAssistant(cleanText, patientContext)
                }
            }.getOrElse {
                buildMedicalFallbackResponse(cleanText, patientContext)
            }

            appendMessage(
                ChatMessage(
                    id = (System.currentTimeMillis() + 1).toString(),
                    text = sanitizeText(response),
                    isUser = false,
                    type = ChatMessageType.TEXT,
                ),
            )
            _isTyping.value = false
        }
    }

    fun sendImageMessage(imageUri: String) {
        if (imageUri.isBlank()) return

        appendMessage(
            ChatMessage(
                id = System.currentTimeMillis().toString(),
                text = when (currentLanguage()) {
                    "en" -> "Image sent"
                    "pt" -> "Imagem enviada"
                    else -> "Imagen enviada"
                },
                isUser = true,
                type = ChatMessageType.IMAGE,
                mediaUri = imageUri,
            ),
        )

        respondWithText(
            when (currentLanguage()) {
                "en" -> "I received your image. If you want, describe what you see and I can help interpret it using your vital signs context."
                "pt" -> "Recebi sua imagem. Se quiser, descreva o que você observa e eu te ajudo a interpretar no contexto dos seus sinais vitais."
                else -> "Recibi tu imagen. Si quieres, describe lo que observas y te ayudo a interpretarlo dentro del contexto de tus signos vitales."
            },
        )
    }

    fun sendAudioMessage(audioUri: String) {
        if (audioUri.isBlank()) return

        appendMessage(
            ChatMessage(
                id = System.currentTimeMillis().toString(),
                text = when (currentLanguage()) {
                    "en" -> "Audio recorded"
                    "pt" -> "Audio gravado"
                    else -> "Audio grabado"
                },
                isUser = true,
                type = ChatMessageType.AUDIO,
                mediaUri = audioUri,
            ),
        )

        respondWithText(
            when (currentLanguage()) {
                "en" -> "I received your audio. For a more precise response, add a short message with your main question."
                "pt" -> "Recebi seu áudio. Para uma resposta mais precisa, adicione uma mensagem curta com sua dúvida principal."
                else -> "Recibi tu audio. Si quieres una respuesta mas precisa, agrega un mensaje corto con tu duda principal."
            },
        )
    }

    private fun respondWithText(text: String) {
        viewModelScope.launch {
            _isTyping.value = true
            delay(800)
            appendMessage(
                ChatMessage(
                    id = (System.currentTimeMillis() + 1).toString(),
                    text = sanitizeText(text),
                    isUser = false,
                    type = ChatMessageType.TEXT,
                ),
            )
            _isTyping.value = false
        }
    }

    private fun appendMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
        saveHistory()
    }

    private fun loadHistory() {
        val raw = prefs.getString(historyKey, null)
        if (raw.isNullOrBlank()) {
            _messages.value = listOf(
                ChatMessage(
                    id = "welcome",
                    text = welcomeText(),
                    isUser = false,
                    type = ChatMessageType.TEXT,
                ),
            )
            saveHistory()
            return
        }

        runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val type = runCatching {
                        ChatMessageType.valueOf(obj.optString("type", ChatMessageType.TEXT.name))
                    }.getOrDefault(ChatMessageType.TEXT)
                    add(
                        ChatMessage(
                            id = obj.optString("id", System.currentTimeMillis().toString()),
                            text = sanitizeText(obj.optString("text", "")),
                            isUser = obj.optBoolean("isUser", false),
                            type = type,
                            mediaUri = obj.optString("mediaUri").takeIf { it.isNotBlank() },
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        }.onSuccess { loaded ->
            _messages.value = if (loaded.isEmpty()) {
                listOf(
                    ChatMessage(
                        id = "welcome",
                        text = welcomeText(),
                        isUser = false,
                        type = ChatMessageType.TEXT,
                    ),
                )
            } else {
                loaded.sortedBy { it.timestamp }
            }
        }.onFailure {
            _messages.value = listOf(
                ChatMessage(
                    id = "welcome",
                    text = welcomeText(),
                    isUser = false,
                    type = ChatMessageType.TEXT,
                ),
            )
            saveHistory()
        }
    }

    private fun saveHistory() {
        val array = JSONArray()
        _messages.value.forEach { msg ->
            array.put(
                JSONObject().apply {
                    put("id", msg.id)
                    put("text", sanitizeText(msg.text))
                    put("isUser", msg.isUser)
                    put("type", msg.type.name)
                    put("mediaUri", msg.mediaUri ?: "")
                    put("timestamp", msg.timestamp)
                },
            )
        }
        prefs.edit().putString(historyKey, array.toString()).apply()
    }

    private fun sanitizeText(text: String): String {
        return text.replace("<", "").replace(">", "").trim()
    }

    private suspend fun fetchPatientContext(): PatientContext = withContext(Dispatchers.IO) {
        if (uid.isBlank() || uid == "guest") {
            return@withContext PatientContext(
                profileSummary = when (currentLanguage()) {
                    "en" -> "No active session."
                    "pt" -> "Sem sessão iniciada."
                    else -> "Sin sesion iniciada."
                },
                latestVitals = null,
                sleepData = null,
                history = emptyList(),
            )
        }

        val profileSnapshot = runCatching { db.getReference("patients/$uid/profile").get().await() }.getOrNull()
        val profileSummary = buildString {
            val nombre = profileSnapshot?.child("nombre")?.getValue(String::class.java).orEmpty()
            val edad = profileSnapshot?.child("edad")?.getValue(Int::class.java)
            val genero = profileSnapshot?.child("genero")?.getValue(String::class.java).orEmpty()
            val sangre = profileSnapshot?.child("tipoSangre")?.getValue(String::class.java).orEmpty()
            append(
                when (currentLanguage()) {
                    "en" -> "Name: "
                    "pt" -> "Nome: "
                    else -> "Nombre: "
                }
            )
            append(if (nombre.isBlank()) userName else nombre)
            append(". ")
            append(if (currentLanguage() == "en") "Age: " else if (currentLanguage() == "pt") "Idade: " else "Edad: ")
            append(edad?.toString() ?: if (currentLanguage() == "en") "Not available" else if (currentLanguage() == "pt") "Não disponível" else "No disponible")
            append(". ")
            append(if (currentLanguage() == "en") "Gender: " else if (currentLanguage() == "pt") "Gênero: " else "Genero: ")
            append(if (genero.isBlank()) (if (currentLanguage() == "en") "Not available" else if (currentLanguage() == "pt") "Não disponível" else "No disponible") else genero)
            append(". ")
            append(if (currentLanguage() == "en") "Blood type: " else if (currentLanguage() == "pt") "Tipo sanguíneo: " else "Tipo de sangre: ")
            append(if (sangre.isBlank()) (if (currentLanguage() == "en") "Not available" else if (currentLanguage() == "pt") "Não disponível" else "No disponible") else sangre)
            append('.')
        }

        val latestNode = runCatching { db.getReference("patients/$uid").get().await() }.getOrNull()
        val latestVitals = latestNode?.getValue(VitalsData::class.java)?.let { vitals ->
            vitals.copy(patientId = uid)
        } ?: run {
            val hr = latestNode?.child("heartRate")?.getValue(Int::class.java) ?: 0
            val spo2 = latestNode?.child("spo2")?.getValue(Int::class.java) ?: 0
            val glucose = latestNode?.child("glucose")?.getValue(Double::class.java) ?: 0.0
            if (hr == 0 && spo2 == 0 && glucose == 0.0) null else VitalsData(patientId = uid, heartRate = hr, spo2 = spo2, glucose = glucose)
        }

        val historySnapshot = runCatching {
            db.getReference("patients/$uid/history")
                .orderByChild("timestamp")
                .limitToLast(20)
                .get()
                .await()
        }.getOrNull()

        val history = historySnapshot?.children?.mapNotNull { item ->
            item.getValue(VitalsData::class.java) ?: run {
                val hr = item.child("heartRate").getValue(Int::class.java) ?: return@run null
                val spo2 = item.child("spo2").getValue(Int::class.java) ?: 0
                val glucose = item.child("glucose").getValue(Double::class.java) ?: 0.0
                val timestamp = item.child("timestamp").getValue(Long::class.java) ?: 0L
                VitalsData(patientId = uid, heartRate = hr, spo2 = spo2, glucose = glucose, timestamp = timestamp)
            }
        }?.sortedBy { it.timestamp } ?: emptyList()

        val todayKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val todaySleep = runCatching {
            db.getReference("sleep/$uid/$todayKey")
                .get()
                .await()
                .getValue(SleepData::class.java)
        }.getOrNull()

        val latestSleep = if (todaySleep != null) {
            todaySleep
        } else {
            runCatching {
                db.getReference("sleep/$uid")
                    .limitToLast(1)
                    .get()
                    .await()
                    .children
                    .firstOrNull()
                    ?.getValue(SleepData::class.java)
            }.getOrNull()
        }

        PatientContext(
            profileSummary = profileSummary,
            latestVitals = latestVitals,
            sleepData = latestSleep,
            history = history,
        )
    }

    /**
     * Llama a Gemini 2.0 Flash via REST API (Generative Language API v1beta).
     *
     * Estructura de la petición:
     * - system_instruction : rol y reglas del asistente médico
     * - contents           : historial de conversación + contexto clínico + pregunta actual
     * - generationConfig   : temperatura baja (0.3) para respuestas consistentes y precisas
     *
     * Endpoint: POST /v1beta/models/gemini-2.0-flash:generateContent?key={apiKey}
     */
    private suspend fun callGeminiMedicalAssistant(userText: String, context: PatientContext): String = withContext(Dispatchers.IO) {
        val systemPrompt = """
            ${if (currentLanguage() == "en") "You are a virtual medical assistant integrated in VitalSense, a telemonitoring app for older adults." else if (currentLanguage() == "pt") "Você é um assistente médico virtual integrado ao VitalSense, um app de telemonitoramento para idosos." else "Eres un médico virtual de apoyo integrado en VitalSense, una app de telemonitoreo para adultos mayores."}
            ${if (currentLanguage() == "en") "Reply in clear, empathetic and direct English." else if (currentLanguage() == "pt") "Responda em português claro, empático e direto." else "Responde en español claro, empático y directo."}
            Usa los datos del perfil y del dispositivo del paciente para personalizar tu orientación.
            Reglas:
            - No inventes datos; si faltan, dilo explícitamente.
            - Da recomendaciones concretas y breves.
            - Usa formato con bullets cortos.
            - Máximo 90 palabras en total.
            - Si hay signos de alarma (dolor de pecho, desmayo, falta de aire severa, confusión neurológica, SpO₂ muy baja), indica atención médica urgente.
            - Estructura de respuesta:
              1) Estado actual (1 línea)
              2) Qué hacer ahora (2–3 bullets)
              3) Cuándo ir a urgencias (1 línea)
            - ${if (currentLanguage() == "en") "Always close with: \"This guidance does not replace an in-person medical consultation.\"" else if (currentLanguage() == "pt") "Sempre termine com: \"Esta orientação não substitui uma consulta médica presencial.\"" else "Cierra siempre con: \"Esta orientación no sustituye una consulta médica presencial.\""}
        """.trimIndent()

        val contextJson = JSONObject().apply {
            put("perfil", context.profileSummary)
            put("ultima_lectura", JSONObject().apply {
                put("heartRate", context.latestVitals?.heartRate ?: JSONObject.NULL)
                put("spo2",      context.latestVitals?.spo2      ?: JSONObject.NULL)
                put("glucose",   context.latestVitals?.glucose   ?: JSONObject.NULL)
                put("timestamp", context.latestVitals?.timestamp ?: JSONObject.NULL)
            })
            put("sueno", JSONObject().apply {
                put("score",  context.sleepData?.score  ?: JSONObject.NULL)
                put("minutos", context.sleepData?.totalMinutes ?: JSONObject.NULL)
                put("horas_completas", context.sleepData?.completeHours ?: JSONObject.NULL)
                put("horas",  context.sleepData?.horas  ?: JSONObject.NULL)
                put("estado", context.sleepData?.estado ?: JSONObject.NULL)
            })
            val historyArray = JSONArray()
            context.history.takeLast(10).forEach { h ->
                historyArray.put(JSONObject().apply {
                    put("heartRate", h.heartRate)
                    put("spo2",      h.spo2)
                    put("glucose",   h.glucose)
                    put("timestamp", h.timestamp)
                })
            }
            put("historial_reciente", historyArray)
        }

        val conversationTail = _messages.value.takeLast(6)
            .filter { it.type == ChatMessageType.TEXT }
            .joinToString("\n") { msg ->
                val role = if (msg.isUser) {
                    if (currentLanguage() == "en") "Patient" else if (currentLanguage() == "pt") "Paciente" else "Paciente"
                } else {
                    if (currentLanguage() == "en") "Assistant" else if (currentLanguage() == "pt") "Assistente" else "Asistente"
                }
                "$role: ${sanitizeText(msg.text)}"
            }

        val fullUserMessage = buildString {
            appendLine(if (currentLanguage() == "en") "Patient clinical context:" else if (currentLanguage() == "pt") "Contexto clínico do paciente:" else "Contexto clínico del paciente:")
            appendLine(contextJson.toString())
            appendLine()
            if (conversationTail.isNotBlank()) {
                appendLine(if (currentLanguage() == "en") "Recent conversation:" else if (currentLanguage() == "pt") "Conversa recente:" else "Conversación reciente:")
                appendLine(conversationTail)
                appendLine()
            }
            appendLine(if (currentLanguage() == "en") "New patient question:" else if (currentLanguage() == "pt") "Nova pergunta do paciente:" else "Nueva pregunta del paciente:")
            appendLine(sanitizeText(userText))
        }

        // Gemini request body
        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", systemPrompt)
                }))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", fullUserMessage)
                }))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature",     0.3)
                put("maxOutputTokens", 500)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Error Gemini: ${response.code}")
            }
            val raw  = response.body?.string().orEmpty()
            val json = JSONObject(raw)
            val text = json
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text", "")
                .orEmpty()
            if (text.isBlank()) throw IllegalStateException("Respuesta Gemini vacía")
            sanitizeText(text)
        }
    }

    private fun buildMedicalFallbackResponse(userText: String, context: PatientContext): String {
        val input = userText.lowercase()
        val latest = context.latestVitals
        val sleep = context.sleepData
        val lang = currentLanguage()

        val dataSummary = buildString {
            if (lang == "en") {
                append("Current data: HR ")
                append(latest?.heartRate?.takeIf { it > 0 }?.toString() ?: "NA")
                append(" bpm, SpO2 ")
                append(latest?.spo2?.takeIf { it > 0 }?.toString() ?: "NA")
                append("%, glucose ")
                append(latest?.glucose?.takeIf { it > 0.0 }?.let { "%.0f".format(it) } ?: "NA")
                append(" mg/dL, sleep ")
                append(sleep?.durationLabel()?.takeIf { it.isNotBlank() } ?: "NA")
                append(".")
            } else if (lang == "pt") {
                append("Dados atuais: FC ")
                append(latest?.heartRate?.takeIf { it > 0 }?.toString() ?: "ND")
                append(" bpm, SpO2 ")
                append(latest?.spo2?.takeIf { it > 0 }?.toString() ?: "ND")
                append("%, glicose ")
                append(latest?.glucose?.takeIf { it > 0.0 }?.let { "%.0f".format(it) } ?: "ND")
                append(" mg/dL, sono ")
                append(sleep?.durationLabel()?.takeIf { it.isNotBlank() } ?: "ND")
                append(".")
            } else {
                append("Datos actuales: ")
                append("FC ")
                append(latest?.heartRate?.takeIf { it > 0 }?.toString() ?: "ND")
                append(" bpm, SpO2 ")
                append(latest?.spo2?.takeIf { it > 0 }?.toString() ?: "ND")
                append("%, glucosa ")
                append(latest?.glucose?.takeIf { it > 0.0 }?.let { "%.0f".format(it) } ?: "ND")
                append(" mg/dL, sueno ")
                append(sleep?.durationLabel()?.takeIf { it != "Sin datos" } ?: "ND")
                append(".")
            }
        }

        if (lang == "en") {
            return "Current status: $dataSummary\n• I can help you review your vital signs and symptoms.\n• Tell me your main concern and when it started.\n• If symptoms are severe or worsening quickly, seek emergency care now.\nThis guidance does not replace an in-person medical consultation."
        }
        if (lang == "pt") {
            return "Estado atual: $dataSummary\n• Posso te ajudar a revisar sinais vitais e sintomas.\n• Diga sua principal dúvida e quando começou.\n• Se os sintomas forem graves ou piorarem rápido, procure urgência agora.\nEsta orientação não substitui uma consulta médica presencial."
        }

        return when {
            input.contains("hola") || input.contains("buenas") -> {
                "Estado actual: $dataSummary\n• Estoy contigo para revisar tus signos vitales.\n• Dime tu sintoma principal o la metrica que te preocupa.\n• Si quieres, empezamos por pulso, SpO2 o glucosa.\nUrgencias: si hay dolor de pecho o falta de aire intensa, acude de inmediato.\nAviso: esta orientacion no sustituye consulta medica presencial."
            }

            input.contains("presion") || input.contains("presion arterial") -> {
                "Estado actual: $dataSummary\n• Si estabas activo, reposa 5 minutos y mide de nuevo.\n• Evita cafeina/tabaco antes de la medicion.\n• Si sigue elevada en varias tomas, agenda valoracion medica.\nUrgencias: si hay dolor de pecho, debilidad de un lado o vision borrosa severa, ve a urgencias.\nAviso: esta orientacion no sustituye consulta medica presencial."
            }

            input.contains("oxigen") || input.contains("spo2") -> {
                "Estado actual: $dataSummary\n• En reposo, una SpO2 >=95% suele ser adecuada.\n• Revisa que el sensor este bien colocado y repite lectura.\n• Si persiste baja, evita esfuerzo y monitorea sintomas.\nUrgencias: SpO2 <92% sostenida o falta de aire marcada requiere atencion inmediata.\nAviso: esta orientacion no sustituye consulta medica presencial."
            }

            input.contains("glucosa") || input.contains("azucar") || input.contains("diabetes") -> {
                "Estado actual: $dataSummary\n• Indica si fue en ayuno o despues de comer.\n• Comparte hora de lectura y sintomas (sed, mareo, vision borrosa).\n• Con eso te doy orientacion mas precisa.\nUrgencias: si hay confusion, vomito persistente o somnolencia intensa, busca atencion medica.\nAviso: esta orientacion no sustituye consulta medica presencial."
            }

            input.contains("sueno") || input.contains("dormi") || input.contains("insomnio") -> {
                "Estado actual: $dataSummary\n• Intenta horario fijo para dormir y despertar.\n• Reduce pantallas 60 minutos antes de acostarte.\n• Evita cafeina en la tarde/noche.\nUrgencias: si hay pausas respiratorias o somnolencia extrema diurna, consulta pronto.\nAviso: esta orientacion no sustituye consulta medica presencial."
            }

            input.contains("dolor") || input.contains("mareo") || input.contains("desmayo") || input.contains("pecho") -> {
                "Estado actual: $dataSummary\n• Deten actividad y mantente en reposo.\n• Si estas solo, pide ayuda ahora.\n• Ten a mano tus datos medicos y ubicacion.\nUrgencias: dolor de pecho, desmayo o falta de aire intensa son emergencia; llama al servicio medico ya.\nAviso: esta orientacion no sustituye consulta medica presencial."
            }

            input.contains("gracias") -> {
                "Con gusto. Si quieres, puedo ayudarte a revisar otra metrica o resolver otra duda."
            }

            else -> {
                "Estado actual: $dataSummary\n• Entendi: '$userText'.\n• Te respondo con enfoque medico y datos del reloj.\n• Dame un poco mas de detalle (inicio, intensidad, sintomas asociados).\nUrgencias: si hay deterioro rapido o sintomas severos, acude a urgencias.\nAviso: esta orientacion no sustituye consulta medica presencial."
            }
        }
    }

    data class PatientContext(
        val profileSummary: String,
        val latestVitals: VitalsData?,
        val sleepData: SleepData?,
        val history: List<VitalsData>,
    )
}
