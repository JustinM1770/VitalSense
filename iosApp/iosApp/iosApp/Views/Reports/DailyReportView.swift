#if os(iOS)
import SwiftUI
import Combine
import FirebaseAuth
import FirebaseDatabase

// MARK: - ViewModel
class DailyReportViewModel: ObservableObject {
    @Published var heartRates:   [Double] = []
    @Published var spo2Values:   [Double] = []
    @Published var glucoseValues:[Double] = []
    @Published var avgHR:   Double = 0
    @Published var avgSpo2: Double = 0
    @Published var avgGlucose: Double = 0
    @Published var sleepScore: Int = 0
    @Published var sleepEstado: String = "Sin datos"
    @Published var sleepHoras: Double = 0
    @Published var selectedFilter: String = "Hoy"

    private var ref: DatabaseReference?
    private let filters = ["Hoy", "Ayer", "7 días", "Mes"]

    func load() {
        loadFromFirebase()
        loadSleepFromHealthKit()
    }

    private func loadFromFirebase() {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        ref = Database.database().reference().child("patients/\(uid)/history")
        ref?.queryOrdered(byChild: "timestamp").queryLimited(toLast: 50)
            .observeSingleEvent(of: .value) { [weak self] snap in
                guard let self else { return }
                var hrs: [Double] = []; var spo2s: [Double] = []; var gls: [Double] = []
                for child in snap.children {
                    let s = child as! DataSnapshot
                    let d = s.value as? [String: Any] ?? [:]
                    if let hr = d["heartRate"] as? Double, hr > 0 { hrs.append(hr) }
                    else if let hr = d["heartRate"] as? Int, hr > 0 { hrs.append(Double(hr)) }
                    if let sp = d["spo2"] as? Double, sp > 0 { spo2s.append(sp) }
                    else if let sp = d["spo2"] as? Int, sp > 0 { spo2s.append(Double(sp)) }
                    if let gl = d["glucose"] as? Double, gl > 0 { gls.append(gl) }
                }
                DispatchQueue.main.async {
                    self.heartRates    = Array(hrs.suffix(14))
                    self.spo2Values    = Array(spo2s.suffix(14))
                    self.glucoseValues = Array(gls.suffix(14))
                    self.avgHR      = hrs.isEmpty ? 0 : hrs.reduce(0, +) / Double(hrs.count)
                    self.avgSpo2    = spo2s.isEmpty ? 0 : spo2s.reduce(0, +) / Double(spo2s.count)
                    self.avgGlucose = gls.isEmpty ? 0 : gls.reduce(0, +) / Double(gls.count)
                }
            }
    }

    private func loadSleepFromHealthKit() {
        let hk = HealthKitService.shared
        Task { @MainActor in
            // Si HealthKit ya está autorizado y tiene datos, úsalos directamente
            if hk.isAuthorized && hk.latestSleepScore > 0 {
                self.sleepScore  = hk.latestSleepScore
                self.sleepHoras  = hk.latestSleepHours
                self.sleepEstado = hk.sleepEstado
                return
            }
            // Solicitar autorización y cargar
            await hk.requestAuthorization()
            // Esperar un poco para que la query de sueño termine
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            if hk.latestSleepScore > 0 {
                self.sleepScore  = hk.latestSleepScore
                self.sleepHoras  = hk.latestSleepHours
                self.sleepEstado = hk.sleepEstado
            } else {
                // Sin datos de HealthKit: mostrar 0 en lugar de valores aleatorios
                self.sleepScore  = 0
                self.sleepHoras  = 0
                self.sleepEstado = "Sin datos"
            }
        }
    }
}

// MARK: - Main View
struct DailyReportView: View {
    @StateObject private var vm = DailyReportViewModel()
    private let filters = ["Hoy", "Ayer", "7 días", "Mes"]

