#if os(iOS)
import Foundation
import WatchConnectivity
import FirebaseAuth
import OSLog

private let logger = Logger(subsystem: "mx.ita.vitalsense.ios", category: "WatchConnectivity")

/// Envía el userId de Firebase al Apple Watch via WatchConnectivity.
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
            let isPaired = UserDefaults.standard.bool(forKey: "code_paired")
            let uid = user?.uid ?? ""
            
            logger.debug("Auth state changed. User: \(uid), Paired: \(isPaired)")
            
            if !uid.isEmpty && isPaired {
                self.sendUserId(uid)
            } else {
                // Si no hay usuario o no está emparejado, asegurar que el reloj lo sepa
                self.sendUnpair()
            }
        }
    }

    func sendUserId(_ uid: String) {
        guard WCSession.default.activationState == .activated else { return }
        try? WCSession.default.updateApplicationContext(["userId": uid])
        if WCSession.default.isReachable {
            WCSession.default.sendMessage(["userId": uid], replyHandler: nil)
        }
    }

    func sendUnpair() {
        guard WCSession.default.activationState == .activated else { return }
        try? WCSession.default.updateApplicationContext(["unpair": true])
        if WCSession.default.isReachable {
            WCSession.default.sendMessage(["unpair": true], replyHandler: nil)
        }
    }

    func sendTestPing() {
        guard WCSession.default.isReachable else {
            logger.warning("El reloj no está accesible en este momento.")
            return
        }
        logger.debug("Enviando PING de prueba al reloj...")
        WCSession.default.sendMessage(["testPing": Date().timeIntervalSince1970], replyHandler: { reply in
            logger.debug("PONG recibido del reloj: \(reply)")
        }) { error in
            logger.error("Error al enviar PING: \(error.localizedDescription)")
        }
    }

    // MARK: - WCSessionDelegate (requerido en iOS)
    func session(_ session: WCSession,
                 activationDidCompleteWith activationState: WCSessionActivationState,
                 error: Error?) {
        if let error = error {
            logger.error("Error de activación en iPhone: \(error.localizedDescription)")
            return
        }

        logger.info("Sesión activada en iPhone. Estado: \(activationState.rawValue)")
        
        let isPaired = UserDefaults.standard.bool(forKey: "code_paired")

        if activationState == .activated {
            if let uid = Auth.auth().currentUser?.uid, isPaired {
                logger.debug("Enviando userId actual: \(uid)")
                sendUserId(uid)
            } else {
                logger.debug("No emparejado o sin sesión. Enviando señal de desvinculación preventiva.")
                sendUnpair()
            }
        }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) {
        WCSession.default.activate()
    }
}

#endif
