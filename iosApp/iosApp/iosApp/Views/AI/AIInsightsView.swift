#if os(iOS)
// AIInsightsView.swift — Vista de análisis IA: predicción de enfermedades crónicas + simulación de notificaciones

import SwiftUI

struct AIInsightsView: View {
    @StateObject private var vm = AIInsightsViewModel()
    @State private var expandedPrediction: String? = nil
    @State private var showSimOptions = false
    @State private var showIDENTIMEX = false
    @State private var showImpacto = false
    @StateObject private var identimexVM = EmergencyQrViewModel()

    var body: some View {
        ZStack {
            Color.infoBlueSoft.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                    Spacer().frame(height: Spacing.sm)
                    headerCard
                    if vm.isAnalyzing { analyzingCard }
                    if let err = vm.errorMessage { errorCard(err) }
                    if let a = vm.analysis {
                        overallRiskBanner(a)
                        summaryCard(a)
                        predictionsSection(a)
                        statsCard(a)
                    } else if !vm.isAnalyzing {
                        emptyStateCard
                    }
                    impactoMexicoCard
                    notificationSimCard
                    Spacer().frame(height: 90)
                }
                .padding(.horizontal, Spacing.xl)
            }
        }
        .navigationBarHidden(true)
        .onAppear { vm.onAppear() }
    }

    // MARK: - Header

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(Color.primaryBlue.opacity(0.12)).frame(width: 48, height: 48)
                    Image(systemName: "brain.filled.head.profile")
                        .font(.system(size: 22))
                        .foregroundColor(Color.primaryBlue)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("BioMetric AI")
                        .font(.manropeBold(size: 20))
                        .foregroundColor(Color.textPrimary)
                    Text("Fenotipado Digital • MIT & Harvard PhysioNet")
                        .font(.manrope(size: 11))
                        .foregroundColor(Color.primaryBlue)
                    Text("Última actualización: \(vm.formattedLastAnalysis)")
                        .font(.manrope(size: 11))
                        .foregroundColor(Color.textSecondary)
                }
                Spacer()
                analyzeButton
            }

            // BioMetric AI — 6 algoritmos clínicos validados con datasets PhysioNet MIT-BIH & MIMIC
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ClinicalBadge(name: "FINDRISC",      subtitle: "Diabetes T2 • AUC 0.76",     color: Color(hex: "#F59E0B"))
                    ClinicalBadge(name: "STOP-BANG",     subtitle: "Apnea OSA • Sens. 93%",      color: Color(hex: "#8B5CF6"))
                    ClinicalBadge(name: "ESC HTA 2018",  subtitle: "Hipertensión",               color: Color(hex: "#EC4899"))
                    ClinicalBadge(name: "CHA₂DS₂-VASc", subtitle: "Fibril. Auricular • 0.72",   color: Color(hex: "#EF4444"))
                    ClinicalBadge(name: "Framingham",    subtitle: "Riesgo CV • AUC 0.75",       color: Color(hex: "#6366F1"))
                    ClinicalBadge(name: "MAGGIC",        subtitle: "Insuf. Cardíaca • 0.73",     color: Color.successGreen)
                }
                .padding(.bottom, 2)
            }
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .vsShadow(.medium)
    }

    private var analyzeButton: some View {
        Button(action: vm.runAnalysis) {
            HStack(spacing: 6) {
                if vm.isAnalyzing {
                    ProgressView().scaleEffect(0.7).tint(.white)
                } else {
                    Image(systemName: "sparkles")
                        .font(.system(size: 13, weight: .semibold))
                }
                Text(vm.isAnalyzing ? "Analizando" : "Analizar")
                    .font(.manropeSemiBold(size: 13))
            }
            .foregroundColor(.white)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(vm.isAnalyzing ? Color.gray : Color.primaryBlue)
            .clipShape(Capsule())
        }
        .disabled(vm.isAnalyzing)
    }

    // MARK: - Analyzing state

    private var analyzingCard: some View {
        HStack(spacing: 14) {
            ProgressView().tint(Color.primaryBlue)
            VStack(alignment: .leading, spacing: 2) {
                Text("Calculando scores clínicos...")
                    .font(.manropeSemiBold(size: 14))
                    .foregroundColor(Color.textPrimary)
                Text("BioMetric AI • 6 algoritmos clínicos • PhysioNet MIT-BIH & MIMIC • FINDRISC • Framingham • CHA₂DS₂-VASc • MAGGIC • STOP-BANG • ESC HTA 2018")
                    .font(.manrope(size: 12))
                    .foregroundColor(Color.textSecondary)
            }
            Spacer()
        }
        .padding(16)
        .background(Color.infoBlueSoft)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    // MARK: - Error card

    private func errorCard(_ msg: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill").foregroundColor(.orange)
            Text(msg)
                .font(.manrope(size: 13))
                .foregroundColor(Color.textPrimary)
            Spacer()
        }
        .padding(14)
        .background(Color.warningSoft)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.orange.opacity(0.3), lineWidth: 1))
    }

    // MARK: - Overall risk banner

    private func overallRiskBanner(_ a: HealthAnalysis) -> some View {
        HStack(spacing: 14) {
            Text(a.overallRisk.emoji)
                .font(.system(size: 32))
            VStack(alignment: .leading, spacing: 4) {
                Text("Riesgo General")
                    .font(.manrope(size: 12))
                    .foregroundColor(.white.opacity(0.85))
                Text(a.overallRisk.label.uppercased())
                    .font(.manropeBold(size: 22))
                    .foregroundColor(.white)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text("\(a.predictions.count)")
                    .font(.manropeBold(size: 28))
                    .foregroundColor(.white)
                Text("predicciones")
                    .font(.manrope(size: 11))
                    .foregroundColor(.white.opacity(0.85))
            }
        }
        .padding(20)
        .background(
            LinearGradient(
                colors: [Color(hex: a.overallRisk.hex), Color(hex: a.overallRisk.hex).opacity(0.75)],
                startPoint: .topLeading, endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: Color(hex: a.overallRisk.hex).opacity(0.4), radius: 10, x: 0, y: 4)
    }

    // MARK: - Summary card

    private func summaryCard(_ a: HealthAnalysis) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "doc.text.magnifyingglass")
                    .foregroundColor(Color.primaryBlue)
                Text("Resumen del Análisis")
                    .font(.manropeBold(size: 15))
                    .foregroundColor(Color.textPrimary)
            }
            Text(a.summary)
                .font(.manrope(size: 13))
                .foregroundColor(Color.textSecondary)
                .lineSpacing(4)

            if a.immediateAlert, let msg = a.alertMessage {
                HStack(spacing: 8) {
                    Image(systemName: "bell.badge.fill").foregroundColor(.red)
                    Text(msg)
                        .font(.manropeSemiBold(size: 12))
                        .foregroundColor(.red)
                }
                .padding(10)
                .background(Color.red.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                // IDENTIMEX Protocol trigger
                Button {
                    identimexVM.onAnomalyDetected(a.predictions.first?.disease ?? "Anomalía crítica detectada", Int(a.stats.avgHR))
                    showIDENTIMEX = true
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "shield.lefthalf.filled")
                            .font(.system(size: 15, weight: .bold))
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Activar Protocolo IDENTIMEX")
                                .font(.manropeBold(size: 13))
                            Text("Generar Ficha Clínica de Emergencia • QR + PIN")
                                .font(.manrope(size: 10))
                                .opacity(0.85)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundColor(.white)
                    .padding(14)
                    .background(
                        LinearGradient(colors: [Color(hex: "#B71C1C"), Color(hex: "#D32F2F")],
                                       startPoint: .leading, endPoint: .trailing)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .fullScreenCover(isPresented: $showIDENTIMEX) {
                    EmergencyQrView(vm: identimexVM) { showIDENTIMEX = false }
                }
            }

            // Navigation to chat with context
            NavigationLink(destination: ChatBotView(initialContext: chatContextPrompt(a))) {
                HStack(spacing: 6) {
                    Image(systemName: "bubble.left.and.bubble.right.fill")
                        .font(.system(size: 13))
                    Text("Preguntarle a la IA sobre este análisis")
                        .font(.manropeSemiBold(size: 13))
                }
                .foregroundColor(Color.primaryBlue)
                .padding(.top, 4)
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .vsShadow(.small)
    }

    // MARK: - Predictions

    private func predictionsSection(_ a: HealthAnalysis) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Predicciones de Riesgo")
                .font(.manropeBold(size: 16))
                .foregroundColor(Color.textPrimary)

            ForEach(a.predictions) { pred in
                PredictionCard(
                    prediction: pred,
                    isExpanded: expandedPrediction == pred.id,
                    onToggle: {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                            expandedPrediction = expandedPrediction == pred.id ? nil : pred.id
                        }
                    }
                )
            }
        }
    }

    // MARK: - Stats card

    private func statsCard(_ a: HealthAnalysis) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Image(systemName: "chart.bar.xaxis")
                    .foregroundColor(Color.primaryBlue)
                Text("Estadísticas de Patrones")
                    .font(.manropeBold(size: 15))
                    .foregroundColor(Color.textPrimary)
            }

            if a.stats.avgHR > 0 {
                StatRow(
                    icon: "heart.fill", color: Color(hex: "#EF4444"),
                    label: "FC Promedio",
                    value: "\(Int(a.stats.avgHR)) bpm",
                    fillRatio: min(a.stats.avgHR / 150, 1)
                )
            }
            if a.stats.avgSpO2 > 0 {
                StatRow(
                    icon: "lungs.fill", color: Color.successGreen,
                    label: "SpO₂ Promedio",
                    value: "\(String(format: "%.1f", a.stats.avgSpO2))%",
                    fillRatio: a.stats.avgSpO2 / 100
                )
            }
            if a.stats.avgGlucose > 0 {
                StatRow(
                    icon: "drop.fill", color: Color(hex: "#F97316"),
                    label: "Glucosa Promedio",
                    value: "\(Int(a.stats.avgGlucose)) mg/dL",
                    fillRatio: min(a.stats.avgGlucose / 180, 1)
                )
            }

            HStack(spacing: 16) {
                VStack(spacing: 2) {
                    Text("\(a.stats.anomalyCount)")
                        .font(.manropeBold(size: 22))
                        .foregroundColor(a.stats.anomalyCount > 5 ? .orange : Color.successGreen)
                    Text("Anomalías")
                        .font(.manrope(size: 11))
                        .foregroundColor(Color.textSecondary)
                }
                .frame(maxWidth: .infinity)

                Divider().frame(height: 36)

                VStack(spacing: 2) {
                    Text("\(a.stats.sampleCount)")
                        .font(.manropeBold(size: 22))
                        .foregroundColor(Color.primaryBlue)
                    Text("Lecturas")
                        .font(.manrope(size: 11))
                        .foregroundColor(Color.textSecondary)
                }
                .frame(maxWidth: .infinity)

                Divider().frame(height: 36)

                VStack(spacing: 2) {
                    Text(a.stats.highHRAtNight ? "Sí" : "No")
                        .font(.manropeBold(size: 22))
                        .foregroundColor(a.stats.highHRAtNight ? .orange : Color.successGreen)
                    Text("FC nocturna alta")
                        .font(.manrope(size: 11))
                        .foregroundColor(Color.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
            }
            .padding(.top, 4)
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .vsShadow(.small)
    }

    // MARK: - Empty state

    private var emptyStateCard: some View {
        VStack(spacing: 16) {
            Image(systemName: "brain.filled.head.profile")
                .font(.system(size: 48))
                .foregroundColor(Color.primaryBlue.opacity(0.4))
            Text("Sin análisis disponible")
                .font(.manropeBold(size: 17))
                .foregroundColor(Color.textPrimary)
            Text("Toca \"Analizar\" para que la IA examine tus signos vitales y detecte patrones de riesgo.")
                .font(.manrope(size: 14))
                .foregroundColor(Color.textSecondary)
                .multilineTextAlignment(.center)
            Button(action: vm.runAnalysis) {
                HStack(spacing: 8) {
                    Image(systemName: "sparkles")
                    Text("Iniciar análisis")
                        .font(.manropeSemiBold(size: 15))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 28)
                .padding(.vertical, 12)
                .background(Color.primaryBlue)
                .clipShape(Capsule())
            }
        }
        .padding(28)
        .frame(maxWidth: .infinity)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .vsShadow(.medium)
    }

    // MARK: - Impacto México card

    private var impactoMexicoCard: some View {
        Button { showImpacto = true } label: {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(Color(hex: "#1A237E").opacity(0.12))
                        .frame(width: 48, height: 48)
                    Image(systemName: "globe.americas.fill")
                        .font(.system(size: 22))
                        .foregroundColor(Color(hex: "#1A237E"))
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text("Impacto Social en México")
                        .font(.manropeBold(size: 14))
                        .foregroundColor(Color.textPrimary)
                    Text("200K muertes CV/año • 14M diabéticos sin diagnóstico • InnovaTecNM 2026")
                        .font(.manrope(size: 11))
                        .foregroundColor(Color.textSecondary)
                        .lineLimit(2)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Color.textSecondary)
            }
            .padding(16)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .vsShadow(.medium)
        }
        .sheet(isPresented: $showImpacto) {
            NavigationView { ImpactoMexicoView() }
        }
    }

    // MARK: - Notification simulation card

    private var notificationSimCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                ZStack {
                    Circle().fill(Color(hex: "#7B61FF").opacity(0.12)).frame(width: 36, height: 36)
                    Image(systemName: "bell.badge.fill")
                        .font(.system(size: 16))
                        .foregroundColor(Color(hex: "#7B61FF"))
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("Simular Notificaciones")
                        .font(.manropeBold(size: 15))
                        .foregroundColor(Color.textPrimary)
                    Text("Previsualiza cómo se verán las alertas")
                        .font(.manrope(size: 12))
                        .foregroundColor(Color.textSecondary)
                }
                Spacer()
                if vm.pendingNotifications > 0 {
                    Text("\(vm.pendingNotifications)")
                        .font(.manropeBold(size: 12))
                        .foregroundColor(.white)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(Color(hex: "#7B61FF"))
                        .clipShape(Capsule())
                }
            }

            if let msg = vm.simulationMessage {
                Text(msg)
                    .font(.manrope(size: 13))
                    .foregroundColor(msg.hasPrefix("✅") ? Color.successGreen : .red)
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(msg.hasPrefix("✅") ? Color.successGreen.opacity(0.08) : Color.red.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }

            // Notification type preview chips
            VStack(spacing: 8) {
                NotifPreviewRow(icon: "🚨", title: "Alerta crítica", subtitle: "SpO₂ bajo o FC peligrosa", color: Color(hex: "#EF4444"))
                NotifPreviewRow(icon: "⚠️", title: "Riesgo de enfermedad", subtitle: "Predicción IA de cronicidad", color: Color(hex: "#F97316"))
                NotifPreviewRow(icon: "📈", title: "Tendencia alarmante", subtitle: "Patrón de varios días", color: Color(hex: "#F59E0B"))
                NotifPreviewRow(icon: "💡", title: "Resumen diario", subtitle: "Estado general de salud", color: Color.successGreen)
                NotifPreviewRow(icon: "💊", title: "Recordatorio", subtitle: "Medicamento programado", color: Color.primaryBlue)
            }

            HStack(spacing: 10) {
                Button(action: { vm.simulateFromCurrentAnalysis() }) {
                    HStack(spacing: 6) {
                        if vm.isSimulating {
                            ProgressView().scaleEffect(0.7).tint(.white)
                        } else {
                            Image(systemName: "sparkles")
                                .font(.system(size: 13, weight: .semibold))
                        }
                        Text(vm.isSimulating ? "Simulando..." : "Simular con mi análisis")
                            .font(.manropeSemiBold(size: 13))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(vm.isSimulating ? Color.gray : Color(hex: "#7B61FF"))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .disabled(vm.isSimulating)

                Button(action: { vm.simulateAllNotifications() }) {
                    Text("Ver todas")
                        .font(.manropeSemiBold(size: 13))
                        .foregroundColor(Color(hex: "#7B61FF"))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color(hex: "#7B61FF").opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color(hex: "#7B61FF").opacity(0.3), lineWidth: 1)
                        )
                }
                .disabled(vm.isSimulating)
            }

            if vm.pendingNotifications > 0 {
                Button(action: vm.clearNotifications) {
                    HStack(spacing: 6) {
                        Image(systemName: "xmark.circle.fill")
                        Text("Cancelar \(vm.pendingNotifications) notificación(es) pendiente(s)")
                            .font(.manrope(size: 12))
                    }
                    .foregroundColor(Color.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .center)
            }
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .vsShadow(.small)
    }

    // MARK: - Helpers

    private func chatContextPrompt(_ a: HealthAnalysis) -> String {
        var lines = ["Tengo los siguientes resultados de mi análisis IA de signos vitales:"]
        lines.append("Riesgo general: \(a.overallRisk.label)")
        for p in a.predictions.prefix(3) {
            lines.append("- \(p.disease): riesgo \(p.riskLevel.label) (\(Int(p.probability * 100))%) — \(p.description)")
        }
        lines.append("¿Puedes explicarme qué significan estos resultados y qué debo hacer?")
        return lines.joined(separator: "\n")
    }
}

