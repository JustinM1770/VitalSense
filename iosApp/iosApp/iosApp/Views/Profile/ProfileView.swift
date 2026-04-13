#if os(iOS)
import SwiftUI
import FirebaseAuth
import FirebaseDatabase

struct ProfileView: View {
    @State private var showSignOutAlert  = false
    @State private var nombre: String    = ""
    @State private var apellidos: String = ""
    @State private var email: String     = ""
    @State private var password: String  = "•••••"
    @State private var nacimiento: String = ""
    @State private var celular: String   = ""
    @State private var genero: String    = "Hombre"
    @State private var frecuencia: String = "72"
    @State private var edad: String      = "30"
    @State private var tipoSangre: String = ""

    // Biometric toggle
    @State private var requireBiometric = false

    // Save states
    @State private var isSaving = false
    @State private var showSavedBanner = false

    // Doctor portal sharing
    @State private var isDoctorLoading  = false
    @State private var doctorShareUrl   = ""
    @State private var showDoctorSheet  = false
    @State private var showDoctorError  = false

    private var user: User? { Auth.auth().currentUser }
    private var uid: String { user?.uid ?? "" }

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 0) {
                    Spacer().frame(height: 52)

                    Spacer().frame(height: Spacing.xl)

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

                    Spacer().frame(height: Spacing.xl)

                    // White form card
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Información de tu perfil")
                            .font(.manropeSemiBold(size: 18))
                            .foregroundColor(Color.textNavy)
                            .padding(.top, 24)
                            .padding(.horizontal, Spacing.xxl)

                        Spacer().frame(height: Spacing.xxl)

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

                            // Genero y Tipo Sangre
                            HStack(spacing: 12) {
                                ProfileTextField(label: "Género", text: $genero)
                                ProfileTextField(label: "Tipo Sangre", text: $tipoSangre)
                            }

                            // Frecuencia y Edad
                            HStack(spacing: 12) {
                                ProfileTextField(label: "FC promedio (bpm)", text: $frecuencia, keyboardType: .numberPad)
                                ProfileTextField(label: "Edad", text: $edad, keyboardType: .numberPad)
                            }
                        }
                        .padding(.horizontal, Spacing.xxl)

                        Spacer().frame(height: Spacing.lg)

                        // ── Toggle biométrico ────────────────────────────
                        HStack {
                            Image(systemName: "faceid")
                                .foregroundColor(Color.dashBlue)
                                .font(.manropeBold(size: 20))
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Bloqueo con Face ID")
                                    .font(.manropeSemiBold(size: 14))
                                    .foregroundColor(Color(hex: "#1A1A2E"))
                                Text("Requiere Face ID al abrir la app")
                                    .font(.manrope(size: 11))
                                    .foregroundColor(Color(hex: "#8A8A8A"))
                            }
                            Spacer()
                            Toggle("", isOn: $requireBiometric)
                                .tint(Color.dashBlue)
                                .onChange(of: requireBiometric) { newValue in
                                    UserDefaults.standard.set(newValue, forKey: "require_biometric_\(uid)")
                                }
                        }
                        .padding(.horizontal, Spacing.xxl)

                        Spacer().frame(height: Spacing.xl)

                        // ── Archivos / Documentos médicos ───────────────
                        NavigationLink(destination: DocumentsView()) {
                            HStack(spacing: 12) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(Color.primaryBlue.opacity(0.12))
                                        .frame(width: 36, height: 36)
                                    Image(systemName: "doc.fill")
                                        .foregroundColor(Color.primaryBlue)
                                        .font(.system(size: 16))
                                }
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Mis documentos")
                                        .font(.manropeSemiBold(size: 14))
                                        .foregroundColor(Color(hex: "#1A1A2E"))
                                    Text("Recetas, laboratorios e imágenes médicas")
                                        .font(.manrope(size: 11))
                                        .foregroundColor(Color(hex: "#8A8A8A"))
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundColor(Color(hex: "#C0C0C0"))
                                    .font(.system(size: 13))
                            }
                            .padding(.horizontal, Spacing.xxl)
                        }

                        Spacer().frame(height: Spacing.xl)

                        // ── Contactos de emergencia ──────────────────────
                        NavigationLink(destination: EmergencyContactsView()) {
                            HStack(spacing: 12) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(Color(hex: "#EF4444").opacity(0.12))
                                        .frame(width: 36, height: 36)
                                    Image(systemName: "person.2.fill")
                                        .foregroundColor(Color(hex: "#EF4444"))
                                        .font(.system(size: 16))
                                }
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Contactos de emergencia")
                                        .font(.manropeSemiBold(size: 14))
                                        .foregroundColor(Color(hex: "#1A1A2E"))
                                    Text("Familia, médico, protección civil")
                                        .font(.manrope(size: 11))
                                        .foregroundColor(Color(hex: "#8A8A8A"))
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundColor(Color(hex: "#C0C0C0"))
                                    .font(.system(size: 13))
                            }
                            .padding(.horizontal, Spacing.xxl)
                        }

                        Spacer().frame(height: Spacing.xl)

                        // Buttons
                        HStack(spacing: 12) {
                            Button(action: saveProfile) {
                                if isSaving {
                                    ProgressView().tint(.white)
                                } else if showSavedBanner {
                                    Label("Guardado", systemImage: "checkmark")
                                        .font(.manropeSemiBold(size: 15))
                                        .foregroundColor(.white)
                                } else {
                                    Text("Guardar")
                                        .font(.manropeSemiBold(size: 15))
                                        .foregroundColor(.white)
                                }
                            }
                            .frame(maxWidth: .infinity, minHeight: 50)
                            .background(showSavedBanner ? Color(hex: "#10B981") : Color.primaryBlue)
                            .cornerRadius(32)
                            .disabled(isSaving)
                            .animation(.easeInOut, value: showSavedBanner)

                            Button(action: {
                                // Navigate to datos importantes
                            }) {
                                Text("Datos Importantes")
                                    .font(.manropeSemiBold(size: 12))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity, minHeight: 50)
                                    .background(Color.primaryBlue)
                                    .cornerRadius(32)
                            }
                        }
                        .padding(.horizontal, Spacing.xxl)

                        Spacer().frame(height: Spacing.lg)

                        // ── Compartir con médico ─────────────────────────
                        Button(action: createDoctorSession) {
                            HStack(spacing: 8) {
                                if isDoctorLoading {
                                    ProgressView().tint(.white).scaleEffect(0.8)
                                } else {
                                    Text("🩺")
                                    Text("Compartir con médico")
                                        .font(.manropeSemiBold(size: 14))
                                }
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, minHeight: 52)
                            .background(Color(hex: "#00838F"))
                            .cornerRadius(26)
                        }
                        .disabled(isDoctorLoading)
                        .padding(.horizontal, Spacing.xxl)

                        Spacer().frame(height: Spacing.md)

                        // Sign out button
                        Button(action: { showSignOutAlert = true }) {
                            Text("Cerrar sesión")
                                .font(.manropeSemiBold(size: 14))
                                .foregroundColor(Color(hex: "#E53935"))
                                .padding(.vertical, 8)
                        }
                        .padding(.horizontal, Spacing.xxl)

                        Spacer().frame(height: Spacing.xxl)
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
        .alert("No se pudo generar el enlace", isPresented: $showDoctorError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Verifica tu conexión e intenta de nuevo.")
        }
        .sheet(isPresented: $showDoctorSheet) {
            DoctorPortalSheet(url: doctorShareUrl)
        }
        .onAppear {
            loadUserData()
        }
    }

    // MARK: - Save Profile (UserDefaults + Firebase)
    private func saveProfile() {
        guard !uid.isEmpty else { return }
        isSaving = true

        let data: [String: Any] = [
            "nombre": nombre,
            "apellidos": apellidos,
            "nacimiento": nacimiento,
            "celular": celular,
            "genero": genero,
            "frecuencia": frecuencia,
            "tipoSangre": tipoSangre,
            "email": email,
            "cuestionarioCompleted": true,
        ]

        // Guardar a Firebase
        Database.database().reference().child("patients/\(uid)/profile").updateChildValues(data) { error, _ in
            isSaving = false
            if error == nil {
                // Guardar a UserDefaults (respaldo local)
                let ud = UserDefaults.standard
                ud.set(nombre,    forKey: "nombre_\(uid)")
                ud.set(apellidos, forKey: "apellidos_\(uid)")
                ud.set(nacimiento,forKey: "nacimiento_\(uid)")
                ud.set(celular,   forKey: "celular_\(uid)")
                ud.set(genero,    forKey: "genero_\(uid)")
                ud.set(frecuencia,forKey: "frecuencia_\(uid)")
                ud.set(tipoSangre,forKey: "tipo_sangre_\(uid)")
                ud.set(true,      forKey: "cuestionario_completed_\(uid)")

                // Update display name
                let req = Auth.auth().currentUser?.createProfileChangeRequest()
                req?.displayName = "\(nombre) \(apellidos)"
                req?.commitChanges(completion: nil)

                showSavedBanner = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                    showSavedBanner = false
                }
            }
        }
    }

    private func createDoctorSession() {
        isDoctorLoading = true
        Task {
            do {
                let url = try await DoctorSessionService().createSession()
                await MainActor.run {
                    doctorShareUrl  = url
                    showDoctorSheet = true
                    isDoctorLoading = false
                }
            } catch {
                await MainActor.run {
                    showDoctorError = true
                    isDoctorLoading = false
                }
            }
        }
    }

    private var initials: String {
        let parts = (user?.displayName ?? "").split(separator: " ")
        if parts.count >= 2 {
            return "\(parts[0].prefix(1))\(parts[1].prefix(1))".uppercased()
        } else if let first = parts.first {
            return String(first.prefix(1)).uppercased()
        }
        return "B"
    }

    private func loadUserData() {
        let displayName = user?.displayName ?? ""
        let parts = displayName.split(separator: " ")
        email = user?.email ?? ""

        // Cargar desde UserDefaults primero (datos del cuestionario)
        let ud = UserDefaults.standard
        nombre = ud.string(forKey: "nombre_\(uid)") ?? String(parts.first ?? "")
        apellidos = ud.string(forKey: "apellidos_\(uid)") ?? parts.dropFirst().joined(separator: " ")
        nacimiento = ud.string(forKey: "nacimiento_\(uid)") ?? ""
        celular = ud.string(forKey: "celular_\(uid)") ?? ""
        genero = ud.string(forKey: "genero_\(uid)") ?? "Hombre"
        frecuencia = ud.string(forKey: "frecuencia_\(uid)") ?? "72"
        tipoSangre = ud.string(forKey: "tipo_sangre_\(uid)") ?? ""
        requireBiometric = ud.bool(forKey: "require_biometric_\(uid)")

        // Calcular edad a partir de nacimiento
        if !nacimiento.isEmpty {
            let formatter = DateFormatter()
            formatter.dateFormat = "dd/MM/yyyy"
            if let birthDate = formatter.date(from: nacimiento) {
                let ageComponents = Calendar.current.dateComponents([.year], from: birthDate, to: Date())
                edad = "\(ageComponents.year ?? 30)"
            }
        }

        // Fallback: cargar de Firebase si UserDefaults vacío
        if nombre.isEmpty {
            Database.database().reference().child("patients/\(uid)/profile")
                .observeSingleEvent(of: .value) { snap in
                    guard let dict = snap.value as? [String: Any] else { return }
                    nombre = dict["nombre"] as? String ?? nombre
                    apellidos = dict["apellidos"] as? String ?? apellidos
                    nacimiento = dict["nacimiento"] as? String ?? nacimiento
                    celular = dict["celular"] as? String ?? celular
                    genero = dict["genero"] as? String ?? genero
                    frecuencia = dict["frecuencia"] as? String ?? frecuencia
                    tipoSangre = dict["tipoSangre"] as? String ?? tipoSangre
                }
        }
    }
}

