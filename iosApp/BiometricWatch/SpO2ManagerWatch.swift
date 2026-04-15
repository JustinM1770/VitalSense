// SpO2ManagerWatch.swift - Transcripción de SpO2Manager.kt
// NOTA: Apple Watch SE 2 NO tiene sensor de SpO2
// Este archivo está preparado para watches Series 6+ que sí lo tienen

import Foundation
import HealthKit
import OSLog

private let logger = Logger(subsystem: "mx.ita.vitalsense.ios.watchkitapp", category: "SpO2Manager")

class SpO2ManagerWatch {
    
    private let healthStore = HKHealthStore()
    private var query: HKObserverQuery?
    private var anchoredQuery: HKAnchoredObjectQuery?
    
    // MARK: - Public API
    
    /// Verifica si el dispositivo soporta SpO2
    /// Apple Watch Series 6+ tienen sensor de oxígeno en sangre
    func isSupported() -> Bool {
        guard let spo2Type = HKObjectType.quantityType(forIdentifier: .oxygenSaturation) else {
            return false
        }
        
        let authStatus = healthStore.authorizationStatus(for: spo2Type)
        
        // Si el tipo no está disponible, el hardware no lo soporta
        // Series 2, 3, SE no tienen sensor SpO2
        return authStatus != .notDetermined || HKHealthStore.isHealthDataAvailable()
    }
    
    /// Solicita permisos de HealthKit para SpO2
    func requestAuthorization(completion: @escaping (Bool, Error?) -> Void) {
        guard let spo2Type = HKObjectType.quantityType(forIdentifier: .oxygenSaturation) else {
            completion(false, NSError(domain: "SpO2Manager", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "SpO2 type not available on this device"
            ]))
            return
        }
        
        healthStore.requestAuthorization(toShare: [], read: [spo2Type]) { granted, error in
            DispatchQueue.main.async {
                completion(granted, error)
            }
        }
    }
    
    /// Observa lecturas de SpO2 en tiempo real
    /// - Parameter callback: Retorna valor de SpO2 (0-100%)
    func observeSpO2(callback: @escaping (Int) -> Void) {
        guard let spo2Type = HKQuantityType.quantityType(forIdentifier: .oxygenSaturation) else {
            logger.warning("SpO2 type not available")
            return
        }
        
        // Query anclada para obtener actualizaciones
        let predicate = HKQuery.predicateForSamples(
            withStart: Date().addingTimeInterval(-60), // Últimos 60 segundos
            end: nil,
            options: .strictStartDate
        )
        
        anchoredQuery = HKAnchoredObjectQuery(
            type: spo2Type,
            predicate: predicate,
            anchor: nil,
            limit: HKObjectQueryNoLimit
        ) { [weak self] query, samples, deletedObjects, anchor, error in
            
            if let error = error {
                logger.error("Query error: \(error.localizedDescription)")
                return
            }

            self?.processSamples(samples, callback: callback)
        }

        // Handler para actualizaciones continuas
        anchoredQuery?.updateHandler = { [weak self] query, samples, deletedObjects, anchor, error in
            if let error = error {
                logger.error("Update error: \(error.localizedDescription)")
                return
            }
            
            self?.processSamples(samples, callback: callback)
        }
        
        healthStore.execute(anchoredQuery!)
        logger.info("Started observing SpO2")
    }

    func stopObserving() {
        if let query = anchoredQuery {
            healthStore.stop(query)
            anchoredQuery = nil
        }
        logger.info("Stopped observing SpO2")
    }
    
    // MARK: - Private
    
    private func processSamples(_ samples: [HKSample]?, callback: @escaping (Int) -> Void) {
        guard let quantitySamples = samples as? [HKQuantitySample],
              let latest = quantitySamples.last else {
            return
        }
        
        // SpO2 en HealthKit está normalizado como porcentaje (0.0 - 1.0)
        let spo2Value = latest.quantity.doubleValue(for: HKUnit.percent())
        
        // Normalizar igual que Android:
        // Si <= 1.0 → multiplicar por 100
        // Si > 1.0 → usar como está
        let normalizedValue: Int
        if spo2Value <= 1.0 {
            normalizedValue = Int(spo2Value * 100)
        } else {
            normalizedValue = Int(spo2Value)
        }
        
        // Validar rango 1-100%
        let finalValue = max(1, min(100, normalizedValue))
        
        logger.debug("SpO2 received: \(finalValue)%")
        
        DispatchQueue.main.async {
            callback(finalValue)
        }
    }
}
