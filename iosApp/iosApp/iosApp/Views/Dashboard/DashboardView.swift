#if os(iOS)
import SwiftUI
import FirebaseAuth

// MARK: - Sleep data model
struct SleepDataiOS {
    let score: Int
    let hours: Double
    let estado: String
}

// MARK: - Dashboard (paridad total con DashboardScreen.kt de Android)
struct DashboardView: View {
    var onEmergency: ((String, Int) -> Void)? = nil
    @StateObject private var vm = DashboardViewModel()
    @ObservedObject private var demo = DemoModeService.shared
    @State private var currentMetricPage = 0
    @State private var showDemoSheet = false

    private let metricPages: [MetricCardType] = [.sleep, .spo2, .heartRate, .kcal]

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                Color.white.ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        Spacer().frame(height: Spacing.xxl)

                        UserHeaderView(
                            name: vm.userName,
                            initial: vm.userInitial,
                            onDemoTap: { showDemoSheet = true }
                        )
                        .padding(.horizontal, Spacing.xxl)

                        Spacer().frame(height: Spacing.xxl)

                        DashboardSearchBarView()
                            .padding(.horizontal, Spacing.xxl)

                        Spacer().frame(height: Spacing.xxl)

                        // ── Blue rounded container ───────────────────────
                        VStack(alignment: .leading, spacing: 0) {

                            // ── "Esta semana" + Pager ────────────────────
                            HStack {
                                DashboardSectionHeader(title: "Esta semana")
                                Spacer()
                                Button(action: {}) {
                                    ZStack {
                                        Circle()
                                            .fill(Color.white)
                                            .frame(width: 30, height: 30)
                                            .shadow(color: .black.opacity(0.08), radius: 2, x: 0, y: 1)
                                        Text("+")
                                            .font(.manropeBold(size: 18))
                                            .foregroundColor(Color.primaryBlue)
                                    }
                                }
                            }
                            .padding(.horizontal, Spacing.xxl)
                            .padding(.top, 24)

                            Spacer().frame(height: Spacing.lg)

                            TabView(selection: $currentMetricPage) {
                                ForEach(metricPages.indices, id: \.self) { i in
                                    metricCard(for: metricPages[i])
                                        .padding(.horizontal, Spacing.xxl)
                                        .tag(i)
                                }
                            }
                            .tabViewStyle(.page(indexDisplayMode: .never))
                            .frame(height: 155)

                            HStack(spacing: 6) {
                                ForEach(metricPages.indices, id: \.self) { i in
                                    Circle()
                                        .fill(i == currentMetricPage ? Color.primaryBlue : Color.white.opacity(0.5))
                                        .frame(width: 6, height: 6)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.top, 8)

                            Spacer().frame(height: Spacing.xxl)

                            LibreQuickCardView(
                                glucose: vm.libreLastGlucose,
                                timestamp: vm.libreLastTime
                            )
                            .padding(.horizontal, Spacing.xxl)

                            Spacer().frame(height: Spacing.lg)

                            QuickActionsCardView()
                                .padding(.horizontal, Spacing.xxl)

                            Spacer().frame(height: Spacing.lg)

                            NavigationLink(destination: WearableView()) {
                                WatchConnectionCard()
                            }
                            .buttonStyle(.plain)
                            .padding(.horizontal, Spacing.xxl)

                            Spacer().frame(height: Spacing.xxl)

                            HealthMetricsGraphCard(
                                patients: vm.patients,
                                vitalsHistory: vm.vitalsHistory
                            )
                            .padding(.horizontal, Spacing.xxl)

                            Spacer().frame(height: Spacing.xxl)

                            MedicationsCardView(medications: vm.medications)
                                .padding(.horizontal, Spacing.xxl)

                            Spacer().frame(height: Spacing.xxl)

                            AIRiskSummaryCard(riskLevel: vm.aiRiskLevel, summary: vm.aiSummary)
                                .padding(.horizontal, Spacing.xxl)

                            Spacer().frame(height: Spacing.xxl)

                            DashboardSectionHeader(title: "Métricas de Salud")
                                .padding(.horizontal, Spacing.xxl)

                            Spacer().frame(height: Spacing.lg)

                            if vm.isLoading {
                                ProgressView("Cargando vitales...")
                                    .font(.manrope(size: 14))
                                    .frame(maxWidth: .infinity, alignment: .center)
                                    .padding(.vertical, 16)
                            } else if vm.patients.isEmpty {
                                EmptyPatientsCardView()
                                    .padding(.horizontal, Spacing.xxl)
                            } else {
                                VStack(spacing: 14) {
                                    ForEach(vm.patients) { patient in
                                        NavigationLink(destination: PatientDetailView(patient: patient)) {
                                            PatientCard(patient: patient)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                                .padding(.horizontal, Spacing.xxl)
                            }

                            Spacer().frame(height: Spacing.xxl)
                        }
                        .background(Color.dashBgAlt)
                        .clipShape(
                            UnevenRoundedRectangle(
                                topLeadingRadius: 36,
                                bottomLeadingRadius: 0,
                                bottomTrailingRadius: 0,
                                topTrailingRadius: 35
                            )
                        )
                    }
                    .padding(.bottom, 90)
                }

                if let alert = vm.alertMessage {
                    AlertBannerView(message: alert)
                        .padding(.horizontal, Spacing.xxl)
                        .padding(.top, 8)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            .navigationBarHidden(true)
        }
        .onAppear {
            vm.startObserving()
            // Restore demo mode if it was active before
            if let uid = Auth.auth().currentUser?.uid {
                DemoModeService.shared.restoreIfNeeded(userId: uid)
            }
        }
        .onDisappear { vm.stopObserving() }
        .animation(.easeInOut, value: vm.alertMessage)
        .sheet(isPresented: $showDemoSheet) {
            DemoModeSheet(demo: demo)
        }
    }

    @ViewBuilder
    private func metricCard(for type: MetricCardType) -> some View {
        switch type {
        case .sleep:
            SleepMetricPagerCard(sleepData: vm.sleepData)
        case .spo2:
            Spo2MiniCard(patients: vm.patients)
        case .heartRate:
            HrMiniCard(patients: vm.patients)
        case .kcal:
            KcalMiniCard(patients: vm.patients)
        }
    }
}

private enum MetricCardType {
    case sleep, spo2, heartRate, kcal
}

// MARK: - Demo Mode Sheet
struct DemoModeSheet: View {
    @ObservedObject var demo: DemoModeService
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                // Status indicator
                ZStack {
                    Circle()
                        .fill(demo.isActive ? Color.successGreen.opacity(0.12) : Color.surfaceGray)
                        .frame(width: 100, height: 100)
                    Image(systemName: demo.isActive ? "play.circle.fill" : "play.circle")
                        .font(.system(size: 48))
                        .foregroundColor(demo.isActive ? Color.successGreen : Color.textSecondary)
                }
                .padding(.top, 24)

