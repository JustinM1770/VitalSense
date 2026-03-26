package mx.ita.vitalsense.data.model

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable

/**
 * Umbrales clínicos basados en guías médicas internacionales vigentes.
 *
 * Referencias bibliográficas:
 * ─ [AHA]   Heidenreich PA et al. "2022 AHA/ACC/HFSA Guideline for the Management
 *           of Heart Failure." J Am Coll Cardiol. 2022;79(17):e263–e421.
 * ─ [ESC]   Brugada J et al. "2019 ESC Guidelines for the management of patients
 *           with supraventricular tachycardia." Eur Heart J. 2020;41(5):655–720.
 * ─ [ADA]   American Diabetes Association. "Standards of Medical Care in
 *           Diabetes—2024." Diabetes Care. 2024;47(Suppl 1).
 * ─ [WHO]   World Health Organization. "Pulse Oximetry Training Manual."
 *           Geneva: WHO Press; 2011.
 * ─ [BTS]   O'Driscoll BR et al. "BTS guideline for oxygen use in adults in
 *           healthcare and emergency settings."
 *           Thorax. 2017;72(Suppl 1):ii1–ii90.
 * ─ [ISPAD] Maahs DM et al. "ISPAD Clinical Practice Consensus Guidelines 2018."
 *           Pediatr Diabetes. 2018;19(Suppl 27).
 */
object ClinicalThresholds {

    // ── Frecuencia Cardíaca en reposo (AHA / ESC) ─────────────────────────────
    //    Rango normal adulto: 60–100 BPM
    //    ESC 2019 §5.1 : taquicardia = FC > 100 BPM en reposo
    //    ESC 2019 §8   : bradicardia severa hemodinámicamente significativa = FC < 40 BPM

    /** Bradicardia leve — FC < 60 BPM en reposo (AHA). */
    const val HR_BRADYCARDIA_MILD = 60

    /** Bradicardia moderada — FC < 50 BPM; puede causar síntomas (ESC 2019). */
    const val HR_BRADYCARDIA_MODERATE = 50

    /** Bradicardia severa — FC < 40 BPM; riesgo hemodinámico; atención inmediata (ESC 2019 §8). */
    const val HR_BRADYCARDIA_SEVERE = 40

    /** Taquicardia leve — FC > 100 BPM en reposo (AHA / ESC). */
    const val HR_TACHYCARDIA_MILD = 100

    /** Taquicardia moderada — FC > 120 BPM; vigilancia estrecha. */
    const val HR_TACHYCARDIA_MODERATE = 120

    /** Taquicardia severa — FC ≥ 150 BPM; umbral TSV / TV; emergencia (ESC 2019 §5.4). */
    const val HR_TACHYCARDIA_SEVERE = 150

    // ── Saturación de Oxígeno / SpO₂ (WHO 2011 / BTS 2017) ───────────────────
    //    BTS §4.3: objetivo SpO₂ 94–98 % para adultos sin riesgo de hipercapnia
    //    BTS §4.4: objetivo SpO₂ 88–92 % para EPOC / riesgo de hipercapnia

    /** SpO₂ normal mínimo: 95 % (WHO, BTS). */
    const val SPO2_NORMAL_MIN = 95

    /** Hipoxemia leve: SpO₂ 91–94 %; aumentar vigilancia (BTS 2017 §4.3). */
    const val SPO2_HYPOXEMIA_MILD = 94

    /** Hipoxemia moderada: SpO₂ 86–90 %; requiere evaluación médica (BTS 2017). */
    const val SPO2_HYPOXEMIA_MODERATE = 90

    /** Hipoxemia severa / crítica: SpO₂ ≤ 85 %; emergencia médica (WHO 2011). */
    const val SPO2_HYPOXEMIA_CRITICAL = 85

    // ── Glucosa en sangre — mg/dL (ADA Standards 2024 / ISPAD 2018) ──────────
    //    ADA 2024 §6: hipoglucemia L1 < 70 mg/dL, L2 < 54 mg/dL
    //    ADA 2024 §6: objetivo TIR (Time-in-Range) 70–180 mg/dL para DM1/DM2
    //    ADA 2024 §2: glucemia ayuno normal < 100 mg/dL; prediabetes 100–125 mg/dL

