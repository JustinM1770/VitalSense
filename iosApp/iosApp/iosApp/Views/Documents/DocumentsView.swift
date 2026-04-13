#if os(iOS)
import SwiftUI
import Combine
import FirebaseAuth
import FirebaseDatabase

// MARK: - Model
struct MedDocument: Identifiable {
    let id: String
    let nombre: String
    let tipo: DocumentType
    let url: String
    let fecha: String
    let timestamp: TimeInterval

    enum DocumentType: String, CaseIterable {
        case laboratorio  = "Laboratorio"
        case receta       = "Receta"
        case imagen       = "Imagen médica"
        case certificado  = "Certificado"
        case otro         = "Otro"

        var icon: String {
            switch self {
            case .laboratorio: return "flask.fill"
            case .receta:      return "pill.fill"
            case .imagen:      return "waveform.path.ecg.rectangle.fill"
            case .certificado: return "checkmark.seal.fill"
            case .otro:        return "doc.fill"
            }
        }
        var color: String {
            switch self {
            case .laboratorio: return "#1169FF"
            case .receta:      return "#34A853"
            case .imagen:      return "#EB4B62"
            case .certificado: return "#225FFF"
            case .otro:        return "#8C8C8C"
            }
        }
    }
}

// MARK: - ViewModel
class DocumentsViewModel: ObservableObject {
    @Published var documents: [MedDocument] = []
    @Published var isLoading = true
    @Published var showAddSheet = false
    @Published var deleteError: String?

    private var ref: DatabaseReference?
    private var uid: String { Auth.auth().currentUser?.uid ?? "" }

    func load() {
        guard !uid.isEmpty else { isLoading = false; return }
        ref = Database.database().reference().child("patients/\(uid)/documents")
        ref?.queryOrdered(byChild: "timestamp")
            .observe(.value) { [weak self] snap in
                guard let self else { return }
                var result: [MedDocument] = []
                for child in snap.children.reversed() {
                    let s = child as! DataSnapshot
                    let d = s.value as? [String: Any] ?? [:]
                    let tipoRaw = d["tipo"] as? String ?? "Otro"
                    let tipo = MedDocument.DocumentType(rawValue: tipoRaw) ?? .otro
                    result.append(MedDocument(
                        id: s.key,
                        nombre: d["nombre"] as? String ?? "Documento",
                        tipo: tipo,
                        url: d["url"] as? String ?? "",
                        fecha: d["fecha"] as? String ?? "",
                        timestamp: d["timestamp"] as? TimeInterval ?? 0
                    ))
                }
                DispatchQueue.main.async {
                    self.documents = result
                    self.isLoading = false
                }
            }
    }

    func delete(_ doc: MedDocument) {
        Database.database().reference()
            .child("patients/\(uid)/documents/\(doc.id)")
            .removeValue { [weak self] error, _ in
                if let error { DispatchQueue.main.async { self?.deleteError = error.localizedDescription } }
            }
    }

    func add(nombre: String, tipo: MedDocument.DocumentType, url: String) {
        guard !uid.isEmpty else { return }
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.locale = Locale(identifier: "es_MX")
        let fecha = formatter.string(from: Date())

        let data: [String: Any] = [
            "nombre": nombre,
            "tipo": tipo.rawValue,
            "url": url,
            "fecha": fecha,
            "timestamp": Date().timeIntervalSince1970 * 1000
        ]
        Database.database().reference()
            .child("patients/\(uid)/documents")
            .childByAutoId()
            .setValue(data)
    }

    func stop() { ref?.removeAllObservers() }
}

// MARK: - Main View
struct DocumentsView: View {
    @StateObject private var vm = DocumentsViewModel()

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                HStack {
                    Text("Archivos")
                        .font(.manropeBold(size: 22))
                        .foregroundColor(Color.textPrimary)
                    Spacer()
                    Button(action: { vm.showAddSheet = true }) {
                        ZStack {
                            Circle()
                                .fill(Color.primaryBlue)
                                .frame(width: 40, height: 40)
                            Image(systemName: "plus")
                                .foregroundColor(.white)
                                .font(.system(size: 16, weight: .bold))
                        }
                    }
                }
                .padding(.horizontal, Spacing.xxl)
                .padding(.top, 20)
                .padding(.bottom, 16)

