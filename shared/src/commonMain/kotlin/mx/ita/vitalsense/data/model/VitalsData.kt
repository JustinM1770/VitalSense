package mx.ita.vitalsense.data.model

import kotlinx.serialization.Serializable

@Serializable
data class VitalsData(
    val patientId: String = "",
    val patientName: String = "Paciente",
    val heartRate: Int = 0,
    val glucose: Double = 0.0,
    val spo2: Int = 0,
    val timestamp: Long = 0L,
    val sleep: SleepData? = null,
)

/**
 * Alerta clínica generada al detectar un signo vital fuera de rango.
 *
 * Los campos opcionales ([severity], [parameter], [value], [reference]) incluyen
 * valores por defecto para mantener compatibilidad con código existente.
 *
 * @param title     Descripción concisa del hallazgo clínico.
 * @param advice    Recomendación de acción basada en la guía de referencia.
 * @param severity  Nivel de severidad ([AlertSeverity.WARNING], [AlertSeverity.URGENT] o [AlertSeverity.CRITICAL]).
 * @param parameter Signo vital involucrado ("FC", "SpO₂", "Glucosa").
 * @param value     Valor registrado con sus unidades clínicas.
 * @param reference Guía médica que define el umbral aplicado.
 */
data class VitalAlert(
    val title: String,
    val advice: String,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val parameter: String = "",
    val value: String = "",
    val reference: String = "",
)

/**
 * Evalúa los signos vitales contra los umbrales personalizados del paciente y
 * devuelve la lista de alertas clínicas activas ordenadas de mayor a menor severidad.
 *
 * Los umbrales siguen:
 * - Frecuencia cardíaca : AHA / ESC 2019
 * - SpO₂               : WHO 2011 / BTS 2017
 * - Glucosa             : ADA Standards of Care 2024
 *
 * Usar [MedicalProfile.computePersonalizedThresholds] para obtener umbrales
 * ajustados por edad y diagnósticos del paciente específico.
 *
 * @param thresholds Umbrales del paciente; si se omite, se aplican los estándares
 *                   clínicos para un adulto sin comorbilidades relevantes.
 */
