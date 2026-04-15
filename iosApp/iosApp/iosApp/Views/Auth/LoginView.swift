#if os(iOS)
import SwiftUI
import FirebaseAuth

struct LoginView: View {
    let onLogin: () -> Void
    let onRegister: () -> Void
    let onBack: () -> Void
    var onForgotPassword: (() -> Void)? = nil

    @StateObject private var viewModel = AuthViewModel()
    @State private var email = ""
    @State private var password = ""
    @State private var passwordVisible = false
    @State private var showSnackbar = false
    @State private var snackbarMessage = ""
    @State private var showFacebookAlert = false

    // Design tokens (idénticos a Android)
    private let textDark   = Color.textDark
    private let inputBg    = Color.surfaceGray
    private let primaryBtn = Color.primaryBlue
    private let dividerClr = Color.divider

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 0) {
                        // ── Header ──────────────────────────────────────────
                        HStack {
                            Button(action: onBack) {
                                Image(systemName: "arrow.left")
                                    .font(.manrope(size: 18))
                                    .foregroundColor(textDark)
                            }
                            Spacer()
                            Text("Iniciar Sesión")
                                .font(.manropeBold(size: 18))
                                .foregroundColor(textDark)
                            Spacer()
                            Color.clear.frame(width: 24)
                        }
                        .frame(height: 40)
                        .padding(.horizontal, Spacing.xl)
                        .padding(.top, 52)

                        Spacer().frame(height: 37)

                        // ── Form fields ─────────────────────────────────────
                        VStack(spacing: 16) {
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
                        .padding(.horizontal, Spacing.xxl)

                        Spacer().frame(height: 10)

                        // ── Olvidaste contraseña ────────────────────────────
                        if let onForgot = onForgotPassword {
                            HStack {
                                Spacer()
                                Button(action: onForgot) {
                                    Text("Olvidaste tu contraseña?")
                                        .font(.manrope(size: 12))
                                        .fontWeight(.medium)
                                        .foregroundColor(Color.dashBgAlt)
                                }
                                .padding(.trailing, 24)
                            }
                        }

                        Spacer().frame(height: 28)

                        // ── Botón Iniciar Sesión ────────────────────────────
                        Button(action: handleLogin) {
                            if case .loading = viewModel.state {
                                ProgressView().tint(.white)
                            } else {
                                Text("Iniciar Sesión")
                                    .font(.manropeSemiBold(size: 16))
                                    .foregroundColor(.white)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 59)
                        .background(
                            viewModel.state == .loading
                                ? primaryBtn.opacity(0.6)
                                : primaryBtn
                        )
                        .cornerRadius(32)
                        .scaleEffect(viewModel.state == .loading ? 0.98 : 1.0)
                        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: viewModel.state == .loading)
                        .padding(.horizontal, Spacing.xxl)
                        .disabled(viewModel.state == .loading)

                        Spacer().frame(height: Spacing.xl)

                        // ── "No tienes cuenta?" ─────────────────────────────
                        Button(action: onRegister) {
                            Text("No tienes cuenta?  ")
                                .font(.manrope(size: 14))
                                .foregroundColor(textDark)
                            +
                            Text("Registrate")
                                .font(.manropeSemiBold(size: 14))
                                .foregroundColor(primaryBtn)
                        }

                        Spacer().frame(height: Spacing.xxl)

                        // ── Divisor con círculo ─────────────────────────────
                        HStack {
                            Rectangle().fill(dividerClr).frame(height: 1)
                            Circle()
                                .stroke(dividerClr, lineWidth: 1)
                                .frame(width: 20, height: 20)
                                .padding(.horizontal, 12)
                            Rectangle().fill(dividerClr).frame(height: 1)
                        }
                        .padding(.horizontal, Spacing.xxl)

                        Spacer().frame(height: Spacing.xl)

                        // ── Botón Google ─────────────────────────────────────
                        SocialLoginButton(
                            action: { viewModel.signInWithGoogle() },
                            isEnabled: viewModel.state != .loading
                        ) {
                            GoogleLogo()
                            Spacer().frame(width: 12)
                            Text("Continuar con Google")
                                .font(.manropeSemiBold(size: 14))
                                .foregroundColor(textDark)
                        }

                        Spacer().frame(height: Spacing.md)

                        // ── Botón Facebook ───────────────────────────────────
                        SocialLoginButton(
                            action: { showFacebookAlert = true },
                            isEnabled: viewModel.state != .loading
                        ) {
                            Text("Facebook")
                                .font(.manropeBold(size: 18))
                                .foregroundColor(Color(hex: "#1877F2"))
                            Spacer().frame(width: 12)
                            Text("Continuar con Facebook")
                                .font(.manropeSemiBold(size: 14))
                                .foregroundColor(textDark)
                        }
                        .alert("Próximamente", isPresented: $showFacebookAlert) {
                            Button("OK", role: .cancel) {}
                        } message: {
                            Text("El inicio de sesión con Facebook estará disponible en la próxima versión.")
                        }

                        Spacer().frame(height: 40)
                    }
                }

                // Snackbar — UI/UX: Help users recognize and recover from errors (Nielsen #9)
                // Shows icon + message with smooth spring entry and auto-dismissal
                if showSnackbar {
                    VStack {
                        Spacer()
                        HStack(spacing: 10) {
                            Image(systemName: "exclamationmark.circle.fill")
                                .font(.system(size: 16))
                                .foregroundColor(.white)
                            Text(snackbarMessage)
                                .font(.manrope(size: 14))
                                .foregroundColor(.white)
                                .multilineTextAlignment(.leading)
                            Spacer()
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
                        .background(Color(hex: "#D32F2F"))
                        .cornerRadius(12)
                        .shadow(color: Color.black.opacity(0.18), radius: 8, x: 0, y: 4)
                        .padding(.horizontal, Spacing.xl)
                        .padding(.bottom, 50)
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .navigationBarHidden(true)
            .onChange(of: viewModel.state) { newValue in
                handleStateChange(newValue)
            }
    }

    private func handleLogin() {
        guard !email.isEmpty, !password.isEmpty else {
            HapticFeedback.warning()
            showError("Completa todos los campos")
            return
        }
        HapticFeedback.medium()
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
        HapticFeedback.error()
        snackbarMessage = message
        withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
            showSnackbar = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            withAnimation(.easeOut(duration: 0.3)) {
                showSnackbar = false
            }
        }
    }
}

