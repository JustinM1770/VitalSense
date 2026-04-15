#if os(iOS)
import SwiftUI
import LocalAuthentication
import FirebaseAuth

struct SplashView: View {
    let onFinish: () -> Void
    @State private var opacity  = 0.0
    @State private var showFaceID = false
    @State private var authError: String? = nil

    var body: some View {
        ZStack {
            Color(hex: "#FFFEFE").ignoresSafeArea()

            // Elipse decorativa de fondo (Figma: Ellipse 259 #FFFEFE)
            Ellipse()
                .fill(Color.onboardingButtonText.opacity(0.4))
                .frame(width: 350, height: 350)
                .offset(y: -100)
                .blur(radius: 60)

            VStack(spacing: 16) {
                // Logo — usa asset si existe, sino SF Symbol
                if UIImage(named: "ic_logo_eye") != nil {
                    Image("ic_logo_eye")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 180, height: 120)
                } else {
                    Image(systemName: "eye.circle.fill")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 90, height: 90)
                        .foregroundColor(Color.primaryBlue)
                }

                // "BioMetric AI" branding
                HStack(spacing: 8) {
                    Text("BioMetric")
                        .font(.manropeBold(size: 34))
                        .foregroundColor(Color(hex: "#0F172A"))
                    Text("AI")
                        .font(.manropeBold(size: 34))
                        .foregroundColor(Color.primaryBlue)
                }

                if showFaceID {
                    VStack(spacing: 8) {
                        Image(systemName: "faceid")
                            .font(.manropeBold(size: 36))
                            .foregroundColor(Color.primaryBlue)
                            .padding(.top, 16)

                        Text("Autenticando...")
                            .font(.manrope(size: 14))
                            .foregroundColor(Color.textSecondary)

                        if let error = authError {
                            Text(error)
                                .font(.manrope(size: 12))
                                .foregroundColor(Color.heartRateRed)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 32)

                            Button("Reintentar") {
                                authError = nil
                                authenticateWithBiometrics()
                            }
                            .font(.manropeSemiBold(size: 14))
                            .foregroundColor(Color.primaryBlue)
                            .padding(.top, 4)

                            Button("Continuar sin Face ID") {
                                onFinish()
                            }
                            .font(.manrope(size: 13))
                            .foregroundColor(Color.textSecondary)
                        }
                    }
                }
            }
            .opacity(opacity)
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 0.8)) { opacity = 1.0 }

            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                let user = Auth.auth().currentUser
                guard user != nil else {
                    onFinish()
                    return
                }

                let uid = user!.uid
                let hasBiometric = UserDefaults.standard.bool(forKey: "require_biometric_\(uid)")

                if hasBiometric {
                    showFaceID = true
                    authenticateWithBiometrics()
                } else {
                    onFinish()
                }
            }
        }
    }

    private func authenticateWithBiometrics() {
        let ctx = LAContext()
        var error: NSError?

        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            onFinish()
            return
        }

        ctx.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: "Verifica tu identidad para acceder a BioMetric AI"
        ) { success, evalError in
            DispatchQueue.main.async {
                if success {
                    onFinish()
                } else {
                    authError = evalError?.localizedDescription ?? "Autenticación fallida"
                }
            }
        }
    }
}

#endif
