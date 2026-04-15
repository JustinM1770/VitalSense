#if os(iOS)
import Foundation
import Combine
import FirebaseAuth
import FirebaseDatabase
import UserNotifications

// Enum para controlar las tarjetas de métricas en el Pager
enum MetricCardType: String, CaseIterable {
    case sleep, heartRate, glucose, spo2, kcal
}

// MARK: - DashboardViewModel (datos reales de HealthKit + Firebase)
@MainActor
class DashboardViewModel: ObservableObject {
    // HealthKit (datos reales del Apple Watch)
    @Published var healthKit = HealthKitService.shared

    // Firebase (medicamentos + libre)
    @Published var medications: [MedicationiOS] = []
    @Published var userName: String = "Usuario"
    @Published var userInitial: String = "U"
    @Published var libreLastGlucose: Double = 0
    @Published var libreLastTime: TimeInterval = 0
    @Published var isLoading = true
    @Published var aiRiskLevel: RiskLevel? = nil
    @Published var aiSummary: String? = nil

    // Manual data overrides (para demos/simuladores)
    @Published var manualHeartRate: Int? = nil
    @Published var manualSleepData: SleepDataiOS? = nil

    // Control de visibilidad de tarjetas (Pager)
    @Published var visibleMetrics: [MetricCardType] = [.sleep]
    let fullMetricOrder: [MetricCardType] = [.sleep, .heartRate, .glucose, .spo2]

    private let dbRef = Database.database().reference()
    private var cancellables = Set<AnyCancellable>()
    private var aiAnalysisDone = false

    func startObserving() {
        isLoading = true
        loadUserInfo()

        // Pedir permisos de HealthKit y empezar a observar
        Task {
            await healthKit.requestAuthorization()
            isLoading = false
        }

        // Cargar datos de Firebase (solo medicamentos y libre)
        loadMedications()
        loadLibreData()

        // Observar cambios del HealthKitService → actualizar UI + Firebase
        healthKit.objectWillChange
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in
                self?.objectWillChange.send()
                self?.updateAlertMessage()
                self?.syncVitalsToFirebase()
            }
            .store(in: &cancellables)

