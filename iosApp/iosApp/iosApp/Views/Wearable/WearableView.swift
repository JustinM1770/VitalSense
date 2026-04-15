#if os(iOS)
import SwiftUI

// MARK: - WearableView
// UI idéntica a DeviceScanScreen.kt de Android.
// Muestra CodePanel cuando no hay wearable emparejado,
// y PairedWatchPanel cuando ya está vinculado.

struct WearableView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var vm = WearableViewModel()

    var body: some View {
        ZStack {
            Color(hex: "#F0F2F5").ignoresSafeArea()

            ScrollView {
                VStack(spacing: 0) {
                    // ── Header
                    ZStack {
                        HStack {
                            Button(action: { dismiss() }) {
                                ZStack {
                                    Circle()
                                        .fill(Color.white)
                                        .frame(width: 40, height: 40)
                                        .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
                                    
                                    Image(systemName: "chevron.left")
                                        .font(.system(size: 18, weight: .bold))
                                        .foregroundColor(.textDark)
                                }
                            }
                            .buttonStyle(.plain)
                            
                            Spacer()
                        }

                        Text("Conectar Wearable")
                            .font(.custom("Manrope-Bold", size: 18))
                            .foregroundColor(.textDark)
                    }
                    .padding(.top, 20)
                    .padding(.bottom, 24)

                    if vm.isCodePaired {
                        PairedWatchPanel(
                            deviceName: vm.pairedDeviceName,
                            vitals: vm.vitals,
                            onDisconnect: { vm.disconnectWatch() }
                        )
                    } else {
                        CodePanel(
                            isConnecting: vm.connectionState == .connecting,
                            errorMessage: vm.codeError,
                            onConnectWithCode: { vm.connectWithCode($0) }
                        )
                    }
                }
                .padding(.horizontal, Spacing.xl)
            }
        }
        .navigationBarHidden(true)
        .onDisappear { vm.stopScan() }
    }
}

// MARK: - CodePanel (idéntico a CodePanel en DeviceScanScreen.kt)

private struct CodePanel: View {
    let isConnecting: Bool
    let errorMessage: String?
    let onConnectWithCode: (String) -> Void

    @State private var code = ""

    var body: some View {
        VStack(spacing: 0) {
            // Ícono bluetooth
            ZStack {
                Circle()
                    .fill(Color.onboardingBlue.opacity(0.10))
                    .frame(width: 80, height: 80)
                Image(systemName: "bluetooth")
                    .font(.system(size: 36))
                    .foregroundColor(.onboardingBlue)
            }

            Spacer().frame(height: Spacing.lg)

            Text("Para ver datos en tiempo real, abre BioMetric AI en tu reloj e ingresa el código de 8 caracteres que aparecerá allí.")
                .font(.custom("Manrope-Medium", size: 14))
                .foregroundColor(.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)

            Spacer().frame(height: Spacing.sm)

            Text("Si ya vinculaste por Bluetooth, recuerda que aún debes ingresar el código para sincronizar con esta app.")
                .font(.custom("Manrope-Medium", size: 12))
                .foregroundColor(.onboardingBlue.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal, Spacing.xxl)

            if let error = errorMessage {
                Spacer().frame(height: Spacing.md)
                Text(error)
                    .font(.custom("Manrope-Medium", size: 13))
                    .foregroundColor(Color(hex: "#EF4444"))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
            }

            Spacer().frame(height: Spacing.xxl)

            // Campo de código
            TextField("Código de vinculación", text: $code)
                .font(.custom("Manrope-Medium", size: 16))
                .textInputAutocapitalization(.characters)
                .disableAutocorrection(true)
                .padding()
                .background(Color.white)
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(errorMessage != nil ? Color(hex: "#EF4444") : Color.borderGray, lineWidth: 1)
                )
                .frame(maxWidth: UIScreen.main.bounds.width * 0.8)
                .onChange(of: code) { newValue in
                    if newValue.count > 8 { code = String(newValue.prefix(8)) }
                    code = code.uppercased()
                }

            Spacer().frame(height: 32)

            // Botón vincular
            Button {
                onConnectWithCode(code)
            } label: {
                ZStack {
                    if isConnecting {
                        HStack(spacing: 8) {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(0.8)
                            Text("Validando código…")
                                .font(.custom("Manrope-SemiBold", size: 16))
                                .foregroundColor(.white)
                        }
                    } else {
                        Text("Vincular")
                            .font(.custom("Manrope-SemiBold", size: 16))
                            .foregroundColor(.white)
                    }
                }
                .frame(width: 240, height: 50)
            }
            .background(
                code.count == 8 && !isConnecting
                    ? Color.onboardingBlue
                    : Color.onboardingBlue.opacity(0.5)
            )
            .cornerRadius(32)
            .disabled(code.count != 8 || isConnecting)
        }
    }
}

