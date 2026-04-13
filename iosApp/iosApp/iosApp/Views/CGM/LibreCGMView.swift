#if os(iOS)
import SwiftUI
import Combine
import FirebaseAuth
import FirebaseDatabase

// MARK: - Model
struct GlucoseReading: Identifiable {
    let id: String
    let value: Double       // mg/dL
    let timestamp: TimeInterval
    let nota: String
    let fuente: String      // "manual" | "freestyle"

    var date: Date { Date(timeIntervalSince1970: timestamp / 1000) }
    var timeString: String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f.string(from: date)
    }
    var dateString: String {
        let f = DateFormatter()
        f.dateStyle = .medium
        f.locale = Locale(identifier: "es_MX")
        return f.string(from: date)
    }

    var status: GlucoseStatus {
        if value < 70  { return .bajo }
        if value <= 99 { return .normal }
        if value <= 125 { return .prediabetes }
        return .alto
    }
}

enum GlucoseStatus {
    case bajo, normal, prediabetes, alto

    var label: String {
        switch self {
        case .bajo:        return "Bajo"
        case .normal:      return "Normal"
        case .prediabetes: return "Prediabetes"
        case .alto:        return "Alto"
        }
    }
    var color: Color {
        switch self {
        case .bajo:        return Color.primaryBlue
        case .normal:      return Color.successGreen
        case .prediabetes: return Color.warningAmber
        case .alto:        return Color.errorRed
        }
    }
    var icon: String {
        switch self {
        case .bajo:        return "arrow.down.circle.fill"
        case .normal:      return "checkmark.circle.fill"
        case .prediabetes: return "exclamationmark.triangle.fill"
        case .alto:        return "arrow.up.circle.fill"
        }
    }
}

// MARK: - ViewModel
class LibreCGMViewModel: ObservableObject {
    @Published var readings: [GlucoseReading] = []
    @Published var isLoading = true
    @Published var showAddSheet = false
    @Published var lastReading: GlucoseReading?

    private var ref: DatabaseReference?
    private var uid: String { Auth.auth().currentUser?.uid ?? "" }

    func load() {
        guard !uid.isEmpty else { isLoading = false; return }
        ref = Database.database().reference().child("patients/\(uid)/glucose_history")
        ref?.queryOrdered(byChild: "timestamp").queryLimited(toLast: 30)
            .observe(.value) { [weak self] snap in
                guard let self else { return }
                var result: [GlucoseReading] = []
                for child in snap.children.reversed() {
                    let s = child as! DataSnapshot
                    let d = s.value as? [String: Any] ?? [:]
                    let val: Double
                    if let v = d["value"] as? Double { val = v }
                    else if let v = d["value"] as? Int { val = Double(v) }
                    else { continue }
                    result.append(GlucoseReading(
                        id: s.key,
                        value: val,
                        timestamp: d["timestamp"] as? TimeInterval ?? 0,
                        nota: d["nota"] as? String ?? "",
                        fuente: d["fuente"] as? String ?? "manual"
                    ))
                }
                DispatchQueue.main.async {
                    self.readings = result
                    self.lastReading = result.first
                    self.isLoading = false
                }
            }
    }

    func addReading(value: Double, nota: String) {
        guard !uid.isEmpty else { return }
        let ts = Date().timeIntervalSince1970 * 1000
        let data: [String: Any] = [
            "value": value,
            "nota": nota,
            "fuente": "manual",
            "timestamp": ts
        ]
        let docRef = Database.database().reference().child("patients/\(uid)/glucose_history").childByAutoId()
        docRef.setValue(data)

        // También actualizar libreLastGlucose para el Dashboard
        Database.database().reference()
            .child("patients/\(uid)/libre")
            .setValue(["lastGlucose": value, "lastTime": ts])
    }

    func delete(_ reading: GlucoseReading) {
        Database.database().reference()
            .child("patients/\(uid)/glucose_history/\(reading.id)")
            .removeValue()
    }

    func stop() { ref?.removeAllObservers() }

    var avgGlucose: Double {
        guard !readings.isEmpty else { return 0 }
        return readings.reduce(0) { $0 + $1.value } / Double(readings.count)
    }
    var minGlucose: Double { readings.map(\.value).min() ?? 0 }
    var maxGlucose: Double { readings.map(\.value).max() ?? 0 }
    var chartValues: [Double] { Array(readings.reversed().suffix(14).map(\.value)) }
}