                // Category filter chips
                DocumentTypeFilter(vm: vm)

                // Content
                if vm.isLoading {
                    Spacer()
                    ProgressView("Cargando documentos...")
                        .font(.manrope(size: 14))
                    Spacer()
                } else if vm.documents.isEmpty {
                    DocumentsEmptyState(onAdd: { vm.showAddSheet = true })
                } else {
                    ScrollView(showsIndicators: false) {
                        LazyVStack(spacing: 12) {
                            ForEach(vm.documents) { doc in
                                DocumentRow(doc: doc, onDelete: { vm.delete(doc) })
                            }
                        }
                        .padding(.horizontal, Spacing.xxl)
                        .padding(.top, 8)
                        .padding(.bottom, 100)
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $vm.showAddSheet) {
            AddDocumentSheet(vm: vm)
        }
        .alert("Error al eliminar", isPresented: Binding(
            get: { vm.deleteError != nil },
            set: { if !$0 { vm.deleteError = nil } }
        )) {
            Button("OK", role: .cancel) { vm.deleteError = nil }
        } message: {
            Text(vm.deleteError ?? "")
        }
        .onAppear { vm.load() }
        .onDisappear { vm.stop() }
    }
}

// MARK: - Category Filter
private struct DocumentTypeFilter: View {
    @ObservedObject var vm: DocumentsViewModel
    @State private var selectedType: MedDocument.DocumentType? = nil

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                FilterChip(label: "Todos", isActive: selectedType == nil) {
                    selectedType = nil
                }
                ForEach(MedDocument.DocumentType.allCases, id: \.self) { tipo in
                    FilterChip(label: tipo.rawValue, isActive: selectedType == tipo) {
                        selectedType = selectedType == tipo ? nil : tipo
                    }
                }
            }
            .padding(.horizontal, Spacing.xxl)
            .padding(.bottom, 12)
        }
    }
}

private struct FilterChip: View {
    let label: String
    let isActive: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.manropeSemiBold(size: 13))
                .foregroundColor(isActive ? Color.textPrimary : Color.textSecondary)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(isActive ? Color.dashBgAlt : Color.surfaceGray)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(isActive ? Color.dashBgAlt : Color.borderGray, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 14))
        }
    }
}

// MARK: - Document Row
private struct DocumentRow: View {
    let doc: MedDocument
    let onDelete: () -> Void
    @State private var showDeleteAlert = false

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color(hex: doc.tipo.color).opacity(0.12))
                    .frame(width: 48, height: 48)
                Image(systemName: doc.tipo.icon)
                    .font(.system(size: 20))
                    .foregroundColor(Color(hex: doc.tipo.color))
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(doc.nombre)
                    .font(.manropeSemiBold(size: 14))
                    .foregroundColor(Color.textPrimary)
                    .lineLimit(1)
                HStack(spacing: 6) {
                    Text(doc.tipo.rawValue)
                        .font(.manrope(size: 11))
                        .foregroundColor(Color(hex: doc.tipo.color))
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2)
                        .background(Color(hex: doc.tipo.color).opacity(0.10))
                        .clipShape(Capsule())
                    Text(doc.fecha)
                        .font(.manrope(size: 11))
                        .foregroundColor(Color.textSecondary)
                }
            }

            Spacer()

            // Actions
            HStack(spacing: 8) {
                if !doc.url.isEmpty, let url = URL(string: doc.url) {
                    Link(destination: url) {
                        Image(systemName: "arrow.up.right.square")
                            .font(.system(size: 18))
                            .foregroundColor(Color.primaryBlue)
                    }
                }

                Button(action: { showDeleteAlert = true }) {
                    Image(systemName: "trash")
                        .font(.system(size: 16))
                        .foregroundColor(Color.errorRed)
                }
            }
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: .black.opacity(0.05), radius: 6, x: 0, y: 2)
        .alert("Eliminar documento", isPresented: $showDeleteAlert) {
            Button("Cancelar", role: .cancel) {}
            Button("Eliminar", role: .destructive) { onDelete() }
        } message: {
            Text("¿Seguro que deseas eliminar \"\(doc.nombre)\"?")
        }
    }
}