                VStack(spacing: 8) {
                    Text(demo.isActive ? "Modo Demo Activo" : "Modo Demo")
                        .font(.manropeBold(size: 22))
                        .foregroundColor(Color.textPrimary)
                    Text(demo.isActive
                         ? "Generando vitales realistas en tiempo real. Desactiva el modo demo antes de la presentación con datos reales."
                         : "Genera 7 días de signos vitales realistas para demostraciones. Ideal para jueces del Innovatec.")
                        .font(.manrope(size: 14))
                        .foregroundColor(Color.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, Spacing.xxl)
                }

                // Live vitals preview (when active)
                if demo.isActive {
                    HStack(spacing: 16) {
                        DemoVitalPill(icon: "❤️", value: "\(Int(demo.liveHeartRate))", unit: "bpm", color: Color.heartRateRed)
                        DemoVitalPill(icon: "🫁", value: "\(Int(demo.liveSpO2))%", unit: "SpO₂", color: Color.successGreen)
                        DemoVitalPill(icon: "🩸", value: "\(Int(demo.liveGlucose))", unit: "mg/dL", color: Color.primaryBlue)
                    }
                    .padding(.horizontal, Spacing.xxl)
                }

                // Progress (during seeding)
                if demo.isActive && demo.seedingProgress < 1.0 {
                    VStack(spacing: 8) {
                        Text("Generando historial de 7 días...")
                            .font(.manrope(size: 13))
                            .foregroundColor(Color.textSecondary)
                        ProgressView(value: demo.seedingProgress)
                            .tint(Color.successGreen)
                            .padding(.horizontal, Spacing.xxl)
                        Text("\(Int(demo.seedingProgress * 100))%")
                            .font(.manropeSemiBold(size: 12))
                            .foregroundColor(Color.successGreen)
                    }
                }