// MARK: - Main View
struct LibreCGMView: View {
    @StateObject private var vm = LibreCGMViewModel()
    @ObservedObject private var nfc = FreestyleLibreNFCService.shared
    @State private var showNFCAlert = false
    @State private var nfcAlertMessage = ""
    @State private var showNFCSuccess = false
    @State private var lastNFCGlucose: Double = 0

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    // Header
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Glucosa")
                                .font(.manropeBold(size: 22))
                                .foregroundColor(Color.textPrimary)
                            Text("FreeStyle Libre / Manual")
                                .font(.manrope(size: 13))
                                .foregroundColor(Color.textSecondary)
                        }
                        Spacer()

                        // NFC Scan button
                        Button(action: startNFCScan) {
                            ZStack {
                                Circle()
                                    .fill(nfc.isScanning ? Color.textSecondary : Color.textNavy)
                                    .frame(width: 44, height: 44)
                                if nfc.isScanning {
                                    ProgressView().tint(.white).scaleEffect(0.7)
                                } else {
                                    Image(systemName: "sensor.tag.radiowaves.forward.fill")
                                        .foregroundColor(.white)
                                        .font(.system(size: 16))
                                }
                            }
                        }
                        .disabled(nfc.isScanning)

                        Button(action: { vm.showAddSheet = true }) {
                            ZStack {
                                Circle()
                                    .fill(Color.primaryBlue)
                                    .frame(width: 44, height: 44)
                                Image(systemName: "plus")
                                    .foregroundColor(.white)
                                    .font(.system(size: 18, weight: .bold))
                            }
                        }
                    }
                    .padding(.horizontal, Spacing.xxl)
                    .padding(.top, 20)

                    // NFC Success banner
                    if showNFCSuccess {
                        HStack(spacing: 10) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(Color.successGreen)
                            Text("Lectura NFC: \(Int(lastNFCGlucose)) mg/dL guardada")
                                .font(.manropeSemiBold(size: 13))
                                .foregroundColor(Color.textPrimary)
                            Spacer()
                        }
                        .padding(12)
                        .background(Color.successGreen.opacity(0.10))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .padding(.horizontal, Spacing.xxl)
                        .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    if vm.isLoading {
                        ProgressView("Cargando...")
                            .padding(.vertical, 40)
                    } else {
                        // Current reading card
                        CurrentGlucoseCard(reading: vm.lastReading)
                            .padding(.horizontal, Spacing.xxl)

                        // Stats row
                        if !vm.readings.isEmpty {
                            GlucoseStatsRow(vm: vm)
                                .padding(.horizontal, Spacing.xxl)

                            // Mini trend chart
                            GlucoseTrendCard(values: vm.chartValues)
                                .padding(.horizontal, Spacing.xxl)
                        }

                        // FreeStyle Libre info card
                        LibreInfoCard()
                            .padding(.horizontal, Spacing.xxl)

                        // History list
                        if !vm.readings.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Historial")
                                    .font(.manropeBold(size: 16))
                                    .foregroundColor(Color.textPrimary)
                                    .padding(.horizontal, Spacing.xxl)

                                ForEach(vm.readings) { r in
                                    GlucoseReadingRow(reading: r, onDelete: { vm.delete(r) })
                                        .padding(.horizontal, Spacing.xxl)
                                }
                            }
                        } else {
                            CGMEmptyState(onAdd: { vm.showAddSheet = true })
                        }

                        Spacer(minLength: 100)
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $vm.showAddSheet) {
            AddGlucoseSheet(vm: vm)
        }
        .alert("Error NFC", isPresented: $showNFCAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(nfcAlertMessage)
        }
        .onAppear { vm.load() }
        .onDisappear { vm.stop() }
        .animation(.easeInOut, value: showNFCSuccess)
    }

    private func startNFCScan() {
        nfc.scan { result in
            switch result {
            case .success(let reading, _):
                lastNFCGlucose = reading.glucose
                showNFCSuccess = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
                    showNFCSuccess = false
                }
            case .failure(let err):
                nfcAlertMessage = err.errorDescription ?? "Error desconocido"
                showNFCAlert = true
            case .notSupported:
                nfcAlertMessage = "NFC no disponible en este dispositivo. Ingresa la lectura manualmente."
                showNFCAlert = true
            }
        }
    }
}

