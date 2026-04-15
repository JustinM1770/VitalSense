#if os(iOS)
import SwiftUI

// MARK: - WearableView
// Flujo: código de vinculación como método principal.
// Free: 1 dispositivo. Premium: ilimitados.

struct WearableView: View {
    @StateObject private var vm = WearableViewModel()
    @ObservedObject private var sub = SubscriptionService.shared
    @State private var showPaywall = false
    @State private var showAddDevice = false
    @State private var deviceToRemove: PairedDevice? = nil

    var body: some View {
        ZStack {
            Color(hex: "#F0F2F5").ignoresSafeArea()

            ScrollView {
                VStack(spacing: 0) {

                    // ── Header ────────────────────────────────────────────────
                    HStack {
                        Text("Mis Wearables")
                            .font(.manropeBold(size: 18))
                            .foregroundColor(.textDark)
                        Spacer()
                        // Indicador de plan
                        PlanBadge()
                    }
                    .padding(.horizontal, Spacing.xl)
                    .padding(.top, 20)
                    .padding(.bottom, 24)

                    // ── Lista de dispositivos vinculados ──────────────────────
                    if sub.pairedDevices.isEmpty {
                        EmptyDevicesView()
                            .padding(.horizontal, Spacing.xl)
                    } else {
                        VStack(spacing: 12) {
                            ForEach(sub.pairedDevices) { device in
                                DeviceCard(
                                    device: device,
                                    isActive: vm.activeDeviceCode == device.code,
                                    vitals: vm.activeDeviceCode == device.code ? vm.vitals : BLEVitals()
                                ) {
                                    deviceToRemove = device
                                }
                            }
                        }
                        .padding(.horizontal, Spacing.xl)
                    }

                    Spacer().frame(height: 24)

                    // ── Botón "Vincular nuevo wearable" ───────────────────────
                    Button {
                        if sub.canAddDevice {
                            showAddDevice = true
                        } else {
                            showPaywall = true
                        }
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: sub.canAddDevice ? "plus.circle.fill" : "crown.fill")
                                .font(.system(size: 18))
                                .foregroundColor(sub.canAddDevice ? .white : .yellow)
                            Text(sub.canAddDevice ? "Vincular wearable" : "Agregar más — Premium")
                                .font(.manropeSemiBold(size: 15))
                                .foregroundColor(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(sub.canAddDevice ? Color.primaryBlue : Color(hex: "#0D47A1"))
                        .cornerRadius(32)
                        .shadow(color: Color.primaryBlue.opacity(0.35), radius: 10, x: 0, y: 4)
                    }
                    .padding(.horizontal, Spacing.xl)

                    // Límite free
                    if sub.plan == .free {
                        Spacer().frame(height: 12)
                        HStack(spacing: 6) {
                            Image(systemName: "info.circle")
                                .font(.system(size: 12))
                            Text("Plan Free: \(sub.devicesUsed)/\(sub.deviceLimit) wearable. Suscríbete para agregar más.")
                                .font(.manrope(size: 12))
                        }
                        .foregroundColor(.textSecondary)
                        .padding(.horizontal, Spacing.xl)

                        Spacer().frame(height: 6)
                        Button {
                            showPaywall = true
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "crown.fill")
                                    .font(.system(size: 12))
                                    .foregroundColor(.yellow)
                                Text("Ver planes Premium")
                                    .font(.manropeSemiBold(size: 13))
                                    .foregroundColor(.primaryBlue)
                            }
                        }
                    }

                    Spacer().frame(height: 40)
                }
            }
        }
        .navigationBarHidden(true)
        // ── Sheet: vincular nuevo wearable ─────────────────────────────────
        .sheet(isPresented: $showAddDevice) {
            AddDeviceSheet(vm: vm, isPresented: $showAddDevice)
        }
        // ── Sheet: paywall premium ──────────────────────────────────────────
        .sheet(isPresented: $showPaywall) {
            PremiumPaywallView()
        }
        // ── Alert: confirmar desvinculación ────────────────────────────────
        .alert("Desvincular wearable", isPresented: Binding(
            get: { deviceToRemove != nil },
            set: { if !$0 { deviceToRemove = nil } }
        )) {
            Button("Desvincular", role: .destructive) {
                if let d = deviceToRemove {
                    sub.removeDevice(d)
                    if vm.activeDeviceCode == d.code { vm.disconnectActive() }
                }
                deviceToRemove = nil
            }
            Button("Cancelar", role: .cancel) { deviceToRemove = nil }
        } message: {
            Text("¿Desvincular \"\(deviceToRemove?.name ?? "")\"? Podrás volver a vincularlo cuando quieras.")
        }
        .task { await sub.setup() }
    }
}

