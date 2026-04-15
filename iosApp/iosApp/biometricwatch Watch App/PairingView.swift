// PairingView — fiel al Figma "Código Reloj"
// Tarjeta blanca full-screen: VitalSense (negro, ultrabold) → spinner → código azul → expiry

import SwiftUI
import Combine
import WatchKit

private let codeBlue = Color(red: 34/255, green: 95/255, blue: 255/255) // #225FFF

struct PairingView: View {
    @State private var code: String = ""
    @State private var status: String = ""
    @State private var secondsLeft: Int = 300
    @State private var isRegistering = true
    @State private var countdownTimer: Timer? = nil

    var body: some View {
        // Tarjeta blanca (replica el "QR Code" frame del Figma)
        ZStack {
            Color.white.ignoresSafeArea()

            VStack(spacing: 0) {
                // "VitalSense" — negro, size=18, weight=900
                Text("VitalSense")
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(Color(red: 34/255, green: 31/255, blue: 31/255))
                    .padding(.top, 12)

                Spacer()

                // Spinner mientras registra, luego muestra el código
                if isRegistering {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(Color(red: 34/255, green: 31/255, blue: 31/255))
                        .scaleEffect(1.2)
                } else {
                    // Código en azul — "9 W E - 8 O P" style, size=24, weight=600
                    Text(formattedCode)
                        .font(.system(size: 24, weight: .semibold, design: .monospaced))
                        .foregroundColor(codeBlue)
                        .multilineTextAlignment(.center)
                }

                Spacer()

                // "El código expira en 5 minutos" — size=14, color=(34,31,31)
                Text(timeString)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundColor(Color(red: 34/255, green: 31/255, blue: 31/255))
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 10)
            }
            .padding(.horizontal, 8)
        }
        .onAppear { startPairingFlow() }
        .onDisappear { countdownTimer?.invalidate() }
    }

    // MARK: - Código formateado: "9WE8OPXY" → "9 W E 8\nO P X Y"

    private var formattedCode: String {
        guard code.count == 8 else { return "--------" }
        let chars = Array(code)
        let top = chars.prefix(4).map(String.init).joined(separator: " ")
        let bot = chars.dropFirst(4).map(String.init).joined(separator: " ")
        return "\(top)\n\(bot)"
    }

    private var timeString: String {
        guard secondsLeft > 0 else { return "Código expirado" }
        let m = secondsLeft / 60
        let s = secondsLeft % 60
        return "El código expira en \(m):\(String(format: "%02d", s))"
    }

    // MARK: - Flujo de emparejamiento

    private func startPairingFlow() {
        countdownTimer?.invalidate()
        secondsLeft   = 300

        // Mostrar código inmediatamente, sin esperar a Firebase
        let newCode = PairingManager.shared.generatePairingCode()
        code = newCode
        isRegistering = false

        // Registrar en Firebase en background — si falla, reintenta sin tocar el countdown
        registerInBackground(code: newCode, attempt: 1)

        // Countdown: solo regenera cuando llega a 0
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { t in
            DispatchQueue.main.async {
                secondsLeft -= 1
                if secondsLeft <= 0 { t.invalidate(); startPairingFlow() }
            }
        }
    }

    private func registerInBackground(code: String, attempt: Int) {
        PairingManager.shared.registerCodeInFirebase(code: code) { success in
            DispatchQueue.main.async {
                if success {
                    listenForPairing(code: code)
                } else if attempt < 5 && self.code == code {
                    // Reintento silencioso con backoff, sin regenerar código
                    let delay = Double(attempt) * 4.0
                    DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                        guard self.code == code else { return }
                        self.registerInBackground(code: code, attempt: attempt + 1)
                    }
                }
            }
        }
    }

    private func listenForPairing(code: String) {
        PairingManager.shared.listenForPairing(code: code) { userId in
            guard userId != nil else { return }
            WKInterfaceDevice.current().play(.success)
            NotificationCenter.default.post(name: .pairingSuccessful, object: nil)
        }
    }
}