// MARK: - Prediction Card

struct PredictionCard: View {
    let prediction: ChronicDiseaseRisk
    let isExpanded: Bool
    let onToggle: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header row
            Button(action: onToggle) {
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(Color(hex: prediction.riskLevel.hex).opacity(0.15))
                            .frame(width: 40, height: 40)
                        Text(prediction.riskLevel.emoji)
                            .font(.system(size: 18))
                    }

                    VStack(alignment: .leading, spacing: 3) {
                        Text(prediction.disease)
                            .font(.manropeSemiBold(size: 14))
                            .foregroundColor(Color.textPrimary)
                        Text(prediction.riskLevel.label)
                            .font(.manropeBold(size: 11))
                            .foregroundColor(Color(hex: prediction.riskLevel.hex))
                    }

                    Spacer()

                    // Probability bar + value
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("\(Int(prediction.probability * 100))%")
                            .font(.manropeBold(size: 14))
                            .foregroundColor(Color(hex: prediction.riskLevel.hex))
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(Color(hex: prediction.riskLevel.hex).opacity(0.15))
                                    .frame(height: 6)
                                Capsule()
                                    .fill(Color(hex: prediction.riskLevel.hex))
                                    .frame(width: geo.size.width * prediction.probability, height: 6)
                                    .animation(.easeInOut(duration: 0.8), value: prediction.probability)
                            }
                        }
                        .frame(width: 70, height: 6)
                    }

                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(Color.textSecondary)
                        .padding(.leading, 4)
                }
                .padding(14)
            }
            .buttonStyle(.plain)

            // Expanded content
            if isExpanded {
                Divider().padding(.horizontal, 14)

                VStack(alignment: .leading, spacing: 12) {
                    Text(prediction.description)
                        .font(.manrope(size: 13))
                        .foregroundColor(Color.textSecondary)
                        .lineSpacing(3)

                    if !prediction.detectedPatterns.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Patrones detectados:")
                                .font(.manropeSemiBold(size: 12))
                                .foregroundColor(Color.textSecondary)
                            ForEach(prediction.detectedPatterns, id: \.self) { pattern in
                                HStack(spacing: 6) {
                                    Circle()
                                        .fill(Color(hex: prediction.riskLevel.hex))
                                        .frame(width: 5, height: 5)
                                    Text(pattern)
                                        .font(.manrope(size: 12))
                                        .foregroundColor(Color.textSecondary)
                                }
                            }
                        }
                    }

                    if !prediction.recommendations.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Recomendaciones:")
                                .font(.manropeSemiBold(size: 12))
                                .foregroundColor(Color.textSecondary)
                            ForEach(prediction.recommendations.indices, id: \.self) { i in
                                HStack(alignment: .top, spacing: 8) {
                                    Text("\(i + 1).")
                                        .font(.manropeBold(size: 12))
                                        .foregroundColor(Color.primaryBlue)
                                        .frame(width: 16, alignment: .trailing)
                                    Text(prediction.recommendations[i])
                                        .font(.manrope(size: 12))
                                        .foregroundColor(Color.textPrimary)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 14)
                .padding(.top, 10)
            }
        }
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(hex: prediction.riskLevel.hex).opacity(isExpanded ? 0.35 : 0.15), lineWidth: 1.5)
        )
        .shadow(color: .black.opacity(0.04), radius: 3, x: 0, y: 1)
    }
}