struct ProfileTextField: View {
    let label: String
    @Binding var text: String
    var isSecure: Bool = false
    var isEnabled: Bool = true
    var keyboardType: UIKeyboardType = .default

    var body: some View {
        ZStack(alignment: .topLeading) {
            // Borde y fondo
            RoundedRectangle(cornerRadius: 8)
                .fill(isEnabled ? Color.white : Color(hex: "#F5F5F5"))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.borderGray, lineWidth: 1)
                )
                .frame(minHeight: 56)

            VStack(alignment: .leading, spacing: 0) {
                // Floating label
                Text(label)
                    .font(.manrope(size: 10))
                    .foregroundColor(Color.textSecondary)
                    .padding(.horizontal, 12)
                    .padding(.top, 8)

                Group {
                    if isSecure {
                        SecureField("", text: $text)
                    } else {
                        TextField("", text: $text)
                            .keyboardType(keyboardType)
                    }
                }
                .font(.manropeSemiBold(size: 14))
                .foregroundColor(isEnabled ? Color.textPrimary : Color(hex: "#8A8A8A"))
                .padding(.horizontal, 12)
                .padding(.bottom, 8)
                .disabled(!isEnabled)
            }
        }
    }
}

// Extension for rounded corners on specific sides
extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

// MARK: - Doctor Portal Sheet
struct DoctorPortalSheet: View {
    let url: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 20) {
            Capsule()
                .fill(Color(hex: "#E0E0E0"))
                .frame(width: 40, height: 4)
                .padding(.top, 12)

            Image(systemName: "stethoscope")
                .font(.manropeBold(size: 40))
                .foregroundColor(Color(hex: "#00838F"))

            Text("Portal para el médico")
                .font(.manropeBold(size: 20))

            Text("El médico abre este enlace en su celular para ver tu historial clínico en tiempo real. Válido 2 horas.")
                .font(.manrope(size: 14))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, Spacing.xxl)

            Text(url)
                .font(.manrope(size: 11))
                .foregroundColor(Color(hex: "#1565C0"))
                .multilineTextAlignment(.center)
                .padding(.horizontal, Spacing.xxl)

            VStack(spacing: 12) {
                if let urlObj = URL(string: url) {
                    Link(destination: urlObj) {
                        HStack {
                            Image(systemName: "safari")
                            Text("Abrir portal médico")
                        }
                        .font(.manropeSemiBold(size: 15))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(Color(hex: "#00838F"))
                        .cornerRadius(26)
                    }
                }

                ShareLink(item: url) {
                    HStack {
                        Image(systemName: "square.and.arrow.up")
                        Text("Compartir enlace")
                    }
                    .font(.manropeSemiBold(size: 15))
                    .foregroundColor(Color(hex: "#00838F"))
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .overlay(
                        RoundedRectangle(cornerRadius: 26)
                            .stroke(Color(hex: "#00838F"), lineWidth: 1.5)
                    )
                }
            }
            .padding(.horizontal, Spacing.xxl)

            Spacer()
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.hidden)
    }
}

// MARK: - Rounded Corner Helper
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

#endif
