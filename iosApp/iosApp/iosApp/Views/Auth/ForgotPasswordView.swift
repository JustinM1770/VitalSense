#if os(iOS)
import SwiftUI
import FirebaseAuth

struct ForgotPasswordView: View {
    let onBack: () -> Void

    @State private var email = ""
    @State private var isLoading = false
    @State private var showSuccess = false
    @State private var errorMessage: String?

    private let primaryBtn = Color.primaryBlue
    private let textDark   = Color.textDark

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            VStack(spacing: 0) {
                // ── Header ──────────────────────────────────────────
                HStack {
                    Button(action: onBack) {
                        Image(systemName: "arrow.left")
                            .font(.manrope(size: 18))
                            .foregroundColor(textDark)
                    }
                    Spacer()
                    Text("Recuperar Contraseña")
                        .font(.manropeBold(size: 18))
                        .foregroundColor(textDark)
                    Spacer()
                    Color.clear.frame(width: 24)
                }
                .frame(height: 40)
                .padding(.horizontal, Spacing.xl)
                .padding(.top, 52)

                Spacer().frame(height: 60)

                // ── Ícono ───────────────────────────────────────────
                ZStack {
                    Circle()
                        .fill(primaryBtn.opacity(0.10))
                        .frame(width: 88, height: 88)
                    Image(systemName: showSuccess ? "checkmark.circle.fill" : "lock.rotation")
                        .font(.manropeBold(size: 38))
                        .foregroundColor(primaryBtn)
                }

                Spacer().frame(height: Spacing.xxl)

                if showSuccess {
                    // ── Estado de éxito ──────────────────────────────
                    VStack(spacing: 12) {
                        Text("¡Enlace enviado!")
                            .font(.manropeBold(size: 22))
                            .foregroundColor(textDark)

                        Text("Revisa tu correo electrónico\n\(email)\npara restablecer tu contraseña.")
                            .font(.manrope(size: 14))
                            .foregroundColor(Color(hex: "#6B7A8D"))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)

                        Spacer().frame(height: 32)

                        Button(action: onBack) {
                            Text("Volver al inicio de sesión")
                                .font(.manropeSemiBold(size: 16))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 56)
                                .background(primaryBtn)
                                .cornerRadius(32)
                        }
                        .padding(.horizontal, Spacing.xxl)
                    }
                } else {
                    // ── Formulario ───────────────────────────────────
                    VStack(spacing: 12) {
                        Text("Ingresa tu correo electrónico y te enviaremos un enlace para restablecer tu contraseña.")
                            .font(.manrope(size: 14))
                            .foregroundColor(Color(hex: "#6B7A8D"))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)

                        Spacer().frame(height: Spacing.xl)

                        HStack(spacing: 12) {
                            Image(systemName: "envelope")
                                .font(.manrope(size: 18))
                                .foregroundColor(textDark.opacity(0.5))
                                .frame(width: 22)

                            TextField("Email", text: $email)
                                .keyboardType(.emailAddress)
                                .font(.manrope(size: 14))
                                .foregroundColor(textDark)
                                .autocapitalization(.none)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 16)
                        .background(Color.surfaceGray)
                        .cornerRadius(10)
                        .padding(.horizontal, Spacing.xxl)

                        if let error = errorMessage {
                            Text(error)
                                .font(.manrope(size: 12))
                                .foregroundColor(Color(hex: "#FF4560"))
                                .padding(.horizontal, Spacing.xxl)
                        }

                        Spacer().frame(height: 28)

                        Button(action: sendResetLink) {
                            if isLoading {
                                ProgressView().tint(.white)
                            } else {
                                Text("Enviar enlace")
                                    .font(.manropeSemiBold(size: 16))
                                    .foregroundColor(.white)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(email.isEmpty ? primaryBtn.opacity(0.4) : primaryBtn)
                        .cornerRadius(32)
                        .padding(.horizontal, Spacing.xxl)
                        .disabled(email.isEmpty || isLoading)
                    }
                }

                Spacer()
            }
        }
        .navigationBarHidden(true)
    }

    private func sendResetLink() {
        guard !email.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Ingresa tu correo electrónico"
            return
        }
        isLoading = true
        errorMessage = nil

        Auth.auth().sendPasswordReset(withEmail: email.trimmingCharacters(in: .whitespaces)) { error in
            isLoading = false
            if let error {
                errorMessage = error.localizedDescription
            } else {
                withAnimation(.easeInOut(duration: 0.3)) {
                    showSuccess = true
                }
            }
        }
    }
}

#endif