                // What it generates
                if !demo.isActive {
                    VStack(alignment: .leading, spacing: 10) {
                        DemoFeatureRow(icon: "heart.fill", text: "FC con patrón circadiano (60-85 bpm)")
                        DemoFeatureRow(icon: "lungs.fill", text: "SpO₂ con desaturaciones nocturnas realistas")
                        DemoFeatureRow(icon: "drop.fill", text: "Glucosa post-prandial (patrón pre-diabético)")
                        DemoFeatureRow(icon: "chart.bar.fill", text: "7 días de historial para análisis IA")
                        DemoFeatureRow(icon: "brain.filled.head.profile", text: "6 algoritmos: FINDRISC · Framingham · STOP-BANG · CHA₂DS₂-VASc · MAGGIC · ESC HTA")
                    }
                    .padding(.horizontal, 32)
                }

                Spacer()

                VStack(spacing: 12) {
                    // Toggle button
                    Button(action: toggleDemo) {
                        Text(demo.isActive ? "Desactivar Modo Demo" : "Activar Modo Demo")
                            .font(.manropeSemiBold(size: 16))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity).frame(height: 52)
                            .background(demo.isActive ? Color.errorRed : Color.successGreen)
                            .clipShape(RoundedRectangle(cornerRadius: 32))
                    }

                    // IDENTIMEX Demo Button — para jueces de InnovaTecNM
                    // Activa el protocolo completo: QR público + WhatsApp E2E al contacto
                    Button {
                        dismiss()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                            NotificationCenter.default.post(
                                name: .triggerIDENTIMEX,
                                object: nil,
                                userInfo: [
                                    "anomalyType": "Fibrilación Auricular — FC 142 bpm · Irregularidad R-R 23% (PhysioNet MIT-BIH)",
                                    "heartRate": 142
                                ]
                            )
                        }
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "shield.lefthalf.filled")
                                .font(.system(size: 15, weight: .bold))
                            VStack(alignment: .leading, spacing: 1) {
                                Text("Demostrar Protocolo IDENTIMEX")
                                    .font(.manropeSemiBold(size: 14))
                                Text("QR público + PIN por WhatsApp E2E")
                                    .font(.manrope(size: 10))
                                    .opacity(0.85)
                            }
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity).frame(height: 56)
                        .background(
                            LinearGradient(colors: [Color(hex: "#B71C1C"), Color(hex: "#D32F2F")],
                                           startPoint: .leading, endPoint: .trailing)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 32))
                    }
                }
                .padding(.horizontal, Spacing.xxl)
                .padding(.bottom, 32)
            }
            .background(Color.white.ignoresSafeArea())
            .navigationTitle("Modo Demo — Innovatec")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") { dismiss() }
                }
            }
        }
    }

    private func toggleDemo() {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        if demo.isActive {
            demo.deactivate(userId: uid)
        } else {
            Task { await demo.activate(userId: uid) }
        }
    }
}

