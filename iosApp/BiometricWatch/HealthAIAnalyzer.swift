// HealthAIAnalyzer.swift - Sistema de IA para detección de patrones y predicción de enfermedades crónicas
// Analiza tendencias de FC, SpO2, y patrones de actividad para alertas tempranas

import Foundation
import HealthKit

class HealthAIAnalyzer {
    
    static let shared = HealthAIAnalyzer()
    
    // MARK: - Modelos de datos históricos
    
    private var heartRateHistory: [HealthDataPoint] = []
    private var spo2History: [HealthDataPoint] = []
    private var lastAnalysisDate: Date?
    
    struct HealthDataPoint {
        let value: Double
        let timestamp: Date
    }
    
    struct HealthAlert {
        enum AlertType: String {
            case cardiacArrhythmia = "Arritmia Cardíaca Detectada"
            case hypertensionRisk = "Riesgo de Hipertensión"
            case hypoxiaRisk = "Riesgo de Hipoxia"
            case chronicFatiguePattern = "Patrón de Fatiga Crónica"
            case diabetesRisk = "Riesgo de Diabetes (FC irregular)"
            case sleepApneaRisk = "Posible Apnea del Sueño"
            case cardiovascularRisk = "Riesgo Cardiovascular Alto"
            case stressPattern = "Patrón de Estrés Crónico"
        }
        
        let type: AlertType
        let severity: Int // 1-10
        let message: String
        let recommendation: String
        let timestamp: Date
        let data: [String: Any]
    }
    
    // MARK: - Agregar datos
    
    func recordHeartRate(_ bpm: Int) {
        heartRateHistory.append(HealthDataPoint(value: Double(bpm), timestamp: Date()))
        
        // Mantener solo últimos 1000 puntos (aprox. 16 horas si es cada minuto)
        if heartRateHistory.count > 1000 {
            heartRateHistory.removeFirst()
        }
        
        // Análisis en tiempo real si hay suficientes datos
        if heartRateHistory.count >= 10 {
            analyzeRealtimeHeartRate()
        }
    }
    
    func recordSpO2(_ percentage: Int) {
        spo2History.append(HealthDataPoint(value: Double(percentage), timestamp: Date()))
        
        if spo2History.count > 500 {
            spo2History.removeFirst()
        }
        
        if spo2History.count >= 5 {
            analyzeRealtimeSpO2()
        }
    }
    
    // MARK: - Análisis en Tiempo Real
    
    private func analyzeRealtimeHeartRate() {
        let recent = Array(heartRateHistory.suffix(10))
        let values = recent.map { $0.value }
        
        // Detectar arritmia: variabilidad extrema
        let stdDev = calculateStandardDeviation(values)
        if stdDev > 25.0 {
            let alert = HealthAlert(
                type: .cardiacArrhythmia,
                severity: 8,
                message: "Se detectó variabilidad cardíaca inusual",
                recommendation: "Considera consultar a un cardiólogo si persiste",
                timestamp: Date(),
                data: ["stdDev": stdDev, "avgBPM": values.average()]
            )
            sendAlert(alert)
        }
        
        // Detectar taquicardia sostenida
        let avg = values.average()
        if avg > 120 && recent.count >= 5 {
            let alert = HealthAlert(
                type: .cardiovascularRisk,
                severity: 7,
                message: "Frecuencia cardíaca elevada sostenida: \(Int(avg)) BPM",
                recommendation: "Descansa y respira profundamente. Si persiste, busca atención médica",
                timestamp: Date(),
                data: ["avgBPM": avg]
            )
            sendAlert(alert)
        }
        
        // Detectar bradicardia
        if avg < 50 && recent.count >= 5 {
            let alert = HealthAlert(
                type: .cardiovascularRisk,
                severity: 6,
                message: "Frecuencia cardíaca inusualmente baja: \(Int(avg)) BPM",
                recommendation: "Monitorea tus síntomas. Consulta a un médico si sientes mareo",
                timestamp: Date(),
                data: ["avgBPM": avg]
            )
            sendAlert(alert)
        }
    }
    
