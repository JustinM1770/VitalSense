import Foundation

struct VitalsDataiOS: Identifiable {
    let id = UUID()
    let patientId: String
    let patientName: String
    let heartRate: Int
    let glucose: Double
    let spo2: Int
    let timestamp: TimeInterval

    var hasAlert: Bool { glucose > 150 || heartRate > 100 || heartRate < 50 || spo2 < 95 }
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
}
