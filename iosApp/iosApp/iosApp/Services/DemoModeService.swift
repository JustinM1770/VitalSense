#if os(iOS)
// DemoModeService.swift — Generador de datos demo para presentaciones y jueces
// Semilla 7 días de historial realista y transmite vitales en tiempo real.
// Sin hardware requerido — ideal para demos del Innovatec 2026.

import Foundation
import FirebaseAuth
import FirebaseDatabase
import Combine

@MainActor
class DemoModeService: ObservableObject {
    static let shared = DemoModeService()

    @Published var isActive = false
    @Published var liveHeartRate: Double = 72
    @Published var liveSpO2: Double = 97
    @Published var liveGlucose: Double = 105
    @Published var seedingProgress: Double = 0  // 0.0 – 1.0

    private let db = Database.database().reference()
    private var liveTimer: Timer?
    private var animationTimer: Timer?
    private var tickCount: Int = 0

    private init() {}

    // MARK: - Public API

    /// Activa el modo demo: siembra historial + inicia vitales en vivo
    func activate(userId: String) async {
        guard !isActive else { return }
        isActive = true
        await seedHistoricalData(userId: userId)
        startLiveUpdates(userId: userId)
    }

    /// Desactiva completamente el modo demo
    func deactivate(userId: String) {
        isActive = false
        liveTimer?.invalidate()
        animationTimer?.invalidate()
        liveTimer = nil
        animationTimer = nil
        db.child("vitals/current/\(userId)").removeValue()
        UserDefaults.standard.set(false, forKey: "demo_mode_active")
    }

    func restoreIfNeeded(userId: String) {
        if UserDefaults.standard.bool(forKey: "demo_mode_active") {
            Task { await activate(userId: userId) }
        }
    }

    // MARK: - Historical Data Seeding

    /// Genera 7 días de signos vitales realistas y los escribe en Firebase.
    /// Patrón fisiológico adulto mayor (65 años, riesgo moderado pre-diabético).
    private func seedHistoricalData(userId: String) async {
        seedingProgress = 0

        let histRef = db.child("patients/\(userId)/history")
        let now = Date()
        let calendar = Calendar.current

        var records: [[String: Any]] = []

        // 7 días × ~24 lecturas/día = 168 puntos de datos
        for dayOffset in (0...6).reversed() {
            guard let dayStart = calendar.date(byAdding: .day, value: -dayOffset, to: now) else { continue }

            // Lecturas cada hora
            for hour in 0...23 {
                guard let sampleDate = calendar.date(byAdding: .hour, value: hour, to: calendar.startOfDay(for: dayStart)) else { continue }

                let hr    = generateHeartRate(hour: hour, dayOffset: dayOffset)
                let spo2  = generateSpO2(hour: hour, dayOffset: dayOffset)
                let glucose = generateGlucose(hour: hour, dayOffset: dayOffset)

                let record: [String: Any] = [
                    "heartRate": hr,
                    "spo2":      spo2,
                    "glucose":   glucose,
                    "timestamp": sampleDate.timeIntervalSince1970 * 1000,
                    "source":    "demo"
                ]
                records.append(record)
            }

            await MainActor.run {
                seedingProgress = Double(7 - dayOffset) / 7.0
            }
        }

        // Escribir en lotes de 20 para no saturar Firebase
        let batchSize = 20
        for (batchIdx, batchStart) in stride(from: 0, to: records.count, by: batchSize).enumerated() {
            let batch = records[batchStart..<min(batchStart + batchSize, records.count)]
            await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
                let batchDict = Dictionary(uniqueKeysWithValues: batch.enumerated().map { i, val in
                    ("\(Int(Date().timeIntervalSince1970 * 1000))_\(batchIdx)_\(i)", val)
                })
                histRef.updateChildValues(batchDict) { _, _ in cont.resume() }
            }
        }

