#if os(iOS)
// SOSMonitorService.swift — Escucha alertas SOS del Watch en Firebase
// Cuando detecta una nueva alerta activa: muestra notificación crítica en iPhone
// y publica estado para mostrar el panel de contactos de emergencia.

import Foundation
import Combine
import FirebaseAuth
import FirebaseDatabase
import UserNotifications
import OSLog

private let logger = Logger(subsystem: "mx.ita.vitalsense.ios", category: "SOSMonitor")

struct SOSAlert: Identifiable {
    let id:        String
    let timestamp: Date
    let source:    String   // "fall_gps", "shake_no_location", etc.
    let lat:       Double
    let lng:       Double
    let heartRate: Double
    let isFall:    Bool
}

class SOSMonitorService: ObservableObject {
    static let shared = SOSMonitorService()

    @Published var activeAlert: SOSAlert? = nil

    private let db  = Database.database().reference()
    private var uid: String { Auth.auth().currentUser?.uid ?? "" }
    private var handle: DatabaseHandle?
    private var listeningUid: String = ""   // uid para el que ya hay un observer activo

    private init() {}

    // MARK: - Start/Stop

    func startListening() {
        let currentUid = uid
        guard !currentUid.isEmpty else {
            // Reintentar cuando haya sesión
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) { [weak self] in
                self?.startListening()
            }
            return
        }

        // Evitar observadores duplicados para el mismo uid
        guard currentUid != listeningUid else { return }

        // Limpiar observer anterior si cambió el uid (ej. logout + nuevo login)
        if let h = handle, !listeningUid.isEmpty {
            db.child("alerts/\(listeningUid)").removeObserver(withHandle: h)
        }
        listeningUid = currentUid

        handle = db.child("alerts/\(currentUid)").observe(.childAdded) { [weak self] snapshot in
            guard let self,
                  let dict = snapshot.value as? [String: Any] else { return }

            let status = dict["status"] as? String ?? ""
            guard status == "active" else { return }

            let source    = dict["source"]    as? String ?? "manual"
            let lat       = dict["lat"]       as? Double ?? 0
            let lng       = dict["lng"]       as? Double ?? 0
            let heartRate = dict["heartRate"] as? Double ?? 0
            let isFall    = source.contains("fall")

            let alert = SOSAlert(
                id:        snapshot.key,
                timestamp: Date(),
                source:    source,
                lat:       lat,
                lng:       lng,
                heartRate: heartRate,
                isFall:    isFall
            )

            DispatchQueue.main.async { self.activeAlert = alert }

            // Notificación crítica en iPhone
            self.sendLocalNotification(alert: alert)

            // Cargar contactos y notificarlos
            EmergencyContactsService.shared.loadContacts()
        }
    }

    func stopListening() {
        if let h = handle, !listeningUid.isEmpty {
            db.child("alerts/\(listeningUid)").removeObserver(withHandle: h)
        }
        handle = nil
        listeningUid = ""
    }

    func dismissAlert(sosId: String) {
        guard !uid.isEmpty else { return }
        db.child("alerts/\(uid)/\(sosId)").updateChildValues(["status": "acknowledged"])
        activeAlert = nil
    }

    // MARK: - Notificación local

    private func sendLocalNotification(alert: SOSAlert) {
        let content = UNMutableNotificationContent()

        if alert.isFall {
            content.title = "🛡️ IDENTIMEX — Caída Detectada"
            content.body  = "BioMetric AI activó el protocolo IDENTIMEX. Protocolo de emergencia en curso. FC: \(Int(alert.heartRate)) bpm"
        } else {
            content.title = "🛡️ IDENTIMEX — SOS Activado"
            content.body  = "BioMetric AI activó el protocolo IDENTIMEX. Ficha Clínica de Emergencia generada. FC: \(Int(alert.heartRate)) bpm"
        }

        content.sound             = .defaultCritical
        content.interruptionLevel = .critical
        content.categoryIdentifier = "SOS_ALERT"
        content.userInfo = ["sosId": alert.id, "isFall": alert.isFall]

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.5, repeats: false)
        let request = UNNotificationRequest(identifier: "sos-\(alert.id)", content: content, trigger: trigger)

        UNUserNotificationCenter.current().add(request) { error in
            if let error { logger.error("Notification error: \(error)") }
        }
    }

}

#endif
