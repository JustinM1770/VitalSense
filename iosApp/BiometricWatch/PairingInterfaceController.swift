// PairingInterfaceController.swift - Pantalla de emparejamiento con código
// Equivalente a CodeScreen en Android WearApp.kt

import WatchKit
import Foundation

class PairingInterfaceController: WKInterfaceController {
    
    @IBOutlet weak var codeLabel: WKInterfaceLabel!
    @IBOutlet weak var timeLabel: WKInterfaceLabel!
    @IBOutlet weak var statusLabel: WKInterfaceLabel!
    
    private let pairingManager = PairingManager.shared
    private var currentCode: String = ""
    private var expirationTimer: Timer?
    
    override func awake(withContext context: Any?) {
        super.awake(withContext: context)
        setupUI()
        startPairingFlow()
    }
    
    override func willActivate() {
        super.willActivate()
    }
    
    override func didDeactivate() {
        super.didDeactivate()
        expirationTimer?.invalidate()
    }
    
    // MARK: - Setup
    
    private func setupUI() {
        codeLabel.setText("------")
        timeLabel.setText("Generando código...")
        statusLabel.setText("Biometric")
    }
    
    // MARK: - Pairing Flow
    
    private func startPairingFlow() {
        // Generar código
        currentCode = pairingManager.generatePairingCode()
        
        // Mostrar código formateado (ej: "A B C 1 2 3 4 5")
        let formattedCode = currentCode.map { String($0) }.joined(separator: " ")
        codeLabel.setText(formattedCode)
        
        // Registrar en Firebase
        pairingManager.registerCodeInFirebase(code: currentCode) { [weak self] success in
            guard success else {
                DispatchQueue.main.async {
                    self?.statusLabel.setText("Error al registrar")
                }
                return
            }
            
            // Comenzar a escuchar emparejamiento
            self?.listenForPairing()
        }
        
        // Iniciar contador de expiración
        startExpirationTimer()
    }
    
    private func listenForPairing() {
        pairingManager.listenForPairing(code: currentCode) { [weak self] userId in
            guard let userId = userId else { return }
            
            print("[Pairing] Success! UserId: \(userId)")
            
            // Notificar éxito
            DispatchQueue.main.async {
                NotificationCenter.default.post(name: .pairingSuccessful, object: nil)
                
                // Navegar a pantalla de éxito
                self?.pushController(withName: "SuccessInterface", context: nil)
                
                // Después de 3 segundos, volver al monitoreo
                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                    self?.pop()
                }
            }
        }
    }
    
    // MARK: - Expiration Timer
    
    private func startExpirationTimer() {
        expirationTimer?.invalidate()
        
        var secondsLeft = 300 // 5 minutos
        
        expirationTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] timer in
            secondsLeft -= 1
            
            let minutes = secondsLeft / 60
            let seconds = secondsLeft % 60
            
            DispatchQueue.main.async {
                self?.timeLabel.setText("Expira en \(minutes):\(String(format: "%02d", seconds))")
            }
            
            if secondsLeft <= 0 {
                timer.invalidate()
                DispatchQueue.main.async {
                    self?.timeLabel.setText("Código expirado")
                    // Generar nuevo código
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                        self?.startPairingFlow()
                    }
                }
            }
        }
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let pairingSuccessful = Notification.Name("biometric.pairingSuccessful")
}
