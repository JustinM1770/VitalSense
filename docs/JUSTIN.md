# BioMetric AI — Instrucciones de Justin (Lead)
## Deadline personal: Viernes 20 de Marzo + integración fin de semana

---

## Lo que ya está hecho ✅

- `Color.kt` — sistema de colores Figma completo
- `Theme.kt` — MaterialTheme flat/card
- `NeuCard.kt` — flat card con elevation
- `AppNavigation.kt` — todas las rutas + check de sesión en Splash
- `HealthSensorApp.kt` — URL Firebase explícita
- `DashboardScreen.kt` — header con avatar, saludo, campana, search bar
- `google-services.json` — proyecto vitalsenseai-1cb9f

---

## Lo que te falta antes del 20 de Marzo

### 1. Ruta para Miguel — agregar en `AppNavigation.kt`

Cuando Miguel avise que terminó, agregar:

```kotlin
// En el objeto Route:
const val CUESTIONARIO_TABS = "cuestionario_tabs"

// En el NavHost (después de Route.CUESTIONARIO):
composable(Route.CUESTIONARIO_TABS) {
    CuestionarioTabsScreen(
        onBack = { navController.popBackStack() },
        onFinish = {
            navController.navigate(Route.DASHBOARD) {
                popUpTo(0) { inclusive = true }
            }
        }
    )
}

// Y cambiar la navegación del cuestionario personal:
// onNext = { navController.navigate(Route.CUESTIONARIO_TABS) }
```

---

### 2. NFC — Leer el Freestyle Libre

**Archivo a crear:** `data/ble/FreestyleLibreReader.kt`

El Freestyle Libre usa NFC (ISO 15693). Android puede leerlo con `NfcAdapter`.

**Agregar permisos en `AndroidManifest.xml`:**
```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="false" />
```

**Código base:**
```kotlin
class FreestyleLibreReader(private val context: Context) {

    fun isNfcAvailable(): Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return nfcAdapter != null && nfcAdapter.isEnabled
    }

    fun enableNfcReading(activity: Activity) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context) ?: return
        val pendingIntent = PendingIntent.getActivity(
            activity, 0,
            Intent(activity, activity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))
        val techList = arrayOf(arrayOf(NfcV::class.java.name)) // Freestyle Libre usa NfcV (ISO 15693)
        nfcAdapter.enableForegroundDispatch(activity, pendingIntent, filters, techList)
    }

    fun disableNfcReading(activity: Activity) {
        NfcAdapter.getDefaultAdapter(context)?.disableForegroundDispatch(activity)
    }

    // Llamar desde onNewIntent de MainActivity cuando llega tag NFC
    fun parseFreestyleLibre(intent: Intent): Float? {
        if (intent.action != NfcAdapter.ACTION_TECH_DISCOVERED) return null
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return null
        val nfcV = NfcV.get(tag) ?: return null

        return try {
            nfcV.connect()
            // Leer 40 bloques del sensor (cada bloque = 8 bytes)
            val readCmd = byteArrayOf(0x60.toByte(), 0x01, 0, 0, 0, 0, 0, 0, 0x27, 0)
            val response = nfcV.transceive(readCmd)
            nfcV.close()
            // Parsear glucosa de los bytes (posición varía por versión del sensor)
            parseGlucoseFromResponse(response)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGlucoseFromResponse(data: ByteArray): Float? {
        // El Freestyle Libre codifica la glucosa en mg/dL en bytes específicos
        // Byte 5 y 6 del bloque de datos actual contienen la lectura
        if (data.size < 344) return null
        val rawGlucose = ((data[5].toInt() and 0xFF) or ((data[6].toInt() and 0x0F) shl 8))
        return rawGlucose * 0.18f // conversión a mg/dL aproximada
    }
}
```

