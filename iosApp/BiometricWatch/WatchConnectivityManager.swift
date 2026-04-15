// WatchConnectivityManager.swift — WCSessionDelegate for watchOS 5
// Replaces WatchSessionReceiver.swift (ObservableObject removed)

import Foundation
import WatchConnectivity
import WatchKit
import OSLog

private let logger = Logger(subsystem: "mx.ita.vitalsense.ios.watchkitapp", category: "WatchConnectivity")

// MARK: - Notification Names

extension Notification.Name {
    static let userIdReceived        = Notification.Name("biometric.userIdReceived")
    static let heartRateUpdated      = Notification.Name("biometric.heartRateUpdated")
    static let spo2Updated           = Notification.Name("biometric.spo2Updated")
    static let emergencyStateUpdated = Notification.Name("biometric.emergencyStateUpdated")
    static let unpaired              = Notification.Name("biometric.unpaired")
    // pairingSuccessful is defined in PairingInterfaceController.swift
}

// MARK: - WatchConnectivityManager

/// Receives the userId from the paired iPhone via WatchConnectivity.
/// Persists it in UserDefaults and broadcasts a notification so
/// InterfaceController can react without any SwiftUI/Combine dependency.
class WatchConnectivityManager: NSObject {

    static let shared = WatchConnectivityManager()

    private override init() {
        super.init()
        activateSession()
    }

    private func activateSession() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    // MARK: - Internal helpers

    private func handleIncomingData(_ data: [String: Any], replyHandler: (([String: Any]) -> Void)? = nil) {
        logger.debug("📥 Datos recibidos del iPhone: \(data.keys.joined(separator: ", "))")
        
        // [TEST] Connection Ping
        if let pingTime = data["testPing"] as? Double {
            let latency = Date().timeIntervalSince1970 - pingTime
            logger.debug("PING recibido con latencia: \(String(format: "%.3f", latency))s")
            WKInterfaceDevice.current().play(.success)
            replyHandler?(["pong": Date().timeIntervalSince1970])
            return
        }

        if let unpair = data["unpair"] as? Bool, unpair {
            logger.info("🚫 Recibida señal de desvinculación (unpair)")
            PairingManager.shared.unpair()
            DispatchQueue.main.async {
                NotificationCenter.default.post(name: .unpaired, object: nil)
            }
            return
        }

        if let uid = data["userId"] as? String, !uid.isEmpty {
            let existing = UserDefaults.standard.string(forKey: "biometric_user_id") ?? ""
            logger.info("👤 Recibido userId: \(uid)")
            
            UserDefaults.standard.set(uid, forKey: "biometric_user_id")
            // Marcar como emparejado para que PairingManager.isPaired devuelva true
            UserDefaults.standard.set(true, forKey: "is_paired")
            if uid != existing {
                DispatchQueue.main.async {
                    NotificationCenter.default.post(name: .userIdReceived, object: nil)
                }
            }
        }
    }
}

// MARK: - WCSessionDelegate

extension WatchConnectivityManager: WCSessionDelegate {

    func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {
        if activationState == .activated {
            // Check for already received context immediately upon activation
            handleIncomingData(session.receivedApplicationContext)
        }
        if let error = error {
            logger.error("Activation error: \(error.localizedDescription)")
        }
    }

    /// Called when iPhone uses sendMessage(_:replyHandler:errorHandler:)
    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        handleIncomingData(message)
    }

    /// Called when iPhone uses sendMessage(_:replyHandler:) and expects a reply
    func session(
        _ session: WCSession,
        didReceiveMessage message: [String: Any],
        replyHandler: @escaping ([String: Any]) -> Void
    ) {
        handleIncomingData(message, replyHandler: replyHandler)
    }

    /// Called when iPhone uses updateApplicationContext(_:)
    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        handleIncomingData(applicationContext)
    }
}
