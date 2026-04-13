// PairingManager.swift - Sistema de emparejamiento con código alfanumérico
// Equivalente a la lógica de emparejamiento en WearApp.kt

import Foundation
import WatchKit

class PairingManager {

    static let shared = PairingManager()

    private let dbBase   = "https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com"
    private let apiKey   = "AIzaSyC031HwEG-y0cCvdcASeJ7hPRPhezWmZnY"
    private let prefs    = UserDefaults.standard

    // UserDefaults keys
    private let keyPaired       = "is_paired"
    private let keyPairingCode  = "pairing_code"
    private let keyUserId       = "biometric_user_id"
    private let keyLastCodeTime = "last_code_time"

    // Anonymous auth token cache
    private var cachedToken: String?
    private var tokenExpiry: Date = .distantPast

    private init() {}

    // MARK: - Public API

    var isPaired: Bool { prefs.bool(forKey: keyPaired) }

    var userId: String? { prefs.string(forKey: keyUserId) }

    var currentPairingCode: String { prefs.string(forKey: keyPairingCode) ?? "" }

    /// Genera un nuevo código de emparejamiento (8 caracteres alfanuméricos sin ambiguos)
    func generatePairingCode() -> String {
        let characters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        let code = String((0..<8).map { _ in characters.randomElement()! })
        prefs.set(code,                          forKey: keyPairingCode)
        prefs.set(Date().timeIntervalSince1970,  forKey: keyLastCodeTime)
        print("[PairingManager] Generated code: \(code)")
        return code
    }

    func isCodeExpired() -> Bool {
        let elapsed = Date().timeIntervalSince1970 - prefs.double(forKey: keyLastCodeTime)
        return elapsed > 300
    }

    // MARK: - Firebase Anonymous Auth

    /// Obtiene (o reutiliza) un token de autenticación anónima de Firebase.
    private func getAuthToken(completion: @escaping (String?) -> Void) {
        // Reusar token si aún es válido (expira en ~55 min de margen)
        if let token = cachedToken, Date() < tokenExpiry {
            completion(token)
            return
        }

        guard let url = URL(string: "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=\(apiKey)") else {
            completion(nil)
            return
        }

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try? JSONSerialization.data(withJSONObject: ["returnSecureToken": true])

        URLSession.shared.dataTask(with: req) { [weak self] data, _, error in
            guard let self, let data, error == nil,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let token = json["idToken"] as? String else {
                print("[PairingManager] Anonymous auth failed: \(error?.localizedDescription ?? "unknown")")
                completion(nil)
                return
            }

            self.cachedToken = token
            self.tokenExpiry = Date().addingTimeInterval(55 * 60) // 55 min
            print("[PairingManager] Anonymous auth OK")
            completion(token)
        }.resume()
    }

    // MARK: - Registrar código en Firebase

    func registerCodeInFirebase(code: String, completion: @escaping (Bool) -> Void) {
        getAuthToken { [weak self] token in
            guard let self else { return }

            let authParam = token.map { "?auth=\($0)" } ?? ""
            guard let url = URL(string: "\(self.dbBase)/patients/pairing_codes/\(code).json\(authParam)") else {
                completion(false)
                return
            }

            let deviceName = WKInterfaceDevice.current().name
            let timestamp  = Int64(Date().timeIntervalSince1970 * 1000)

            let data: [String: Any] = [
                "code":       code,
                "active":     true,
                "paired":     false,
                "deviceName": deviceName,
                "timestamp":  timestamp,
                "platform":   "watchOS"
            ]

            var request = URLRequest(url: url)
            request.httpMethod = "PUT"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")

            do {
                request.httpBody = try JSONSerialization.data(withJSONObject: data)
            } catch {
                print("[PairingManager] Serialization error: \(error)")
                completion(false)
                return
            }

            URLSession.shared.dataTask(with: request) { _, response, error in
                if let error = error {
                    print("[PairingManager] Network error: \(error.localizedDescription)")
                    completion(false)
                    return
                }

                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                if status == 200 {
                    print("[PairingManager] Code \(code) registered in Firebase ✓")
                    completion(true)
                } else {
                    print("[PairingManager] Firebase rejected write: HTTP \(status)")
                    completion(false)
                }
            }.resume()
        }
    }

    // MARK: - Escuchar emparejamiento

    func listenForPairing(code: String, callback: @escaping (String?) -> Void) {
        getAuthToken { [weak self] token in
            guard let self else { return }

            let authParam = token.map { "?auth=\($0)" } ?? ""
            guard let url = URL(string: "\(self.dbBase)/patients/pairing_codes/\(code).json\(authParam)") else {
                callback(nil)
                return
            }

            // Polling cada 2 segundos
            Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] timer in
                URLSession.shared.dataTask(with: url) { [weak self] data, _, error in
                    guard let self,
                          let data,
                          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                        return
                    }

                    if let paired = json["paired"] as? Bool, paired,
                       let userId = json["userId"] as? String {

                        timer.invalidate()

                        self.prefs.set(true,   forKey: self.keyPaired)
                        self.prefs.set(userId, forKey: self.keyUserId)

                        print("[PairingManager] Paired successfully with userId: \(userId)")
                        DispatchQueue.main.async { callback(userId) }
                    }
                }.resume()
            }
        }
    }

    // MARK: - Desemparejar

    func unpair() {
        prefs.set(false, forKey: keyPaired)
        prefs.removeObject(forKey: keyUserId)
        prefs.removeObject(forKey: keyPairingCode)
        print("[PairingManager] Unpaired")
    }
}