// MARK: - Current Reading Card
private struct CurrentGlucoseCard: View {
    let reading: GlucoseReading?

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill((reading?.status.color ?? Color.textSecondary).opacity(0.12))
                    .frame(width: 64, height: 64)
                Image(systemName: "drop.fill")
                    .font(.system(size: 28))
                    .foregroundColor(reading?.status.color ?? Color.textSecondary)
            }

            VStack(alignment: .leading, spacing: 4) {
                if let r = reading {
                    HStack(alignment: .bottom, spacing: 4) {
                        Text("\(Int(r.value))")
                            .font(.manropeBold(size: 40))
                            .foregroundColor(r.status.color)
                        Text("mg/dL")
                            .font(.manrope(size: 14))
                            .foregroundColor(Color.textSecondary)
                            .padding(.bottom, 8)
                    }
                    HStack(spacing: 6) {
                        Image(systemName: r.status.icon)
                            .foregroundColor(r.status.color)
                            .font(.system(size: 12))
                        Text(r.status.label)
                            .font(.manropeSemiBold(size: 13))
                            .foregroundColor(r.status.color)
                        Text("• \(r.timeString)")
                            .font(.manrope(size: 12))
                            .foregroundColor(Color.textSecondary)
                    }
                } else {
                    Text("Sin lectura")
                        .font(.manropeBold(size: 22))
                        .foregroundColor(Color.textSecondary)
                    Text("Agrega tu primera lectura")
                        .font(.manrope(size: 13))
                        .foregroundColor(Color.textHint)
                }
            }

            Spacer()
        }
        .padding(20)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .vsShadow(.medium)
    }
}

// MARK: - Stats Row
private struct GlucoseStatsRow: View {
    @ObservedObject var vm: LibreCGMViewModel

    var body: some View {
        HStack(spacing: 12) {
            StatPill(label: "Promedio", value: "\(Int(vm.avgGlucose))", unit: "mg/dL", color: Color.primaryBlue)
            StatPill(label: "Mínimo",   value: "\(Int(vm.minGlucose))", unit: "mg/dL", color: Color.successGreen)
            StatPill(label: "Máximo",   value: "\(Int(vm.maxGlucose))", unit: "mg/dL", color: Color.errorRed)
        }
    }
}

private struct StatPill: View {
    let label: String; let value: String; let unit: String; let color: Color
    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.manropeBold(size: 20))
                .foregroundColor(color)
            Text(unit)
                .font(.manrope(size: 10))
                .foregroundColor(Color.textSecondary)
            Text(label)
                .font(.manropeSemiBold(size: 11))
                .foregroundColor(Color.textPrimary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(color.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

// MARK: - Trend Chart
private struct GlucoseTrendCard: View {
    let values: [Double]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Tendencia (últimas lecturas)")
                    .font(.manropeSemiBold(size: 14))
                    .foregroundColor(Color.textPrimary)
                Spacer()
                // Rangos de referencia
                HStack(spacing: 10) {
                    RangeLabel(color: Color.successGreen, text: "70–125")
                    RangeLabel(color: Color.errorRed, text: ">125")
                }
            }
            // Línea de referencia + chart
            ZStack(alignment: .bottom) {
                // Zona normal (70-125 mg/dL)
                GeometryReader { geo in
                    let maxV = max((values.max() ?? 180) + 20, 180)
                    let normalTop = geo.size.height - (geo.size.height * (125 / maxV))
                    let normalBot = geo.size.height - (geo.size.height * (70 / maxV))
                    Rectangle()
                        .fill(Color.successGreen.opacity(0.07))
                        .frame(height: normalBot - normalTop)
                        .offset(y: normalTop)
                }
                LineChartView(values: values, color: Color.primaryBlue)
            }
            .frame(height: 80)
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: .black.opacity(0.05), radius: 6, x: 0, y: 2)
    }
}

private struct RangeLabel: View {
    let color: Color; let text: String
    var body: some View {
        HStack(spacing: 4) {
            Circle().fill(color).frame(width: 7, height: 7)
            Text(text).font(.manrope(size: 10)).foregroundColor(Color.textSecondary)
        }
    }
}