    private func analyzeRealtimeSpO2() {
        let recent = Array(spo2History.suffix(5))
        let values = recent.map { $0.value }
        let avg = values.average()
        
        // Hipoxia: SpO2 < 90%
        if avg < 90 {
            let alert = HealthAlert(
                type: .hypoxiaRisk,
                severity: 9,
                message: "⚠️ Niveles de oxígeno peligrosamente bajos: \(Int(avg))%",
                recommendation: "BUSCA ATENCIÓN MÉDICA INMEDIATA",
                timestamp: Date(),
                data: ["avgSpO2": avg]
            )
            sendAlert(alert)
        }
        // SpO2 bajo moderado
        else if avg < 94 {
            let alert = HealthAlert(
                type: .hypoxiaRisk,
                severity: 7,
                message: "Niveles de oxígeno bajos: \(Int(avg))%",
                recommendation: "Respira profundamente. Si persiste, consulta a un médico",
                timestamp: Date(),
                data: ["avgSpO2": avg]
            )
            sendAlert(alert)
        }
    }
    
    // MARK: - Análisis Predictivo (ejecutar periódicamente)
    
    func performPredictiveAnalysis() {
        guard heartRateHistory.count >= 50 else {
            print("[HealthAI] Insuficientes datos para análisis predictivo")
            return
        }
        
        // Análisis de tendencias a largo plazo
        detectHypertensionRisk()
        detectDiabetesRisk()
        detectSleepApneaRisk()
        detectChronicStressPattern()
        detectFatiguePattern()
        
        lastAnalysisDate = Date()
        print("[HealthAI] Análisis predictivo completado")
    }
    
    // MARK: - Detección de Condiciones Crónicas
    
    private func detectHypertensionRisk() {
        // Hipertensión: FC en reposo consistentemente > 90 BPM
        let last24Hours = heartRateHistory.filter { Date().timeIntervalSince($0.timestamp) < 86400 }
        guard last24Hours.count >= 20 else { return }
        
        let avgRestingHR = last24Hours.map { $0.value }.average()
        
        if avgRestingHR > 90 {
            let alert = HealthAlert(
                type: .hypertensionRisk,
                severity: 6,
                message: "Tu FC en reposo promedio es alta: \(Int(avgRestingHR)) BPM",
                recommendation: "Reduce sal y cafeína. Consulta a tu médico para medir presión arterial",
                timestamp: Date(),
                data: ["avgRestingHR": avgRestingHR, "period": "24h"]
            )
            sendAlert(alert)
        }
    }
    
    private func detectDiabetesRisk() {
        // Diabetes tipo 2: FC irregular + variabilidad reducida (paradójico)
        let last7Days = heartRateHistory.filter { Date().timeIntervalSince($0.timestamp) < 604800 }
        guard last7Days.count >= 100 else { return }
        
        let values = last7Days.map { $0.value }
        let stdDev = calculateStandardDeviation(values)
        
        // Variabilidad cardíaca reducida es indicador de riesgo de diabetes
        if stdDev < 5.0 {
            let alert = HealthAlert(
                type: .diabetesRisk,
                severity: 5,
                message: "Variabilidad cardíaca reducida detectada",
                recommendation: "Considera hacerte un chequeo de glucosa. Aumenta actividad física",
                timestamp: Date(),
                data: ["hrVariability": stdDev, "period": "7days"]
            )
            sendAlert(alert)
        }
    }
    
