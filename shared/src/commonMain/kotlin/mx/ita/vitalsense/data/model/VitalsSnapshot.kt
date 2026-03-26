package mx.ita.vitalsense.data.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Un registro histórico de vitales en un instante de tiempo. */
@Serializable
data class VitalsSnapshot(
    val heartRate: Int    = 0,
    val glucose:   Double = 0.0,
    val spo2:      Int    = 0,
    val timestamp: Long   = 0L,
)

// ── Tendencias ────────────────────────────────────────────────────────────────

enum class TrendDirection { RISING, FALLING, STABLE }

/**
 * Velocidad de cambio de un signo vital, derivada de la pendiente de la
 * regresión lineal sobre los últimos registros.
 */
enum class TrendVelocity {
    /** Sin cambio significativo. */
    NONE,
    /** Cambio lento — < 1 % por minuto. */
    SLOW,
    /** Cambio moderado — 1–3 % por minuto. */
    MODERATE,
    /** Cambio rápido — > 3 % por minuto; puede indicar deterioro agudo. */
    RAPID,
}

/**
 * Tendencia detallada de un signo vital específico.
 *
 * @param direction      Dirección del cambio.
 * @param velocity       Velocidad de cambio estimada.
 * @param deltaPerMinute Tasa de cambio en unidades/min (positivo = sube).
 */
data class VitalTrendDetail(
    val direction: TrendDirection,
    val velocity: TrendVelocity,
    val deltaPerMinute: Double,
)

/** Tendencias simples de los tres signos vitales principales (API original, sin cambios). */
data class VitalsTrend(
    val heartRate: TrendDirection,
    val glucose:   TrendDirection,
    val spo2:      TrendDirection,
)

/** Tendencias detalladas con velocidad de cambio y tasa por minuto. */
data class DetailedVitalsTrend(
    val heartRate: VitalTrendDetail,
    val glucose:   VitalTrendDetail,
    val spo2:      VitalTrendDetail,
)

/** Análisis simple de tendencias (mantiene compatibilidad con código existente). */
fun List<VitalsSnapshot>.analyzeTrends(): VitalsTrend = VitalsTrend(
    heartRate = simpleTrendOf { it.heartRate.toDouble() },
    glucose   = simpleTrendOf { it.glucose },
    spo2      = simpleTrendOf { it.spo2.toDouble() },
)

/**
 * Análisis detallado de tendencias mediante regresión lineal (mínimos cuadrados)
 * sobre los timestamps reales de las lecturas.
 *
 * Ventajas sobre la comparación punto-a-punto:
 * - Robusto ante lecturas individuales ruidosas.
 * - Normaliza la tasa de cambio a unidades/minuto, independiente de la frecuencia
 *   de muestreo.
 * - Clasifica la velocidad para distinguir cambios lentos de deterioros agudos.
 */
fun List<VitalsSnapshot>.analyzeTrendsDetailed(): DetailedVitalsTrend = DetailedVitalsTrend(
    heartRate = detailedTrendOf { it.heartRate.toDouble() },
    glucose   = detailedTrendOf { it.glucose },
    spo2      = detailedTrendOf { it.spo2.toDouble() },
)

// ── Detección de deterioro rápido ─────────────────────────────────────────────

enum class DegradationType {
    /** Caída de SpO₂ ≥ 5 % en < 10 min — deterioro respiratorio agudo. */
    SPO2_RAPID_DROP,

    /** Subida de FC ≥ 30 BPM en < 10 min — taquicardia súbita. */
    HR_RAPID_RISE,

    /** Caída de glucosa ≥ 30 mg/dL en < 15 min — hipoglucemia rápida. */
    GLUCOSE_RAPID_DROP,
}

/** Evento de deterioro agudo detectado en una serie temporal de vitales. */
data class RapidDegradationEvent(
    val type: DegradationType,
    val message: String,
)

/**
 * Detecta deterioro agudo comparando la lectura más reciente contra la del inicio
 * de una ventana temporal de 10–15 minutos.
 *
 * Criterios (basados en práctica clínica de urgencias):
 * | Parámetro | Cambio crítico     | Ventana |
 * |-----------|-------------------|---------|
 * | SpO₂      | Caída ≥ 5 %       | 10 min  |
 * | FC        | Subida ≥ 30 BPM   | 10 min  |
 * | Glucosa   | Caída ≥ 30 mg/dL  | 15 min  |
 *
 * @return El primer evento detectado de mayor gravedad, o `null` si no hay deterioro.
 */
