import Foundation
import Combine
import HealthKit
import FirebaseDatabase
import FirebaseAuth
import CoreImage.CIFilterBuiltins
import SwiftUI
import CoreMotion

class WatchViewModel: NSObject, ObservableObject {
    @Published var heartRate: Double = 0.0
    @Published var pairingCode: String = ""
    @Published var isPaired: Bool = false
    @Published var userId: String?
    @Published var isSOSMode: Bool = false
    @Published var showSOSConfirmation: Bool = false
    
    private let healthStore = HKHealthStore()
    private let dbRef = Database.database().reference()
    private var pairingCodeRef: DatabaseReference?
    private var vitalsTimer: Timer?
    private let motionManager = CMMotionManager()
    
    override init() {
        super.init()
        checkExistingSession()
    }
    
    private func checkExistingSession() {
        if let savedUserId = UserDefaults.standard.string(forKey: "userId") {
            self.userId = savedUserId
            self.isPaired = true
            self.requestHealthPermissions()
        } else {
            generatePairingCode()
        }
    }
    
    func generatePairingCode() {
        let code = String(format: "%08d", Int.random(in: 10000000...99999999))
        self.pairingCode = code
        
        pairingCodeRef = dbRef.child("pairing_codes").child(code)
        let initialData: [String: Any] = [
            "code": code,
            "paired": false,
            "timestamp": ServerValue.timestamp()
        ]
        
        pairingCodeRef?.setValue(initialData)
        
        // Listen for iPhone to claim this code
        pairingCodeRef?.observe(.childAdded) { [weak self] snapshot in
            if snapshot.key == "userId", let uId = snapshot.value as? String {
                self?.handleSuccessfulPairing(uId: uId)
            }
        }
        
        pairingCodeRef?.observe(.childChanged) { [weak self] snapshot in
            if snapshot.key == "userId", let uId = snapshot.value as? String {
                self?.handleSuccessfulPairing(uId: uId)
            }
        }
    }
    
    private func handleSuccessfulPairing(uId: String) {
        UserDefaults.standard.set(uId, forKey: "userId")
        self.userId = uId
        self.isPaired = true
        
        // Clean up pairing code from DB
        pairingCodeRef?.removeValue()
        pairingCodeRef?.removeAllObservers()
        
        requestHealthPermissions()
        startShakeDetection()
    }
    
    func unpair() {
        UserDefaults.standard.removeObject(forKey: "userId")
        self.userId = nil
        self.isPaired = false
        stopSimulation()
        motionManager.stopAccelerometerUpdates()
        generatePairingCode()
    }
    
    // MARK: - HealthKit
    
    func requestHealthPermissions() {
        guard HKHealthStore.isHealthDataAvailable() else { return }
        
        let heartRateType = HKObjectType.quantityType(forIdentifier: .heartRate)!
        healthStore.requestAuthorization(toShare: nil, read: [heartRateType]) { [weak self] success, error in
            if success {
                DispatchQueue.main.async {
                    self?.startHeartRateQuery()
                }
            }
        }
    }
    
    private func startHeartRateQuery() {
        // En un dispositivo real, aquí iniciamos un HKAnchoredObjectQuery o HKWorkoutSession.
        // Dado que Xcode Simulator no siempre envía latidos, vamos a iniciar una lectura periódica.
        // Simularemos datos temporalmente si la lectura de HealthKit falla por falta de sesión física.
        
        let heartRateType = HKObjectType.quantityType(forIdentifier: .heartRate)!
        let query = HKAnchoredObjectQuery(type: heartRateType, predicate: nil, anchor: nil, limit: HKObjectQueryNoLimit) { [weak self] _, samples, _, _, _ in
            self?.processHeartRateSamples(samples)
        }
        
        query.updateHandler = { [weak self] _, samples, _, _, _ in
            self?.processHeartRateSamples(samples)
        }
        
        healthStore.execute(query)
        
        // Timer de respaldo para enviar a la DB periódicamente
        vitalsTimer?.invalidate()
        vitalsTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            self?.syncToFirebase()
        }
    }
    
    private func processHeartRateSamples(_ samples: [HKSample]?) {
        guard let heartRateSamples = samples as? [HKQuantitySample],
              let latest = heartRateSamples.last else { return }
        
        let hrUnit = HKUnit(from: "count/min")
        let hrValue = latest.quantity.doubleValue(for: hrUnit)
        
        DispatchQueue.main.async {
            self.heartRate = hrValue
        }
    }
    
    private func syncToFirebase() {
        guard let uId = userId else { return }
        // Si no hay lecturas del sensor (Simulador), generamos un latido falso para que no quede en cero
        let currentHR = self.heartRate > 0 ? self.heartRate : Double.random(in: 60...100)
        
        let vitalsRef = dbRef.child("patients").child(uId)
        let data: [String: Any] = [
            "heartRate": Int(currentHR),
            "glucose": Int.random(in: 80...120),  // Simulated per your android model
            "spo2": Int.random(in: 95...100),     // Simulated
            "timestamp": ServerValue.timestamp()
        ]
        
        vitalsRef.updateChildValues(data)
    }
    
    private func stopSimulation() {
        vitalsTimer?.invalidate()
        vitalsTimer = nil
        heartRate = 0.0
    }
    
    // MARK: - CoreMotion (Shake Detection)
    private func startShakeDetection() {
        guard motionManager.isAccelerometerAvailable else { return }
        motionManager.accelerometerUpdateInterval = 0.2
        motionManager.startAccelerometerUpdates(to: .main) { [weak self] data, error in
            guard let self = self, let data = data else { return }
            
            // Calculamos la magnitud de la aceleración (Fuerza G total)
            let acceleration = data.acceleration
            let magnitude = sqrt(pow(acceleration.x, 2) + pow(acceleration.y, 2) + pow(acceleration.z, 2))
            
            // 1.0 es la gravedad normal. > 2.5 indica una sacudida o movimiento muy brusco
            if magnitude > 2.5 {
                if !self.showSOSConfirmation && !self.isSOSMode {
                    self.showSOSConfirmation = true
                    // Vibración del reloj para alertar al usuario que la sacudida fue registrada
                    #if os(watchOS)
                    WKInterfaceDevice.current().play(.notification)
                    #endif
                }
            }
        }
    }
    
    func confirmSOS() {
        // Enviar alerta a Firebase
        if let uId = userId {
            let alertRef = dbRef.child("alerts").childByAutoId()
            alertRef.setValue([
                "patientId": uId,
                "type": "SOS_MANUAL",
                "message": "🚨 El paciente ha activado el Modo SOS de emergencia.",
                "timestamp": ServerValue.timestamp(),
                "resolved": false
            ])
            
            // Actualizar status del paciente a SOS
            dbRef.child("patients").child(uId).updateChildValues(["isSosMode": true])
        }
        
        self.isSOSMode = true
    }
    
    // MARK: - SOS QR Generator
    func generateQRCode() -> UIImage {
        // En una app real, podrías descargar los datos de la DB.
        // Aquí armamos un JSON de emergencia o un link dinámico usando el userId.
        let uId = userId ?? "desconocido"
        let dataString = "https://vitalsenseai.com/sos?patientId=\(uId)"
        
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(dataString.utf8)

        if let outputImage = filter.outputImage {
            let context = CIContext()
            if let cgImage = context.createCGImage(outputImage, from: outputImage.extent) {
                return UIImage(cgImage: cgImage)
            }
        }
        return UIImage(systemName: "xmark.circle") ?? UIImage()
    }
}