private struct DemoVitalPill: View {
    let icon: String; let value: String; let unit: String; let color: Color
    var body: some View {
        VStack(spacing: 4) {
            Text(icon).font(.system(size: 20))
            Text(value).font(.manropeBold(size: 18)).foregroundColor(color)
            Text(unit).font(.manrope(size: 10)).foregroundColor(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(color.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct DemoFeatureRow: View {
    let icon: String; let text: String
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(Color.primaryBlue)
                .frame(width: 22)
            Text(text)
                .font(.manrope(size: 13))
                .foregroundColor(Color.textPrimary)
        }
    }
}

// MARK: - User Header
struct UserHeaderView: View {
    let name: String
    let initial: String
    var onDemoTap: (() -> Void)? = nil
    @ObservedObject private var demo = DemoModeService.shared

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.primaryBlue)
                    .frame(width: 56, height: 56)
                    .overlay(
                        Text(initial)
                            .font(.manropeBold(size: 20))
                            .foregroundColor(.white)
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text("Bienvenido")
                        .font(.manrope(size: 12))
                        .foregroundColor(Color.textSecondary)
                    Text(name)
                        .font(.manropeBold(size: 24))
                        .foregroundColor(Color(hex: "#2B2B2B"))
                }

                Spacer()

                // Demo mode toggle button
                Button(action: { onDemoTap?() }) {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(demo.isActive ? Color.successGreen : Color.textSecondary)
                            .frame(width: 7, height: 7)
                        Text(demo.isActive ? "Demo" : "Demo")
                            .font(.manropeSemiBold(size: 11))
                            .foregroundColor(demo.isActive ? Color.successGreen : Color.textSecondary)
                    }
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background((demo.isActive ? Color.successGreen : Color.textSecondary).opacity(0.10))
                    .clipShape(Capsule())
                }

                NavigationLink(destination: NotificationsView()) {
                    ZStack(alignment: .topTrailing) {
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(Color.white)
                            .frame(width: 48, height: 48)
                            .shadow(color: .black.opacity(0.08), radius: 3, x: 0, y: 1)
                            .overlay(
                                Image(systemName: "bell.fill")
                                    .foregroundColor(Color.textDark)
                            )

                        Circle()
                            .fill(Color.red)
                            .frame(width: 8, height: 8)
                            .offset(x: -8, y: 8)
                    }
                }
                .buttonStyle(.plain)
            }

            // Demo seeding progress bar
            if demo.isActive && demo.seedingProgress < 1.0 {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Generando datos de demostración...")
                        .font(.manrope(size: 11))
                        .foregroundColor(Color.textSecondary)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 3).fill(Color.borderGray).frame(height: 6)
                            RoundedRectangle(cornerRadius: 3).fill(Color.successGreen)
                                .frame(width: geo.size.width * demo.seedingProgress, height: 6)
                        }
                    }
                    .frame(height: 6)
                }
                .transition(.opacity)
            }
        }
    }
}

// MARK: - Sleep Metric Pager Card
struct SleepMetricPagerCard: View {
    let sleepData: SleepDataiOS?

    private var score: Int { sleepData?.score ?? 0 }
    private var hours: Double { sleepData?.hours ?? 0 }
    private var estado: String { sleepData?.estado ?? "Sin datos" }
    private var ringColor: Color {
        score >= 85 ? Color(hex: "#10B981") : score >= 60 ? Color.warningAmber : Color.heartRateRed
    }

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .stroke(ringColor.opacity(0.2), lineWidth: 6)
                    .frame(width: 70, height: 70)
                Circle()
                    .trim(from: 0, to: CGFloat(score) / 100)
                    .stroke(ringColor, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: 70, height: 70)
                VStack(spacing: 0) {
                    Text("\(score)%")
                        .font(.manropeBold(size: 14))
                        .foregroundColor(Color.textDark)
                    Text("pts")
                        .font(.manrope(size: 9))
                        .foregroundColor(Color.textSecondary)
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Sueño")
                    .font(.manropeBold(size: 18))
                    .foregroundColor(ringColor)
                Text(estado)
                    .font(.manropeSemiBold(size: 13))
                    .foregroundColor(ringColor)
                Text(String(format: "%.1f horas esta noche", hours))
                    .font(.manrope(size: 12))
                    .foregroundColor(Color.textSecondary)
            }
            Spacer()
        }
        .padding(20)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .vsShadow(.medium)
    }
}