    /** Hipoglucemia Nivel 1 (alerta) — < 70 mg/dL; tratar con carbohidratos (ADA 2024). */
    const val GLUCOSE_HYPO_L1 = 70.0

    /** Hipoglucemia Nivel 2 (clínicamente significativa) — < 54 mg/dL; riesgo de inconsciencia (ADA 2024). */
    const val GLUCOSE_HYPO_L2 = 54.0

    /** Glucemia en ayunas normal máximo — 99 mg/dL (ADA 2024). */
    const val GLUCOSE_NORMAL_MAX = 99.0

    /** Inicio de rango prediabetes (IFG) — 100 mg/dL en ayunas (ADA 2024). */
    const val GLUCOSE_PREDIABETES_MIN = 100.0

    /** Hiperglucemia (límite TIR) — > 180 mg/dL; fuera del rango objetivo ADA/ISPAD. */
    const val GLUCOSE_HYPER_POSTPRANDIAL = 180.0

    /** Hiperglucemia significativa — > 250 mg/dL; riesgo de cetoacidosis (ADA 2024 §14). */
    const val GLUCOSE_HYPER_SIGNIFICANT = 250.0

    /** Hiperglucemia en crisis (DKA/SHH) — > 300 mg/dL; emergencia médica (ADA 2024 §15). */
    const val GLUCOSE_HYPER_CRISIS = 300.0
}

// ── Severidad de alerta ───────────────────────────────────────────────────────

/** Nivel de severidad clínica de una alerta de signos vitales. */
enum class AlertSeverity {
    /** Valor fuera del rango normal; se recomienda vigilancia aumentada. */
    WARNING,

    /** Valor en rango de alerta clínica; se sugiere evaluación médica. */
    URGENT,

    /** Valor en rango de emergencia; requiere atención médica inmediata. */
    CRITICAL,
}

// ── Umbrales personalizados por paciente ─────────────────────────────────────

/**
 * Umbrales de alarma ajustados para un paciente específico.
 * Derivan de [ClinicalThresholds] con modificaciones basadas en edad y diagnósticos.
 *
 * Construir con [MedicalProfile.computePersonalizedThresholds]; los valores por
 * defecto corresponden a un adulto sin comorbilidades relevantes.
 */
@Serializable
data class PatientThresholds(
    // Frecuencia cardíaca
    val hrTachycardiaMild: Int         = ClinicalThresholds.HR_TACHYCARDIA_MILD,
    val hrTachycardiaModerate: Int     = ClinicalThresholds.HR_TACHYCARDIA_MODERATE,
    val hrTachycardiaSevere: Int       = ClinicalThresholds.HR_TACHYCARDIA_SEVERE,
    val hrBradycardiaMild: Int         = ClinicalThresholds.HR_BRADYCARDIA_MILD,
    val hrBradycardiaModerate: Int     = ClinicalThresholds.HR_BRADYCARDIA_MODERATE,
    val hrBradycardiaSevere: Int       = ClinicalThresholds.HR_BRADYCARDIA_SEVERE,
    // SpO₂
    val spo2NormalMin: Int             = ClinicalThresholds.SPO2_NORMAL_MIN,
    val spo2HypoxemiaMild: Int         = ClinicalThresholds.SPO2_HYPOXEMIA_MILD,
    val spo2HypoxemiaModerate: Int     = ClinicalThresholds.SPO2_HYPOXEMIA_MODERATE,
    val spo2HypoxemiaCritical: Int     = ClinicalThresholds.SPO2_HYPOXEMIA_CRITICAL,
    // Glucosa
    val glucoseHypoL1: Double          = ClinicalThresholds.GLUCOSE_HYPO_L1,
    val glucoseHypoL2: Double          = ClinicalThresholds.GLUCOSE_HYPO_L2,
    val glucoseHyperAlert: Double      = ClinicalThresholds.GLUCOSE_HYPER_POSTPRANDIAL,
    val glucoseHyperSignificant: Double = ClinicalThresholds.GLUCOSE_HYPER_SIGNIFICANT,
    val glucoseHyperCrisis: Double     = ClinicalThresholds.GLUCOSE_HYPER_CRISIS,
)

