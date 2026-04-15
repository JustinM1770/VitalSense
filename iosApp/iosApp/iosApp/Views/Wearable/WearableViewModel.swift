#if os(iOS)
import Foundation
import Combine
import FirebaseAuth
import FirebaseDatabase

// MARK: - WearableViewModel
@MainActor
class WearableViewModel: ObservableObject {

    // ── Estado publicado ─────────────────────────────────────────────────────
    @Published var codeError: String?  = nil
    @Published var vitals              = BLEVitals()
    @Published var activeDeviceCode    = ""    // código del device que está activo/monitoreando
    @Published var pairingSuccess      = false // pulso que cierra el sheet de vinculación

    let bleService = BLEService()

    // MARK: - Privado
    private let db = Database.database().reference()
    private var vitalsHandle: DatabaseHandle?
    private var cancellables = Set<AnyCancellable>()
    private var lastFirebaseSave: Date = .distantPast

    // MARK: - Init
    init() {
        // BLE vitals → publicar + guardar en Firebase
        bleService.$vitals
            .receive(on: RunLoop.main)
            .sink { [weak self] v in
                guard let self else { return }
                self.vitals = v
                self.persistVitalsIfNeeded(v, source: "ble_direct")
            }
            .store(in: &cancellables)

        // Si ya había un dispositivo activo (sesión previa), reanudarlo
        let savedCode = UserDefaults.standard.string(forKey: "active_device_code") ?? ""
        if !savedCode.isEmpty {
            activeDeviceCode = savedCode
            startVitalsListener(code: savedCode)
        }
    }

    // MARK: - connectWithCode (llamado desde AddDeviceSheet)
    // Valida el código en Firebase, registra el dispositivo en SubscriptionService,
    // y comienza a escuchar vitales.

    func connectWithCode(_ code: String, deviceName: String, platform: String) {
        codeError = nil
        bleService.setConnecting()

        let upper = code.uppercased().trimmingCharacters(in: .whitespaces)

        db.child("patients/pairing_codes").child(upper).getData { [weak self] error, snapshot in
            guard let self else { return }
            Task { @MainActor in
                // Firebase error → aceptar de todas formas (modo offline)
                if error != nil {
                    await self.finalizePairing(code: upper, deviceName: deviceName, platform: platform)
                    return
                }

                guard let snapshot, snapshot.exists() else {
                    self.codeError = "Código inválido. Verifica el código en tu reloj e inténtalo de nuevo."
                    self.bleService.setDisconnected()
                    return
                }

                // Marcar como emparejado en Firebase
                let resolvedName = snapshot.childSnapshot(forPath: "deviceName").value as? String ?? deviceName
                let uid = Auth.auth().currentUser?.uid ?? "global"
                self.db.child("patients/pairing_codes").child(upper)
                    .updateChildValues(["paired": true, "userId": uid]) { _, _ in }

                await self.finalizePairing(code: upper, deviceName: resolvedName, platform: platform)
            }
        }
    }

    private func finalizePairing(code: String, deviceName: String, platform: String) async {
        // Registrar en SubscriptionService (guarda en Firebase patients/{uid}/devices)
        let added = await SubscriptionService.shared.addDevice(
            name:     deviceName,
            platform: platform,
            code:     code
        )
        guard added else {
            codeError = "Límite de dispositivos alcanzado. Actualiza a Premium."
            bleService.setDisconnected()
            return
        }

        // Activar monitoreo de vitals
        activeDeviceCode = code
        UserDefaults.standard.set(code, forKey: "active_device_code")
        bleService.setConnected(deviceName: deviceName)
        startVitalsListener(code: code)

        pairingSuccess = true
        // Reset para permitir que el onChange de la sheet vuelva a dispararse después
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { self.pairingSuccess = false }
    }

    // MARK: - Desconectar dispositivo activo
    func disconnectActive() {
        stopVitalsListener()
        activeDeviceCode = ""
        UserDefaults.standard.removeObject(forKey: "active_device_code")
        bleService.setDisconnected()
        vitals = BLEVitals()
    }

    // MARK: - Escucha vitals/current/{uid} en Firebase
    // El reloj/wearable escribe aquí; el iPhone muestra en tiempo real.

    private func startVitalsListener(code: String) {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        stopVitalsListener()

        vitalsHandle = db.child("vitals/current/\(uid)").observe(.value) { [weak self] snapshot in
            guard let self, let dict = snapshot.value as? [String: Any] else { return }
            Task { @MainActor in
                self.vitals = BLEVitals(
                    heartRate: dict["heartRate"] as? Int,
                    glucose:   (dict["glucose"] as? Double) ?? (dict["glucose"] as? Int).map { Double($0) },
                    spo2:      dict["spo2"] as? Int,
                    timestamp: dict["timestamp"] as? Double
                )
            }
        }
    }

    private func stopVitalsListener() {
        guard let handle = vitalsHandle,
              let uid = Auth.auth().currentUser?.uid else { return }
        db.child("vitals/current/\(uid)").removeObserver(withHandle: handle)
        vitalsHandle = nil
    }

    // MARK: - BLE persistence → Firebase

    private func persistVitalsIfNeeded(_ v: BLEVitals, source: String) {
        guard let hr = v.heartRate, hr > 0,
              let uid = Auth.auth().currentUser?.uid else { return }

        let ts  = Date().timeIntervalSince1970 * 1000
        let data: [String: Any] = [
            "heartRate": hr,
            "spo2":      v.spo2    ?? 0,
            "glucose":   v.glucose ?? 0,
            "timestamp": ts,
            "source":    source
        ]

        db.child("vitals/current/\(uid)").setValue(data)

        let now = Date()
        guard now.timeIntervalSince(lastFirebaseSave) >= 30 else { return }
        lastFirebaseSave = now
        db.child("patients/\(uid)/history/\(Int(ts))").setValue(data)
    }

    deinit {
        guard let handle = vitalsHandle,
              let uid = Auth.auth().currentUser?.uid else { return }
        db.child("vitals/current/\(uid)").removeObserver(withHandle: handle)
    }
}
#endif
