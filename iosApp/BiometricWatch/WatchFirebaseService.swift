// WatchFirebaseService.swift — URLSession REST API for watchOS 5
// Rewritten: no @Published, no ObservableObject, no Combine.
// Posts emergency state changes via NotificationCenter (.emergencyStateUpdated).

import Foundation
import OSLog

private let logger = Logger(subsystem: "mx.ita.vitalsense.ios.watchkitapp", category: "Firebase")

class WatchFirebaseService: NSObject {

    static let shared = WatchFirebaseService()

    private let dbBase = "https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com"
    private var userId: String { UserDefaults.standard.string(forKey: "biometric_user_id") ?? "" }
    private var pollTimer: Timer?

    private override init() {
        super.init()
    }

    // MARK: - Polling

    /// Begins polling for active emergencies every 30 s.
    /// Retries every 5 s if userId is not yet available.
    func startPolling() {
        guard !userId.isEmpty else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 5) { [weak self] in
                self?.startPolling()
            }
            return
        }

        // Immediate first poll
        pollEmergency()

        // Recurring poll
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(
            timeInterval: 30,
            target: self,
            selector: #selector(pollEmergency),
            userInfo: nil,
            repeats: true
        )
    }

    func stopPolling() {
        pollTimer?.invalidate()
        pollTimer = nil
    }

    // MARK: - Emergency Polling

    @objc private func pollEmergency() {
        guard !userId.isEmpty else { return }

        guard let url = URL(string: "\(dbBase)/patients/\(userId)/activeEmergency.json") else { return }

        URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            guard let self = self else { return }

            // Null / empty response means no active emergency
            guard
                let data = data,
                !data.isEmpty,
                let raw = String(data: data, encoding: .utf8),
                raw.trimmingCharacters(in: .whitespaces) != "null",
                let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            else {
                self.broadcastEmergency(active: false, pin: "", anomaly: "", expiresAt: 0)
                return
            }

            let expiresAt = json["expiresAt"] as? Double ?? 0
            let nowMs     = Date().timeIntervalSince1970 * 1000

            if expiresAt > nowMs {
                let pin     = json["pin"]         as? String ?? ""
                let anomaly = json["anomalyType"] as? String ?? "Emergencia"
                self.broadcastEmergency(active: true, pin: pin, anomaly: anomaly, expiresAt: expiresAt)
            } else {
                self.broadcastEmergency(active: false, pin: "", anomaly: "", expiresAt: 0)
            }
        }.resume()
    }

    private func broadcastEmergency(active: Bool, pin: String, anomaly: String, expiresAt: Double) {
        let info: [String: Any] = [
            "active"      : active,
            "pin"         : pin,
            "anomalyType" : anomaly,
            "expiresAt"   : expiresAt,
        ]
        DispatchQueue.main.async {
            NotificationCenter.default.post(
                name: .emergencyStateUpdated,
                object: nil,
                userInfo: info
            )
        }
    }

    // MARK: - Sync Vitals

    /// Writes heart-rate (and a placeholder spo2=0 since Series 2 has no sensor)
    /// to vitals/current/{userId} and patients/{userId}/history/{ts}.
    func syncVitals(hr: Int, spo2: Int) {
        guard !userId.isEmpty else { return }

        let ts   = Int(Date().timeIntervalSince1970 * 1000)
        let body: [String: Any] = [
            "heartRate" : hr,
            "spo2"      : spo2,   // 0 — Series 2 has no SpO2 sensor
            "glucose"   : 0,
            "timestamp" : ts,
            "source"    : "apple_watch",
        ]
        guard let bodyData = try? JSONSerialization.data(withJSONObject: body) else { return }

        // 1) History entry
        if let histURL = URL(string: "\(dbBase)/patients/\(userId)/history/\(ts).json") {
            var req = URLRequest(url: histURL)
            req.httpMethod = "PUT"
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = bodyData
            URLSession.shared.dataTask(with: req).resume()
        }

        // 2) vitals/current/{userId} — consumed by iPhone WearableView
        if let curURL = URL(string: "\(dbBase)/vitals/current/\(userId).json") {
            var req = URLRequest(url: curURL)
            req.httpMethod = "PUT"
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = bodyData
            URLSession.shared.dataTask(with: req).resume()
        }
    }

    // MARK: - SOS

    func triggerSOS(hr: Int) {
        guard !userId.isEmpty else { return }

        let sosId = UUID().uuidString
        guard let url = URL(string: "\(dbBase)/alerts/\(userId)/\(sosId).json") else { return }

        let body: [String: Any] = [
            "sosId"     : sosId,
            "userId"    : userId,
            "heartRate" : hr,
            "timestamp" : Int(Date().timeIntervalSince1970 * 1000),
            "status"    : "active",
            "source"    : "apple_watch",
            "read"      : false,
        ]
        guard let bodyData = try? JSONSerialization.data(withJSONObject: body) else { return }

        var req = URLRequest(url: url)
        req.httpMethod = "PUT"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = bodyData
        URLSession.shared.dataTask(with: req).resume()
    }
    
    // MARK: - SOS Alerts (Transcripción de Android)
    
    /// Envía alerta SOS a Firebase
    func pushSosAlert(userId: String, sosId: String, data: [String: Any]) {
        guard let url = URL(string: "\(dbBase)/alerts/\(userId)/\(sosId).json") else { return }
        
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: data)
        } catch {
            logger.error("Failed to serialize SOS data: \(error)")
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                logger.error("SOS push error: \(error.localizedDescription)")
                return
            }
            logger.info("SOS alert pushed: \(sosId)")
        }.resume()
    }
    
    /// Actualiza estado de alerta SOS
    func updateSosAlert(userId: String, sosId: String, updates: [String: Any]) {
        guard let url = URL(string: "\(dbBase)/alerts/\(userId)/\(sosId).json") else { return }
        
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: updates)
        } catch {
            logger.error("Failed to serialize SOS updates: \(error)")
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                logger.error("SOS update error: \(error.localizedDescription)")
                return
            }
            logger.info("SOS alert updated: \(sosId)")
        }.resume()
    }

    // MARK: - Health AI Alerts

    func pushHealthAlert(userId: String, alertData: [String: Any]) {
        let alertId = "healthAI_\(Int(Date().timeIntervalSince1970 * 1000))"
        guard let url = URL(string: "\(dbBase)/patients/\(userId)/healthAlerts/\(alertId).json") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: alertData)
        } catch {
            logger.error("Failed to serialize health alert: \(error)")
            return
        }
        URLSession.shared.dataTask(with: request) { _, _, error in
            if let error = error { logger.error("Health alert error: \(error.localizedDescription)") }
        }.resume()
    }
}