        // Cargar análisis IA en caché y lanzar si tiene más de 6 horas
        loadAIInsights()
    }

    private func loadAIInsights() {
        guard let uid = Auth.auth().currentUser?.uid, !aiAnalysisDone else { return }
        aiAnalysisDone = true
        Task {
            let service = AIHealthService.shared
            await service.loadCachedAnalysis(userId: uid)
            if let a = service.analysis {
                await MainActor.run {
                    self.aiRiskLevel = a.overallRisk
                    self.aiSummary = a.summary
                }
                // Si el análisis tiene más de 6 horas, actualizar en background
                let age = Date().timeIntervalSince(a.timestamp)
                if age > 6 * 3600 {
                    await service.runAnalysis(userId: uid)
                    if let updated = service.analysis {
                        await MainActor.run {
                            self.aiRiskLevel = updated.overallRisk
                            self.aiSummary = updated.summary
                        }
                    }
                }
            } else {
                // Primera vez: lanzar análisis automático
                await service.runAnalysis(userId: uid)
                if let a = service.analysis {
                    await MainActor.run {
                        self.aiRiskLevel = a.overallRisk
                        self.aiSummary = a.summary
                    }
                }
            }
        }
    }

    func stopObserving() {
        healthKit.stopObserving()
        cancellables.removeAll()
    }

    // MARK: - User Info
    private func loadUserInfo() {
        let user = Auth.auth().currentUser
        userName = user?.displayName ?? "Usuario"
        let parts = userName.split(separator: " ")
        userInitial = parts.first.map { String($0.prefix(1)).uppercased() } ?? "U"
    }

    // MARK: - Medications (Firebase)
    private func loadMedications() {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        let todayKey = Self.todayDateKey()
        dbRef.child("medications/\(uid)").observeSingleEvent(of: .value) { [weak self] snap in
            guard let self else { return }
            var meds: [MedicationiOS] = []
            for child in snap.children {
                guard let s = child as? DataSnapshot,
                      let d = s.value as? [String: Any] else { continue }
                let activo = d["activo"] as? Bool ?? true
                guard activo else { continue }
                let takenDict = d["tomados"] as? [String: Bool] ?? [:]
                meds.append(MedicationiOS(
                    id: s.key,
                    nombre: d["nombre"] as? String ?? "",
                    dosis: d["dosis"] as? String ?? "",
                    horario: d["cadaCuanto"] as? String ?? "",
                    activo: activo,
                    takenToday: takenDict[todayKey] == true
                ))
            }
            Task { @MainActor in
                self.medications = meds
            }
        }
    }

    func markMedicationTaken(_ med: MedicationiOS) {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        let todayKey = Self.todayDateKey()
        dbRef.child("medications/\(uid)/\(med.id)/tomados/\(todayKey)").setValue(true)
        if let idx = medications.firstIndex(where: { $0.id == med.id }) {
            medications[idx].takenToday = true
        }
    }

    private static func todayDateKey() -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        return fmt.string(from: Date())
    }

    // MARK: - Libre Data (UserDefaults)
    private func loadLibreData() {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        libreLastGlucose = UserDefaults.standard.double(forKey: "libre_last_glucose_\(uid)")
        libreLastTime = UserDefaults.standard.double(forKey: "libre_last_time_\(uid)")
    }

    // MARK: - Alert
    @Published var alertMessage: String?

    // MARK: - Patients (vitales del usuario actual como VitalsDataiOS)
    var patients: [VitalsDataiOS] {
        let hr = manualHeartRate ?? Int(healthKit.latestHeartRate)
        let spo2 = Int(healthKit.latestSpO2)
        guard hr > 0 || spo2 > 0 else { return [] }
        return [VitalsDataiOS(
            patientId: Auth.auth().currentUser?.uid ?? "",
            patientName: userName,
            heartRate: hr,
            glucose: libreLastGlucose,
            spo2: spo2,
            timestamp: Date().timeIntervalSince1970
        )]
    }

    // MARK: - Vitals history (historial de HR del día)
    var vitalsHistory: [VitalsDataiOS] {
        let history = healthKit.heartRateHistory
        let baseHr = manualHeartRate ?? 0
        
        return history.enumerated().map { i, hr in
            let displayHr = baseHr > 0 && i == history.count - 1 ? baseHr : Int(hr)
            return VitalsDataiOS(
                patientId: Auth.auth().currentUser?.uid ?? "",
                patientName: userName,
                heartRate: displayHr,
                glucose: libreLastGlucose,
                spo2: Int(healthKit.latestSpO2),
                timestamp: Date().timeIntervalSince1970 - Double((history.count - i) * 60)
            )
        }
    }

    // MARK: - Sleep data (derivado de HealthKit o Manual)
    var sleepData: SleepDataiOS? {
        if let manual = manualSleepData { return manual }
        guard healthKit.latestSleepScore > 0 || healthKit.latestSleepHours > 0 else { return nil }
        return SleepDataiOS(
            score: healthKit.latestSleepScore,
            hours: healthKit.latestSleepHours,
            estado: healthKit.sleepEstado
        )
    }

    // MARK: - Alert logic
    private func updateAlertMessage() {
        let hr = Int(healthKit.latestHeartRate)
        let spo2 = Int(healthKit.latestSpO2)
        if hr > 120 {
            alertMessage = "Frecuencia cardíaca elevada: \(hr) bpm"
        } else if spo2 > 0 && spo2 < 90 {
            alertMessage = "SpO₂ crítico: \(spo2)%"
        } else {
            alertMessage = nil
        }
    }

    // MARK: - Sync Apple Watch / HealthKit vitals to Firebase
    // Se llama cada vez que HealthKit reporta nuevos datos.
    // Escribe en vitals/current y en patients/{uid}/history (throttled 60s).

    private var lastHKFirebaseSave: Date = .distantPast

    func syncVitalsToFirebase() {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        let hr = Int(healthKit.latestHeartRate)
        let spo2 = Int(healthKit.latestSpO2)
        guard hr > 0 else { return }   // necesitamos al menos FC

        let now = Date()
        let ts  = now.timeIntervalSince1970 * 1000

        let data: [String: Any] = [
            "heartRate": hr,
            "spo2":      spo2,
            "glucose":   libreLastGlucose,
            "timestamp": ts,
            "source":    "apple_watch_healthkit"
        ]

        // Actualizar vitales en vivo
        dbRef.child("vitals/current/\(uid)").setValue(data)

        // Guardar en history máximo cada 60 segundos
        guard now.timeIntervalSince(lastHKFirebaseSave) >= 60 else { return }
        lastHKFirebaseSave = now
        dbRef.child("patients/\(uid)/history/\(Int(ts))").setValue(data)
    }

    // MARK: - Manual Data Entry
    func setManualData(hr: Int?, sleepHours: Double?, sleepScore: Int?) {
        if let hr = hr {
            self.manualHeartRate = hr
        }
        if let hours = sleepHours, let score = sleepScore {
            let estado = score >= 85 ? "Óptimo" : score >= 60 ? "Regular" : "Bajo"
            self.manualSleepData = SleepDataiOS(score: score, hours: hours, estado: estado)
        }
        objectWillChange.send()
    }

    func clearManualHeartRate() {
        self.manualHeartRate = nil
        objectWillChange.send()
    }

    func clearManualSleep() {
        self.manualSleepData = nil
        objectWillChange.send()
    }

    // MARK: - Dynamic Pager Actions
    func addNextMetric() {
        // Encontrar la siguiente métrica del orden que no esté visible
        if let next = fullMetricOrder.first(where: { !visibleMetrics.contains($0) }) {
            visibleMetrics.append(next)
            objectWillChange.send()
        }
    }

    func removeMetric(_ type: MetricCardType) {
        // Solo permitir borrar si hay más de 1
        guard visibleMetrics.count > 1 else { return }
        visibleMetrics.removeAll(where: { $0 == type })
        objectWillChange.send()
    }
}

#endif
