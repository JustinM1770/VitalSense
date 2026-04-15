// InterfaceController.swift — Main WKInterfaceController for watchOS
// Actualizado con integración completa de VitalSignsServiceWatch
// Equivalente a MonitoringScreen en Android WearApp.kt

import WatchKit
import Foundation

class InterfaceController: WKInterfaceController {

    // MARK: - Outlets (connected in Interface.storyboard)
    @IBOutlet weak var heartRateLabel: WKInterfaceLabel!
    @IBOutlet weak var statusLabel:    WKInterfaceLabel!
    @IBOutlet weak var sosButton:      WKInterfaceButton!

    // MARK: - State
    private var currentHeartRate: Int = 0
    private var currentSpO2: Int = 0
    private var sosConfirming         = false
    private var sosSent               = false
    private var sosResetTimer:   Timer?
    private var confirmResetTimer: Timer?
    private var emergencyTimer:  Timer?
    private var countdownTimer:  Timer?
    private var clockTimer: Timer?

    // Emergency state
    private var hasActiveEmergency  = false
    private var emergencyPin        = ""
    private var emergencyAnomalyType = ""
    private var emergencyTokenId = ""
    private var emergencyExpiresAt: Int64 = 0

    // Services
    private let vitalSignsService = VitalSignsServiceWatch.shared
    private let firebase     = WatchFirebaseService.shared
    private let healthKit    = HealthKitWatchManager.shared
    private let pairingManager = PairingManager.shared

    // MARK: - Lifecycle

    override func awake(withContext context: Any?) {
        super.awake(withContext: context)
        setupInitialUI()
        checkPairingStatus()
    }

    override func willActivate() {
        super.willActivate()
        registerNotifications()
        
        if pairingManager.isPaired {
            startServices()
        }
        
        refreshUI()
        startClockTimer()
    }

    override func didDeactivate() {
        super.didDeactivate()
        NotificationCenter.default.removeObserver(self)
        emergencyTimer?.invalidate()
        countdownTimer?.invalidate()
        clockTimer?.invalidate()
    }

    // MARK: - Setup

    private func setupInitialUI() {
        heartRateLabel.setText("--")
        statusLabel.setText("Iniciando...")
        sosButton.setTitle("SOS")
        sosButton.setEnabled(false)
    }
    
    private func checkPairingStatus() {
        if !pairingManager.isPaired {
            // Mostrar pantalla de emparejamiento
            presentController(withName: "PairingInterface", context: nil)
        }
    }

