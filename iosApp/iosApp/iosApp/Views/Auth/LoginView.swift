import SwiftUI
import FirebaseAuth

struct LoginView: View {
    let onLogin: () -> Void
    let onRegister: () -> Void
    let onBack: () -> Void

    @StateObject private var viewModel = AuthViewModel()
    @State private var email = ""
    @State private var password = ""
    @State private var passwordVisible = false
    @State private var showSnackbar = false
    @State private var snackbarMessage = ""

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
                        Text("Inicia Sesión")
                            .font(.manropeBold(size: 18))
                            .foregroundColor(Color.textDark)
                        Spacer()
                        Color.clear.frame(width: 24)
                    }
                    .frame(height: 40)
                    .padding(.horizontal, 20)
                    .padding(.top, 52)

                    Spacer().frame(height: 40)

                    // Form fields
                    VStack(spacing: 20) {
                        LoginField(
                            value: $email,
                            placeholder: "Email",
                            leadingIcon: "envelope",
                            keyboardType: .emailAddress,
                            isEnabled: viewModel.state != .loading
                        )

                        LoginField(
                            value: $password,
                            placeholder: "Contraseña",
                            leadingIcon: "lock",
                            trailingIcon: passwordVisible ? "eye" : "eye.slash",
                            onTrailingTap: { passwordVisible.toggle() },
                            isSecure: !passwordVisible,
                            isEnabled: viewModel.state != .loading
                        )
                    }
                    .padding(.horizontal, 32)

                    Spacer().frame(height: 40)

                    // Login button
                    Button(action: handleLogin) {
                        if case .loading = viewModel.state {
                            ProgressView().tint(.white)
                        } else {
                            Text("Entrar")
                                .font(.manropeSemiBold(size: 16))
                                .foregroundColor(.white)
                        }
                    }
                    .frame(width: 325, height: 59)
                    .background(Color.primaryBlue)
                    .cornerRadius(32)
                    .disabled(viewModel.state == .loading)

                    Spacer().frame(height: 24)

                    // Google Sign-In button
                    Button(action: {
                        viewModel.signInWithGoogle()
                    }) {
                        HStack(spacing: 8) {
                            GoogleLogo()
                            Text("Continuar con Google")
                                .font(.manrope(size: 14))
                                .foregroundColor(Color.textDark)
                        }
                    }
                    .frame(width: 325, height: 52)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 32)
                            .stroke(Color(hex: "#E5E5E5"), lineWidth: 1)
                    )
                    .disabled(viewModel.state == .loading)

                    Spacer().frame(height: 32)

                    // Register link
                    Button(action: onRegister) {
                        Text("¿No tienes cuenta? ")
                            .font(.manrope(size: 14))
                            .foregroundColor(Color.textDark)
                        +
                        Text("Regístrate")
                            .font(.manropeBold(size: 14))
                            .foregroundColor(Color.primaryBlue)
                    }

                    Spacer()
                }
            }

            // Snackbar
            if showSnackbar {
                VStack {
                    Spacer()
                    Text(snackbarMessage)
                        .font(.manrope(size: 14))
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.red)
                        .cornerRadius(8)
                        .padding(.bottom, 50)
                }
                .transition(.move(edge: .bottom))
                .animation(.easeInOut, value: showSnackbar)
            }
        }
        .navigationBarHidden(true)
        .onChange(of: viewModel.state) { newValue in
            handleStateChange(newValue)
        }
    }

    private func handleLogin() {
        guard !email.isEmpty, !password.isEmpty else {
            showError("Completa todos los campos")
            return
        }
        viewModel.loginWithEmail(email: email, password: password)
    }

    private func handleStateChange(_ state: AuthUiState) {
        switch state {
        case .success:
            onLogin()
        case .error(let message):
            showError(message)
            viewModel.clearError()
        default:
            break
        }
    }

    private func showError(_ message: String) {
        snackbarMessage = message
        showSnackbar = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            showSnackbar = false
        }
    }
}

struct LoginField: View {
    @Binding var value: String
    let placeholder: String
    let leadingIcon: String
    var trailingIcon: String? = nil
    var onTrailingTap: (() -> Void)? = nil
    var isSecure: Bool = false
    var keyboardType: UIKeyboardType = .default
    var isEnabled: Bool = true

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: leadingIcon)
                .font(.manrope(size: 18))
                .foregroundColor(Color.textDark)
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
        .cornerRadius(8)
        .disabled(!isEnabled)
    }
}

// Google logo with multicolor text
struct GoogleLogo: View {
    var body: some View {
        HStack(spacing: 0) {
            Text("G").foregroundColor(Color(hex: "#4285F4"))
            Text("o").foregroundColor(Color(hex: "#EA4335"))
            Text("o").foregroundColor(Color(hex: "#FBBC05"))
            Text("g").foregroundColor(Color(hex: "#4285F4"))
            Text("l").foregroundColor(Color(hex: "#34A853"))
            Text("e").foregroundColor(Color(hex: "#EA4335"))
        }
        .font(.manropeBold(size: 16))
    }
}
