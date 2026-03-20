import SwiftUI
import FirebaseAuth

struct ProfileView: View {
    @State private var showSignOutAlert = false
    @State private var nombre: String = ""
    @State private var apellidos: String = ""
    @State private var email: String = ""
    @State private var password: String = "•••••"
    @State private var nacimiento: String = "**/**/2000"
    @State private var celular: String = ""
    @State private var genero: String = "Hombre"
    @State private var frecuencia: String = "72"
    @State private var edad: String = "30"

    private var user: User? { Auth.auth().currentUser }

    var body: some View {
        ZStack {
            Color.dashBg.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 0) {
                    // Spacer top
                    Spacer().frame(height: 52)

                    // Back button (if needed in tab context - optional)
                    // Can be removed if always accessed via tab

                    Spacer().frame(height: 20)

                    // Avatar with edit button
                    ZStack(alignment: .bottomTrailing) {
                        Circle()
                            .fill(Color.dashBlue)
                            .frame(width: 100, height: 100)
                            .overlay(
                                Text(initials)
                                    .font(.manropeBold(size: 32))
                                    .foregroundColor(.white)
                            )

                        Circle()
                            .fill(Color.white)
                            .frame(width: 30, height: 30)
                            .overlay(
                                Image(systemName: "pencil")
                                    .font(.manrope(size: 12))
                                    .foregroundColor(.dashBlue)
                            )
                    }

                    Spacer().frame(height: 20)

                    // White form card
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Editar datos personales")
                            .font(.manropeBold(size: 20))
                            .foregroundColor(Color(hex: "#1A1A2E"))
                            .padding(.top, 24)
                            .padding(.horizontal, 24)

                        Spacer().frame(height: 24)

                        VStack(spacing: 14) {
                            // Nombre y Apellidos
                            HStack(spacing: 12) {
                                ProfileTextField(label: "Nombre", text: $nombre)
                                ProfileTextField(label: "Apellidos", text: $apellidos)
                            }

                            // Email (disabled)
                            ProfileTextField(label: "Email", text: $email, isEnabled: false)

                            // Contraseña
                            ProfileTextField(label: "Contraseña", text: $password, isSecure: true)

                            // Nacimiento y Celular
                            HStack(spacing: 12) {
                                ProfileTextField(label: "Nacimiento", text: $nacimiento)
                                ProfileTextField(label: "Celular", text: $celular, keyboardType: .phonePad)
                            }

                            // Genero
                            ProfileTextField(label: "Genero", text: $genero)

                            // Frecuencia y Edad
                            HStack(spacing: 12) {
                                ProfileTextField(label: "Frecuencia promedio", text: Binding(
                                    get: { "❤️ \(frecuencia)" },
                                    set: { frecuencia = $0.replacingOccurrences(of: "❤️ ", with: "") }
                                ), keyboardType: .numberPad)
                                ProfileTextField(label: "Edad", text: $edad, keyboardType: .numberPad)
                            }
                        }
                        .padding(.horizontal, 24)

                        Spacer().frame(height: 28)

                        // Buttons
                        HStack(spacing: 12) {
                            Button(action: {
                                // TODO: Save profile changes
                            }) {
                                Text("Guardar")
                                    .font(.manropeSemiBold(size: 15))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity, minHeight: 50)
                                    .background(Color.dashBlue)
                                    .cornerRadius(25)
                            }

                            Button(action: {
                                // TODO: Navigate to datos importantes
                            }) {
                                Text("Datos Importantes")
                                    .font(.manropeSemiBold(size: 12))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity, minHeight: 50)
                                    .background(Color.dashBlue)
                                    .cornerRadius(25)
                            }
                        }
                        .padding(.horizontal, 24)

                        Spacer().frame(height: 16)

                        // Sign out button
                        Button(action: { showSignOutAlert = true }) {
                            Text("Cerrar sesión")
                                .font(.manropeSemiBold(size: 14))
                                .foregroundColor(Color(hex: "#E53935"))
                                .padding(.vertical, 8)
                        }
                        .padding(.horizontal, 24)

                        Spacer().frame(height: 24)
                    }
                    .frame(maxWidth: .infinity)
                    .background(Color.white)
                    .cornerRadius(24, corners: [.topLeft, .topRight])
                }
            }
        }
        .navigationBarHidden(true)
        .alert("Cerrar sesión", isPresented: $showSignOutAlert) {
            Button("Cancelar", role: .cancel) {}
            Button("Salir", role: .destructive) { try? Auth.auth().signOut() }
        } message: {
            Text("¿Seguro que quieres cerrar sesión?")
        }
        .onAppear {
            loadUserData()
        }
    }

    private var initials: String {
        let parts = (user?.displayName ?? "").split(separator: " ")
        if parts.count >= 2 {
            return "\(parts[0].prefix(1))\(parts[1].prefix(1))".uppercased()
        } else if let first = parts.first {
            return String(first.prefix(1)).uppercased()
        }
        return "VS"
    }

    private func loadUserData() {
        let displayName = user?.displayName ?? ""
        let parts = displayName.split(separator: " ")
        nombre = String(parts.first ?? "")
        apellidos = parts.dropFirst().joined(separator: " ")
        email = user?.email ?? ""
    }
}

struct ProfileTextField: View {
    let label: String
    @Binding var text: String
    var isSecure: Bool = false
    var isEnabled: Bool = true
    var keyboardType: UIKeyboardType = .default

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.manrope(size: 11))
                .foregroundColor(Color(hex: "#B0B0B0"))
                .padding(.leading, 12)

            Group {
                if isSecure {
                    SecureField("", text: $text)
                } else {
                    TextField("", text: $text)
                        .keyboardType(keyboardType)
                }
            }
            .font(.manrope(size: 14))
            .foregroundColor(isEnabled ? Color(hex: "#1A1A2E") : Color(hex: "#8A8A8A"))
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background(isEnabled ? Color.white : Color(hex: "#F5F5F5"))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color(hex: "#E0E0E0"), lineWidth: 1)
            )
            .disabled(!isEnabled)
        }
    }
}

// Extension for rounded corners on specific sides
extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}
