// PairingView — fiel al Figma "Código Reloj"
// Tarjeta blanca full-screen: VitalSense (negro, ultrabold) → spinner → código azul → expiry

import SwiftUI
import Combine
import WatchKit

private let codeBlue = Color(red: 34/255, green: 95/255, blue: 255/255) // #225FFF

struct PairingView: View {
    @State private var code: String = ""
    @State private var secondsLeft: Int = 300
    @State private var isRegistering = true
    @State private var registrationFailed = false
    @State private var countdownTimer: Timer? = nil
    @State private var retryTimer: Timer? = nil   // timer de reintento (separado del countdown)
    @State private var retryCount: Int = 0

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            VStack(spacing: 0) {
                // "VitalSense" — negro, size=18, weight=900
                Text("VitalSense")
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(Color(red: 34/255, green: 31/255, blue: 31/255))
                    .padding(.top, 12)

                Spacer()

                if isRegistering {
                    VStack(spacing: 6) {
                        ProgressView()
                            .progressViewStyle(.circular)
                            .tint(Color(red: 34/255, green: 31/255, blue: 31/255))
                            .scaleEffect(1.2)
                        if retryCount > 0 {
                            Text("Reintentando…")
                                .font(.system(size: 10))
                                .foregroundColor(.gray)
                        }
                    }
                } else {
                    // Código en azul — siempre el mismo hasta que expire
                    Text(formattedCode)
                        .font(.system(size: 24, weight: .semibold, design: .monospaced))
                        .foregroundColor(registrationFailed ? .gray : codeBlue)
                        .multilineTextAlignment(.center)

                    if registrationFailed {
                        Text("Sin conexión — usa este código de todos modos")
                            .font(.system(size: 9))
                            .foregroundColor(.gray)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 8)
                            .padding(.top, 4)
                    }
                }

                Spacer()

                Text(timeString)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundColor(Color(red: 34/255, green: 31/255, blue: 31/255))
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 10)
            }
            .padding(.horizontal, 8)
        }
        .onAppear { startPairingFlow() }
        .onDisappear {
            cancelAllTimers()
            PairingManager.shared.stopListening()
        }
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
        cancelAllTimers()
        secondsLeft       = 300
        isRegistering     = true
        registrationFailed = false
        retryCount        = 0

        // Generar código nuevo solo si no hay uno vigente
        let existing = PairingManager.shared.currentPairingCode
        let useExisting = !existing.isEmpty && !PairingManager.shared.isCodeExpired()
        let newCode = useExisting ? existing : PairingManager.shared.generatePairingCode()
        code = newCode

        registerCode(newCode)
        startCountdown()
    }

    /// Registra el código en Firebase; en caso de fallo reintenta con el MISMO código
    /// hasta un máximo de 5 intentos, luego muestra el código de todas formas.
    private func registerCode(_ codeToRegister: String) {
        PairingManager.shared.registerCodeInFirebase(code: codeToRegister) { success in
            DispatchQueue.main.async {
                if success {
                    isRegistering = false
                    registrationFailed = false
                    retryCount = 0
                    listenForPairing(code: codeToRegister)
                } else if retryCount < 4 {
                    // Reintentar con el MISMO código — sin cambiar el display
                    retryCount += 1
                    retryTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: false) { _ in
                        registerCode(codeToRegister)
                    }
                } else {
                    // Agotados los reintentos: mostrar código de todas formas
                    // El usuario puede ingresarlo manualmente; la app iOS también
                    // intenta leer de Firebase pero puede funcionar en modo offline.
                    isRegistering = false
                    registrationFailed = true
                }
            }
        }
    }

    private func startCountdown() {
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { t in
            DispatchQueue.main.async {
                secondsLeft -= 1
                if secondsLeft <= 0 {
                    t.invalidate()
                    startPairingFlow()   // código expiró → generar nuevo
                }
            }
        }
    }

    private func cancelAllTimers() {
        countdownTimer?.invalidate()
        retryTimer?.invalidate()
        countdownTimer = nil
        retryTimer = nil
    }

    private func listenForPairing(code: String) {
        PairingManager.shared.listenForPairing(code: code) { userId in
            guard userId != nil else { return }
            WKInterfaceDevice.current().play(.success)
            NotificationCenter.default.post(name: .pairingSuccessful, object: nil)
        }
    }
}
