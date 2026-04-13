#if os(iOS)
import SwiftUI
import Combine
import FirebaseAuth
import FirebaseDatabase

struct AddMedicationView: View {
    let onBack: () -> Void

    @State private var nombre     = ""
    @State private var dosis      = ""
    @State private var cadaCuanto = "Cada 8 horas"
    @State private var duracion   = "7 dias"
    @State private var customFreq = ""
    @State private var isSaving   = false
    @State private var saved      = false
    @State private var nombreError = false

    private let dashBlue  = Color.primaryBlue
    private let frecuencias = ["Cada 4 horas", "Cada 6 horas", "Cada 8 horas", "Cada 12 horas", "Cada 24 horas", "Personalizado"]
    private let duraciones  = ["2 dias", "7 dias", "2 semanas", "1 mes", "Indeterminado"]

    private var isCustomFreq: Bool { cadaCuanto == "Personalizado" }
    private var formValid: Bool    { !nombre.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    Spacer().frame(height: 52)

                    // Header
                    HStack(spacing: 12) {
                        Button(action: onBack) {
                            Circle()
                                .fill(dashBlue.opacity(0.12))
                                .frame(width: 34, height: 34)
                                .overlay(Image(systemName: "chevron.left").foregroundColor(dashBlue).font(.system(size: 13, weight: .semibold)))
                        }
                        Text("Agregar medicamento")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(dashBlue)
                        Spacer()
                    }
                    .padding(.horizontal, Spacing.xxl)

                    Spacer().frame(height: 28)

                    VStack(spacing: 20) {
                        // Nombre del medicamento
                        MedField(label: "Nombre del medicamento", text: $nombre, placeholder: "Ej. Metformina", hasError: nombreError)

                        // Dosis
                        MedField(label: "Dosis", text: $dosis, placeholder: "Ej. 500mg")

                        // Frecuencia dropdown
                        MedDropdown(label: "Frecuencia", selected: $cadaCuanto, options: frecuencias)

                        // Campo personalizado
                        if isCustomFreq {
                            MedField(label: "Frecuencia personalizada (horas)", text: $customFreq, placeholder: "Ej. Cada 10 horas", keyboard: .numberPad)
                                .transition(.opacity)
                        }

                        // Duración dropdown
                        MedDropdown(label: "Duración", selected: $duracion, options: duraciones)

                        // Info text
                        Text("El medicamento aparecerá en tu panel de salud y recibirás recordatorios.")
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: "#6B7280"))
                            .padding(.top, 4)

                        // Save button
                        Button(action: save) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 14)
                                    .fill(formValid ? dashBlue : dashBlue.opacity(0.35))
                                    .frame(height: 52)
                                if isSaving {
                                    ProgressView().tint(.white)
                                } else if saved {
                                    Label("Guardado", systemImage: "checkmark")
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundColor(.white)
                                } else {
                                    Text("Guardar medicamento")
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundColor(.white)
                                }
                            }
                        }
                        .disabled(!formValid || isSaving || saved)
                        .animation(.easeInOut, value: saved)
                    }
                    .padding(.horizontal, Spacing.xxl)

                    Spacer().frame(height: 40)
                }
            }
        }
        .navigationBarHidden(true)
        .animation(.easeInOut, value: isCustomFreq)
    }

    private func save() {
        nombreError = nombre.trimmingCharacters(in: .whitespaces).isEmpty
        guard formValid else { return }
        guard let uid = FirebaseAuth.Auth.auth().currentUser?.uid else { return }

        isSaving = true
        let id  = UUID().uuidString
        let now = Int(Date().timeIntervalSince1970 * 1000)
        let freq = isCustomFreq ? customFreq : cadaCuanto

        let data: [String: Any] = [
            "id": id, "nombre": nombre, "dosis": dosis,
            "cadaCuanto": freq, "duracion": duracion,
            "activo": true, "createdAt": now
        ]
        Database.database().reference().child("medications/\(uid)/\(id)").setValue(data) { _, _ in
            isSaving = false
            saved = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { onBack() }
        }
    }
}

// MARK: - Subviews
struct MedField: View {
    let label: String
    @Binding var text: String
    let placeholder: String
    var hasError: Bool = false
    var keyboard: UIKeyboardType = .default

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label).font(.system(size: 13, weight: .semibold))
                .foregroundColor(hasError ? Color(hex: "#D32F2F") : Color(hex: "#374151"))
            TextField(placeholder, text: $text)
                .keyboardType(keyboard)
                .font(.system(size: 15))
                .padding(12)
                .background(Color.white)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(hasError ? Color(hex: "#D32F2F") : Color.borderGray, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            if hasError {
                Text("Campo requerido").font(.system(size: 12)).foregroundColor(Color(hex: "#D32F2F"))
            }
        }
    }
}

struct MedDropdown: View {
    let label: String
    @Binding var selected: String
    let options: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label).font(.system(size: 13, weight: .semibold)).foregroundColor(Color(hex: "#374151"))
            Menu {
                ForEach(options, id: \.self) { opt in Button(opt) { selected = opt } }
            } label: {
                HStack {
                    Text(selected).font(.system(size: 15)).foregroundColor(Color(hex: "#0D1B2A"))
                    Spacer()
                    Image(systemName: "chevron.down").foregroundColor(Color.primaryBlue).font(.system(size: 12))
                }
                .padding(12)
                .background(Color.white)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.borderGray, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

#endif