        // Semillar datos de glucosa también en la tabla libre
        let glucoseRef = db.child("patients/\(userId)/glucose_history")
        var glucoseRecords: [String: Any] = [:]
        let mealHours = [7, 8, 13, 14, 19, 20]
        for dayOffset in (0...6).reversed() {
            guard let dayStart = calendar.date(byAdding: .day, value: -dayOffset, to: now) else { continue }
            for hour in mealHours {
                guard let sampleDate = calendar.date(byAdding: .hour, value: hour, to: calendar.startOfDay(for: dayStart)) else { continue }
                let glucose = generateGlucose(hour: hour, dayOffset: dayOffset)
                let key = "\(Int(sampleDate.timeIntervalSince1970 * 1000))"
                glucoseRecords[key] = [
                    "value": glucose,
                    "timestamp": sampleDate.timeIntervalSince1970 * 1000,
                    "context": hour < 10 ? "ayunas" : hour < 15 ? "post_desayuno" : hour < 21 ? "post_comida" : "post_cena"
                ]
            }
        }
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            glucoseRef.updateChildValues(glucoseRecords) { _, _ in cont.resume() }
        }

        seedingProgress = 1.0
        UserDefaults.standard.set(true, forKey: "demo_mode_active")
    }

    // MARK: - Live Updates

    private func startLiveUpdates(userId: String) {
        liveTimer?.invalidate()
        let ref = db.child("vitals/current/\(userId)")

        liveTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            guard let self else { return }
            Task { @MainActor in
                self.tickCount += 1
                let hr      = self.generateRealtimeHR()
                let spo2    = self.generateRealtimeSpO2()
                let glucose = self.generateRealtimeGlucose()

                self.liveHeartRate = hr
                self.liveSpO2 = spo2
                self.liveGlucose = glucose

                let data: [String: Any] = [
                    "heartRate": Int(hr),
                    "spo2":      Int(spo2),
                    "glucose":   glucose,
                    "timestamp": Date().timeIntervalSince1970 * 1000,
                    "source":    "demo_live"
                ]
                ref.setValue(data)

                // Cada 5 minutos (60 ticks × 5s = 5min), guardar en historial
                if self.tickCount % 60 == 0 {
                    let histKey = "\(Int(Date().timeIntervalSince1970 * 1000))"
                    self.db.child("patients/\(userId)/history/\(histKey)").setValue(data)
                }
            }
        }
    }

    // MARK: - Physiological Signal Generators

    // Frecuencia cardíaca con patrón circadiano realista
    private func generateHeartRate(hour: Int, dayOffset: Int) -> Int {
        let baseHR: Double
        switch hour {
        case 0...5:   baseHR = 62   // sueño profundo
        case 6:       baseHR = 68   // despertar
        case 7:       baseHR = 75   // mañana activa
        case 8...10:  baseHR = 78   // actividad matutina
        case 11...13: baseHR = 82   // antes/durante comida
        case 14...15: baseHR = 74   // siesta/reposo post-comida
        case 16...18: baseHR = 80   // tarde activa
        case 19...20: baseHR = 77   // cena
        case 21...22: baseHR = 70   // relajación nocturna
        default:      baseHR = 65   // preparación sueño
        }

        // Variación día a día (+/- 5 bpm)
        let dayVariation = Double((dayOffset * 3) % 7) - 3.5

        // Evento especial: día 3 (hace 3 días), hora 17 → episodio ejercicio
        if dayOffset == 3 && hour == 17 { return 108 }
        // Noche con FC alta (posible apnea): día 5, hora 3
        if dayOffset == 5 && hour == 3  { return 89 }

        let noise = Double.random(in: -4...4)
        return max(55, min(115, Int(baseHR + dayVariation + noise)))
    }

    // SpO2 con desaturaciones nocturnas ocasionales
    private func generateSpO2(hour: Int, dayOffset: Int) -> Int {
        let baseSpO2: Double
        switch hour {
        case 0...5:  baseSpO2 = 96.5  // sueño — puede bajar
        case 6...22: baseSpO2 = 97.8  // despierto — más estable
        default:     baseSpO2 = 97.0
        }

        // Desaturación real día 5, hora 3 (correlaciona con FC alta)
        if dayOffset == 5 && hour == 3 { return 93 }
        // Leve dip otro día nocturno
        if dayOffset == 1 && (hour == 2 || hour == 4) { return 95 }

        let noise = Double.random(in: -1.0...0.5)
        return max(93, min(99, Int((baseSpO2 + noise).rounded())))
    }

    // Glucosa con patrón post-prandial (pre-diabético moderado)
    private func generateGlucose(hour: Int, dayOffset: Int) -> Double {
        // Glucosa basal en ayunas: 105-118 mg/dL (prediabetes)
        let fasting: Double = 110 + Double((dayOffset * 2) % 8) - 4

        switch hour {
        case 0...6:   return fasting - 8 + Double.random(in: -5...5)   // nadir nocturno
        case 7:       return fasting + Double.random(in: -5...5)        // ayunas AM
        case 8:       return fasting + 30 + Double.random(in: -8...8)   // post-desayuno pico
        case 9:       return fasting + 22 + Double.random(in: -5...5)   // bajando
        case 10:      return fasting + 10 + Double.random(in: -5...5)
        case 11...12: return fasting + 2  + Double.random(in: -5...5)   // pre-comida
        case 13:      return fasting + 35 + Double.random(in: -8...8)   // post-comida pico
        case 14:      return fasting + 20 + Double.random(in: -6...6)
        case 15:      return fasting + 8  + Double.random(in: -5...5)
        case 16...18: return fasting + 2  + Double.random(in: -5...5)
        case 19:      return fasting + 32 + Double.random(in: -8...8)   // post-cena pico
        case 20:      return fasting + 18 + Double.random(in: -6...6)
        case 21:      return fasting + 5  + Double.random(in: -5...5)
        default:      return fasting + Double.random(in: -5...3)
        }
    }

    // Generadores en tiempo real con suavizado
    private func generateRealtimeHR() -> Double {
        let hour = Calendar.current.component(.hour, from: Date())
        let base = Double(generateHeartRate(hour: hour, dayOffset: 0))
        // Suavizado hacia base gradualmente
        let smoothed = liveHeartRate + (base - liveHeartRate) * 0.3
        return (smoothed + Double.random(in: -1.5...1.5)).rounded()
    }

    private func generateRealtimeSpO2() -> Double {
        let hour = Calendar.current.component(.hour, from: Date())
        let base = Double(generateSpO2(hour: hour, dayOffset: 0))
        let smoothed = liveSpO2 + (base - liveSpO2) * 0.4
        return min(99, max(94, (smoothed + Double.random(in: -0.5...0.3)).rounded()))
    }

    private func generateRealtimeGlucose() -> Double {
        let hour = Calendar.current.component(.hour, from: Date())
        let base = generateGlucose(hour: hour, dayOffset: 0)
        let smoothed = liveGlucose + (base - liveGlucose) * 0.15
        return (smoothed * 10).rounded() / 10
    }
}

#endif
