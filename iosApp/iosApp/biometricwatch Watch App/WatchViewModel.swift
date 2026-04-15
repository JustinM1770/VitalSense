// WatchViewModel.swift — ObservableObject que conecta los servicios existentes con SwiftUI

import SwiftUI
import Combine
import WatchKit

class WatchViewModel: ObservableObject {
    @Published var heartRate: Int = 0
    @Published var isPaired: Bool = PairingManager.shared.isPaired
    @Published var hasActiveEmergency: Bool = false
    @Published var emergencyPin: String = ""
    @Published var emergencyAnomalyType: String = ""
    @Published var emergencyExpiresAt: Double = 0
    @Published var activeSosId: String? = nil
    @Published var activeSosUserId: String? = nil

    private let vitalService = VitalSignsServiceWatch.shared
    private let firebase     = WatchFirebaseService.shared

    init() {
        subscribeNotifications()
        if isPaired {
            startServices()
        }
    }

    func startServices() {
        vitalService.startMonitoring()
        firebase.startPolling()
    }

    func triggerManualSOS() {
        // Usa el servicio completo: vibración + .sosTriggered notification + Firebase
        vitalService.triggerSosAlert(source: "manual")
    }

    func dismissSOS() {
        if let sosId = activeSosId, let userId = activeSosUserId {
            vitalService.resolveSos(sosId: sosId, userId: userId)
        }
        activeSosId   = nil
        activeSosUserId = nil
    }

    private func subscribeNotifications() {
        let nc = NotificationCenter.default
        nc.addObserver(self, selector: #selector(onHeartRate(_:)),    name: .heartRateUpdated,      object: nil)
        nc.addObserver(self, selector: #selector(onEmergency(_:)),    name: .emergencyStateUpdated, object: nil)
        nc.addObserver(self, selector: #selector(onSos(_:)),          name: .sosTriggered,          object: nil)
        nc.addObserver(self, selector: #selector(onSosResolved),      name: .sosResolved,           object: nil)
        nc.addObserver(self, selector: #selector(onUserIdReceived),   name: .userIdReceived,        object: nil)
        nc.addObserver(self, selector: #selector(onPairingSuccess),   name: .pairingSuccessful,     object: nil)
        nc.addObserver(self, selector: #selector(onUnpaired),         name: .unpaired,              object: nil)
    }

    @objc private func onHeartRate(_ n: Notification) {
        guard let bpm = n.userInfo?["bpm"] as? Int else { return }
        DispatchQueue.main.async { self.heartRate = bpm }
    }

    @objc private func onEmergency(_ n: Notification) {
        guard let info = n.userInfo else { return }
        let active = info["active"] as? Bool ?? false
        DispatchQueue.main.async {
            self.hasActiveEmergency    = active
            self.emergencyPin          = info["pin"]         as? String ?? ""
            self.emergencyAnomalyType  = info["anomalyType"] as? String ?? ""
            self.emergencyExpiresAt    = info["expiresAt"]   as? Double ?? 0
        }
    }

    @objc private func onSos(_ n: Notification) {
        DispatchQueue.main.async {
            self.activeSosId     = n.userInfo?["sosId"]  as? String
            self.activeSosUserId = n.userInfo?["userId"] as? String
        }
    }

    @objc private func onSosResolved() {
        DispatchQueue.main.async {
            self.activeSosId     = nil
            self.activeSosUserId = nil
        }
    }

    @objc private func onUserIdReceived() {
        DispatchQueue.main.async {
            // userId llegó via WatchConnectivity desde el iPhone → emparejamiento exitoso
            self.isPaired = true
            self.startServices()
        }
    }

    @objc private func onPairingSuccess() {
        DispatchQueue.main.async {
            self.isPaired = true
            self.startServices()
        }
    }

    @objc private func onUnpaired() {
        DispatchQueue.main.async {
            self.isPaired = false
            self.heartRate = 0
            self.vitalService.stopMonitoring()
            self.firebase.stopPolling()
        }
    }
}
