// PairingManager.swift - Sistema de emparejamiento con código alfanumérico
// Equivalente a la lógica de emparejamiento en WearApp.kt

import Foundation
import WatchKit
import WatchConnectivity

class PairingManager {

    static let shared = PairingManager()

    private let dbBase   = "https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com"
    private let prefs    = UserDefaults.standard

    // UserDefaults keys
    private let keyPaired       = "is_paired"
    private let keyPairingCode  = "pairing_code"
    private let keyUserId       = "biometric_user_id"
    private let keyLastCodeTime = "last_code_time"

    // Timer activo de polling
    private var pollingTimer: Timer?

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

    // MARK: - Registrar código en Firebase
    // Estrategia dual:
    // 1. Intenta PUT directo a Firebase REST (sin auth — funciona si las reglas lo permiten)
    // 2. Envía el código al iPhone por WatchConnectivity → iPhone registra con su token autenticado

    func registerCodeInFirebase(code: String, completion: @escaping (Bool) -> Void) {
        // Canal 1: WatchConnectivity — siempre primero, es el más confiable
        // applicationContext garantiza entrega incluso si iPhone no está abierto ahora
        sendCodeToIPhone(code: code)

        // Canal 2: REST directo (funciona si las reglas de Firebase permiten escritura anónima)
        attemptDirectRegistration(code: code) { success in
            if success {
                completion(true)
            } else {
                // REST falló, pero WC ya envió el código al iPhone.
                // Si la sesión WC está activa, el iPhone registrará el código en Firebase.
                // Retornar true para que la UI muestre el código sin "Sin conexión".
                let wcActive = WCSession.default.activationState == .activated
                print("[PairingManager] REST falló, WC activo=\(wcActive) → completion(\(wcActive))")
                completion(wcActive)
            }
        }
    }

    /// Intenta registrar directamente en Firebase REST sin autenticación.
    private func attemptDirectRegistration(code: String, completion: @escaping (Bool) -> Void) {
        guard let url = URL(string: "\(dbBase)/patients/pairing_codes/\(code).json") else {
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
        request.timeoutInterval = 5

        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: data)
        } catch {
            print("[PairingManager] Serialization error: \(error)")
            completion(false)
            return
        }

        URLSession.shared.dataTask(with: request) { _, response, error in
            if let error = error {
                print("[PairingManager] REST directo error: \(error.localizedDescription)")
                completion(false)
                return
            }
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            if status == 200 {
                print("[PairingManager] Código \(code) registrado en Firebase (REST directo) ✓")
                completion(true)
            } else {
                print("[PairingManager] Firebase REST rechazó: HTTP \(status)")
                completion(false)
            }
        }.resume()
    }

    /// Envía el código al iPhone vía WatchConnectivity para que él lo registre en Firebase.
    /// No bloquea — fire-and-forget con fallback a applicationContext.
    private func sendCodeToIPhone(code: String) {
        let payload: [String: Any] = [
            "pairingCode": code,
            "timestamp":   Int64(Date().timeIntervalSince1970 * 1000),
            "deviceName":  WKInterfaceDevice.current().name
        ]

        // Guardar siempre en applicationContext como red de seguridad
        try? WCSession.default.updateApplicationContext(payload)

        // Intentar sendMessage si el iPhone está accesible
        if WCSession.default.activationState == .activated && WCSession.default.isReachable {
            WCSession.default.sendMessage(payload, replyHandler: { reply in
                let ok = reply["registered"] as? Bool ?? false
                print("[PairingManager] WC sendMessage reply: registered=\(ok)")
            }, errorHandler: { error in
                print("[PairingManager] WC sendMessage error: \(error.localizedDescription)")
            })
        } else {
            print("[PairingManager] iPhone no accesible, código guardado en applicationContext")
        }
    }

    // MARK: - Escuchar emparejamiento (polling)

    func stopListening() {
        pollingTimer?.invalidate()
        pollingTimer = nil
    }

    func listenForPairing(code: String, callback: @escaping (String?) -> Void) {
        stopListening()

        guard let url = URL(string: "\(dbBase)/patients/pairing_codes/\(code).json") else {
            callback(nil)
            return
        }

        // Polling cada 2 segundos
        let timer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] t in
            URLSession.shared.dataTask(with: url) { [weak self] data, _, error in
                guard let self,
                      let data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    return
                }

                if let paired = json["paired"] as? Bool, paired,
                   let userId = json["userId"] as? String {

                    t.invalidate()
                    self.pollingTimer = nil

                    self.prefs.set(true,   forKey: self.keyPaired)
                    self.prefs.set(userId, forKey: self.keyUserId)

                    print("[PairingManager] Emparejado exitosamente con userId: \(userId)")
                    DispatchQueue.main.async { callback(userId) }
                }
            }.resume()
        }
        pollingTimer = timer
    }

    // MARK: - Desemparejar

    func unpair() {
        prefs.set(false, forKey: keyPaired)
        prefs.removeObject(forKey: keyUserId)
        prefs.removeObject(forKey: keyPairingCode)
        print("[PairingManager] Unpaired")
    }
}