    var body: some View {
        ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    // Filter chips
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(filters, id: \.self) { f in
                                Button(action: { vm.selectedFilter = f }) {
                                    Text(f)
                                        .font(.manropeSemiBold(size: 14))
                                        .foregroundColor(vm.selectedFilter == f ? Color.textPrimary : Color.textSecondary)
                                        .padding(.horizontal, 16)
                                        .padding(.vertical, 8)
                                        .background(vm.selectedFilter == f ? Color.dashBgAlt : Color.surfaceGray)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 16)
                                                .stroke(vm.selectedFilter == f ? Color.dashBgAlt : Color.borderGray, lineWidth: 1)
                                        )
                                        .clipShape(RoundedRectangle(cornerRadius: 16))
                                }
                            }
                        }
                        .padding(.horizontal, Spacing.xl)
                    }

                    // Health Pentagon Card
                    HealthRadarCard(vm: vm)
                        .padding(.horizontal, Spacing.xl)

                    // Averages card
                    PeriodAverageCard(vm: vm)
                        .padding(.horizontal, Spacing.xl)

                    // Metrics title
                    HStack {
                        Text("Métricas de Salud")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(Color.textPrimary)
                        Spacer()
                    }
                    .padding(.horizontal, Spacing.xl)

                    // HR Trend
                    HeartRateTrendCard(values: vm.heartRates)
                        .padding(.horizontal, Spacing.xl)

                    // SpO2 Card
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 10) {
                            Image(systemName: "lungs.fill")
                                .foregroundColor(Color.successGreen)
                                .font(.title3)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Oxigenación SpO₂")
                                    .font(.system(size: 13))
                                    .foregroundColor(Color.textSecondary)
                                Text(vm.avgSpo2 > 0 ? "\(Int(vm.avgSpo2))%" : "--")
                                    .font(.system(size: 28, weight: .bold))
                                    .foregroundColor(Color.successGreen)
                            }
                        }
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .vsShadow(.medium)
                    .padding(.horizontal, Spacing.xl)

                    // Sleep Card — navigates to SleepDetailView
                    NavigationLink(destination: SleepDetailView(score: vm.sleepScore, horas: vm.sleepHoras, estado: vm.sleepEstado)) {
                        SleepMetricCard(vm: vm).padding(.horizontal, Spacing.xl)
                    }
                    .buttonStyle(.plain)

                    // Detailed Report link
                    NavigationLink(destination: DetailedReportView()) {
                        HStack {
                            Image(systemName: "chart.xyaxis.line").foregroundColor(Color.primaryBlue)
                            Text("Ver reporte detallado").font(.system(size: 14, weight: .semibold)).foregroundColor(Color.primaryBlue)
                            Spacer()
                            Image(systemName: "chevron.right").foregroundColor(Color.primaryBlue)
                        }
                        .padding(16)
                        .background(Color(hex: "#EBF2FF"))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .padding(.horizontal, Spacing.xl)
                    }

                    Spacer(minLength: 90)
                }
                .padding(.top, 16)
        }
        .background(Color.white.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 2) {
                    Text(Date(), style: .date)
                        .font(Font.custom("MavenPro-Medium", size: 14))
                        .foregroundColor(Color(hex: "#7E7E7E"))
                    Text("Reporte Diario")
                        .font(.manropeBold(size: 22))
                        .foregroundColor(Color.textPrimary)
                }
            }
        }
        .onAppear { vm.load() }
    }
}

// MARK: - Health Radar (Pentagon) Card
struct HealthRadarCard: View {
    @ObservedObject var vm: DailyReportViewModel

    private var sleepPct:   CGFloat { CGFloat(vm.sleepScore) / 100 }
    private var glucosePct: CGFloat { vm.avgGlucose > 0 ? CGFloat(min(vm.avgGlucose / 140, 1)) : 0.6 }
    private var spo2Pct:    CGFloat { vm.avgSpo2 > 0 ? CGFloat(min(vm.avgSpo2 / 100, 1)) : 0.9 }
    private var hrPct:      CGFloat { vm.avgHR > 0 ? CGFloat(min(vm.avgHR / 160, 1)) : 0.5 }
    private var actPct:     CGFloat { 0.7 }