// ── Cálculo de umbrales personalizados ───────────────────────────────────────

/**
 * Calcula [PatientThresholds] personalizados a partir del perfil médico del paciente.
 *
 * Ajustes aplicados con base en evidencia clínica:
 *
 * | Condición | Ajuste | Fuente |
 * |-----------|--------|--------|
 * | Edad ≥ 65 | Umbral bradicardia leve → 50 BPM | AHA: FC 50–59 BPM puede ser fisiológica en adultos mayores, atletas o bajo β-bloqueo. |
 * | EPOC / Asma grave | SpO₂ target ajustado 88–92 % | BTS 2017 §4.4: en riesgo de hipercapnia, SpO₂ > 92 % puede suprimir estímulo respiratorio. |
 * | Diabetes conocida | Umbral hiperglucemia → 180 mg/dL (TIR) | ADA 2024: objetivo Time-in-Range 70–180 mg/dL para DM1/DM2. |
 */
fun MedicalProfile.computePersonalizedThresholds(): PatientThresholds {
    val age             = parseFechaNacimiento(fechaNacimiento)
    val dxLower         = padecimientos.lowercase()
    val hasCOPD         = dxLower.contains("epoc") ||
                          dxLower.contains("enfermedad pulmonar obstructiva")
    val hasSevereAsthma = dxLower.contains("asma") &&
                          (dxLower.contains("grave") || dxLower.contains("severa") || dxLower.contains("persistente"))
    val isElderly       = age >= 65

    return PatientThresholds(
        // ── Frecuencia cardíaca ─────────────────────────────────────────────
        // AHA: en adultos mayores de 65, FC 50–59 BPM puede ser fisiológica
        hrBradycardiaMild = if (isElderly) 50 else ClinicalThresholds.HR_BRADYCARDIA_MILD,

        // ── SpO₂ ────────────────────────────────────────────────────────────
        // BTS 2017 §4.4: EPOC/asma grave → target 88–92 %; evitar hiperoxia
        spo2NormalMin         = if (hasCOPD || hasSevereAsthma) 92 else ClinicalThresholds.SPO2_NORMAL_MIN,
        spo2HypoxemiaMild     = if (hasCOPD || hasSevereAsthma) 91 else ClinicalThresholds.SPO2_HYPOXEMIA_MILD,
        spo2HypoxemiaModerate = if (hasCOPD || hasSevereAsthma) 88 else ClinicalThresholds.SPO2_HYPOXEMIA_MODERATE,

        // ── Glucosa ─────────────────────────────────────────────────────────
        // ADA 2024: para diabéticos el objetivo TIR es 70–180 mg/dL
        // Para no-diabéticos: alerta a 130 mg/dL (~1.3× ayuno normal máximo)
        glucoseHyperAlert = if (dxLower.contains("diabet"))
            ClinicalThresholds.GLUCOSE_HYPER_POSTPRANDIAL
        else
            (ClinicalThresholds.GLUCOSE_NORMAL_MAX * 1.3),
    )
}

/** Calcula edad en años desde una cadena de fecha (soporta yyyy-MM-dd y dd/MM/yyyy). */
private fun parseFechaNacimiento(fechaNacimiento: String): Int {
    if (fechaNacimiento.isBlank()) return 0
    return try {
        val date: LocalDate = when {
            fechaNacimiento.length >= 10 &&
            fechaNacimiento[4] == '-' ->
                LocalDate.parse(fechaNacimiento.take(10))          // yyyy-MM-dd
            fechaNacimiento.contains('/') -> {
                val p = fechaNacimiento.split('/')
                if (p.size < 3) return 0
                LocalDate(p[2].toInt(), p[1].toInt(), p[0].toInt()) // dd/MM/yyyy
            }
            else -> return 0
        }
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        var age = today.year - date.year
        if (today.monthNumber < date.monthNumber ||
            (today.monthNumber == date.monthNumber && today.dayOfMonth < date.dayOfMonth))
            age--
        age.coerceAtLeast(0)
    } catch (_: Exception) { 0 }
}