// MARK: - FreeStyle Libre Info Card
private struct LibreInfoCard: View {
    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.primaryBlueLight)
                        .frame(width: 48, height: 48)
                    Image(systemName: "sensor.tag.radiowaves.forward.fill")
                        .font(.system(size: 20))
                        .foregroundColor(Color.primaryBlue)
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text("FreeStyle Libre — NFC")
                        .font(.manropeSemiBold(size: 14))
                        .foregroundColor(Color.textPrimary)
                    Text("Toca el ícono de sensor (arriba derecha) para leer el glucómetro con NFC. Coloca el iPhone sobre el parche del brazo.")
                        .font(.manrope(size: 12))
                        .foregroundColor(Color.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            // How-to steps
            VStack(alignment: .leading, spacing: 6) {
                NfcStep(number: "1", text: "Activa NFC en tu iPhone (Configuración)")
                NfcStep(number: "2", text: "Coloca el iPhone sobre el sensor Libre (brazo)")
                NfcStep(number: "3", text: "Espera 2-3 segundos hasta escuchar el tono NFC")
                NfcStep(number: "4", text: "La lectura se guarda automáticamente")
            }
            .padding(.top, 4)
        }
        .padding(16)
        .background(Color.surfaceGray)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.primaryBlueLight, lineWidth: 1)
        )
    }
}

private struct NfcStep: View {
    let number: String
    let text: String
    var body: some View {
        HStack(spacing: 8) {
            Text(number)
                .font(.manropeBold(size: 10))
                .foregroundColor(.white)
                .frame(width: 18, height: 18)
                .background(Color.primaryBlue)
                .clipShape(Circle())
            Text(text)
                .font(.manrope(size: 12))
                .foregroundColor(Color.textPrimary)
        }
    }
}

// MARK: - Reading Row
private struct GlucoseReadingRow: View {
    let reading: GlucoseReading
    let onDelete: () -> Void
    @State private var showDelete = false

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(reading.status.color.opacity(0.12))
                    .frame(width: 40, height: 40)
                Image(systemName: reading.status.icon)
                    .font(.system(size: 16))
                    .foregroundColor(reading.status.color)
            }

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text("\(Int(reading.value)) mg/dL")
                        .font(.manropeSemiBold(size: 15))
                        .foregroundColor(reading.status.color)
                    Text(reading.status.label)
                        .font(.manrope(size: 11))
                        .foregroundColor(reading.status.color)
                        .padding(.horizontal, 7).padding(.vertical, 2)
                        .background(reading.status.color.opacity(0.10))
                        .clipShape(Capsule())
                }
                Text("\(reading.dateString) • \(reading.timeString)")
                    .font(.manrope(size: 12))
                    .foregroundColor(Color.textSecondary)
                if !reading.nota.isEmpty {
                    Text(reading.nota)
                        .font(.manrope(size: 11))
                        .foregroundColor(Color.textHint)
                        .lineLimit(1)
                }
            }

            Spacer()

            Button(action: { showDelete = true }) {
                Image(systemName: "trash")
                    .font(.system(size: 14))
                    .foregroundColor(Color.textHint)
            }
        }
        .padding(14)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .vsShadow(.small)
        .alert("Eliminar lectura", isPresented: $showDelete) {
            Button("Cancelar", role: .cancel) {}
            Button("Eliminar", role: .destructive) { onDelete() }
        }
    }
}

// MARK: - Empty State
private struct CGMEmptyState: View {
    let onAdd: () -> Void
    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle().fill(Color.primaryBlueLight).frame(width: 90, height: 90)
                Image(systemName: "drop.fill")
                    .font(.system(size: 36))
                    .foregroundColor(Color.primaryBlue)
            }
            Text("Sin lecturas de glucosa")
                .font(.manropeBold(size: 16))
                .foregroundColor(Color.textPrimary)
            Text("Agrega tu primera lectura manualmente o desde el sensor FreeStyle Libre.")
                .font(.manrope(size: 13))
                .foregroundColor(Color.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Button(action: onAdd) {
                Text("Agregar lectura")
                    .font(.manropeSemiBold(size: 15))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity).frame(height: 50)
                    .background(Color.primaryBlue)
                    .clipShape(RoundedRectangle(cornerRadius: 32))
            }
            .padding(.horizontal, 40)
        }
        .padding(.vertical, 20)
    }
}