    private var scoreColor: Color {
        vm.sleepScore >= 85 ? Color.successGreen : vm.sleepScore >= 60 ? Color.warningAmber : Color.heartRateRed
    }

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Estado de Salud")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color.textPrimary)
                    Text("Periodo: \(vm.selectedFilter)")
                        .font(.system(size: 12))
                        .foregroundColor(Color.textSecondary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text("\(vm.sleepScore)")
                        .font(.system(size: 32, weight: .black))
                        .foregroundColor(scoreColor)
                    Text(vm.sleepEstado)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(scoreColor)
                }
            }

            PentagonChart(values: [sleepPct, glucosePct, spo2Pct, hrPct, actPct])
                .frame(height: 200)

            // Legend
            HStack(spacing: 0) {
                ForEach(["Sueño", "Glucosa", "SpO₂", "FC", "Act."], id: \.self) { label in
                    VStack(spacing: 4) {
                        Circle()
                            .fill(Color.primaryBlue)
                            .frame(width: 8, height: 8)
                        Text(label)
                            .font(.system(size: 10))
                            .foregroundColor(Color.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(20)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: .black.opacity(0.06), radius: 8, x: 0, y: 2)
    }
}

// MARK: - Pentagon Canvas
struct PentagonChart: View {
    let values: [CGFloat] // 5 values 0..1

    var body: some View {
        Canvas { ctx, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let maxR = min(size.width, size.height) * 0.42
            let count = 5
            let step = (2 * Double.pi) / Double(count)
            let start = -Double.pi / 2

            // Grid rings
            for ring in [0.25, 0.5, 0.75, 1.0] {
                var path = Path()
                for i in 0..<count {
                    let angle = start + Double(i) * step
                    let r = maxR * ring
                    let pt = CGPoint(x: center.x + r * cos(angle), y: center.y + r * sin(angle))
                    i == 0 ? path.move(to: pt) : path.addLine(to: pt)
                }
                path.closeSubpath()
                ctx.stroke(path, with: .color(.gray.opacity(0.15)), lineWidth: 1)
            }

            // Axes
            for i in 0..<count {
                let angle = start + Double(i) * step
                var path = Path()
                path.move(to: center)
                path.addLine(to: CGPoint(x: center.x + maxR * cos(angle), y: center.y + maxR * sin(angle)))
                ctx.stroke(path, with: .color(.gray.opacity(0.2)), lineWidth: 1)
            }

            // Data polygon
            let safeValues = values.count >= count ? values : values + Array(repeating: 0.5, count: count - values.count)
            var dataPath = Path()
            for i in 0..<count {
                let angle = start + Double(i) * step
                let r = maxR * safeValues[i]
                let pt = CGPoint(x: center.x + r * cos(angle), y: center.y + r * sin(angle))
                i == 0 ? dataPath.move(to: pt) : dataPath.addLine(to: pt)
            }
            dataPath.closeSubpath()
            ctx.fill(dataPath, with: .color(Color.primaryBlue.opacity(0.25)))
            ctx.stroke(dataPath, with: .color(Color.primaryBlue), lineWidth: 2)
        }
    }
}

// MARK: - Period Average Card
struct PeriodAverageCard: View {
    @ObservedObject var vm: DailyReportViewModel

    var body: some View {
        VStack(spacing: 14) {
            HStack {
                Text("Promedios del periodo")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Color.textPrimary)
                Spacer()
                Text(vm.selectedFilter)
                    .font(.system(size: 12))
                    .foregroundColor(Color.textSecondary)
            }
            HStack(spacing: 0) {
                AveragePill(icon: "heart.fill", value: vm.avgHR > 0 ? "\(Int(vm.avgHR))" : "--", unit: "bpm", color: Color.heartRateRed)
                Spacer()
                AveragePill(icon: "drop.fill", value: vm.avgGlucose > 0 ? "\(Int(vm.avgGlucose))" : "--", unit: "mg/dL", color: Color.warningAmber)
                Spacer()
                AveragePill(icon: "lungs.fill", value: vm.avgSpo2 > 0 ? "\(Int(vm.avgSpo2))" : "--", unit: "SpO₂", color: Color.successGreen)
                Spacer()
                AveragePill(icon: "moon.fill", value: "\(vm.sleepScore)", unit: "pts", color: Color(hex: "#00C48C"))
            }
        }
        .padding(20)
        .background(Color(hex: "#F0F2F5"))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct AveragePill: View {
    let icon: String; let value: String; let unit: String; let color: Color
    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon).foregroundColor(color).font(.system(size: 16))
            Text(value).font(.system(size: 18, weight: .bold)).foregroundColor(Color.textPrimary)
            Text(unit).font(.system(size: 10)).foregroundColor(Color.textSecondary)
        }
    }
}

