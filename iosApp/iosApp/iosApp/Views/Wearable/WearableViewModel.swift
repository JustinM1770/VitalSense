#if os(iOS)
import Foundation
import Combine
import FirebaseAuth
import FirebaseDatabase

// MARK: - WearableViewModel
// Equivalente completo a DeviceViewModel.kt de Android.
// Maneja: emparejamiento por código, lectura Firebase en tiempo real,
// persistencia en UserDefaults, y control de BLEService.

@MainActor
class WearableViewModel: ObservableObject {

    // MARK: - Estado publicado (mirror de DeviceViewModel StateFlow)
    @Published var isCodePaired: Bool
    @Published var pairedDeviceName: String
    @Published var codeError: String?  = nil
    @Published var connectionState: BLEConnectionState = .disconnected
    @Published var vitals = BLEVitals()

    let bleService = BLEService()

    // MARK: - Constantes (equivalente a companion object)
    private let KEY_CODE_PAIRED       = "code_paired"
    private let KEY_PAIRED_CODE       = "paired_code"
    private let KEY_PAIRED_DEVICE_NAME = "paired_device_name"

    private let db = Database.database().reference()
    private var vitalsHandle: DatabaseHandle?
    private var cancellables = Set<AnyCancellable>()
    private var lastFirebaseSave: Date = .distantPast   // throttle a 30s

    // MARK: - Init (equivalente al init {} de DeviceViewModel)

    init() {
        isCodePaired    = UserDefaults.standard.bool(forKey: "code_paired")
        pairedDeviceName = UserDefaults.standard.string(forKey: "paired_device_name") ?? "Wearable"

        // Propagar cambios de BLEService → ViewModel (mismo patrón que StateFlow en Android)
        bleService.$connectionState
            .receive(on: RunLoop.main)
            .assign(to: \.connectionState, on: self)
            .store(in: &cancellables)

        bleService.$vitals
            .receive(on: RunLoop.main)
            .sink { [weak self] newVitals in
                guard let self else { return }
                self.vitals = newVitals
                self.persistBLEVitalsIfNeeded(newVitals)
            }
            .store(in: &cancellables)

        if isCodePaired {
            startWatchDataReading()
        }
    }

    // MARK: - Escaneo BLE (equivalente a startScan / stopScan)

    func startScan() { bleService.startScan() }
    func stopScan()  { bleService.stopScan() }

    // MARK: - connectWithCode (idéntico a DeviceViewModel.connectWithCode)
    // Valida el código contra Firebase pairing_codes/{code},
    // guarda en UserDefaults y comienza a leer vitales.

    func connectWithCode(_ code: String) {
        codeError = nil
        bleService.setConnecting()

        let upperCode = code.uppercased().trimmingCharacters(in: .whitespaces)

        db.child("patients/pairing_codes").child(upperCode).getData { [weak self] error, snapshot in
            guard let self else { return }

            Task { @MainActor in
                if let error {
                    // Firebase error → emparejar de todas formas (igual que Android catch)
                    self.pairSuccessfully(code: upperCode, deviceName: "Wearable")
                    return
                }

                guard let snapshot, snapshot.exists() else {
                    self.codeError = "Código inválido. Verifica el código en tu reloj e inténtalo de nuevo."
                    self.bleService.setDisconnected()
                    return
                }

                let deviceName = snapshot.childSnapshot(forPath: "deviceName").value as? String ?? "Wearable"
                let userId = Auth.auth().currentUser?.uid ?? "global"

                self.db.child("patients/pairing_codes").child(upperCode)
                    .updateChildValues(["paired": true, "userId": userId])

                self.pairSuccessfully(code: upperCode, deviceName: deviceName)
            }
        }
    }

    // MARK: - Desconectar reloj (idéntico a disconnectWatch)