// MARK: - Add Glucose Sheet
struct AddGlucoseSheet: View {
    @ObservedObject var vm: LibreCGMViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var valueText = ""
    @State private var nota = ""
    @State private var valueError = false

    private var parsedValue: Double? {
        guard let v = Double(valueText.replacingOccurrences(of: ",", with: ".")), v > 0 else { return nil }
        return v
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                // Value input
                VStack(alignment: .center, spacing: 8) {
                    Text("Nivel de glucosa")
                        .font(.manrope(size: 13))
                        .foregroundColor(Color.textSecondary)

                    HStack(alignment: .bottom, spacing: 6) {
                        TextField("0", text: $valueText)
                            .font(.manropeBold(size: 56))
                            .foregroundColor(parsedValue.map { GlucoseReading(id: "", value: $0, timestamp: 0, nota: "", fuente: "").status.color } ?? Color.textHint)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.center)
                            .frame(width: 160)
                        Text("mg/dL")
                            .font(.manropeSemiBold(size: 18))
                            .foregroundColor(Color.textSecondary)
                            .padding(.bottom, 10)
                    }

                    if let v = parsedValue {
                        let status = GlucoseReading(id: "", value: v, timestamp: 0, nota: "", fuente: "").status
                        HStack(spacing: 6) {
                            Image(systemName: status.icon).foregroundColor(status.color).font(.system(size: 13))
                            Text(status.label).font(.manropeSemiBold(size: 14)).foregroundColor(status.color)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 6)
                        .background(status.color.opacity(0.10))
                        .clipShape(Capsule())
                    }

                    if valueError {
                        Text("Ingresa un valor válido (ej: 95)")
                            .font(.manrope(size: 12))
                            .foregroundColor(Color.errorRed)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 8)

                // Rangos de referencia
                VStack(spacing: 6) {
                    Text("Rangos de referencia en ayunas")
                        .font(.manrope(size: 12))
                        .foregroundColor(Color.textHint)
                    HStack(spacing: 12) {
                        ReferenceRange(range: "<70", label: "Bajo", color: Color.primaryBlue)
                        ReferenceRange(range: "70–99", label: "Normal", color: Color.successGreen)
                        ReferenceRange(range: "100–125", label: "Pre-DM", color: Color.warningAmber)
                        ReferenceRange(range: ">125", label: "Alto", color: Color.errorRed)
                    }
                    .padding(.horizontal, 16)
                }

                Divider().padding(.horizontal, Spacing.xxl)

                // Nota
                VStack(alignment: .leading, spacing: 6) {
                    Text("Nota (opcional)")
                        .font(.manrope(size: 12))
                        .foregroundColor(Color.textSecondary)
                        .padding(.horizontal, Spacing.xxl)
                    TextField("Ej: Ayunas, después de comer...", text: $nota)
                        .font(.manrope(size: 14))
                        .foregroundColor(Color.textPrimary)
                        .padding(.horizontal, 16).padding(.vertical, 12)
                        .background(Color.surfaceGray)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.borderGray, lineWidth: 1))
                        .padding(.horizontal, Spacing.xxl)
                }

                Spacer()

                // Save button
                Button(action: save) {
                    Text("Guardar lectura")
                        .font(.manropeSemiBold(size: 16))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity).frame(height: 52)
                        .background(parsedValue != nil ? Color.primaryBlue : Color.primaryBlue.opacity(0.4))
                        .clipShape(RoundedRectangle(cornerRadius: 32))
                }
                .disabled(parsedValue == nil)
                .padding(.horizontal, Spacing.xxl)
                .padding(.bottom, 24)
            }
            .background(Color.white.ignoresSafeArea())
            .navigationTitle("Nueva lectura")
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
        guard let v = parsedValue else { valueError = true; return }
        vm.addReading(value: v, nota: nota)
        dismiss()
    }
}

private struct ReferenceRange: View {
    let range: String; let label: String; let color: Color
    var body: some View {
        VStack(spacing: 2) {
            Text(range).font(.manropeSemiBold(size: 11)).foregroundColor(color)
            Text(label).font(.manrope(size: 10)).foregroundColor(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
        .background(color.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}
#endif
