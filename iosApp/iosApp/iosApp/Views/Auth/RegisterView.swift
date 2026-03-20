import SwiftUI
import FirebaseAuth

struct RegisterView: View {
    let onRegister: () -> Void
    let onBack: () -> Void
    let onLogin: () -> Void

    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var passwordVisible = false
    @State private var termsAccepted = false
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 0) {
                    // Header
                    HStack {
                        Button(action: onBack) {
                            Image(systemName: "arrow.left")
                                .font(.manrope(size: 18))
                                .foregroundColor(Color.textDark)
                        }
                        Spacer()
                        Text("Registrate")
                            .font(.manropeBold(size: 18))
                            .foregroundColor(Color.textDark)
                        Spacer()
                        Color.clear.frame(width: 24)
                    }
                    .frame(height: 40)
                    .padding(.horizontal, 20)
                    .padding(.top, 52)

                    Spacer().frame(height: 37)

                    // Form fields
                    VStack(spacing: 16) {
                        RegisterField(
                            value: $name,
                            placeholder: "Nombre",
                            leadingIcon: "person"
                        )

                        RegisterField(
                            value: $email,
                            placeholder: "Email",
                            leadingIcon: "envelope",
                            keyboardType: .emailAddress
                        )

                        RegisterField(
                            value: $password,
                            placeholder: "Contraseña",
                            leadingIcon: "lock",
                            trailingIcon: passwordVisible ? "eye" : "eye.slash",
                            onTrailingTap: { passwordVisible.toggle() },
                            isSecure: !passwordVisible
                        )
                    }
                    .padding(.horizontal, 24)

                    Spacer().frame(height: 20)

                    // Terms checkbox
                    Button(action: { termsAccepted.toggle() }) {
                        HStack(alignment: .top, spacing: 10) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 4)
                                    .stroke(termsAccepted ? Color.primaryBlue : Color(hex: "#B0B0B0"), lineWidth: 1.5)
                                    .frame(width: 22, height: 22)

                                if termsAccepted {
                                    Image(systemName: "checkmark")
                                        .font(.manropeBold(size: 12))
                                        .foregroundColor(Color.primaryBlue)
                                }
                            }

                            HStack(spacing: 2) {
                                Text("Acepto los ")
                                    .foregroundColor(Color.textDark)
                                Text("Terminos de Servicio")
                                    .foregroundColor(Color.primaryBlue)
                                    .fontWeight(.semibold)
                                Text(" y ")
                                    .foregroundColor(Color.textDark)
                                Text("Politicas de Privacidad")
                                    .foregroundColor(Color.primaryBlue)
                                    .fontWeight(.semibold)
                            }
                            .font(.manrope(size: 13))
                            .lineSpacing(4)
                            .multilineTextAlignment(.leading)

                            Spacer()
                        }
                    }
                    .padding(.horizontal, 24)

                    if let error = errorMessage {
                        Text(error)
                            .foregroundColor(.red)
                            .font(.manrope(size: 12))
                            .padding(.top, 12)
                    }

                    Spacer()

                    Spacer().frame(height: 40)

                    // Register button
                    Button(action: register) {
                        if isLoading {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Text("Registrate")
                                .font(.manropeSemiBold(size: 16))
                                .foregroundColor(.white)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 59)
                    .background(termsAccepted && !isLoading ? Color.primaryBlue : Color.primaryBlue.opacity(0.4))
                    .cornerRadius(32)
                    .padding(.horizontal, 24)
                    .disabled(!termsAccepted || isLoading)

                    Spacer().frame(height: 20)

                    // Login link
                    Button(action: onLogin) {
                        Text("Ya tienes cuenta?  ")
                            .font(.manrope(size: 14))
                            .foregroundColor(Color.textDark)
                        +
                        Text("Inicia Sesión")
                            .font(.manropeSemiBold(size: 14))
                            .foregroundColor(Color.primaryBlue)
                    }
                    .padding(.bottom, 40)
                }
            }
        }
        .navigationBarHidden(true)
    }

    private func register() {
        guard !name.isEmpty, !email.isEmpty, !password.isEmpty else {
            errorMessage = "Completa todos los campos"
            return
        }
        guard termsAccepted else {
            errorMessage = "Debes aceptar los términos"
            return
        }
        isLoading = true
        errorMessage = nil
        Auth.auth().createUser(withEmail: email, password: password) { result, error in
            isLoading = false
            if let error {
                errorMessage = error.localizedDescription
                return
            }
            let req = Auth.auth().currentUser?.createProfileChangeRequest()
            req?.displayName = name
            req?.commitChanges { _ in onRegister() }
        }
    }
}

struct RegisterField: View {
    @Binding var value: String
    let placeholder: String
    let leadingIcon: String
    var trailingIcon: String? = nil
    var onTrailingTap: (() -> Void)? = nil
    var isSecure: Bool = false
    var keyboardType: UIKeyboardType = .default

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: leadingIcon)
                .font(.manrope(size: 18))
                .foregroundColor(Color.textDark.opacity(0.5))
                .frame(width: 22)

            Group {
                if isSecure {
                    SecureField(placeholder, text: $value)
                } else {
                    TextField(placeholder, text: $value)
                        .keyboardType(keyboardType)
                }
            }
            .font(.manrope(size: 14))
            .foregroundColor(Color.textDark)

            if let trailingIcon = trailingIcon {
                Button(action: { onTrailingTap?() }) {
                    Image(systemName: trailingIcon)
                        .font(.manrope(size: 18))
                        .foregroundColor(Color.textDark.opacity(0.5))
                        .frame(width: 22)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .background(Color.inputBg)
        .cornerRadius(10)
    }
}
