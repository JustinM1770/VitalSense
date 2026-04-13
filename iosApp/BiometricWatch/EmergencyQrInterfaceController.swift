// EmergencyQrInterfaceController.swift - Pantalla de emergencia con QR + PIN
// Equivalente a EmergencyQrWearScreen en Android WearApp.kt

import WatchKit
import Foundation

class EmergencyQrInterfaceController: WKInterfaceController {
    
    @IBOutlet weak var emergencyLabel: WKInterfaceLabel!
    @IBOutlet weak var anomalyLabel: WKInterfaceLabel!
    @IBOutlet weak var qrImage: WKInterfaceImage!
    @IBOutlet weak var pinLabel: WKInterfaceLabel!
    @IBOutlet weak var countdownLabel: WKInterfaceLabel!
    
    private var tokenId: String?
    private var pin: String?
    private var anomalyType: String?
    private var expiresAt: Int64 = 0
    
    private var countdownTimer: Timer?
    
    override func awake(withContext context: Any?) {
        super.awake(withContext: context)
        
        if let emergencyContext = context as? [String: Any] {
            tokenId = emergencyContext["tokenId"] as? String
            pin = emergencyContext["pin"] as? String
            anomalyType = emergencyContext["anomalyType"] as? String
            expiresAt = emergencyContext["expiresAt"] as? Int64 ?? 0
        }
        
        setupUI()
        generateQRCode()
        startCountdown()
    }
    
    override func willActivate() {
        super.willActivate()
    }
    
    override func didDeactivate() {
        super.didDeactivate()
        countdownTimer?.invalidate()
    }
    
    // MARK: - Setup
    
    private func setupUI() {
        // Fondo rojo (simulado con labels rojos)
        emergencyLabel.setText("⚠️ EMERGENCIA")
        emergencyLabel.setTextColor(.white)
        
        anomalyLabel.setText(anomalyType ?? "Anomalía detectada")
        anomalyLabel.setTextColor(.white)
        
        // PIN formateado: "1 - 2 - 3 - 4"
        if let pin = pin {
            let formattedPin = pin.map { String($0) }.joined(separator: " - ")
            pinLabel.setText("PIN: \(formattedPin)")
            pinLabel.setTextColor(.white)
        }
        
        countdownLabel.setTextColor(.yellow)
    }
    
    private func generateQRCode() {
        guard let tokenId = tokenId else {
            print("[EmergencyQr] Missing tokenId")
            return
        }
        
        // watchOS cannot render QR images — show the deep link as text instead
        if let deepLink = QRCodeGenerator.generateEmergencyQR(tokenId: tokenId) {
            anomalyLabel.setText(deepLink)
        } else {
            anomalyLabel.setText("Error al generar QR")
        }
    }
    
    // MARK: - Countdown
    
    private func startCountdown() {
        countdownTimer?.invalidate()
        
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] timer in
            guard let self = self else {
                timer.invalidate()
                return
            }
            
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let remaining = self.expiresAt - now
            
            if remaining <= 0 {
                timer.invalidate()
                DispatchQueue.main.async {
                    self.countdownLabel.setText("EXPIRADO")
                    // Volver después de 2 segundos
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                        self.pop()
                    }
                }
                return
            }
            
            let seconds = remaining / 1000
            let minutes = seconds / 60
            let secs = seconds % 60
            
            DispatchQueue.main.async {
                self.countdownLabel.setText(String(format: "%02d:%02d", minutes, secs))
                
                // Cambiar a amarillo si quedan menos de 5 minutos
                if minutes < 5 {
                    self.countdownLabel.setTextColor(.yellow)
                }
            }
        }
    }
}