// MARK: - SpO2 Mini Card
struct Spo2MiniCard: View {
    let patients: [VitalsDataiOS]

    private var avgSpo2: Int {
        guard !patients.isEmpty else { return 0 }
        return patients.reduce(0) { $0 + $1.spo2 } / patients.count
    }

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .stroke(Color.spO2Green.opacity(0.2), lineWidth: 6)
                    .frame(width: 70, height: 70)
                Circle()
                    .trim(from: 0, to: CGFloat(min(max(Double(avgSpo2) / 100.0, 0), 1)))
                    .stroke(Color.spO2Green, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: 70, height: 70)
                Text("\(avgSpo2)%")
                    .font(.manropeBold(size: 14))
                    .foregroundColor(Color.textDark)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("SpO₂")
                    .font(.manropeBold(size: 18))
                    .foregroundColor(Color.spO2Green)
                Text(avgSpo2 >= 96 ? "Estable" : avgSpo2 >= 90 ? "Moderado" : "Bajo")
                    .font(.manropeSemiBold(size: 13))
                    .foregroundColor(Color.spO2Green)
                Text("Promedio de hoy")
                    .font(.manrope(size: 12))
                    .foregroundColor(Color.textSecondary)
            }
            Spacer()
        }
        .padding(20)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .vsShadow(.medium)
    }
}

// MARK: - HR Mini Card
struct HrMiniCard: View {
    let patients: [VitalsDataiOS]

    private var avgHR: Int {
        guard !patients.isEmpty else { return 0 }
        return patients.reduce(0) { $0 + $1.heartRate } / patients.count
    }

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .stroke(Color.heartRateRed.opacity(0.2), lineWidth: 6)
                    .frame(width: 70, height: 70)
                Circle()
                    .trim(from: 0, to: CGFloat(min(max(Double(avgHR) / 200.0, 0), 1)))
                    .stroke(Color.heartRateRed, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: 70, height: 70)
                VStack(spacing: 0) {
                    Text("\(avgHR)")
                        .font(.manropeBold(size: 14))
                        .foregroundColor(Color.textDark)
                    Text("bpm")
                        .font(.manrope(size: 9))
                        .foregroundColor(Color.textSecondary)
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Ritmo Cardíaco")
                    .font(.manropeBold(size: 18))
                    .foregroundColor(Color.heartRateRed)
                Text(avgHR > 100 ? "Elevado" : avgHR > 60 ? "Normal" : avgHR > 0 ? "Bajo" : "Sin datos")
                    .font(.manropeSemiBold(size: 13))
                    .foregroundColor(Color.heartRateRed)
                Text("Promedio de hoy")
                    .font(.manrope(size: 12))
                    .foregroundColor(Color.textSecondary)
            }
            Spacer()
        }
        .padding(20)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .vsShadow(.medium)
    }
}

// MARK: - Kcal Mini Card
struct KcalMiniCard: View {
    let patients: [VitalsDataiOS]

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .stroke(Color.glucoseOrange.opacity(0.2), lineWidth: 6)
                    .frame(width: 70, height: 70)
                Circle()
                    .trim(from: 0, to: 0.65)
                    .stroke(Color.glucoseOrange, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: 70, height: 70)
                VStack(spacing: 0) {
                    Text("1,420")
                        .font(.manropeBold(size: 12))
                        .foregroundColor(Color.textDark)
                    Text("kcal")
                        .font(.manrope(size: 9))
                        .foregroundColor(Color.textSecondary)
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Calorías")
                    .font(.manropeBold(size: 18))
                    .foregroundColor(Color.glucoseOrange)
                Text("Estimado (actividad)")
                    .font(.manropeSemiBold(size: 13))
                    .foregroundColor(Color.glucoseOrange)
                Text("Basado en datos del día")
                    .font(.manrope(size: 12))
                    .foregroundColor(Color.textSecondary)
            }
            Spacer()
        }
        .padding(20)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .vsShadow(.medium)
    }
}

