import Foundation

// MARK: - Modelos iOS que reflejan los data class de Kotlin
// Mientras se integra Compose Multiplatform, estos structs
// son el espejo exacto de VitalsData.kt y VitalsSnapshot.kt

struct VitalsDataiOS: Identifiable {
    let id = UUID()
    let patientId: String
    let patientName: String
    let heartRate: Int
    let glucose: Double
    let spo2: Int
    let timestamp: TimeInterval

    var hasAlert: Bool {
        glucose > 150 || heartRate > 100 || heartRate < 50 || spo2 < 95
    }
}

struct VitalsSnapshotiOS: Identifiable {
    let id = UUID()
    let heartRate: Int
    let glucose: Double
    let spo2: Int
    let timestamp: TimeInterval
}

struct MedicationiOS: Identifiable {
    let id: String
    let nombre: String
    let dosis: String
    let horario: String
    let activo: Bool
    var takenToday: Bool = false
}