// MARK: - Plan badge

private struct PlanBadge: View {
    @ObservedObject private var sub = SubscriptionService.shared

    var body: some View {
        HStack(spacing: 5) {
            if sub.plan == .premium {
                Image(systemName: "crown.fill")
                    .font(.system(size: 10))
                    .foregroundColor(.yellow)
            }
            Text(sub.plan.displayName.uppercased())
                .font(.manropeSemiBold(size: 10))
                .foregroundColor(sub.plan == .premium ? Color(hex: "#0D47A1") : .textSecondary)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(sub.plan == .premium ? Color.yellow.opacity(0.15) : Color.borderGray.opacity(0.5))
        .cornerRadius(12)
    }
}

// MARK: - Empty state

private struct EmptyDevicesView: View {
    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.primaryBlue.opacity(0.08))
                    .frame(width: 80, height: 80)
                Image(systemName: "applewatch.slash")
                    .font(.system(size: 32))
                    .foregroundColor(.primaryBlue.opacity(0.5))
            }
            Text("Sin wearables vinculados")
                .font(.manropeSemiBold(size: 15))
                .foregroundColor(.textDark)
            Text("Toca \"Vincular wearable\" y sigue\nlas instrucciones en tu dispositivo.")
                .font(.manrope(size: 13))
                .foregroundColor(.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 48)
        .padding(.horizontal, 24)
        .background(Color.white)
        .cornerRadius(20)
    }
}

// MARK: - Device card

private struct DeviceCard: View {
    let device: PairedDevice
    let isActive: Bool
    let vitals: BLEVitals
    let onRemove: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // ── Cabecera del dispositivo ───────────────────────────────────
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(isActive ? Color.spO2Green.opacity(0.12) : Color.primaryBlue.opacity(0.08))
                        .frame(width: 46, height: 46)
                    Image(systemName: platformIcon(device.platform))
                        .font(.system(size: 20))
                        .foregroundColor(isActive ? .spO2Green : .primaryBlue)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(device.name)
                        .font(.manropeSemiBold(size: 14))
                        .foregroundColor(.textDark)
                    HStack(spacing: 4) {
                        Circle()
                            .fill(isActive ? Color.spO2Green : Color.textSecondary.opacity(0.4))
                            .frame(width: 6, height: 6)
                        Text(isActive ? "Conectado" : "Vinculado")
                            .font(.manrope(size: 11))
                            .foregroundColor(isActive ? .spO2Green : .textSecondary)
                    }
                }

                Spacer()

                Button(action: onRemove) {
                    Image(systemName: "trash")
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: "#EF4444").opacity(0.7))
                        .padding(8)
                        .background(Color(hex: "#EF4444").opacity(0.08))
                        .cornerRadius(8)
                }
            }
            .padding(16)

            // ── Vitals si está activo ──────────────────────────────────────
            if isActive && (vitals.heartRate != nil || vitals.spo2 != nil) {
                Divider().padding(.horizontal, 16)

                HStack {
                    if let hr = vitals.heartRate {
                        MiniVitalPill(icon: "heart.fill", color: .heartRateRed, value: "\(hr)", unit: "bpm")
                    }
                    if let spo2 = vitals.spo2 {
                        MiniVitalPill(icon: "lungs.fill", color: .spO2Green, value: "\(spo2)", unit: "%")
                    }
                    if let gluc = vitals.glucose {
                        MiniVitalPill(icon: "drop.fill", color: .glucoseOrange, value: String(format: "%.0f", gluc), unit: "mg/dL")
                    }
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 14)
            }
        }
        .background(Color.white)
        .cornerRadius(16)
        .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)
    }

    private func platformIcon(_ platform: String) -> String {
        switch platform.lowercased() {
        case "watchos": return "applewatch"
        case "wearos":  return "watchface.applewatch.case"
        default:        return "sensor.tag.radiowaves.forward"
        }
    }
}

private struct MiniVitalPill: View {
    let icon: String
    let color: Color
    let value: String
    let unit: String

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 10))
                .foregroundColor(color)
            Text("\(value) \(unit)")
                .font(.manropeSemiBold(size: 11))
                .foregroundColor(.textDark)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(color.opacity(0.08))
        .cornerRadius(20)
    }
}