// MARK: - Libre Quick Card
struct LibreQuickCardView: View {
    let glucose: Double
    let timestamp: TimeInterval

    private var timeAgo: String {
        guard timestamp > 0 else { return "Sin lectura" }
        let diff = Date().timeIntervalSince1970 - (timestamp / 1000)
        let min = Int(diff / 60)
        if min < 1 { return "Hace un momento" }
        if min < 60 { return "Hace \(min) min" }
        let hr = min / 60
        return "Hace \(hr)h"
    }

    private var glucoseColor: Color {
        if glucose <= 0 { return Color(hex: "#6B7A8D") }
        if glucose < 70 { return Color.heartRateRed }
        if glucose <= 140 { return Color(hex: "#10B981") }
        return Color.warningAmber
    }

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(glucoseColor.opacity(0.12))
                    .frame(width: 48, height: 48)
                Image(systemName: "drop.fill")
                    .font(.manropeBold(size: 20))
                    .foregroundColor(glucoseColor)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("Glucosa Libre")
                    .font(.manropeSemiBold(size: 13))
                    .foregroundColor(Color.textDark)
                Text(timeAgo)
                    .font(.manrope(size: 11))
                    .foregroundColor(Color.textSecondary)
            }

            Spacer()

            if glucose > 0 {
                Text("\(Int(glucose)) mg/dL")
                    .font(.manropeBold(size: 20))
                    .foregroundColor(glucoseColor)
            } else {
                Text("— mg/dL")
                    .font(.manropeBold(size: 20))
                    .foregroundColor(Color(hex: "#B0B8C4"))
            }
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: .black.opacity(0.06), radius: 4, x: 0, y: 2)
    }
}

// MARK: - Quick Actions
struct QuickActionsCardView: View {
    var body: some View {
        HStack(spacing: 12) {
            NavigationLink(destination: LibreCGMView()) {
                quickActionItem(
                    icon: "sensor.tag.radiowaves.forward.fill",
                    label: "Glucosa\nLibre",
                    color: Color(hex: "#7B61FF")
                )
            }
            .buttonStyle(.plain)

            NavigationLink(destination: AddMedicationView(onBack: {})) {
                quickActionItem(
                    icon: "pill.fill",
                    label: "Agregar\nMedicamento",
                    color: Color.primaryBlue
                )
            }
            .buttonStyle(.plain)
        }
    }

    private func quickActionItem(icon: String, label: String, color: Color) -> some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(color.opacity(0.12))
                    .frame(width: 44, height: 44)
                Image(systemName: icon)
                    .font(.manropeSemiBold(size: 18))
                    .foregroundColor(color)
            }
            Text(label)
                .font(.manropeSemiBold(size: 11))
                .foregroundColor(Color.textDark)
                .multilineTextAlignment(.center)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: .black.opacity(0.04), radius: 3, x: 0, y: 1)
    }
}

// MARK: - Health Metrics Graph
struct HealthMetricsGraphCard: View {
    let patients: [VitalsDataiOS]
    let vitalsHistory: [VitalsDataiOS]

    private var hrValues: [Double] {
        let hist = vitalsHistory.map { Double($0.heartRate) }.filter { $0 > 0 }
        if hist.count > 1 { return Array(hist.suffix(14)) }
        return patients.map { Double($0.heartRate) }.filter { $0 > 0 }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Métricas de Salud")
                    .font(.manropeBold(size: 16))
                    .foregroundColor(Color.textDark)
                Spacer()
                NavigationLink(destination: DetailedReportView()) {
                    Text("Ver todo")
                        .font(.manropeSemiBold(size: 12))
                        .foregroundColor(Color.primaryBlue)
                }
            }