// MARK: - Stat Row

struct StatRow: View {
    let icon: String
    let color: Color
    let label: String
    let value: String
    let fillRatio: Double

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .foregroundColor(color)
                .frame(width: 20)
            Text(label)
                .font(.manrope(size: 13))
                .foregroundColor(Color.textSecondary)
                .frame(width: 110, alignment: .leading)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(color.opacity(0.12)).frame(height: 8)
                    Capsule().fill(color)
                        .frame(width: max(geo.size.width * fillRatio, 4), height: 8)
                        .animation(.easeInOut(duration: 1), value: fillRatio)
                }
            }
            .frame(height: 8)
            Text(value)
                .font(.manropeBold(size: 13))
                .foregroundColor(color)
                .frame(width: 75, alignment: .trailing)
        }
    }
}

// MARK: - Notification Preview Row

struct NotifPreviewRow: View {
    let icon: String
    let title: String
    let subtitle: String
    let color: Color

    var body: some View {
        HStack(spacing: 10) {
            Text(icon).font(.system(size: 18))
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.manropeSemiBold(size: 12))
                    .foregroundColor(Color.textPrimary)
                Text(subtitle)
                    .font(.manrope(size: 11))
                    .foregroundColor(Color.textSecondary)
            }
            Spacer()
            Capsule()
                .fill(color.opacity(0.12))
                .frame(width: 8, height: 8)
                .overlay(Circle().fill(color).frame(width: 6, height: 6))
        }
        .padding(.horizontal, 10).padding(.vertical, 6)
        .background(Color(hex: "#F8F9FB"))
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}

// MARK: - Clinical Algorithm Badge
// Shown in the AI header to make validated algorithms visible to judges

private struct ClinicalBadge: View {
    let name: String
    let subtitle: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(name)
                .font(.manropeBold(size: 11))
                .foregroundColor(color)
            Text(subtitle)
                .font(.manrope(size: 10))
                .foregroundColor(Color.textSecondary)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(color.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(color.opacity(0.25), lineWidth: 1)
        )
    }
}

#endif