// MARK: - Sheet: agregar dispositivo por código

struct AddDeviceSheet: View {
    @ObservedObject var vm: WearableViewModel
    @Binding var isPresented: Bool
    @State private var code = ""
    @State private var deviceName = ""
    @State private var selectedPlatform = "watchOS"

    private let platforms = ["watchOS", "wearOS", "Otro"]

    private var isConnecting: Bool {
        if case .connecting = vm.bleService.connectionState { return true }
        return false
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {

                    // Instrucción
                    VStack(spacing: 12) {
                        Image(systemName: "iphone.and.arrow.forward")
                            .font(.system(size: 44))
                            .foregroundColor(.primaryBlue)

                        Text("Vincula tu wearable")
                            .font(.manropeBold(size: 20))
                            .foregroundColor(.textDark)

                        Text("Abre BioMetric AI en tu dispositivo wearable. Aparecerá un código de 8 caracteres. Ingrésalo aquí.")
                            .font(.manrope(size: 14))
                            .foregroundColor(.textSecondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 8)

                    // Plataforma
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Tipo de dispositivo")
                            .font(.manropeSemiBold(size: 13))
                            .foregroundColor(.textDark)

                        HStack(spacing: 10) {
                            ForEach(platforms, id: \.self) { p in
                                Button {
                                    selectedPlatform = p
                                } label: {
                                    Text(p)
                                        .font(.manropeSemiBold(size: 13))
                                        .foregroundColor(selectedPlatform == p ? .white : .textDark)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 8)
                                        .background(selectedPlatform == p ? Color.primaryBlue : Color(hex: "#F0F2F5"))
                                        .cornerRadius(20)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    // Nombre del dispositivo
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Nombre del dispositivo")
                            .font(.manropeSemiBold(size: 13))
                            .foregroundColor(.textDark)
                        TextField("Ej: Apple Watch de Justin", text: $deviceName)
                            .font(.manrope(size: 14))
                            .padding()
                            .background(Color(hex: "#F0F2F5"))
                            .cornerRadius(12)
                    }

                    // Código
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Código de vinculación")
                            .font(.manropeSemiBold(size: 13))
                            .foregroundColor(.textDark)

                        TextField("Ej: 9WE8OPXY", text: $code)
                            .font(.system(size: 22, weight: .semibold, design: .monospaced))
                            .textInputAutocapitalization(.characters)
                            .disableAutocorrection(true)
                            .multilineTextAlignment(.center)
                            .padding()
                            .background(Color(hex: "#F0F2F5"))
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(vm.codeError != nil ? Color(hex: "#EF4444") : Color.borderGray, lineWidth: 1)
                            )
                            .onChange(of: code) { v in
                                if v.count > 8 { code = String(v.prefix(8)) }
                                code = code.uppercased()
                            }

                        if let error = vm.codeError {
                            HStack(spacing: 6) {
                                Image(systemName: "exclamationmark.circle.fill")
                                    .font(.system(size: 12))
                                Text(error)
                                    .font(.manrope(size: 12))
                            }
                            .foregroundColor(Color(hex: "#EF4444"))
                        }
                    }

                    // Botón vincular
                    Button {
                        let name = deviceName.isEmpty ? "\(selectedPlatform) Watch" : deviceName
                        vm.connectWithCode(code, deviceName: name, platform: selectedPlatform)
                    } label: {
                        Group {
                            if isConnecting {
                                HStack(spacing: 8) {
                                    ProgressView().tint(.white).scaleEffect(0.85)
                                    Text("Validando código…").font(.manropeSemiBold(size: 16)).foregroundColor(.white)
                                }
                            } else {
                                Text("Vincular wearable")
                                    .font(.manropeSemiBold(size: 16))
                                    .foregroundColor(.white)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                    }
                    .background(code.count == 8 && !isConnecting ? Color.primaryBlue : Color.primaryBlue.opacity(0.4))
                    .cornerRadius(32)
                    .disabled(code.count != 8 || isConnecting)
                    .onChange(of: vm.pairingSuccess) { success in
                        if success { isPresented = false }
                    }
                }
                .padding(24)
            }
            .navigationTitle("Nuevo wearable")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { isPresented = false }
                }
            }
        }
    }
}
#endif