// MARK: - Social Login Button (reutilizable)
struct SocialLoginButton<Content: View>: View {
    let action: () -> Void
    let isEnabled: Bool
    @ViewBuilder let content: () -> Content

    private let dividerClr = Color.divider

    var body: some View {
        Button(action: action) {
            HStack(spacing: 0) {
                content()
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(dividerClr, lineWidth: 1)
            )
        }
        .padding(.horizontal, Spacing.xxl)
        .disabled(!isEnabled)
    }
}

// MARK: - Login Field (Floating Label)
// UI/UX principle: Floating labels keep context visible even after the user starts typing,
// reducing cognitive load vs. plain placeholders that disappear on focus.
// This pattern follows Google Material Design and Apple HIG (iOS 17 form styling).
struct LoginField: View {
    @Binding var value: String
    let placeholder: String
    let leadingIcon: String
    var trailingIcon: String? = nil
    var onTrailingTap: (() -> Void)? = nil
    var isSecure: Bool = false
    var keyboardType: UIKeyboardType = .default
    var isEnabled: Bool = true
    var errorMessage: String? = nil

    @FocusState private var isFocused: Bool

    private var isFloated: Bool { isFocused || !value.isEmpty }

    private var borderColor: Color {
        if errorMessage != nil { return Color.sosRed }
        if isFocused { return Color.primaryBlue }
        return Color.textDark
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ZStack(alignment: .leading) {
                // Floating label
                Text(placeholder)
                    .font(isFloated ? .manrope(size: 10) : .manrope(size: 14))
                    .foregroundColor(
                        errorMessage != nil ? Color.sosRed
                        : isFocused ? Color.primaryBlue
                        : Color.textSecondary
                    )
                    .offset(
                        x: 38,
                        y: isFloated ? -12 : 0
                    )
                    .animation(.spring(response: 0.25, dampingFraction: 0.7), value: isFloated)
                    .zIndex(1)

                HStack(spacing: 12) {
                    Image(systemName: leadingIcon)
                        .font(.system(size: 16))
                        .foregroundColor(isFocused ? Color.primaryBlue : Color.textDark.opacity(0.45))
                        .frame(width: 22)
                        .animation(.easeInOut(duration: 0.2), value: isFocused)

                    Group {
                        if isSecure {
                            SecureField("", text: $value)
                        } else {
                            TextField("", text: $value)
                                .keyboardType(keyboardType)
                        }
                    }
                    .font(.manrope(size: 14))
                    .foregroundColor(Color.textDark)
                    .focused($isFocused)
                    .padding(.top, isFloated ? 10 : 0)
                    .animation(.spring(response: 0.25, dampingFraction: 0.7), value: isFloated)

                    if let trailingIcon {
                        Button(action: {
                            HapticFeedback.light()
                            onTrailingTap?()
                        }) {
                            Image(systemName: trailingIcon)
                                .font(.system(size: 16))
                                .foregroundColor(Color.textDark.opacity(0.45))
                                .frame(width: 22)
                        }
                        .minimumTapTarget()
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
            }
            .frame(height: 58)
            .background(Color.surfaceGray)
            .cornerRadius(6)
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(borderColor, lineWidth: isFocused ? 1.5 : 1)
                    .animation(.easeInOut(duration: 0.2), value: isFocused)
            )
            .contentShape(Rectangle())
            .onTapGesture { isFocused = true }
            .disabled(!isEnabled)

            // Error message
            if let msg = errorMessage {
                HStack(spacing: 4) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .font(.system(size: 11))
                    Text(msg)
                        .font(.manrope(size: 11))
                }
                .foregroundColor(Color.sosRed)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
    }
}

// Google logo with multicolor text
struct GoogleLogo: View {
    var body: some View {
        HStack(spacing: 0) {
            Text("G").foregroundColor(Color(hex: "#4285F4"))
            Text("o").foregroundColor(Color.errorRed)
            Text("o").foregroundColor(Color(hex: "#FBBC05"))
            Text("g").foregroundColor(Color(hex: "#4285F4"))
            Text("l").foregroundColor(Color.successGreen)
            Text("e").foregroundColor(Color.errorRed)
        }
        .font(.manropeBold(size: 18))
    }
}

#endif