// MARK: - HR Trend Card
struct HeartRateTrendCard: View {
    let values: [Double]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "heart.fill").foregroundColor(Color.heartRateRed)
                Text("Frecuencia Cardíaca").font(.system(size: 15, weight: .semibold)).foregroundColor(Color.textPrimary)
                Spacer()
                if let last = values.last {
                    Text("\(Int(last)) bpm").font(.system(size: 15, weight: .bold)).foregroundColor(Color.heartRateRed)
                }
            }
            if values.count > 1 {
                LineChartView(values: values, color: Color.heartRateRed)
                    .frame(height: 80)
            } else {
                Text("Sin datos suficientes").font(.system(size: 13)).foregroundColor(Color.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .center).frame(height: 80)
            }
        }
        .padding(20)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .vsShadow(.medium)
    }
}

// MARK: - Simple line chart canvas
struct LineChartView: View {
    let values: [Double]
    let color: Color

    var body: some View {
        Canvas { ctx, size in
            guard values.count > 1 else { return }
            let minV = values.min()! - 5
            let maxV = values.max()! + 5
            let range = maxV - minV
            guard range > 0 else { return }

            let w = size.width / CGFloat(values.count - 1)

            func pt(_ i: Int) -> CGPoint {
                let x = CGFloat(i) * w
                let y = size.height - CGFloat((values[i] - minV) / range) * size.height
                return CGPoint(x: x, y: y)
            }

            // Gradient fill
            var fillPath = Path()
            fillPath.move(to: CGPoint(x: 0, y: size.height))
            for i in 0..<values.count { fillPath.addLine(to: pt(i)) }
            fillPath.addLine(to: CGPoint(x: size.width, y: size.height))
            fillPath.closeSubpath()
            ctx.fill(fillPath, with: .color(color.opacity(0.15)))

            // Line
            var linePath = Path()
            linePath.move(to: pt(0))
            for i in 1..<values.count { linePath.addLine(to: pt(i)) }
            ctx.stroke(linePath, with: .color(color), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
        }
    }
}

// MARK: - Sleep Card
struct SleepMetricCard: View {
    @ObservedObject var vm: DailyReportViewModel

    private var ringColor: Color {
        vm.sleepScore >= 85 ? Color(hex: "#00C48C") : vm.sleepScore >= 60 ? Color.warningAmber : Color.heartRateRed
    }

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle().stroke(ringColor.opacity(0.2), lineWidth: 8).frame(width: 72, height: 72)
                Circle()
                    .trim(from: 0, to: CGFloat(vm.sleepScore) / 100)
                    .stroke(ringColor, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: 72, height: 72)
                VStack(spacing: 0) {
                    Text("\(vm.sleepScore)").font(.system(size: 18, weight: .black)).foregroundColor(ringColor)
                    Text("pts").font(.system(size: 10)).foregroundColor(Color.textSecondary)
                }
            }
            VStack(alignment: .leading, spacing: 6) {
                Text("Sueño").font(.system(size: 16, weight: .bold)).foregroundColor(Color.textPrimary)
                Text(vm.sleepEstado).font(.system(size: 13, weight: .semibold)).foregroundColor(ringColor)
                Text(String(format: "%.1f horas esta noche", vm.sleepHoras))
                    .font(.system(size: 12)).foregroundColor(Color.textSecondary)
            }
            Spacer()
            Image(systemName: "chevron.right").foregroundColor(Color.textSecondary)
        }
        .padding(20)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .vsShadow(.medium)
    }
}

#endif
