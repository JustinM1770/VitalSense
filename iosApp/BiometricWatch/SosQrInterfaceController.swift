// SosQrInterfaceController.swift - Pantalla de QR para SOS
// Equivalente a SosQrScreen en Android WearApp.kt

import WatchKit
import Foundation

class SosQrInterfaceController: WKInterfaceController {
    
    @IBOutlet weak var titleLabel: WKInterfaceLabel!
    @IBOutlet weak var qrImage: WKInterfaceImage!
    @IBOutlet weak var instructionLabel: WKInterfaceLabel!
    @IBOutlet weak var cancelButton: WKInterfaceButton!
    
    private var sosId: String?
    private var userId: String?
    
    override func awake(withContext context: Any?) {
        super.awake(withContext: context)
        
        if let sosContext = context as? [String: String] {
            sosId = sosContext["sosId"]
            userId = sosContext["userId"]
        }
        
        setupUI()
        generateQRCode()
    }
    
    override func willActivate() {
        super.willActivate()
    }
    
    override func didDeactivate() {
        super.didDeactivate()
    }
    
    // MARK: - Setup
    
    private func setupUI() {
        titleLabel.setText("SOS ACTIVO")
        titleLabel.setTextColor(.red)
        instructionLabel.setText("Escanea para ayudar")
        cancelButton.setTitle("Cancelar SOS")
        cancelButton.setBackgroundColor(.orange)
    }
    
    private func generateQRCode() {
        guard let userId = userId, let sosId = sosId else {
            print("[SosQr] Missing userId or sosId")
            return
        }
        
        // watchOS cannot render QR images — show the deep link as text instead
        if let deepLink = QRCodeGenerator.generateSosQR(userId: userId, sosId: sosId) {
            instructionLabel.setText(deepLink)
        } else {
            instructionLabel.setText("Error al generar QR")
        }
    }
    
    // MARK: - Actions
    
    @IBAction func cancelSosPressed() {
        guard let userId = userId, let sosId = sosId else { return }
        
        // Resolver el SOS
        VitalSignsServiceWatch.shared.resolveSos(sosId: sosId, userId: userId)
        
        // Vibrar
        WKInterfaceDevice.current().play(.success)
        
        // Volver
        pop()
    }
}
