#if os(iOS)
import Foundation
import FirebaseAuth
import FirebaseDatabase

/// Crea sesiones de "portal médico" en Firebase Realtime Database.
/// El UUID del sessionId actúa como token — solo quien tenga el QR puede acceder.
/// TTL: 2 horas.
class DoctorSessionService {

    private let db = Database.database().reference()
    private let ttlMs: Double = 2 * 60 * 60 * 1000  // 2 horas en ms

    static let webBaseUrl = "https://vitalsenseai-1cb9f.web.app"

    /// Crea la sesión y devuelve la URL del portal médico.
    func createSession() async throws -> String {
        guard let userId = Auth.auth().currentUser?.uid else {
            throw NSError(domain: "DoctorSession", code: 401, userInfo: [NSLocalizedDescriptionKey: "Usuario no autenticado"])
        }

        // Leer perfil médico
        let profileSnap = try await db.child("users/\(userId)/datosMedicos").getData()
        let profile     = profileSnap.value as? [String: Any] ?? [:]

        // Últimas 20 lecturas
        let historySnap = try await db.child("patients/\(userId)/history")
            .queryOrdered(byChild: "timestamp").queryLimited(toLast: 20).getData()

        var historyList: [[String: Any]] = []
        for child in historySnap.children.allObjects {
            guard let snap  = child as? DataSnapshot,
                  let dict  = snap.value as? [String: Any],
                  let hr    = dict["heartRate"] as? Int else { continue }
            historyList.append([
                "heartRate" : hr,
                "spo2"      : dict["spo2"]      ?? 0,
                "glucose"   : dict["glucose"]   ?? 0,
                "timestamp" : dict["timestamp"] ?? 0,
            ])
        }

        let latestVitals = historyList.last ?? [:]

        // Último insight IA
        let aiSnap   = try? await db.child("patients/\(userId)/lastAiInsight").getData()
        let aiInsight = aiSnap?.value as? String ?? ""

        let sessionId = UUID().uuidString
        let now       = Date().timeIntervalSince1970 * 1000  // ms

        let sessionData: [String: Any] = [
            "sessionId"          : sessionId,
            "patientId"          : userId,
            "patientName"        : "\(profile["nombre"] ?? "") \(profile["apellidos"] ?? "")".trimmingCharacters(in: .whitespaces),
            "tipoSangre"         : profile["tipoSangre"]         ?? "",
            "alergias"           : profile["alergias"]           ?? "",
            "padecimientos"      : profile["padecimientos"]      ?? "",
            "medicamentos"       : profile["medicamentos"]       ?? "",
            "telefonoEmergencia" : profile["telefonoEmergencia"] ?? "",
            "contactoEmergencia" : profile["contactoEmergencia"] ?? "",
            "aiInsight"          : aiInsight,
            "vitals"             : latestVitals,
            "history"            : historyList,
            "createdAt"          : now,
            "expiresAt"          : now + ttlMs,
        ]

        try await db.child("doctor_sessions/\(sessionId)").setValue(sessionData)

        return "\(Self.webBaseUrl)/medico.html?s=\(sessionId)"
    }
}

#endif
