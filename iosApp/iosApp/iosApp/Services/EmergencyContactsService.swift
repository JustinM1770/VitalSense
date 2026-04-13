#if os(iOS)
// EmergencyContactsService.swift — Gestión de contactos de emergencia
// Guarda nombre + teléfono en Firebase bajo patients/{uid}/emergencyContacts

import Foundation
import Combine
import FirebaseAuth
import FirebaseDatabase

struct EmergencyContact: Identifiable, Codable {
    var id:     String
    var name:   String
    var phone:  String      // formato internacional: +52XXXXXXXXXX
    var relation: String    // "Familiar", "Médico", "Protección Civil", etc.
}

class EmergencyContactsService: ObservableObject {
    static let shared = EmergencyContactsService()

    @Published var contacts: [EmergencyContact] = []

    private let db = Database.database().reference()
    private var uid: String { Auth.auth().currentUser?.uid ?? "" }

    private init() {}

    // MARK: - Load

    func loadContacts() {
        guard !uid.isEmpty else { return }
        db.child("patients/\(uid)/emergencyContacts").observeSingleEvent(of: .value) { [weak self] snapshot in
            guard let self else { return }
            var loaded: [EmergencyContact] = []
            for child in snapshot.children {
                guard let snap = child as? DataSnapshot,
                      let dict = snap.value as? [String: Any] else { continue }
                let contact = EmergencyContact(
                    id:       snap.key,
                    name:     dict["name"]     as? String ?? "",
                    phone:    dict["phone"]    as? String ?? "",
                    relation: dict["relation"] as? String ?? "Familiar"
                )
                loaded.append(contact)
            }
            DispatchQueue.main.async { self.contacts = loaded }
        }
    }

    // MARK: - Add

    func addContact(_ contact: EmergencyContact, completion: @escaping (Bool) -> Void) {
        guard !uid.isEmpty else { completion(false); return }
        let ref  = db.child("patients/\(uid)/emergencyContacts").childByAutoId()
        let data: [String: Any] = [
            "name":     contact.name,
            "phone":    contact.phone,
            "relation": contact.relation
        ]
        ref.setValue(data) { error, _ in
            DispatchQueue.main.async {
                if error == nil {
                    let saved = EmergencyContact(id: ref.key ?? UUID().uuidString,
                                                 name: contact.name,
                                                 phone: contact.phone,
                                                 relation: contact.relation)
                    self.contacts.append(saved)
                }
                completion(error == nil)
            }
        }
    }

    // MARK: - Delete

    func deleteContact(id: String) {
        guard !uid.isEmpty else { return }
        db.child("patients/\(uid)/emergencyContacts/\(id)").removeValue()
        contacts.removeAll { $0.id == id }
    }

    // MARK: - Notify contacts (genera URLs de SMS/llamada)

    /// Devuelve la URL para abrir SMS pre-llenado al contacto con el mensaje de emergencia
    func smsURL(for contact: EmergencyContact, sosId: String, lat: Double, lng: Double) -> URL? {
        let location = lat != 0 ? "Ubicación: https://maps.google.com/?q=\(lat),\(lng)" : ""
        let body = "🚨 ALERTA VitalSense — \(contact.name), se detectó una CAÍDA o emergencia. \(location) ID: \(sosId)"
        let encoded = body.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "sms:\(contact.phone)&body=\(encoded)")
    }

    func callURL(for contact: EmergencyContact) -> URL? {
        URL(string: "tel:\(contact.phone)")
    }

    // MARK: - WhatsApp E2E (NOM-024-SSA3)
    // El PIN y el expediente completo viajan SOLO por este canal cifrado.
    // Nunca se muestran en pantalla pública ni se transmiten en texto plano.

    func whatsAppEmergencyURL(
        for contact: EmergencyContact,
        tokenId: String,
        pin: String,
        patientName: String,
        anomalyType: String
    ) -> URL? {
        let expedienteURL = "https://vitalsenseai-1cb9f.web.app/emergency.html?t=\(tokenId)"

        // Mensaje estructurado — el destinatario es el único que recibe PIN + URL
        let message = """
        🛡️ *PROTOCOLO IDENTIMEX — BioMetric AI*

        ⚠️ *ALERTA MÉDICA*
        Paciente: \(patientName.isEmpty ? "No disponible" : patientName)
        Anomalía detectada: \(anomalyType)

        📋 *Expediente médico completo:*
        \(expedienteURL)

        🔐 *PIN de acceso:* \(pin)

        _Cifrado de extremo a extremo · NOM-024-SSA3 · No reenviar_
        """

        // Limpiar número: solo dígitos, sin espacios ni guiones
        let digits = contact.phone.filter { $0.isNumber }
        let encoded = message.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "https://wa.me/\(digits)?text=\(encoded)")
    }
}

#endif