**En `MainActivity.kt`, agregar:**
```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    // Leer NFC y guardar en Firebase
    val glucose = FreestyleLibreReader(this).parseFreestyleLibre(intent)
    glucose?.let {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance()
            .getReference("patients/paciente_001/glucose")
            .setValue(it.toInt())
    }
}
```

---

### 3. SOS — Botón físico en el reloj → alerta al tutor

**Archivo a crear:** `data/sos/SosManager.kt`

```kotlin
object SosManager {

    fun enviarSOS(patientName: String) {
        val db = FirebaseDatabase.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Escribir en Firebase — el tutor escucha este nodo
        val sosRef = db.getReference("sos/$userId")
        sosRef.setValue(mapOf(
            "activo" to true,
            "timestamp" to System.currentTimeMillis(),
            "paciente" to patientName,
            "mensaje" to "⚠️ SOS activado por $patientName"
        ))

        // También crear notificación en el historial
        db.getReference("users/$userId/notificaciones").push().setValue(mapOf(
            "titulo" to "🆘 SOS Activado",
            "descripcion" to "$patientName necesita ayuda inmediata",
            "tipo" to "SOS",
            "timestamp" to System.currentTimeMillis(),
            "leida" to false
        ))
    }

    // El tutor escucha este nodo en tiempo real
    fun escucharSOS(userId: String, onSOS: (String) -> Unit) {
        FirebaseDatabase.getInstance()
            .getReference("sos/$userId/activo")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val activo = snapshot.getValue(Boolean::class.java) ?: false
                    if (activo) onSOS("SOS activado")
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
```

**Agregar en `DashboardViewModel.kt`:**
```kotlin
init {
    observePatients()
    escucharSOS() // ← agregar
}

private fun escucharSOS() {
    val userId = getApplication<Application>()
        .let { FirebaseAuth.getInstance().currentUser?.uid } ?: return
    SosManager.escucharSOS(userId) { mensaje ->
        // Mandar notificación de alta prioridad
        sendAlertNotification(VitalsData(patientName = "Adulto Mayor"), "🆘 $mensaje")
    }
}
```

---

### 4. IA Preventiva con Claude API

**Archivo a crear:** `data/ai/VitalSenseAI.kt`

```kotlin
class VitalSenseAI {

    // Usar OkHttp para llamar a Claude API
    // Agregar en libs.versions.toml:
    // okhttp = "4.12.0"
    // Y en build.gradle.kts:
    // implementation(libs.okhttp)

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
```

**Agregar la API key en `local.properties` (NO en el repo):**
```
CLAUDE_API_KEY=tu_api_key_aqui
```

**En `build.gradle.kts`:**
```kotlin
android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "CLAUDE_API_KEY",
            "\"${project.findProperty("CLAUDE_API_KEY") ?: ""}\"")
    }
}
```

---

## Después del 20 de Marzo — Integración

1. Revisar cada PR del equipo
2. Hacer merge de `feature/axhel`, `feature/omar`, `feature/jonathan`, `feature/miguel`
3. Resolver conflictos si los hay
4. Agregar ruta `CUESTIONARIO_TABS` de Miguel
5. Build final y prueba completa con Jonathan

---

## Demo perfecta para el stand (ensayar con el equipo)

```
1. Abrir app en el tel de Jonathan
2. Login con Google → Dashboard
3. "Aquí ven los datos en tiempo real del adulto mayor"
   → Mostrar HR + SpO2 llegando de Firebase
4. Acercar teléfono al Freestyle Libre
   → Glucosa se actualiza en pantalla
5. "La IA analiza patrones de 7 días"
   → Mostrar recomendación en el Dashboard
6. Ir a Reporte Diario → mostrar radar chart + score A+
7. Tutor manda recordatorio de medicamento desde el Dashboard
8. "El adulto presiona SOS" → presionar botón → notificación llega al tel
   (esto es lo que impacta al jurado)
```

**Esto es el 50% del puntaje nacional. Si sale sin fallas, ganamos.**
