package mx.ita.vitalsense.data.ai

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

class BioMetricMLEngine(private val context: Context) {

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessions: List<OrtSession?>
    private val loaded = AtomicBoolean(false)

    init {
        sessions = modelNames.map { modelName ->
            runCatching {
                val bytes = context.assets.open("ml/$modelName").use { it.readBytes() }
                environment.createSession(bytes, OrtSession.SessionOptions())
            }.onFailure {
                println("[BioMetricML] No se pudo cargar $modelName: ${it.message}")
            }.getOrNull()
        }
        loaded.set(sessions.any { it != null })
    }

    fun isAvailable(): Boolean = loaded.get()

    fun predictRisks(
        avgHr: Float,
        anomalyRate: Float,
        avgSpo2: Float,
        minSpo2: Float,
        avgGlucose: Float,
        maxGlucose: Float,
        highHrNight: Float,
        hrTrend: Float,
        glucoseTrend: Float,
        qrsDur: Float = 97f,
        qtcBazett: Float = 433f,
        prInterval: Float = 166f,
        pFound: Float = 1f,
        rAxis: Float = 20f,
        stAmpIi: Float = 0f,
        tAmpIi: Float = 0.5f,
    ): FloatArray {
        val features = floatArrayOf(
            avgHr,
            if (avgHr > 0f) 60000f / avgHr else 850f,
            anomalyRate.coerceIn(0f, 1f),
            avgSpo2,
            minSpo2,
            avgGlucose,
            maxGlucose,
            highHrNight,
            hrTrend,
            glucoseTrend,
            qrsDur,
            qtcBazett,
            prInterval,
            pFound,
            rAxis,
            stAmpIi,
            tAmpIi,
        )

        val inputName = sessions.firstNotNullOfOrNull { session ->
            session?.inputNames?.firstOrNull()
        } ?: return fallbackRisks(features)

        val shape = longArrayOf(1L, features.size.toLong())
        val tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(features), shape)

        tensor.use {
            return FloatArray(modelNames.size) { index ->
                val session = sessions[index]
                if (session == null) {
                    return@FloatArray fallbackRisks(features)[index]
                }

                runCatching {
                    session.run(mapOf(inputName to tensor)).use { result ->
                        extractProbability(result.get(0)?.value)
                    }
                }.getOrNull() ?: fallbackRisks(features)[index]
            }
        }
    }

    private fun fallbackRisks(features: FloatArray): FloatArray {
        val avgHr = features.getOrNull(0) ?: 0f
        val anomalyRate = features.getOrNull(2) ?: 0f
        val avgSpo2 = features.getOrNull(3) ?: 0f
        val avgGlucose = features.getOrNull(5) ?: 0f
        val qrsDur = features.getOrNull(10) ?: 97f
        val qtcBazett = features.getOrNull(11) ?: 433f
        val pFound = features.getOrNull(13) ?: 1f

        return floatArrayOf(
            (0.08f + anomalyRate * 0.55f + (1f - pFound) * 0.20f).coerceIn(0f, 0.95f),
            (0.10f + maxOf(0f, (avgHr - 85f)) / 120f).coerceIn(0f, 0.95f),
            (0.10f + maxOf(0f, (avgHr - 80f)) / 180f + maxOf(0f, 94f - avgSpo2) / 100f).coerceIn(0f, 0.95f),
            (0.08f + maxOf(0f, qrsDur - 110f) / 120f + maxOf(0f, 94f - avgSpo2) / 140f).coerceIn(0f, 0.95f),
            (0.06f + maxOf(0f, qtcBazett - 450f) / 180f + anomalyRate * 0.35f).coerceIn(0f, 0.95f),
            (0.10f + maxOf(0f, avgGlucose - 110f) / 180f).coerceIn(0f, 0.95f),
        )
    }

    private fun extractProbability(value: Any?): Float {
        return when (value) {
            is Float -> value
            is Double -> value.toFloat()
            is Int -> value.toFloat()
            is Long -> value.toFloat()
            is FloatArray -> value.firstOrNull() ?: 0f
            is DoubleArray -> value.firstOrNull()?.toFloat() ?: 0f
            is Array<*> -> value.firstNotNullOfOrNull { extractProbability(it) } ?: 0f
            is Map<*, *> -> {
                val direct = listOf(1, 1L, 1.0, "1").firstNotNullOfOrNull { key ->
                    value[key] as? Number
                }
                direct?.toFloat() ?: value.values.firstNotNullOfOrNull { it as? Number }?.toFloat() ?: 0f
            }
            else -> 0f
        }
    }

    companion object {
        private val modelNames = listOf(
            "BioMetricRiskModel_AF.onnx",
            "BioMetricRiskModel_HTA.onnx",
            "BioMetricRiskModel_CVD.onnx",
            "BioMetricRiskModel_HF.onnx",
            "BioMetricRiskModel_ARRHYTHMIA.onnx",
            "BioMetricRiskModel_DIABETES.onnx",
        )
    }
}
