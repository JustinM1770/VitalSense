#if os(iOS)
import Foundation
import HealthKit
import Combine

/// Servicio de HealthKit para leer datos reales del Apple Watch.
/// Lee: frecuencia cardíaca, SpO₂, sueño, calorías activas y pasos.
@MainActor
class HealthKitService: ObservableObject {
    static let shared = HealthKitService()

    private let store = HKHealthStore()
    private var anchorQueries: [HKAnchoredObjectQuery] = []

    // MARK: - Published data (datos reales del Apple Watch)
    @Published var latestHeartRate: Double = 0
    @Published var latestSpO2: Double = 0
    @Published var latestSleepHours: Double = 0
    @Published var latestSleepScore: Int = 0       // calculado de eficiencia
    @Published var activeCalories: Double = 0
    @Published var steps: Int = 0

    // Historial del día
    @Published var heartRateHistory: [Double] = []
    @Published var spo2History: [Double] = []
    @Published var isAuthorized = false
    @Published var authorizationError: String?

    // Apple Watch conectado
    @Published var isWatchAvailable: Bool = false

    // MARK: - Types
    private let heartRateType      = HKQuantityType.quantityType(forIdentifier: .heartRate)!
    private let spo2Type           = HKQuantityType.quantityType(forIdentifier: .oxygenSaturation)!
    private let activeEnergyType   = HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned)!
    private let stepsType          = HKQuantityType.quantityType(forIdentifier: .stepCount)!
    private let sleepType          = HKCategoryType.categoryType(forIdentifier: .sleepAnalysis)!

    private let readTypes: Set<HKObjectType> = [
        HKQuantityType.quantityType(forIdentifier: .heartRate)!,
        HKQuantityType.quantityType(forIdentifier: .oxygenSaturation)!,
        HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned)!,
        HKQuantityType.quantityType(forIdentifier: .stepCount)!,
        HKCategoryType.categoryType(forIdentifier: .sleepAnalysis)!,
    ]

    var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    // MARK: - Authorization
    func requestAuthorization() async {
        guard isAvailable else {
            authorizationError = "HealthKit no está disponible en este dispositivo"
            return
        }

        do {
            try await store.requestAuthorization(toShare: [], read: readTypes)
            isAuthorized = true
            isWatchAvailable = HKHealthStore.isHealthDataAvailable()
            startObservingAll()
        } catch {
            authorizationError = error.localizedDescription
        }
    }

    // MARK: - Start All Observers
    func startObservingAll() {
        fetchTodayHeartRate()
        fetchTodaySpO2()
        fetchTodaySleep()
        fetchTodayCalories()
        fetchTodaySteps()

        // Observers en tiempo real (se actualizan cuando llegan nuevos datos del Watch)
        observeHeartRate()
        observeSpO2()
    }

    func stopObserving() {
        anchorQueries.forEach { store.stop($0) }
        anchorQueries.removeAll()
    }

    // MARK: - Heart Rate (tiempo real)
    private func fetchTodayHeartRate() {
        let start = Calendar.current.startOfDay(for: Date())
        let predicate = HKQuery.predicateForSamples(withStart: start, end: Date(), options: .strictStartDate)
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)

        let query = HKSampleQuery(sampleType: heartRateType, predicate: predicate, limit: 100, sortDescriptors: [sort]) { [weak self] _, samples, _ in
            guard let self, let samples = samples as? [HKQuantitySample] else { return }
            let unit = HKUnit.count().unitDivided(by: .minute())
            let values = samples.map { $0.quantity.doubleValue(for: unit) }

            Task { @MainActor in
                self.heartRateHistory = values.reversed()   // cronológico
                self.latestHeartRate = values.first ?? 0
            }
        }
        store.execute(query)
    }

    private func observeHeartRate() {
        let start = Calendar.current.startOfDay(for: Date())
        let predicate = HKQuery.predicateForSamples(withStart: start, end: nil, options: .strictStartDate)
        let unit = HKUnit.count().unitDivided(by: .minute())

        let handler: (HKAnchoredObjectQuery, [HKSample]?, [HKDeletedObject]?, HKQueryAnchor?, (any Error)?) -> Void = { [weak self] _, samples, _, _, _ in
            guard let quantitySamples = samples as? [HKQuantitySample], !quantitySamples.isEmpty else { return }
            let latest = quantitySamples.last!.quantity.doubleValue(for: unit)
            let newValues = quantitySamples.map { $0.quantity.doubleValue(for: unit) }
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.latestHeartRate = latest
                self.heartRateHistory.append(contentsOf: newValues)
                if self.heartRateHistory.count > 100 {
                    self.heartRateHistory = Array(self.heartRateHistory.suffix(100))
                }
            }
        }

        let query = HKAnchoredObjectQuery(type: heartRateType, predicate: predicate, anchor: nil, limit: HKObjectQueryNoLimit, resultsHandler: handler)
        query.updateHandler = handler
        store.execute(query)
        anchorQueries.append(query)
    }

    // MARK: - SpO2
    private func fetchTodaySpO2() {
        let start = Calendar.current.startOfDay(for: Date())
        let predicate = HKQuery.predicateForSamples(withStart: start, end: Date(), options: .strictStartDate)
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)

        let query = HKSampleQuery(sampleType: spo2Type, predicate: predicate, limit: 50, sortDescriptors: [sort]) { [weak self] _, samples, _ in
            guard let self, let samples = samples as? [HKQuantitySample] else { return }
            let unit = HKUnit.percent()
            let values = samples.map { $0.quantity.doubleValue(for: unit) * 100 }

            Task { @MainActor in
                self.spo2History = values.reversed()
                self.latestSpO2 = values.first ?? 0
            }
        }
        store.execute(query)
    }

    private func observeSpO2() {
        let start = Calendar.current.startOfDay(for: Date())
        let predicate = HKQuery.predicateForSamples(withStart: start, end: nil, options: .strictStartDate)
        let unit = HKUnit.percent()

        let handler: (HKAnchoredObjectQuery, [HKSample]?, [HKDeletedObject]?, HKQueryAnchor?, (any Error)?) -> Void = { [weak self] _, samples, _, _, _ in
            guard let quantitySamples = samples as? [HKQuantitySample], !quantitySamples.isEmpty else { return }
            let latest = quantitySamples.last!.quantity.doubleValue(for: unit) * 100
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.latestSpO2 = latest
                self.spo2History.append(latest)
            }
        }

        let query = HKAnchoredObjectQuery(type: spo2Type, predicate: predicate, anchor: nil, limit: HKObjectQueryNoLimit, resultsHandler: handler)
        query.updateHandler = handler
        store.execute(query)
        anchorQueries.append(query)
    }

    // MARK: - Sleep (última noche)
    private func fetchTodaySleep() {
        // Buscar sueño de las últimas 24 horas
        let end = Date()
        let start = Calendar.current.date(byAdding: .hour, value: -24, to: end)!
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)

        let query = HKSampleQuery(sampleType: sleepType, predicate: predicate, limit: 100, sortDescriptors: [sort]) { [weak self] _, samples, _ in
            guard let self, let samples = samples as? [HKCategorySample] else { return }

            // Filtrar solo "asleep" (no "inBed")
            let asleepSamples = samples.filter { sample in
                if #available(iOS 16.0, *) {
                    return sample.value == HKCategoryValueSleepAnalysis.asleepCore.rawValue ||
                           sample.value == HKCategoryValueSleepAnalysis.asleepDeep.rawValue ||
                           sample.value == HKCategoryValueSleepAnalysis.asleepREM.rawValue ||
                           sample.value == HKCategoryValueSleepAnalysis.asleepUnspecified.rawValue
                } else {
                    return sample.value == HKCategoryValueSleepAnalysis.asleep.rawValue
                }
            }

            let totalSeconds = asleepSamples.reduce(0.0) { total, sample in
                total + sample.endDate.timeIntervalSince(sample.startDate)
            }
            let hours = totalSeconds / 3600.0

            // Calcular score basado en duración (7-9h ideal)
            let score: Int
            if hours >= 7 && hours <= 9 {
                score = min(95, Int(80 + (hours - 7) * 7.5))
            } else if hours >= 6 {
                score = Int(60 + (hours - 6) * 20)
            } else if hours > 0 {
                score = max(20, Int(hours / 6 * 60))
            } else {
                score = 0
            }

            Task { @MainActor in
                self.latestSleepHours = hours
                self.latestSleepScore = score
            }
        }
        store.execute(query)
    }

    // MARK: - Active Calories
    private func fetchTodayCalories() {
        let start = Calendar.current.startOfDay(for: Date())
        let predicate = HKQuery.predicateForSamples(withStart: start, end: Date(), options: .strictStartDate)

        let query = HKStatisticsQuery(quantityType: activeEnergyType, quantitySamplePredicate: predicate, options: .cumulativeSum) { [weak self] _, result, _ in
            guard let self, let sum = result?.sumQuantity() else { return }
            let cal = sum.doubleValue(for: .kilocalorie())

            Task { @MainActor in
                self.activeCalories = cal
            }
        }
        store.execute(query)
    }

    // MARK: - Steps
    private func fetchTodaySteps() {
        let start = Calendar.current.startOfDay(for: Date())
        let predicate = HKQuery.predicateForSamples(withStart: start, end: Date(), options: .strictStartDate)

        let query = HKStatisticsQuery(quantityType: stepsType, quantitySamplePredicate: predicate, options: .cumulativeSum) { [weak self] _, result, _ in
            guard let self, let sum = result?.sumQuantity() else { return }
            let count = Int(sum.doubleValue(for: .count()))

            Task { @MainActor in
                self.steps = count
            }
        }
        store.execute(query)
    }

    // MARK: - Convenience
    var avgHeartRate: Int {
        guard !heartRateHistory.isEmpty else { return 0 }
        return Int(heartRateHistory.reduce(0, +) / Double(heartRateHistory.count))
    }

    var avgSpO2: Int {
        guard !spo2History.isEmpty else { return 0 }
        return Int(spo2History.reduce(0, +) / Double(spo2History.count))
    }

    var sleepEstado: String {
        if latestSleepScore >= 85 { return "Óptimo" }
        if latestSleepScore >= 60 { return "Regular" }
        if latestSleepScore > 0  { return "Bajo" }
        return "Sin datos"
    }
}

#endif