    func disconnectWatch() {
        stopWatchDataReading()

        Task { @MainActor in
            if let pairedCode = UserDefaults.standard.string(forKey: KEY_PAIRED_CODE) {
                db.child("patients/pairing_codes").child(pairedCode)
                    .updateChildValues(["paired": false])
            }
            if let userId = Auth.auth().currentUser?.uid {
                db.child("vitals/current/\(userId)").removeValue()
            }

            UserDefaults.standard.set(false, forKey: KEY_CODE_PAIRED)
            UserDefaults.standard.removeObject(forKey: KEY_PAIRED_CODE)
            UserDefaults.standard.removeObject(forKey: KEY_PAIRED_DEVICE_NAME)

            isCodePaired = false
            pairedDeviceName = "Wearable"
            bleService.disconnect()
        }
    }

    // MARK: - Private

    private func pairSuccessfully(code: String, deviceName: String) {
        UserDefaults.standard.set(true, forKey: KEY_CODE_PAIRED)
        UserDefaults.standard.set(code, forKey: KEY_PAIRED_CODE)
        UserDefaults.standard.set(deviceName, forKey: KEY_PAIRED_DEVICE_NAME)

        isCodePaired = true
        pairedDeviceName = deviceName
        bleService.setConnected(deviceName: deviceName)
        startWatchDataReading()
    }

    // startWatchDataReading — equivalente a startWatchDataReading() en Android
    // Observa vitals/current/{userId} en Firebase (el Wear OS escribe ahí).

    private func startWatchDataReading() {
        bleService.setConnected(deviceName: pairedDeviceName)
        guard let userId = Auth.auth().currentUser?.uid else { return }

        vitalsHandle = db.child("vitals/current/\(userId)").observe(.value) { [weak self] snapshot in
            guard let self, let dict = snapshot.value as? [String: Any] else { return }

            Task { @MainActor in
                let heartRate = dict["heartRate"] as? Int
                let glucose   = (dict["glucose"] as? Double)
                             ?? (dict["glucose"] as? Int).map { Double($0) }
                let spo2      = dict["spo2"] as? Int
                let timestamp = dict["timestamp"] as? Double

                self.bleService.updateVitals(BLEVitals(
                    heartRate: heartRate,
                    glucose:   glucose,
                    spo2:      spo2,
                    timestamp: timestamp
                ))
            }
        }
    }

    // MARK: - BLE → Firebase persistence
    // Guarda cada lectura BLE en vitals/current y en history (throttled 30s)
    // para que el ClinicalScoringEngine y la IA puedan analizarlos.

    private func persistBLEVitalsIfNeeded(_ v: BLEVitals) {
        guard let hr = v.heartRate, hr > 0,
              let uid = Auth.auth().currentUser?.uid else { return }

        let now = Date()
        let ts  = now.timeIntervalSince1970 * 1000

        let data: [String: Any] = [
            "heartRate": hr,
            "spo2":      v.spo2  ?? 0,
            "glucose":   v.glucose ?? 0,
            "timestamp": ts,
            "source":    "ble_direct"
        ]

        // Siempre actualizar vitals/current (para que Dashboard lo muestre en vivo)
        db.child("vitals/current/\(uid)").setValue(data)

        // Escribir en history máximo cada 30 segundos (evita saturar Firebase)
        guard now.timeIntervalSince(lastFirebaseSave) >= 30 else { return }
        lastFirebaseSave = now
        let key = "\(Int(ts))"
        db.child("patients/\(uid)/history/\(key)").setValue(data)
    }

    private func stopWatchDataReading() {
        guard let handle = vitalsHandle,
              let userId = Auth.auth().currentUser?.uid else { return }
        db.child("vitals/current/\(userId)").removeObserver(withHandle: handle)
        vitalsHandle = nil
    }

    deinit {
        guard let handle = vitalsHandle,
              let userId = Auth.auth().currentUser?.uid else { return }
        db.child("vitals/current/\(userId)").removeObserver(withHandle: handle)
    }
}

#endif
