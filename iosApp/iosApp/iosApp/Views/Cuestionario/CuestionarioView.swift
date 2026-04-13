#if os(iOS)
import SwiftUI
import Combine
import FirebaseAuth
import FirebaseDatabase

struct CuestionarioView: View {
    let onBack: () -> Void
    let onNext: () -> Void

    @State private var nombre    = ""
    @State private var apellidos = ""
    @State private var nacimiento = ""
    @State private var celular   = ""
    @State private var genero    = "Masculino"
    @State private var frecuencia = "72"
    @State private var tipoSangre = ""
    @State private var isSaving  = false
    @State private var showDatePicker = false
    @State private var selectedDate = Date()

    @State private var nombreError    = false
    @State private var apellidosError = false
    @State private var celularError   = false
    @State private var tipoSangreError = false

    private let generos     = ["Masculino", "Femenino", "Otro"]
    private let tiposSangre = ["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"]
    private let dashBlue    = Color.primaryBlue

    private var formIsValid: Bool {
        !nombre.isBlank && !apellidos.isBlank && !nacimiento.isBlank && !celular.isBlank && !tipoSangre.isBlank
    }

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    Spacer().frame(height: 52)

                    // Header
                    HStack {
                        Button(action: {
                            try? FirebaseAuth.Auth.auth().signOut(); onBack()
                        }) {
                            Circle()
                                .fill(dashBlue.opacity(0.12))
                                .frame(width: 36, height: 36)
                                .overlay(Image(systemName: "chevron.left").foregroundColor(dashBlue).font(.system(size: 14, weight: .semibold)))
                        }
                        Spacer().frame(width: 12)
                        Text("Datos Personales")
                            .font(Font.custom("LeagueSpartan-SemiBold", size: 24))
                            .foregroundColor(Color.primaryBlueDark)
                        Spacer()
                    }
                    .padding(.horizontal, Spacing.xxl)

                    Spacer().frame(height: Spacing.xxl)

                    // Avatar placeholder
                    Circle()
                        .fill(dashBlue.opacity(0.10))
                        .frame(width: 96, height: 96)
                        .overlay(Image(systemName: "person.fill").font(.system(size: 38)).foregroundColor(dashBlue))

                    Spacer().frame(height: 28)

                    VStack(spacing: 14) {
                        // Nombre & Apellidos (row)
                        HStack(spacing: 12) {
                            CuestionarioField(label: "Nombre", text: $nombre, hasError: nombreError, placeholder: "Juan")
                            CuestionarioField(label: "Apellidos", text: $apellidos, hasError: apellidosError, placeholder: "García")
                        }

                        // Email (disabled)
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Email").font(.system(size: 11, weight: .semibold)).foregroundColor(Color(hex: "#B0B0B0"))
                            TextField("", text: .constant(FirebaseAuth.Auth.auth().currentUser?.email ?? ""))
                                .disabled(true)
                                .font(.system(size: 15))
                                .foregroundColor(Color(hex: "#6B7A8D"))
                                .padding(12)
                                .background(Color(hex: "#F0F2F5"))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }

                        // Nacimiento & Celular
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Nacimiento").font(.system(size: 11, weight: .semibold)).foregroundColor(Color(hex: "#B0B0B0"))
                                Button(action: { showDatePicker = true }) {
                                    HStack {
                                        Text(nacimiento.isEmpty ? "dd/mm/aaaa" : nacimiento)
                                            .font(.system(size: 15))
                                            .foregroundColor(nacimiento.isEmpty ? Color(hex: "#B0B8C4") : Color(hex: "#0D1B2A"))
                                        Spacer()
                                        Image(systemName: "calendar").foregroundColor(dashBlue).font(.system(size: 14))
                                    }
                                    .padding(12)
                                    .background(Color.white)
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.borderGray, lineWidth: 1))
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                }
                                .frame(maxWidth: .infinity)
                            }
                            CuestionarioField(label: "Celular", text: $celular, hasError: celularError, placeholder: "5512345678", keyboard: .phonePad)
                        }

                        // Género dropdown
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Género").font(.system(size: 11, weight: .semibold)).foregroundColor(Color(hex: "#B0B0B0"))
                            Menu {
                                ForEach(generos, id: \.self) { g in
                                    Button(g) { genero = g }
                                }
                            } label: {
                                HStack {
                                    Text(genero).font(.system(size: 15)).foregroundColor(Color(hex: "#0D1B2A"))
                                    Spacer()
                                    Image(systemName: "chevron.down").foregroundColor(dashBlue).font(.system(size: 12))
                                }
                                .padding(12)
                                .background(Color.white)
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.borderGray, lineWidth: 1))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            }
                        }

                        // Frecuencia & Tipo Sangre
                        HStack(spacing: 12) {
                            CuestionarioField(label: "Frec. Cardíaca", text: $frecuencia, hasError: false, placeholder: "72", keyboard: .numberPad)
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Tipo de Sangre").font(.system(size: 11, weight: .semibold)).foregroundColor(tipoSangreError ? Color(hex: "#E53935") : Color(hex: "#B0B0B0"))
                                Menu {
                                    ForEach(tiposSangre, id: \.self) { t in
                                        Button(t) { tipoSangre = t; tipoSangreError = false }
                                    }
                                } label: {
                                    HStack {
                                        Text(tipoSangre.isEmpty ? "Seleccionar" : tipoSangre)
                                            .font(.system(size: 15))
                                            .foregroundColor(tipoSangre.isEmpty ? Color(hex: "#B0B8C4") : Color(hex: "#0D1B2A"))
                                        Spacer()
                                        Image(systemName: "chevron.down").foregroundColor(dashBlue).font(.system(size: 12))
                                    }
                                    .padding(12)
                                    .background(Color.white)
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(tipoSangreError ? Color(hex: "#E53935") : Color.borderGray, lineWidth: 1))
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                }
                            }
                        }
                    }
                    .padding(.horizontal, Spacing.xxl)

                    Spacer().frame(height: 32)

                    // Save button
                    Button(action: save) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 24)
                                .fill(formIsValid ? Color.primaryBlueDark : Color.primaryBlueDark.opacity(0.35))
                                .frame(height: 54)
                            if isSaving {
                                ProgressView().tint(.white)
                            } else {
                                Text("Siguiente")
                                    .font(Font.custom("LeagueSpartan-Regular", size: 16))
                                    .foregroundColor(Color(hex: "#ECF1FF"))
                            }
                        }
                    }
                    .disabled(!formIsValid || isSaving)
                    .padding(.horizontal, Spacing.xxl)

                    Spacer().frame(height: 40)
                }
            }
        }
        .sheet(isPresented: $showDatePicker) {
            VStack {
                DatePicker("", selection: $selectedDate, in: ...Date(), displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .padding()
                Button("Confirmar") {
                    let f = DateFormatter(); f.dateFormat = "dd/MM/yyyy"
                    nacimiento = f.string(from: selectedDate)
                    showDatePicker = false
                }
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(dashBlue)
                .padding(.bottom, 16)
            }
            .presentationDetents([.medium])
        }
        .navigationBarHidden(true)
    }

    private func save() {
        nombreError    = nombre.isBlank
        apellidosError = apellidos.isBlank
        celularError   = celular.isBlank
        tipoSangreError = tipoSangre.isBlank
        guard formIsValid else { return }

        isSaving = true
        guard let uid = FirebaseAuth.Auth.auth().currentUser?.uid else { isSaving = false; return }

        let data: [String: Any] = [
            "nombre": nombre, "apellidos": apellidos,
            "nacimiento": nacimiento, "celular": celular,
            "genero": genero, "frecuencia": frecuencia,
            "tipoSangre": tipoSangre, "email": FirebaseAuth.Auth.auth().currentUser?.email ?? "",
            "cuestionarioCompleted": true
        ]
        Database.database().reference().child("patients/\(uid)/profile").setValue(data)

        // UserDefaults (equivalent to SharedPreferences)
        let ud = UserDefaults.standard
        ud.set(nombre,    forKey: "nombre_\(uid)")
        ud.set(apellidos, forKey: "apellidos_\(uid)")
        ud.set(nacimiento,forKey: "nacimiento_\(uid)")
        ud.set(celular,   forKey: "celular_\(uid)")
        ud.set(genero,    forKey: "genero_\(uid)")
        ud.set(frecuencia,forKey: "frecuencia_\(uid)")
        ud.set(tipoSangre,forKey: "tipo_sangre_\(uid)")
        ud.set(true,      forKey: "cuestionario_completed_\(uid)")

        isSaving = false
        onNext()
    }
}

// MARK: - Reusable field
struct CuestionarioField: View {
    let label: String
    @Binding var text: String
    let hasError: Bool
    let placeholder: String
    var keyboard: UIKeyboardType = .default

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(hasError ? Color.errorRed : Color.borderGray, lineWidth: 1)
                )
                .frame(minHeight: 56)

            VStack(alignment: .leading, spacing: 0) {
                Text(label)
                    .font(.manrope(size: 10))
                    .foregroundColor(hasError ? Color.errorRed : Color.textSecondary)
                    .padding(.horizontal, 12)
                    .padding(.top, 8)

                TextField(placeholder, text: $text)
                    .keyboardType(keyboard)
                    .font(.manropeSemiBold(size: 14))
                    .foregroundColor(Color.textPrimary)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 8)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespaces).isEmpty }
}

#endif