// MARK: - Empty State
private struct DocumentsEmptyState: View {
    let onAdd: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            ZStack {
                Circle()
                    .fill(Color.primaryBlueLight)
                    .frame(width: 100, height: 100)
                Image(systemName: "doc.badge.plus")
                    .font(.system(size: 40))
                    .foregroundColor(Color.primaryBlue)
            }
            Text("Sin documentos")
                .font(.manropeBold(size: 18))
                .foregroundColor(Color.textPrimary)
            Text("Guarda recetas, resultados de laboratorio y archivos médicos en un solo lugar.")
                .font(.manrope(size: 14))
                .foregroundColor(Color.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button(action: onAdd) {
                Text("Agregar documento")
                    .font(.manropeSemiBold(size: 16))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(Color.primaryBlue)
                    .clipShape(RoundedRectangle(cornerRadius: 32))
            }
            .padding(.horizontal, 40)
            Spacer()
        }
    }
}

// MARK: - Add Document Sheet
struct AddDocumentSheet: View {
    @ObservedObject var vm: DocumentsViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var nombre = ""
    @State private var tipo: MedDocument.DocumentType = .laboratorio
    @State private var url = ""
    @State private var nombreError = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Nombre
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Nombre del documento")
                            .font(.manrope(size: 11))
                            .foregroundColor(nombreError ? Color.errorRed : Color.textSecondary)
                        TextField("Ej: Análisis sangre completo", text: $nombre)
                            .font(.manropeSemiBold(size: 14))
                            .foregroundColor(Color.textPrimary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 14)
                            .background(Color.white)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(nombreError ? Color.errorRed : Color.borderGray, lineWidth: 1)
                            )
                    }

                    // Tipo
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Tipo de documento")
                            .font(.manrope(size: 11))
                            .foregroundColor(Color.textSecondary)

                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                            ForEach(MedDocument.DocumentType.allCases, id: \.self) { t in
                                Button(action: { tipo = t }) {
                                    HStack(spacing: 8) {
                                        Image(systemName: t.icon)
                                            .font(.system(size: 14))
                                            .foregroundColor(Color(hex: t.color))
                                        Text(t.rawValue)
                                            .font(.manrope(size: 13))
                                            .foregroundColor(Color.textPrimary)
                                        Spacer()
                                        if tipo == t {
                                            Image(systemName: "checkmark.circle.fill")
                                                .foregroundColor(Color.primaryBlue)
                                                .font(.system(size: 14))
                                        }
                                    }
                                    .padding(12)
                                    .background(tipo == t ? Color.primaryBlueLight : Color.surfaceGray)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                            .stroke(tipo == t ? Color.primaryBlue : Color.borderGray, lineWidth: 1)
                                    )
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                }
                            }
                        }
                    }

                    // URL / Enlace
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Enlace al archivo (Google Drive, Dropbox, etc.)")
                            .font(.manrope(size: 11))
                            .foregroundColor(Color.textSecondary)
                        TextField("https://drive.google.com/...", text: $url)
                            .font(.manrope(size: 14))
                            .foregroundColor(Color.textPrimary)
                            .keyboardType(.URL)
                            .autocapitalization(.none)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 14)
                            .background(Color.white)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(Color.borderGray, lineWidth: 1)
                            )
                        Text("Opcional. Si el archivo está en la nube, pega el enlace aquí.")
                            .font(.manrope(size: 11))
                            .foregroundColor(Color.textHint)
                    }

                    // Save button
                    Button(action: save) {
                        Text("Guardar documento")
                            .font(.manropeSemiBold(size: 16))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(!nombre.isEmpty ? Color.primaryBlue : Color.primaryBlue.opacity(0.4))
                            .clipShape(RoundedRectangle(cornerRadius: 32))
                    }
                    .disabled(nombre.isEmpty)
                }
                .padding(24)
            }
            .background(Color.white.ignoresSafeArea())
            .navigationTitle("Nuevo documento")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                        .font(.manrope(size: 14))
                }
            }
        }
    }

    private func save() {
        guard !nombre.trimmingCharacters(in: .whitespaces).isEmpty else {
            nombreError = true; return
        }
        vm.add(nombre: nombre, tipo: tipo, url: url)
        dismiss()
    }
}
#endif