    private func registerNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleHeartRateUpdate(_:)),
            name: .heartRateUpdated,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleUserIdReceived),
            name: .userIdReceived,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleEmergencyUpdate(_:)),
            name: .emergencyStateUpdated,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleSosTriggered(_:)),
            name: .sosTriggered,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleSosResolved),
            name: .sosResolved,
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
            selector: #selector(handlePairingSuccess),
            name: .pairingSuccessful,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleUnpaired),
            name: .unpaired,
            object: nil
        )
    }

    private func startServices() {
        let userId = UserDefaults.standard.string(forKey: "biometric_user_id") ?? ""
        if userId.isEmpty {
            statusLabel.setText("Esperando emparejamiento...")
            sosButton.setEnabled(false)
        } else {
            // Start VitalSignsService (coordina todo)
            vitalSignsService.startMonitoring()
            
            // Start Firebase polling para emergencias
            firebase.startPolling()
            
            sosButton.setEnabled(true)
            statusLabel.setText("Listo")
        }
    }
    
    private func startClockTimer() {
        clockTimer?.invalidate()
        clockTimer = Timer.scheduledTimer(withTimeInterval: 60.0, repeats: true) { [weak self] _ in
            self?.updateClock()
        }
        updateClock() // Actualizar inmediatamente
    }
    
    private func updateClock() {
        // Mostrar hora en esquina superior derecha (simulado en statusLabel)
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        let timeString = formatter.string(from: Date())
        // En Android esto va en esquina, aquí lo omitimos o usamos otro label
    }

    private func refreshUI() {
        let userId = UserDefaults.standard.string(forKey: "biometric_user_id") ?? ""
        guard !userId.isEmpty else { return }

        if hasActiveEmergency {
            showEmergencyUI()
        } else {
            showNormalUI()
        }
    }

    // MARK: - Notification Handlers

    @objc private func handleHeartRateUpdate(_ notification: Notification) {
        guard let bpm = notification.userInfo?["bpm"] as? Int else { return }
        currentHeartRate = bpm

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if !self.hasActiveEmergency {
                self.heartRateLabel.setText(bpm > 0 ? "\(bpm) bpm" : "-- bpm")
                // Sync to Firebase whenever we get a new reading
                if bpm > 0 {
                    self.firebase.syncVitals(hr: bpm, spo2: 0)
                }
            }
        }
    }

    @objc private func handleUserIdReceived() {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.healthKit.requestAuthAndStart()
            self.firebase.startPolling()
            self.sosButton.setEnabled(true)
            self.showNormalUI()
        }
    }

    @objc private func handleEmergencyUpdate(_ notification: Notification) {
        guard let userInfo = notification.userInfo else { return }
        
        if let pin = userInfo["pin"] as? String,
           let anomalyType = userInfo["anomalyType"] as? String,
           let expiresAt = userInfo["expiresAt"] as? Double {
            
            hasActiveEmergency = true
            emergencyPin = pin
            emergencyAnomalyType = anomalyType
            emergencyExpiresAt = Int64(expiresAt)
            emergencyTokenId = userInfo["tokenId"] as? String ?? ""
            
            DispatchQueue.main.async { [weak self] in
                self?.showEmergencyQR()
            }
        } else {
            hasActiveEmergency = false
            emergencyPin = ""
            emergencyAnomalyType = ""
            emergencyExpiresAt = 0
            
            DispatchQueue.main.async { [weak self] in
                self?.showNormalUI()
            }
        }
    }
    
    @objc private func handleSosTriggered(_ notification: Notification) {
        guard let sosId = notification.userInfo?["sosId"] as? String,
              let userId = notification.userInfo?["userId"] as? String else { return }
        
        DispatchQueue.main.async { [weak self] in
            self?.showSosQR(sosId: sosId, userId: userId)
        }
    }
    
    @objc private func handleSosResolved() {
        DispatchQueue.main.async { [weak self] in
            self?.sosSent = false
            self?.sosConfirming = false
            self?.showNormalUI()
        }
    }
    
    @objc private func handleSpO2Update(_ notification: Notification) {
        guard let spo2 = notification.userInfo?["spo2"] as? Int else { return }
        currentSpO2 = spo2
        
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if !self.hasActiveEmergency && self.healthKit.hasSpo2Sensor {
                self.statusLabel.setText("SpO2: \(spo2)%")
            }
        }
    }
    
    @objc private func handlePairingSuccess() {
        DispatchQueue.main.async { [weak self] in
            self?.startServices()
            self?.refreshUI()
        }
    }

    @objc private func handleUnpaired() {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.vitalSignsService.stopMonitoring()
            self.firebase.stopPolling()
            self.countdownTimer?.invalidate()
            self.emergencyTimer?.invalidate()
            self.presentController(withName: "PairingInterface", context: nil)
        }
    }

    // MARK: - Navigation
    
    private func showSosQR(sosId: String, userId: String) {
        let context: [String: String] = [
            "sosId": sosId,
            "userId": userId
        ]
        pushController(withName: "SosQrInterface", context: context)
    }
    
    private func showEmergencyQR() {
        let context: [String: Any] = [
            "tokenId": emergencyTokenId,
            "pin": emergencyPin,
            "anomalyType": emergencyAnomalyType,
            "expiresAt": Int64(emergencyExpiresAt)
        ]
        presentController(withName: "EmergencyQrInterface", context: context)
    }

    // MARK: - UI Modes

    private func showNormalUI() {
        countdownTimer?.invalidate()
        let bpm = currentHeartRate
        heartRateLabel.setText(bpm > 0 ? "\(bpm) bpm" : "-- bpm")

        let userId = UserDefaults.standard.string(forKey: "biometric_user_id") ?? ""
        if userId.isEmpty {
            statusLabel.setText("Esperando iPhone...")
        } else {
            statusLabel.setText("SpO2: --")   // Series 2 has no SpO2 sensor
        }

        sosButton.setTitle("SOS")
        sosButton.setEnabled(true)
        sosButton.setBackgroundColor(UIColor(red: 0.9, green: 0.15, blue: 0.15, alpha: 1))
    }

    private func showEmergencyUI() {
        // Format PIN with dashes: "1234" -> "1 - 2 - 3 - 4"
        let pinDisplay = emergencyPin.map { String($0) }.joined(separator: " - ")
        heartRateLabel.setText("PIN: \(pinDisplay)")
        statusLabel.setText("EMERGENCIA: \(emergencyAnomalyType)")
        sosButton.setTitle("ACTIVA")
        sosButton.setEnabled(false)
        sosButton.setBackgroundColor(UIColor(red: 0.72, green: 0.06, blue: 0.06, alpha: 1))

        // Start countdown
        countdownTimer?.invalidate()
        updateCountdownLabel()
        countdownTimer = Timer.scheduledTimer(
            timeInterval: 1.0,
            target: self,
            selector: #selector(updateCountdownLabel),
            userInfo: nil,
            repeats: true
        )
    }

    @objc private func updateCountdownLabel() {
        let nowMs    = Date().timeIntervalSince1970 * 1000
        let diffSecs = Int((Double(emergencyExpiresAt) - nowMs) / 1000)
        let remaining = max(0, diffSecs)
        let m = remaining / 60
        let s = remaining % 60
        let timeStr = String(format: "%02d:%02d", m, s)
        statusLabel.setText("EMERGENCIA \(timeStr)")

        if remaining == 0 {
            countdownTimer?.invalidate()
        }
    }

    // MARK: - SOS Action

    @IBAction func sosTapped() {
        guard !hasActiveEmergency else { return }

        if sosSent { return }

        if sosConfirming {
            // Second tap — fire SOS
            confirmResetTimer?.invalidate()
            sosConfirming = false
            sosSent       = true

            WKInterfaceDevice.current().play(.notification)
            firebase.triggerSOS(hr: currentHeartRate)

            sosButton.setTitle("Alerta enviada")
            sosButton.setEnabled(false)
            sosButton.setBackgroundColor(UIColor(red: 0.2, green: 0.78, blue: 0.35, alpha: 1))

            sosResetTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: false) { [weak self] _ in
                DispatchQueue.main.async {
                    self?.sosSent = false
                    self?.showNormalUI()
                }
            }
        } else {
            // First tap — enter confirm state
            sosConfirming = true
            WKInterfaceDevice.current().play(.click)
            sosButton.setTitle("Toca de nuevo")
            sosButton.setBackgroundColor(UIColor(red: 1.0, green: 0.6, blue: 0.0, alpha: 1))

            confirmResetTimer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: false) { [weak self] _ in
                DispatchQueue.main.async {
                    guard self?.sosSent == false else { return }
                    self?.sosConfirming = false
                    self?.showNormalUI()
                }
            }
        }
    }
}
