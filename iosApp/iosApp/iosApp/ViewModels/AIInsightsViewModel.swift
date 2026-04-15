// AIInsightsViewModel.swift — ViewModel para la vista de análisis IA

import Foundation
import Combine
import FirebaseAuth

@MainActor
class AIInsightsViewModel: ObservableObject {
    @Published var analysis: HealthAnalysis?
    @Published var isAnalyzing = false
    @Published var isSimulating = false
    @Published var simulationMessage: String?
    @Published var errorMessage: String?
    @Published var pendingNotifications = 0

    private let aiService = AIHealthService.shared
    private let notifService = NotificationService.shared

    func onAppear() {
        // Enlazar publicados del servicio compartido
        analysis = aiService.analysis
        isAnalyzing = aiService.isAnalyzing

        Task {
            await notifService.checkAuthorizationStatus()
            if let uid = Auth.auth().currentUser?.uid {
                await aiService.loadCachedAnalysis(userId: uid)
                analysis = aiService.analysis
            }
            pendingNotifications = await notifService.getPendingCount()
        }
    }

    // MARK: - Analyze

    func runAnalysis() {
        guard let uid = Auth.auth().currentUser?.uid else {
            errorMessage = "Debes estar autenticado para analizar."
            return
        }
        Task {
            isAnalyzing = true
            errorMessage = nil
            await aiService.runAnalysis(userId: uid)
            analysis = aiService.analysis
            errorMessage = aiService.lastError
            isAnalyzing = false
            pendingNotifications = await notifService.getPendingCount()
        }
    }

    // MARK: - Simulate notifications

    func simulateAllNotifications() {
        isSimulating = true
        simulationMessage = nil
        Task {
            if !notifService.isAuthorized {
                await notifService.requestAuthorization()
            }
            if notifService.isAuthorized {
                await notifService.simulateAllNotifications()
                simulationMessage = "✅ 5 notificaciones programadas. Aparecerán en los próximos 15 segundos."
            } else {
                simulationMessage = "❌ Activa las notificaciones en Configuración para ver las simulaciones."
            }
            pendingNotifications = await notifService.getPendingCount()
            isSimulating = false
        }
    }

    func simulateFromCurrentAnalysis() {
        guard let a = analysis else {
            simulateAllNotifications()
            return
        }
        isSimulating = true
        simulationMessage = nil
        Task {
            if !notifService.isAuthorized {
                await notifService.requestAuthorization()
            }
            if notifService.isAuthorized {
                await notifService.simulateFromAnalysis(a)
                let count = min(a.predictions.count + 1, 5)
                simulationMessage = "✅ \(count) notificaciones basadas en tu análisis. Aparecerán en segundos."
            } else {
                simulationMessage = "❌ Activa las notificaciones en Configuración para ver las simulaciones."
            }
            pendingNotifications = await notifService.getPendingCount()
            isSimulating = false
        }
    }

    func requestNotificationPermission() {
        Task { await notifService.requestAuthorization() }
    }

    func clearNotifications() {
        notifService.cancelAll()
        Task {
            pendingNotifications = await notifService.getPendingCount()
            simulationMessage = nil
        }
    }

    // MARK: - Chat context

    var chatContext: String {
        guard let a = analysis else { return "" }
        var lines = ["[Análisis IA BioMetric AI - \(formattedDate(a.timestamp))]"]
        lines.append("Riesgo general: \(a.overallRisk.label)")
        lines.append("Resumen: \(a.summary)")
        for p in a.predictions.prefix(3) {
            lines.append("• \(p.disease): \(p.riskLevel.label) (\(Int(p.probability * 100))%)")
        }
        return lines.joined(separator: "\n")
    }

    private func formattedDate(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateStyle = .medium; f.timeStyle = .short; f.locale = Locale(identifier: "es_MX")
        return f.string(from: date)
    }

    var formattedLastAnalysis: String {
        guard let ts = analysis?.timestamp else { return "Sin análisis" }
        let diff = Date().timeIntervalSince(ts)
        let minutes = Int(diff / 60)
        if minutes < 1  { return "Hace un momento" }
        if minutes < 60 { return "Hace \(minutes) min" }
        let hours = minutes / 60
        if hours < 24   { return "Hace \(hours)h" }
        return "Hace \(hours / 24)d"
    }
}