            HStack {
                Image(systemName: "heart.fill")
                    .foregroundColor(Color.heartRateRed)
                Text("Frecuencia Cardíaca")
                    .font(.manropeSemiBold(size: 13))
                    .foregroundColor(Color(hex: "#0D1B2A"))
                Spacer()
                if let last = hrValues.last {
                    Text("\(Int(last)) bpm")
                        .font(.manropeBold(size: 14))
                        .foregroundColor(Color.heartRateRed)
                }
            }

            if hrValues.count > 1 {
                LineChartView(values: hrValues, color: Color.heartRateRed)
                    .frame(height: 70)
            } else {
                Text("Sin datos suficientes")
                    .font(.manrope(size: 13))
                    .foregroundColor(Color.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .frame(height: 70)
            }
        }
        .padding(20)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .vsShadow(.medium)
    }
}

// MARK: - Medications Card
struct MedicationsCardView: View {
    let medications: [MedicationiOS]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "pill.fill")
                    .foregroundColor(Color.primaryBlue)
                Text("Medicamentos")
                    .font(.manropeBold(size: 16))
                    .foregroundColor(Color.textDark)
                Spacer()
                NavigationLink(destination: AddMedicationView(onBack: {})) {
                    Text("Agregar")
                        .font(.manropeSemiBold(size: 12))
                        .foregroundColor(Color.primaryBlue)
                }
            }

            if medications.isEmpty {
                HStack {
                    Image(systemName: "pills")
                        .foregroundColor(Color(hex: "#B0B8C4"))
                    Text("Sin medicamentos registrados")
                        .font(.manrope(size: 13))
                        .foregroundColor(Color.textSecondary)
                }
                .padding(.vertical, 8)
            } else {
                ForEach(medications) { med in
                    HStack(spacing: 12) {
                        Circle()
                            .fill(Color.primaryBlue.opacity(0.12))
                            .frame(width: 36, height: 36)
                            .overlay(
                                Image(systemName: "pill.fill")
                                    .font(.manrope(size: 14))
                                    .foregroundColor(Color.primaryBlue)
                            )

                        VStack(alignment: .leading, spacing: 2) {
                            Text(med.nombre)
                                .font(.manropeSemiBold(size: 14))
                                .foregroundColor(Color.textDark)
                            Text("\(med.dosis) • \(med.horario)")
                                .font(.manrope(size: 11))
                                .foregroundColor(Color.textSecondary)
                        }
                        Spacer()
                    }
                }
            }
        }
        .padding(20)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .vsShadow(.medium)
    }
}

// MARK: - Alert Banner
struct AlertBannerView: View {
    let message: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.orange)
            Text(message)
                .font(.manropeSemiBold(size: 13))
                .foregroundColor(Color.alertText)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color.alertBackground)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.alertBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

// MARK: - Componentes base
struct DashboardSearchBarView: View {
    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(Color.textHint)
                Text("Buscar")
                    .font(.manrope(size: 16))
                    .foregroundColor(Color.textHint)
                Spacer()
            }
            .padding(.horizontal, 16)
            .frame(height: 56)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.borderGray, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.primaryBlue)
                .frame(width: 56, height: 56)
                .overlay(
                    Image(systemName: "slider.horizontal.3")
                        .foregroundColor(.white)
                )
        }
    }
}

struct DashboardSectionHeader: View {
    let title: String

    var body: some View {
        HStack(spacing: 8) {
            Text(title)
                .font(.manropeSemiBold(size: 15))
                .foregroundColor(Color(hex: "#000000"))
            Image(systemName: "arrow.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(Color(hex: "#000000"))
        }
    }
}

struct EmptyPatientsCardView: View {
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "heart.slash")
                .font(.manropeBold(size: 34))
                .foregroundColor(Color.textSecondary)
            Text("Sin pacientes registrados")
                .foregroundColor(Color.textSecondary)
                .font(.manropeSemiBold(size: 15))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct PatientCard: View {
    let patient: VitalsDataiOS

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Circle()
                    .fill(Color.primaryBlue.opacity(0.15))
                    .frame(width: 40, height: 40)
                    .overlay(
                        Text(String(patient.patientName.prefix(1)))
                            .font(.manropeBold(size: 18))
                            .foregroundColor(Color.primaryBlue)
                    )
                Text(patient.patientName)
                    .font(.manropeBold(size: 16))
                Spacer()
                if patient.hasAlert {
                    Image(systemName: "exclamationmark.circle.fill")
                        .foregroundColor(.orange)
                }
            }

