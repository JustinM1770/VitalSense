// HealthKitWatchManager.swift — Transcripción mejorada de HeartRateManager.kt + SpO2Manager.kt
// Callback-based HealthKit manager compatible con watchOS 5+
// Publica actualizaciones vía NotificationCenter
// SpO2 es condicional según modelo de watch

import Foundation
import HealthKit
import OSLog

private let logger = Logger(subsystem: "mx.ita.vitalsense.ios.watchkitapp", category: "HealthKit")

class HealthKitWatchManager: NSObject {

    static let shared = HealthKitWatchManager()

    private let store = HKHealthStore()
    private var workoutSession: HKWorkoutSession?
    private var builder: HKLiveWorkoutBuilder?
    private var hrAnchoredQuery: HKAnchoredObjectQuery?
    
    // SpO2 Manager (condicional)
    private let spo2Manager = SpO2ManagerWatch()

    // Current values — readable by InterfaceController
    private(set) var heartRate: Int = 0
    private(set) var spo2: Int = 0
    private(set) var hasSpo2Sensor: Bool = false

    private override init() {
        super.init()
        // Detectar si el watch tiene sensor SpO2
        hasSpo2Sensor = spo2Manager.isSupported()
        logger.info("SpO2 sensor available: \(self.hasSpo2Sensor)")
    }

    // MARK: - Public entry point

    func requestAuthAndStart() {
        guard HKHealthStore.isHealthDataAvailable() else { return }

        // Solicitar permisos para HR
        guard let hrType = HKObjectType.quantityType(forIdentifier: .heartRate) else { return }
        var readTypes: Set<HKObjectType> = [hrType]
        
        // Agregar SpO2 si está disponible
        if hasSpo2Sensor, let spo2Type = HKObjectType.quantityType(forIdentifier: .oxygenSaturation) {
            readTypes.insert(spo2Type)
        }

        store.requestAuthorization(toShare: [], read: readTypes) { [weak self] granted, error in
            guard granted else {
                if let error = error {
                    logger.error("Auth denied: \(error.localizedDescription)")
                }
                return
            }
            DispatchQueue.main.async {
                self?.startLiveHR()
                
                // Iniciar SpO2 si está disponible
                if self?.hasSpo2Sensor == true {
                    self?.startLiveSpO2()
                }
            }
        }
    }

    // MARK: - Live HR via HKWorkoutSession + HKLiveWorkoutBuilder

    private func startLiveHR() {
        let config = HKWorkoutConfiguration()
        config.activityType = .other
        config.locationType  = .indoor

        do {
            workoutSession = try HKWorkoutSession(healthStore: store, configuration: config)
            builder = workoutSession?.associatedWorkoutBuilder()
            builder?.dataSource = HKLiveWorkoutDataSource(
                healthStore: store,
                workoutConfiguration: config
            )

            workoutSession?.delegate = self
            builder?.delegate = self

            workoutSession?.startActivity(with: Date())
            builder?.beginCollection(withStart: Date()) { success, error in
                if let error = error {
                    logger.error("beginCollection error: \(error.localizedDescription)")
                } else {
                    logger.info("Live HR collection started")
                }
            }
        } catch {
            logger.error("Failed to start workout session: \(error.localizedDescription)")
            startAnchoredQuery()
        }
    }
    
    // MARK: - Live SpO2
    
    private func startLiveSpO2() {
        spo2Manager.observeSpO2 { [weak self] spo2Value in
            self?.spo2 = spo2Value
            NotificationCenter.default.post(
                name: .spo2Updated,
                object: nil,
                userInfo: ["spo2": spo2Value]
            )
        }
        logger.info("SpO2 monitoring started")
    }
    
    func stopSpO2Monitoring() {
        spo2Manager.stopObserving()
    }

    // MARK: - Fallback: HKAnchoredObjectQuery (simulator / no workout session)

    private func startAnchoredQuery() {
        guard let hrType = HKObjectType.quantityType(forIdentifier: .heartRate) else { return }

        let query = HKAnchoredObjectQuery(
            type: hrType,
            predicate: nil,
            anchor: nil,
            limit: HKObjectQueryNoLimit
        ) { [weak self] _, samples, _, _, _ in
            self?.processHRSamples(samples)
        }
        query.updateHandler = { [weak self] _, samples, _, _, _ in
            self?.processHRSamples(samples)
        }
        store.execute(query)
        self.hrAnchoredQuery = query
    }

    // MARK: - Sample processing

    private func processHRSamples(_ samples: [HKSample]?) {
        guard let samples = samples as? [HKQuantitySample], let latest = samples.last else { return }
        let unit = HKUnit(from: "count/min")
        let bpm  = Int(latest.quantity.doubleValue(for: unit))
        guard bpm > 0 else { return }

        DispatchQueue.main.async { [weak self] in
            self?.heartRate = bpm
            NotificationCenter.default.post(
                name: .heartRateUpdated,
                object: nil,
                userInfo: ["bpm": bpm]
            )
        }
    }

    private func notifyHeartRate(_ bpm: Int) {
        guard bpm > 0 else { return }
        DispatchQueue.main.async { [weak self] in
            self?.heartRate = bpm
            NotificationCenter.default.post(
                name: .heartRateUpdated,
                object: nil,
                userInfo: ["bpm": bpm]
            )
        }
    }
}

// MARK: - HKWorkoutSessionDelegate

extension HealthKitWatchManager: HKWorkoutSessionDelegate {

    func workoutSession(
        _ session: HKWorkoutSession,
        didChangeTo toState: HKWorkoutSessionState,
        from fromState: HKWorkoutSessionState,
        date: Date
    ) {}

    func workoutSession(_ session: HKWorkoutSession, didFailWithError error: Error) {
        logger.error("WorkoutSession error: \(error.localizedDescription). Falling back to anchored query.")
        startAnchoredQuery()
    }
}

// MARK: - HKLiveWorkoutBuilderDelegate

extension HealthKitWatchManager: HKLiveWorkoutBuilderDelegate {

    func workoutBuilderDidCollectEvent(_ workoutBuilder: HKLiveWorkoutBuilder) {}

    func workoutBuilder(
        _ builder: HKLiveWorkoutBuilder,
        didCollectDataOf collectedTypes: Set<HKSampleType>
    ) {
        for type in collectedTypes {
            guard let qType = type as? HKQuantityType else { continue }
            guard qType == HKQuantityType.quantityType(forIdentifier: .heartRate) else { continue }

            let unit = HKUnit(from: "count/min")
            if let bpm = builder.statistics(for: qType)?.mostRecentQuantity()?.doubleValue(for: unit) {
                notifyHeartRate(Int(bpm))
            }
        }
    }
}
