// VitalSignsServiceWatch.swift - Transcripción de VitalSignsService.kt
// Servicio principal que coordina HR, SpO2, Firebase y SOS
// watchOS no usa "Services" como Android, pero esta clase actúa como coordinador

import Foundation
import WatchKit
import CoreLocation
import OSLog

private let logger = Logger(subsystem: "mx.ita.vitalsense.ios.watchkitapp", category: "VitalSigns")

class VitalSignsServiceWatch: NSObject {
    
    static let shared = VitalSignsServiceWatch()
    
    // Managers
    private let healthKitManager = HealthKitWatchManager.shared
    private let firebaseService = WatchFirebaseService.shared
    private let connectivityManager = WatchConnectivityManager.shared
    
    // Detectores de movimiento
    private var shakeDetector: ShakeDetectorWatch?
    private var fallDetector:  FallDetectorWatch?
    
    // Location manager para SOS
    private let locationManager = CLLocationManager()
    
    // State
    private(set) var currentHeartRate: Double = 0.0
    private(set) var currentSpO2: Int = 0
    private(set) var activeSosId: String? = nil
    
    private var userId: String {
        return UserDefaults.standard.string(forKey: "biometric_user_id") ?? "global"
    }
    
    private override init() {
        super.init()
    }
    
    // MARK: - Public API
    
    func startMonitoring() {
        logger.info("Starting monitoring for user: \(self.userId)")
        
        // Setup observers and location (idempotent)
        setupNotificationObservers()
        setupLocationManager()
        
        // Iniciar HealthKit
        healthKitManager.requestAuthAndStart()
        
        // Shake detector (SOS manual — 3 agitadas)
        if shakeDetector == nil {
            shakeDetector = ShakeDetectorWatch { [weak self] in
                self?.triggerSosAlert(source: "shake")
            }
        }
        shakeDetector?.start()

        // Fall detector (caída detectada por acelerómetro)
        if fallDetector == nil {
            fallDetector = FallDetectorWatch { [weak self] in
                self?.triggerSosAlert(source: "fall")
            }
        }
        fallDetector?.start()
        
        // Solicitar permisos de ubicación
        requestLocationPermissions()
        
        logger.info("Monitoring started")
    }
    
    func stopMonitoring() {
        shakeDetector?.stop()
        fallDetector?.stop()
        healthKitManager.stopSpO2Monitoring()
        logger.info("Monitoring stopped")
    }
    
    // MARK: - SOS Alert (equivalente a triggerSosAlert en Android)
    
    func triggerSosAlert(source: String = "manual") {
        logger.warning("SOS triggered — source: \(source)")

        let sosId = "sos_\(Int(Date().timeIntervalSince1970 * 1000))"
        activeSosId = sosId

        // Vibración de alerta
        WKInterfaceDevice.current().play(.notification)

        // Mostrar UI de SOS en el Watch
        DispatchQueue.main.async {
            NotificationCenter.default.post(
                name: .sosTriggered,
                object: nil,
                userInfo: ["sosId": sosId, "userId": self.userId, "source": source]
            )
        }

        // Enviar a Firebase con ubicación y fuente
        sendSosWithBestLocation(remoteUid: userId, sosId: sosId, source: source)
    }
    
    // MARK: - Location & SOS Push (equivalente a sendSosWithBestLocation)
    
    private func sendSosWithBestLocation(remoteUid: String, sosId: String, source: String = "manual") {
        // Intentar obtener ubicación actual
        locationManager.requestLocation()
        
        // Timer: si no hay ubicación en 5 segundos, enviar sin ubicación
        DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) { [weak self] in
            guard let self = self else { return }
            
            let location = self.locationManager.location
            let lat = location?.coordinate.latitude ?? 0.0
            let lng = location?.coordinate.longitude ?? 0.0
            
            let locationSource = lat == 0.0 ? "\(source)_no_location" : "\(source)_gps"
            self.pushSosAlert(
                remoteUid: remoteUid,
                sosId: sosId,
                lat: lat,
                lng: lng,
                source: locationSource
            )
        }
    }
    
    private func pushSosAlert(remoteUid: String, sosId: String, lat: Double, lng: Double, source: String) {
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        
        let alertData: [String: Any] = [
            "timestamp": timestamp,
            "type": "SOS",
            "lat": lat,
            "lng": lng,
            "status": "active",
            "read": false,
            "source": source,
            "heartRate": currentHeartRate,
            "spo2": currentSpO2
        ]
        
        firebaseService.pushSosAlert(userId: remoteUid, sosId: sosId, data: alertData)
        
        logger.info("SOS alert pushed: \(sosId)")
    }
    
    // MARK: - Resolve SOS (equivalente a resolveSosFromNotification)
    
    func resolveSos(sosId: String, userId: String) {
        guard !sosId.isEmpty && !userId.isEmpty else { return }
        
        let resolvedData: [String: Any] = [
            "status": "resolved",
            "resolvedBy": "wear_cancel_action",
            "resolvedAt": Int64(Date().timeIntervalSince1970 * 1000)
        ]
        
        firebaseService.updateSosAlert(userId: userId, sosId: sosId, updates: resolvedData)
        
        activeSosId = nil
        
        DispatchQueue.main.async {
            NotificationCenter.default.post(name: .sosResolved, object: nil)
        }
        
        logger.info("SOS resolved: \(sosId)")
    }
    
    // MARK: - Notification Observers
    
    private func setupNotificationObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleHeartRateUpdate(_:)),
            name: .heartRateUpdated,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleSpO2Update(_:)),
            name: .spo2Updated,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleUserIdReceived),
            name: .userIdReceived,
            object: nil
        )
    }
    
    @objc private func handleHeartRateUpdate(_ notification: Notification) {
        if let hr = notification.userInfo?["bpm"] as? Int {
            currentHeartRate = Double(hr)
            
            // Sincronizar con Firebase cada actualización
            if userId != "global" {
                syncVitalsToFirebase()
            }
        }
    }
    
    @objc private func handleSpO2Update(_ notification: Notification) {
        if let spo2 = notification.userInfo?["spo2"] as? Int {
            currentSpO2 = spo2
            syncVitalsToFirebase()
        }
    }
    
    @objc private func handleUserIdReceived() {
        logger.info("UserId received, restarting monitoring")
        startMonitoring()
    }
    
    // MARK: - Firebase Sync
    
    private func syncVitalsToFirebase() {
        firebaseService.syncVitals(hr: Int(currentHeartRate), spo2: currentSpO2)
    }
    
    // MARK: - Location Manager Setup
    
    private func setupLocationManager() {
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
    }
    
    private func requestLocationPermissions() {
        let status = locationManager.authorizationStatus
        
        if status == .notDetermined {
            locationManager.requestWhenInUseAuthorization()
        }
    }
}

// MARK: - CLLocationManagerDelegate

extension VitalSignsServiceWatch: CLLocationManagerDelegate {
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let coord = locations.last?.coordinate {
            logger.debug("Location updated: \(coord.latitude), \(coord.longitude)")
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        logger.error("Location error: \(error.localizedDescription)")
    }

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        logger.info("Location authorization: \(status.rawValue)")
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let sosTriggered = Notification.Name("biometric.sosTriggered")
    static let sosResolved = Notification.Name("biometric.sosResolved")
}
