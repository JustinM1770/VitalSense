// WatchConnectivityManager.swift — WCSessionDelegate for watchOS 5
// Replaces WatchSessionReceiver.swift (ObservableObject removed)

import Foundation
import WatchConnectivity
import WatchKit

// MARK: - Notification Names

extension Notification.Name {
    static let userIdReceived        = Notification.Name("biometric.userIdReceived")
    static let heartRateUpdated      = Notification.Name("biometric.heartRateUpdated")
    static let spo2Updated           = Notification.Name("biometric.spo2Updated")
    static let emergencyStateUpdated = Notification.Name("biometric.emergencyStateUpdated")
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
        // [TEST] Connection Ping
        if let pingTime = data["testPing"] as? Double {
            let latency = Date().timeIntervalSince1970 - pingTime
            print("[WatchConnectivity] PING recibido con latencia: \(String(format: "%.3f", latency))s")
            WKInterfaceDevice.current().play(.success)
            replyHandler?(["pong": Date().timeIntervalSince1970])
            return
        }

        if let uid = data["userId"] as? String, !uid.isEmpty {
            let existing = UserDefaults.standard.string(forKey: "biometric_user_id") ?? ""
            UserDefaults.standard.set(uid, forKey: "biometric_user_id")
            // Only broadcast if the userId actually changed
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
            print("[WatchConnectivity] Activation error: \(error.localizedDescription)")
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
