import SwiftUI
import Combine
import WatchKit

// MARK: - Tokens del Figma
private let vsGreen = Color(red: 52/255,  green: 168/255, blue: 83/255)   // #34A853
private let vsSOS   = Color(red: 234/255, green: 67/255,  blue: 53/255)   // #EA4335

struct ContentView: View {
    @EnvironmentObject var vm: WatchViewModel
    @State private var sosConfirming = false
    @State private var sosSent       = false
    @State private var confirmTimer: Timer? = nil

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if !vm.isPaired {
                PairingView()
            } else if let sosId = vm.activeSosId, let userId = vm.activeSosUserId {
                SosView(sosId: sosId, userId: userId)
            } else if vm.hasActiveEmergency {
                EmergencyView()
            } else {
                monitoringView
            }
        }
    }

    // MARK: - Pantalla principal — fiel al Figma

    private var monitoringView: some View {
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                Color.black.ignoresSafeArea()

                // Dot verde — indicador de estado (izquierda, ~35% del alto)
                Circle()
                    .fill(vsGreen)
                    .frame(width: 14, height: 14)
                    .position(x: 14, y: geo.size.height * 0.38)

                // BPM — centro-derecha, ~30% del alto
                VStack(spacing: 0) {
                    Text(vm.heartRate > 0 ? "\(vm.heartRate) bpm" : "-- bpm")
                        .font(.system(size: 36, weight: .regular))
                        .foregroundColor(.white)
                        .monospacedDigit()
                        .contentTransition(.numericText())
                }
                .frame(width: geo.size.width - 30, alignment: .leading)
                .position(x: geo.size.width / 2 + 8, y: geo.size.height * 0.33)

                // Fila SOS — roja, parte inferior, ~65% del alto
                VStack(spacing: 0) {
                    Button(action: sosTapped) {
                        ZStack {
                            (sosConfirming ? Color.orange : sosSent ? vsGreen : vsSOS)
                            Text(sosLabel)
                                .font(.system(size: sosConfirming ? 13 : 24, weight: .regular))
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)
                        }
                        .frame(width: geo.size.width - 6, height: 57)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                    }
                    .buttonStyle(.plain)
                    .disabled(sosSent)
                }
                .position(x: geo.size.width / 2, y: geo.size.height * 0.77)
            }
        }
    }

    // MARK: - SOS labels

    private var sosLabel: String {
        if sosSent        { return "Alerta enviada ✓" }
        if sosConfirming  { return "TOCA DE NUEVO\nPARA CONFIRMAR" }
        return "SOS"
    }

    private func sosTapped() {
        guard !sosSent else { return }
        if sosConfirming {
            confirmTimer?.invalidate()
            sosConfirming = false
            sosSent = true
            vm.triggerManualSOS()
            DispatchQueue.main.asyncAfter(deadline: .now() + 5) { sosSent = false }
        } else {
            sosConfirming = true
            WKInterfaceDevice.current().play(.click)
            confirmTimer = Timer.scheduledTimer(withTimeInterval: 4, repeats: false) { _ in
                DispatchQueue.main.async { sosConfirming = false }
            }
        }
    }
}
