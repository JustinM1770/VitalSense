#if os(iOS)
import SwiftUI
import FirebaseAuth

struct RegisterView: View {
    let onRegister: () -> Void
    let onBack: () -> Void
    let onLogin: () -> Void

    @StateObject private var viewModel = AuthViewModel()
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var passwordVisible = false
    @State private var termsAccepted = false
    @State private var isLoading = false
    @State private var errorMessage: String?
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
                        Text("Regístrate")
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
                    .padding(.horizontal, Spacing.xxl)

                    Spacer().frame(height: Spacing.xl)

                    // ── Terms checkbox ───────────────────────────────────
                    Button(action: { termsAccepted.toggle() }) {
                        HStack(alignment: .top, spacing: 10) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(termsAccepted ? primaryBtn : Color.white)
                                    .frame(width: 22, height: 22)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(Color.textDark, lineWidth: 1.5)
                                    )

                                if termsAccepted {
                                    Image(systemName: "checkmark")
                                        .font(.manropeBold(size: 12))
                                        .foregroundColor(.white)
                                }
                            }

                            (
                                Text("Acepto los ")
                                    .foregroundColor(textDark)
                                + Text("Términos de Servicio")
                                    .foregroundColor(primaryBtn)
                                    .fontWeight(.semibold)
                                + Text(" y ")
                                    .foregroundColor(textDark)
                                + Text("Políticas de Privacidad")
                                    .foregroundColor(primaryBtn)
                                    .fontWeight(.semibold)
                            )
                            .font(.manrope(size: 13))
                            .lineSpacing(4)
                            .multilineTextAlignment(.leading)

                            Spacer()
                        }
                    }
                    .padding(.horizontal, Spacing.xxl)

                    if let error = errorMessage {
                        Text(error)
                            .foregroundColor(.red)
                            .font(.manrope(size: 12))
                            .padding(.top, 12)
                    }

                    Spacer().frame(height: 40)

                    // ── Botón Regístrate ─────────────────────────────────
                    Button(action: register) {
                        if isLoading {
                            ProgressView().tint(.white)
                        } else {
                            Text("Regístrate")
                                .font(.manropeSemiBold(size: 16))
                                .foregroundColor(.white)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(termsAccepted && !isLoading ? primaryBtn : primaryBtn.opacity(0.4))
                    .cornerRadius(32)
                    .padding(.horizontal, Spacing.xxl)
                    .disabled(!termsAccepted || isLoading)

                    Spacer().frame(height: Spacing.xxl)

                    // ── "O" separator ────────────────────────────────────
                    HStack {
                        Rectangle().fill(dividerClr).frame(height: 1)
                        Text("  O  ")
                            .font(.manropeSemiBold(size: 13))
                            .foregroundColor(Color(hex: "#8A8A8A"))
                        Rectangle().fill(dividerClr).frame(height: 1)
                    }
                    .padding(.horizontal, 32)

                    Spacer().frame(height: Spacing.xxl)

                    // ── Google Button ────────────────────────────────────
                    Button(action: { viewModel.signInWithGoogle() }) {
                        HStack(spacing: 4) {
                            Text("Regístrate con ")
                                .font(.manrope(size: 14))
                                .foregroundColor(textDark)
                            GoogleLogo()
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 32)
                                .stroke(dividerClr, lineWidth: 1)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 32))
                    }
                    .padding(.horizontal, Spacing.xxl)

                    Spacer().frame(height: Spacing.md)

                    // ── Facebook Button ──────────────────────────────────
                    Button(action: { showFacebookAlert = true }) {
                        HStack(spacing: 4) {
                            Text("Regístrate con ")
                                .font(.manrope(size: 14))
                                .foregroundColor(textDark)
                            Text("Facebook")
                                .font(.manropeBold(size: 14))
                                .foregroundColor(Color(hex: "#1877F2"))
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 32)
                                .stroke(dividerClr, lineWidth: 1)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 32))
                    }
                    .padding(.horizontal, Spacing.xxl)

                    Spacer().frame(height: Spacing.xxl)

                    // ── Login link ───────────────────────────────────────
                    Button(action: onLogin) {
                        Text("¿Ya tienes cuenta? ")
                            .font(.manrope(size: 14))
                            .foregroundColor(textDark)
                        +
                        Text("Inicia Sesión")
                            .font(.manropeSemiBold(size: 14))
                            .foregroundColor(primaryBtn)
                    }
                    .padding(.bottom, 40)
                }
            }
        }
        .alert("Próximamente", isPresented: $showFacebookAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("El registro con Facebook estará disponible en la próxima versión.")
        }
        .navigationBarHidden(true)
        .onChange(of: viewModel.state) { newValue in
            switch newValue {
            case .success: onRegister()
            case .error(let msg): errorMessage = msg; viewModel.clearError()
            default: break
            }
        }
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

// RegisterField shares the same floating label design as LoginField
// for visual consistency (Nielsen #4: Consistency and Standards)
struct RegisterField: View {
    @Binding var value: String
    let placeholder: String
    let leadingIcon: String
    var trailingIcon: String? = nil
    var onTrailingTap: (() -> Void)? = nil
    var isSecure: Bool = false
    var keyboardType: UIKeyboardType = .default

    @FocusState private var isFocused: Bool
    private var isFloated: Bool { isFocused || !value.isEmpty }

    var body: some View {
        ZStack(alignment: .leading) {
            Text(placeholder)
                .font(isFloated ? .manrope(size: 10) : .manrope(size: 14))
                .foregroundColor(isFocused ? Color.primaryBlue : Color.textSecondary)
                .offset(x: 38, y: isFloated ? -12 : 0)
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
                .stroke(
                    isFocused ? Color.primaryBlue : Color.textDark,
                    lineWidth: isFocused ? 1.5 : 1
                )
                .animation(.easeInOut(duration: 0.2), value: isFocused)
        )
        .contentShape(Rectangle())
        .onTapGesture { isFocused = true }
    }
}

#endif