            HStack(spacing: 0) {
                VitalBadge(icon: "heart.fill", value: "\(patient.heartRate)", unit: "bpm", color: Color.heartRateRed)
                Spacer()
                VitalBadge(icon: "drop.fill", value: "\(Int(patient.glucose))", unit: "mg/dL", color: .orange)
                Spacer()
                VitalBadge(icon: "lungs.fill", value: "\(patient.spo2)", unit: "%", color: .green)
            }
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: .black.opacity(0.08), radius: 8, x: 0, y: 2)
    }
}

// MARK: - Watch Connection Card
struct WatchConnectionCard: View {
    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(Color.primaryBlue.opacity(0.12))
                    .frame(width: 48, height: 48)
                Image(systemName: "applewatch")
                    .font(.system(size: 22))
                    .foregroundColor(Color.primaryBlue)
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text("Conectar Reloj")
                        .font(.manropeBold(size: 15))
                        .foregroundColor(Color.textDark)
                    Spacer()
                    Button(action: { WatchConnectivitySender.shared.sendTestPing() }) {
                        Text("Probar")
                            .font(.manropeBold(size: 11))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.primaryBlue.opacity(0.1))
                            .cornerRadius(8)
                    }
                }
                Text("Ingresa el código de 8 dígitos que aparece en tu reloj")
                    .font(.manrope(size: 12))
                    .foregroundColor(Color.textSecondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Color(hex: "#B0B8C4"))
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: .black.opacity(0.06), radius: 4, x: 0, y: 2)
    }
}

struct VitalBadge: View {
    let icon: String
    let value: String
    let unit: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon).foregroundColor(color).font(.title3)
            Text(value).font(.manropeBold(size: 18))
            Text(unit).font(.manrope(size: 10)).foregroundColor(.secondary)
        }
    }
}

// MARK: - AI Risk Summary Card (Dashboard)

struct AIRiskSummaryCard: View {
    let riskLevel: RiskLevel?
    let summary: String?

    var body: some View {
        NavigationLink(destination: AIInsightsView()) {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(cardColor.opacity(0.15))
                        .frame(width: 48, height: 48)
                    Image(systemName: "brain.filled.head.profile")
                        .font(.system(size: 22))
                        .foregroundColor(cardColor)
                }

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text("Análisis IA")
                            .font(.manropeBold(size: 15))
                            .foregroundColor(Color(hex: "#0D1B2A"))
                        if let risk = riskLevel {
                            Text(risk.emoji + " " + risk.label)
                                .font(.manropeBold(size: 11))
                                .foregroundColor(cardColor)
                                .padding(.horizontal, 7).padding(.vertical, 2)
                                .background(cardColor.opacity(0.12))
                                .clipShape(Capsule())
                        }
                    }
                    Text(displaySummary)
                        .font(.manrope(size: 12))
                        .foregroundColor(Color.textSecondary)
                        .lineLimit(2)
                }

                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Color(hex: "#B0B8C4"))
            }
            .padding(16)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(color: cardColor.opacity(0.15), radius: 6, x: 0, y: 2)
        }
        .buttonStyle(.plain)
    }

    private var cardColor: Color {
        guard let r = riskLevel else { return Color.primaryBlue }
        return Color(hex: r.hex)
    }

    private var displaySummary: String {
        if let s = summary, !s.isEmpty { return s }
        return riskLevel == nil ? "Toca para iniciar análisis predictivo de enfermedades crónicas" : "Ver predicciones detalladas →"
    }
}

#endif