// MARK: - PairedWatchPanel (idéntico a PairedWatchPanel en DeviceScanScreen.kt)

private struct PairedWatchPanel: View {
    let deviceName: String
    let vitals: BLEVitals
    let onDisconnect: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Badge conectado (equivalente al Row verde con dot animado)
            HStack(spacing: 8) {
                Circle()
                    .fill(Color.spO2Green)
                    .frame(width: 8, height: 8)
                Text("\(deviceName) vinculado")
                    .font(.custom("Manrope-SemiBold", size: 13))
                    .foregroundColor(.spO2Green)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(Color.spO2Green.opacity(0.12))
            .cornerRadius(32)

            Spacer().frame(height: Spacing.md)

            Text("Datos en tiempo real")
                .font(.custom("Manrope-Medium", size: 12))
                .foregroundColor(.textSecondary)

            Spacer().frame(height: 28)

            // Fila 1: Ritmo cardíaco + Glucosa
            HStack(spacing: 12) {
                BLEVitalCard(
                    icon: "heart",
                    iconColor: .heartRateRed,
                    label: "Ritmo cardíaco",
                    value: vitals.heartRate.map { "\($0)" } ?? "—",
                    unit: "BPM"
                )
                BLEVitalCard(
                    icon: "drop",
                    iconColor: .glucoseOrange,
                    label: "Glucosa",
                    value: vitals.glucose.map { String(format: "%.0f", $0) } ?? "—",
                    unit: "mg/dL"
                )
            }

            Spacer().frame(height: Spacing.md)

            // Fila 2: SpO₂ + Sueño
            HStack(spacing: 12) {
                BLEVitalCard(
                    icon: "waveform.path.ecg",
                    iconColor: .spO2Green,
                    label: "SpO₂",
                    value: vitals.spo2.map { "\($0)" } ?? "—",
                    unit: "%"
                )
                BLEVitalCard(
                    icon: "moon.fill",
                    iconColor: Color(hex: "#10B981"),
                    label: "Sueño",
                    value: "—",
                    unit: "%"
                )
            }

            Spacer().frame(height: Spacing.lg)

            Text("Los datos se actualizan automáticamente cada 5 segundos.")
                .font(.custom("Manrope-Medium", size: 12))
                .foregroundColor(.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)

            Spacer().frame(height: 32)

            // Botón desvincular
            Button(action: onDisconnect) {
                HStack(spacing: 8) {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .semibold))
                    Text("Desvincular Reloj")
                        .font(.custom("Manrope-SemiBold", size: 16))
                }
                .foregroundColor(.white)
                .frame(width: 240, height: 50)
            }
            .background(Color(hex: "#EF4444"))
            .cornerRadius(32)
        }
    }
}

// MARK: - BLEVitalCard (idéntico a BleVitalCard Composable)

private struct BLEVitalCard: View {
    let icon: String
    let iconColor: Color
    let label: String
    let value: String
    let unit: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(iconColor)

            Spacer().frame(height: Spacing.sm)

            Text(label)
                .font(.custom("Manrope-Medium", size: 11))
                .foregroundColor(.textSecondary)

            Spacer().frame(height: 4)

            HStack(alignment: .bottom, spacing: 4) {
                Text(value)
                    .font(.custom("Manrope-Bold", size: 28))
                    .foregroundColor(.textDark)
                    .lineLimit(1)
                Text(unit)
                    .font(.system(size: 12))
                    .foregroundColor(.textSecondary)
                    .padding(.bottom, 3)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.borderGray, lineWidth: 1)
        )
    }
}

#endif