    private func detectSleepApneaRisk() {
        // Apnea del sueño: SpO2 cae durante la noche
        guard spo2History.count >= 20 else { return }
        
        let nightData = spo2History.filter { data in
            let hour = Calendar.current.component(.hour, from: data.timestamp)
            return hour >= 22 || hour <= 6
        }
        
        if nightData.count >= 10 {
            let minSpO2 = nightData.map { $0.value }.min() ?? 100
            let avgSpO2 = nightData.map { $0.value }.average()
            
            if minSpO2 < 90 || avgSpO2 < 93 {
                let alert = HealthAlert(
                    type: .sleepApneaRisk,
                    severity: 7,
                    message: "Posible apnea del sueño: SpO2 nocturno bajo",
                    recommendation: "Consulta a un especialista en sueño para estudio polisomnográfico",
                    timestamp: Date(),
                    data: ["minNightSpO2": minSpO2, "avgNightSpO2": avgSpO2]
                )
                sendAlert(alert)
            }
        }
    }
    
    private func detectChronicStressPattern() {
        // Estrés crónico: FC elevada persistente sin causa física
        let last3Days = heartRateHistory.filter { Date().timeIntervalSince($0.timestamp) < 259200 }
        guard last3Days.count >= 50 else { return }
        
        let avgHR = last3Days.map { $0.value }.average()
        
        if avgHR > 85 {
            let alert = HealthAlert(
                type: .stressPattern,
                severity: 5,
                message: "Patrón de estrés detectado: FC promedio \(Int(avgHR)) BPM",
                recommendation: "Practica meditación, mejora tu sueño y considera terapia",
                timestamp: Date(),
                data: ["avgHR3Days": avgHR]
            )
            sendAlert(alert)
        }
    }
    
    private func detectFatiguePattern() {
        // Fatiga crónica: FC alta en reposo + poca variabilidad
        let last7Days = heartRateHistory.filter { Date().timeIntervalSince($0.timestamp) < 604800 }
        guard last7Days.count >= 100 else { return }
        
        let values = last7Days.map { $0.value }
        let avg = values.average()
        let stdDev = calculateStandardDeviation(values)
        
        if avg > 80 && stdDev < 8.0 {
            let alert = HealthAlert(
                type: .chronicFatiguePattern,
                severity: 6,
                message: "Posible fatiga crónica: FC alta sin variabilidad",
                recommendation: "Mejora tu sueño, hidrátate y considera chequeo de tiroides",
                timestamp: Date(),
                data: ["avg7DaysHR": avg, "variability": stdDev]
            )
            sendAlert(alert)
        }
    }
    
    // MARK: - Utilidades
    
    private func calculateStandardDeviation(_ values: [Double]) -> Double {
        let mean = values.average()
        let variance = values.map { pow($0 - mean, 2) }.reduce(0, +) / Double(values.count)
        return sqrt(variance)
    }
    
    private func sendAlert(_ alert: HealthAlert) {
        print("[HealthAI] 🚨 ALERTA: \(alert.type.rawValue)")
        print("           Severidad: \(alert.severity)/10")
        print("           Mensaje: \(alert.message)")
        print("           Recomendación: \(alert.recommendation)")
        
        // Enviar notificación local
        NotificationCenter.default.post(
            name: .healthAIAlert,
            object: nil,
            userInfo: [
                "alert": alert,
                "type": alert.type.rawValue,
                "severity": alert.severity,
                "message": alert.message,
                "recommendation": alert.recommendation
            ]
        )
        
        // Guardar en Firebase
        saveAlertToFirebase(alert)
    }
    
    private func saveAlertToFirebase(_ alert: HealthAlert) {
        guard let userId = UserDefaults.standard.string(forKey: "biometric_user_id") else { return }
        
        let alertData: [String: Any] = [
            "type": alert.type.rawValue,
            "severity": alert.severity,
            "message": alert.message,
            "recommendation": alert.recommendation,
            "timestamp": ISO8601DateFormatter().string(from: alert.timestamp),
            "data": alert.data
        ]
        
        WatchFirebaseService.shared.pushHealthAlert(userId: userId, alertData: alertData)
    }
}

// MARK: - Extensions

extension Array where Element == Double {
    func average() -> Double {
        return isEmpty ? 0 : reduce(0, +) / Double(count)
    }
}

extension Notification.Name {
    static let healthAIAlert = Notification.Name("healthAIAlert")
}