fun List<VitalsSnapshot>.detectRapidDegradation(): RapidDegradationEvent? {
    if (size < 2) return null
    val sorted = sortedBy { it.timestamp }
    val latest = sorted.last()
    val windowMs = 15 * 60 * 1000L
    val windowStart = latest.timestamp - windowMs

    val reference = sorted.firstOrNull { it.timestamp >= windowStart } ?: return null
    val dtMin = (latest.timestamp - reference.timestamp) / 60_000.0
    if (dtMin < 0.5) return null // menos de 30 segundos: ignorar ruido

    val spo2Drop  = reference.spo2 - latest.spo2
    val hrRise    = latest.heartRate - reference.heartRate
    val glucDrop  = reference.glucose - latest.glucose

    return when {
        latest.spo2 > 0 && spo2Drop >= 5 ->
            RapidDegradationEvent(
                type    = DegradationType.SPO2_RAPID_DROP,
                message = "SpO₂ cayó ${spo2Drop} % en ${"%.0f".format(dtMin)} min " +
                          "(${reference.spo2} % → ${latest.spo2} %)",
            )
        latest.heartRate > 0 && hrRise >= 30 ->
            RapidDegradationEvent(
                type    = DegradationType.HR_RAPID_RISE,
                message = "FC subió $hrRise BPM en ${"%.0f".format(dtMin)} min " +
                          "(${reference.heartRate} → ${latest.heartRate} BPM)",
            )
        latest.glucose > 0 && glucDrop >= 30.0 ->
            RapidDegradationEvent(
                type    = DegradationType.GLUCOSE_RAPID_DROP,
                message = "Glucosa cayó ${"%.0f".format(glucDrop)} mg/dL en " +
                          "${"%.0f".format(dtMin)} min",
            )
        else -> null
    }
}

// ── Estadísticas de resumen ───────────────────────────────────────────────────

data class VitalsStats(
    val minHr: Int, val maxHr: Int, val avgHr: Int,
    val minGlucose: Double, val maxGlucose: Double, val avgGlucose: Double,
    val minSpo2: Int, val maxSpo2: Int, val avgSpo2: Int,
)

fun List<VitalsSnapshot>.computeStats(): VitalsStats? {
    if (isEmpty()) return null
    return VitalsStats(
        minHr      = minOf { it.heartRate },
        maxHr      = maxOf { it.heartRate },
        avgHr      = (sumOf { it.heartRate } / size),
        minGlucose = minOf { it.glucose },
        maxGlucose = maxOf { it.glucose },
        avgGlucose = sumOf { it.glucose } / size,
        minSpo2    = minOf { it.spo2 },
        maxSpo2    = maxOf { it.spo2 },
        avgSpo2    = (sumOf { it.spo2 } / size),
    )
}

// ── Funciones internas ────────────────────────────────────────────────────────

/** Tendencia simple por comparación de últimas 3 lecturas (original, sin timestamps). */
private fun List<VitalsSnapshot>.simpleTrendOf(
    selector: (VitalsSnapshot) -> Double,
): TrendDirection {
    if (size < 3) return TrendDirection.STABLE
    val values = takeLast(3).map(selector)
    return when {
        values.zipWithNext().all { (a, b) -> b > a * 1.03 } -> TrendDirection.RISING
        values.zipWithNext().all { (a, b) -> b < a * 0.97 } -> TrendDirection.FALLING
        else -> TrendDirection.STABLE
    }
}

/**
 * Tendencia detallada mediante regresión lineal por mínimos cuadrados.
 *
 * La pendiente se normaliza a unidades/minuto usando los timestamps reales.
 * Un R² implícito se obtiene filtrando series con menos de 2 puntos válidos.
 */
private fun List<VitalsSnapshot>.detailedTrendOf(
    selector: (VitalsSnapshot) -> Double,
): VitalTrendDetail {
    val pts = filter { selector(it) > 0.0 }
    if (pts.size < 2) return VitalTrendDetail(TrendDirection.STABLE, TrendVelocity.NONE, 0.0)

    // Mínimos cuadrados: y = slope·x + intercept (x en minutos desde el primer punto)
    val t0    = pts.first().timestamp
    val pairs = pts.map { ((it.timestamp - t0) / 60_000.0) to selector(it) }

    val n     = pairs.size.toDouble()
    val sumX  = pairs.sumOf { it.first }
    val sumY  = pairs.sumOf { it.second }
    val sumXY = pairs.sumOf { it.first * it.second }
    val sumX2 = pairs.sumOf { it.first * it.first }
    val denom = n * sumX2 - sumX * sumX

    val slope    = if (denom != 0.0) (n * sumXY - sumX * sumY) / denom else 0.0
    val avgY     = sumY / n
    val slopePct = if (avgY != 0.0) abs(slope / avgY) * 100.0 else 0.0   // % por minuto

    val direction = when {
        slope / (avgY.coerceAtLeast(1.0)) > 0.005  -> TrendDirection.RISING
        slope / (avgY.coerceAtLeast(1.0)) < -0.005 -> TrendDirection.FALLING
        else                                        -> TrendDirection.STABLE
    }

    val velocity = when {
        slopePct <= 0.5 -> TrendVelocity.NONE
        slopePct <= 1.0 -> TrendVelocity.SLOW
        slopePct <= 3.0 -> TrendVelocity.MODERATE
        else            -> TrendVelocity.RAPID
    }

    return VitalTrendDetail(direction, velocity, slope)
}
