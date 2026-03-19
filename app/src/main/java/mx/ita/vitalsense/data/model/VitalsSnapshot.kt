package mx.ita.vitalsense.data.model

/** Un registro histórico de vitales en un instante de tiempo. */
data class VitalsSnapshot(
    val heartRate: Int    = 0,
    val glucose:   Double = 0.0,
    val spo2:      Int    = 0,
    val timestamp: Long   = 0L,
)

// ── Análisis de tendencias ────────────────────────────────────────────────────

enum class TrendDirection { RISING, FALLING, STABLE }

data class VitalsTrend(
    val heartRate: TrendDirection,
    val glucose:   TrendDirection,
    val spo2:      TrendDirection,
)

fun List<VitalsSnapshot>.analyzeTrends(): VitalsTrend = VitalsTrend(
    heartRate = trendOf { it.heartRate.toDouble() },
    glucose   = trendOf { it.glucose },
    spo2      = trendOf { it.spo2.toDouble() },
)

private fun List<VitalsSnapshot>.trendOf(
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