fun VitalsData.computeAlerts(
    thresholds: PatientThresholds = PatientThresholds(),
): List<VitalAlert> = buildList {

    // ── Frecuencia Cardíaca ────────────────────────────────────────────────────
    if (heartRate > 0) {
        // Taquicardia (de mayor a menor severidad)
        when {
            heartRate >= thresholds.hrTachycardiaSevere ->
                add(VitalAlert(
                    title     = "Taquicardia severa",
                    advice    = "FC $heartRate BPM supera el umbral crítico (${thresholds.hrTachycardiaSevere} BPM). " +
                                "Activar protocolo de emergencia.",
                    severity  = AlertSeverity.CRITICAL,
                    parameter = "FC",
                    value     = "$heartRate BPM",
                    reference = "ESC 2019 §5.4",
                ))
            heartRate >= thresholds.hrTachycardiaModerate ->
                add(VitalAlert(
                    title     = "Taquicardia moderada",
                    advice    = "FC $heartRate BPM. Indicar reposo, evitar esfuerzos. " +
                                "Consultar si persiste más de 30 minutos.",
                    severity  = AlertSeverity.URGENT,
                    parameter = "FC",
                    value     = "$heartRate BPM",
                    reference = "AHA / ESC 2019",
                ))
            heartRate >= thresholds.hrTachycardiaMild ->
                add(VitalAlert(
                    title     = "Taquicardia leve",
                    advice    = "FC $heartRate BPM. Reposo y respiración diafragmática. Vigilar evolución.",
                    severity  = AlertSeverity.WARNING,
                    parameter = "FC",
                    value     = "$heartRate BPM",
                    reference = "AHA",
                ))
        }
        // Bradicardia (de mayor a menor severidad)
        when {
            heartRate < thresholds.hrBradycardiaSevere ->
                add(VitalAlert(
                    title     = "Bradicardia severa",
                    advice    = "FC $heartRate BPM. Posible inestabilidad hemodinámica. " +
                                "Atención médica inmediata.",
                    severity  = AlertSeverity.CRITICAL,
                    parameter = "FC",
                    value     = "$heartRate BPM",
                    reference = "ESC 2019 §8",
                ))
            heartRate < thresholds.hrBradycardiaModerate ->
                add(VitalAlert(
                    title     = "Bradicardia moderada",
                    advice    = "FC $heartRate BPM. Puede causar mareo o síncope. Consultar médico.",
                    severity  = AlertSeverity.URGENT,
                    parameter = "FC",
                    value     = "$heartRate BPM",
                    reference = "ESC 2019",
                ))
            heartRate < thresholds.hrBradycardiaMild ->
                add(VitalAlert(
                    title     = "Bradicardia leve",
                    advice    = "FC $heartRate BPM. Vigilar síntomas (mareo, fatiga). Informar al médico.",
                    severity  = AlertSeverity.WARNING,
                    parameter = "FC",
                    value     = "$heartRate BPM",
                    reference = "AHA",
                ))
        }
    }

    // ── SpO₂ ──────────────────────────────────────────────────────────────────
    if (spo2 > 0) {
        when {
            spo2 <= thresholds.spo2HypoxemiaCritical ->
                add(VitalAlert(
                    title     = "Hipoxemia crítica",
                    advice    = "SpO₂ $spo2 %. Oxigenación insuficiente para la función orgánica. " +
                                "Llamar a emergencias (911) de inmediato.",
                    severity  = AlertSeverity.CRITICAL,
                    parameter = "SpO₂",
                    value     = "$spo2 %",
                    reference = "WHO 2011",
                ))
            spo2 <= thresholds.spo2HypoxemiaModerate ->
                add(VitalAlert(
                    title     = "Hipoxemia moderada",
                    advice    = "SpO₂ $spo2 %. Requiere evaluación médica. " +
                                "Sentar al paciente erguido, ventilar el ambiente, evitar esfuerzos.",
                    severity  = AlertSeverity.URGENT,
                    parameter = "SpO₂",
                    value     = "$spo2 %",
                    reference = "BTS 2017",
                ))
            spo2 <= thresholds.spo2HypoxemiaMild ->
                add(VitalAlert(
                    title     = "Hipoxemia leve",
                    advice    = "SpO₂ $spo2 %. Vigilar. Respiración diafragmática, ventilar el ambiente.",
                    severity  = AlertSeverity.WARNING,
                    parameter = "SpO₂",
                    value     = "$spo2 %",
                    reference = "BTS 2017 §4.3",
                ))
        }
    }

    // ── Glucosa ───────────────────────────────────────────────────────────────
    if (glucose > 0.0) {
        val glucStr = "${"%.0f".format(glucose)} mg/dL"
        when {
            // Hipoglucemia (de mayor a menor severidad)
            glucose < thresholds.glucoseHypoL2 ->
                add(VitalAlert(
                    title     = "Hipoglucemia severa",
                    advice    = "$glucStr — por debajo del umbral crítico (< ${thresholds.glucoseHypoL2.toInt()} mg/dL). " +
                                "Administrar glucosa IV o glucagón IM/SC. Llamar emergencias.",
                    severity  = AlertSeverity.CRITICAL,
                    parameter = "Glucosa",
                    value     = glucStr,
                    reference = "ADA 2024 §6",
                ))
            glucose < thresholds.glucoseHypoL1 ->
                add(VitalAlert(
                    title     = "Hipoglucemia",
                    advice    = "$glucStr. Ingerir 15–20 g de carbohidratos de acción rápida. " +
                                "Volver a medir en 15 min (Regla 15-15).",
                    severity  = AlertSeverity.URGENT,
                    parameter = "Glucosa",
                    value     = glucStr,
                    reference = "ADA 2024 §6 — Regla 15-15",
                ))
            // Hiperglucemia (de mayor a menor severidad)
            glucose >= thresholds.glucoseHyperCrisis ->
                add(VitalAlert(
                    title     = "Hiperglucemia en crisis",
                    advice    = "$glucStr. Riesgo de cetoacidosis diabética (CAD) o síndrome " +
                                "hiperosmolar hiperglucémico (SHH). Hidratación IV y atención médica urgente.",
                    severity  = AlertSeverity.CRITICAL,
                    parameter = "Glucosa",
                    value     = glucStr,
                    reference = "ADA 2024 §15",
                ))
            glucose >= thresholds.glucoseHyperSignificant ->
                add(VitalAlert(
                    title     = "Hiperglucemia significativa",
                    advice    = "$glucStr. Riesgo de cetoacidosis. Aumentar hidratación, " +
                                "revisar dosis de insulina, consultar médico tratante.",
                    severity  = AlertSeverity.URGENT,
                    parameter = "Glucosa",
                    value     = glucStr,
                    reference = "ADA 2024 §14",
                ))
            glucose >= thresholds.glucoseHyperAlert ->
                add(VitalAlert(
                    title     = "Hiperglucemia",
                    advice    = "$glucStr. Fuera del rango TIR (70–180 mg/dL). " +
                                "Hidratación, actividad física ligera, verificar dosis.",
                    severity  = AlertSeverity.WARNING,
                    parameter = "Glucosa",
                    value     = glucStr,
                    reference = "ADA 2024 — TIR",
                ))
        }
    }

}.sortedByDescending { it.severity.ordinal }

// ── Estado global ─────────────────────────────────────────────────────────────

enum class OverallStatus { STABLE, ALERT }

fun VitalsData.overallStatus(): OverallStatus =
    if (computeAlerts().isEmpty()) OverallStatus.STABLE else OverallStatus.ALERT

/**
 * Estado global con umbrales personalizados del paciente.
 * Devuelve [OverallStatus.ALERT] si existe al menos una alerta activa.
 */
fun VitalsData.overallStatus(thresholds: PatientThresholds): OverallStatus =
    if (computeAlerts(thresholds).isEmpty()) OverallStatus.STABLE else OverallStatus.ALERT
