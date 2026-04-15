#if os(iOS)
import Foundation
import WatchConnectivity
import FirebaseAuth
import FirebaseDatabase

/// Envía el userId de Firebase al Apple Watch via WatchConnectivity.
/// También recibe el código de emparejamiento desde el Watch y lo registra en Firebase.
/// Llamar `setup()` una vez al arrancar la app.
class WatchConnectivitySender: NSObject, WCSessionDelegate {

    static let shared = WatchConnectivitySender()

    override init() {
        super.init()
        if WCSession.isSupported() {
            WCSession.default.delegate = self
            WCSession.default.activate()
        }
    }

    /// Llama esto desde `iosAppApp.init()` para escuchar cambios de Auth.
    func setup() {
        Auth.auth().addStateDidChangeListener { [weak self] _, user in
            guard let self else { return }
            let uid = user?.uid ?? ""
            self.sendUserId(uid)
        }
    }

    func sendUserId(_ uid: String) {
        guard WCSession.default.activationState == .activated else { return }
        try? WCSession.default.updateApplicationContext(["userId": uid])
        if WCSession.default.isReachable {
            WCSession.default.sendMessage(["userId": uid], replyHandler: nil)
        }
    }

    func sendTestPing() {
        guard WCSession.default.isReachable else {
            print("[WatchConnectivity] El reloj no está accesible en este momento.")
            return
        }
        print("[WatchConnectivity] Enviando PING de prueba al reloj...")
        WCSession.default.sendMessage(["testPing": Date().timeIntervalSince1970], replyHandler: { reply in
            print("[WatchConnectivity] PONG recibido del reloj: \(reply)")
        }) { error in
            print("[WatchConnectivity] Error al enviar PING: \(error.localizedDescription)")
        }
    }

    // MARK: - WCSessionDelegate (requerido en iOS)
    func session(_ session: WCSession,
                 activationDidCompleteWith activationState: WCSessionActivationState,
                 error: Error?) {
        if let error = error {
            print("[WatchConnectivity] Error de activación en iPhone: \(error.localizedDescription)")
            return
        }
        
        print("[WatchConnectivity] Sesión activada en iPhone. Estado: \(activationState.rawValue)")
        
        if activationState == .activated, let uid = Auth.auth().currentUser?.uid {
            print("[WatchConnectivity] Enviando userId actual: \(uid)")
            sendUserId(uid)
        }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) {
        WCSession.default.activate()
    }

    // MARK: - Recibir mensajes del Watch

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        handleIncomingFromWatch(message, replyHandler: nil)
    }

    func session(_ session: WCSession,
                 didReceiveMessage message: [String: Any],
                 replyHandler: @escaping ([String: Any]) -> Void) {
        handleIncomingFromWatch(message, replyHandler: replyHandler)
    }

    func session(_ session: WCSession,
                 didReceiveApplicationContext applicationContext: [String: Any]) {
        handleIncomingFromWatch(applicationContext, replyHandler: nil)
    }

    private func handleIncomingFromWatch(_ payload: [String: Any],
                                         replyHandler: (([String: Any]) -> Void)?) {
        // Código de emparejamiento enviado desde el Watch
        if let code = payload["pairingCode"] as? String, !code.isEmpty {
            let deviceName = payload["deviceName"] as? String ?? "Apple Watch"
            registerPairingCodeFromWatch(code, deviceName: deviceName) { success in
                replyHandler?(["registered": success])
            }
        }
    }

    /// Registra el código de emparejamiento en Firebase usando el SDK autenticado del iPhone.
    private func registerPairingCodeFromWatch(_ code: String,
                                               deviceName: String,
                                               completion: @escaping (Bool) -> Void) {
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        let data: [String: Any] = [
            "code":              code,
            "active":            true,
            "paired":            false,
            "deviceName":        deviceName,
            "timestamp":         timestamp,
            "platform":          "watchOS",
            "registeredByPhone": true
        ]

        Database.database()
            .reference()
            .child("patients/pairing_codes/\(code)")
            .setValue(data) { error, _ in
                if let error = error {
                    print("[WatchConnectivity] Error registrando código \(code): \(error.localizedDescription)")
                    completion(false)
                } else {
                    print("[WatchConnectivity] Código \(code) registrado en Firebase por iPhone ✓")
                    completion(true)
                }
            }
    }
}

#endif
