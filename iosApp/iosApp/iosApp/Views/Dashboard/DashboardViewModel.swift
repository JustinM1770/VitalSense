import Foundation
import Combine
import FirebaseDatabase

@MainActor
class DashboardViewModel: ObservableObject {
    @Published var patients: [VitalsDataiOS] = []
    @Published var isLoading = true
    @Published var alertMessage: String?

    private let dbRef = Database.database().reference()
    private var handle: DatabaseHandle?

    func startObserving() {
        isLoading = true
        handle = dbRef.child("patients").observe(.value) { [weak self] snapshot in
            guard let self else { return }
            var result: [VitalsDataiOS] = []
            var alert: String?
            for child in snapshot.children {
                guard let snap = child as? DataSnapshot,
                      let dict = snap.value as? [String: Any] else { continue }
                let glucose = dict["glucose"] as? Double ?? Double(dict["glucose"] as? Int ?? 0)
                let p = VitalsDataiOS(
                    patientId: snap.key,
                    patientName: dict["patientName"] as? String ?? "Paciente",
                    heartRate: dict["heartRate"] as? Int ?? 0,
                    glucose: glucose,
                    spo2: dict["spo2"] as? Int ?? 0,
                    timestamp: dict["timestamp"] as? TimeInterval ?? 0
                )
                result.append(p)
                if p.glucose > 150 { alert = "⚠️ Glucosa alta: \(Int(p.glucose)) mg/dL — \(p.patientName)" }
            }
            self.patients = result
            self.alertMessage = alert
            self.isLoading = false
        }
    }

    func stopObserving() {
        if let handle { dbRef.child("patients").removeObserver(withHandle: handle) }
    }
}
