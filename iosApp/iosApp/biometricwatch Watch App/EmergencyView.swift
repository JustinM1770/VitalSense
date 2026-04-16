import SwiftUI
import Combine

struct EmergencyView: View {
    @EnvironmentObject var vm: WatchViewModel
    @State private var timeRemaining: Int = 0
    @State private var countdownTimer: Timer? = nil

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 6) {
                Text("BioMetric AI")
                    .font(.system(size: 14, weight: .black))
                    .foregroundColor(.white)

                Text("EMERGENCIA")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(Color(red: 234/255, green: 67/255, blue: 53/255))

                // PIN con separadores
                Text(pinDisplay)
                    .font(.system(size: 26, weight: .semibold, design: .monospaced))
                    .foregroundColor(.white)

                Text(vm.emergencyAnomalyType.isEmpty ? "Alerta detectada" : vm.emergencyAnomalyType)
                    .font(.system(size: 11))
                    .foregroundColor(.orange)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)

                Text(countdownText)
                    .font(.system(size: 13, weight: .semibold, design: .monospaced))
                    .foregroundColor(timeRemaining < 60 ? .red : Color(white: 0.5))
            }
            .padding(.horizontal, 8)
        }
        .onAppear { startCountdown() }
        .onDisappear { countdownTimer?.invalidate() }
        .onChange(of: vm.emergencyExpiresAt) { _ in
            countdownTimer?.invalidate()
            startCountdown()
        }
    }

    private var pinDisplay: String {
        guard !vm.emergencyPin.isEmpty else { return "----" }
        return vm.emergencyPin.map(String.init).joined(separator: " ")
    }

    private var countdownText: String {
        String(format: "%02d:%02d", timeRemaining / 60, timeRemaining % 60)
    }

    private func startCountdown() {
        updateTime()
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            DispatchQueue.main.async { updateTime() }
        }
    }

    private func updateTime() {
        let nowMs = Date().timeIntervalSince1970 * 1000
        timeRemaining = max(0, Int((vm.emergencyExpiresAt - nowMs) / 1000))
        if timeRemaining == 0 { countdownTimer?.invalidate() }
    }
}
