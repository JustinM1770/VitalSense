// NotificationService.swift — Notificaciones locales (UNUserNotificationCenter)
// Gestiona alertas de salud, predicciones de IA, tendencias y simulaciones

import Foundation
import Combine
import UserNotifications
import OSLog
#if os(iOS)
import UIKit
#endif

private let logger = Logger(subsystem: "mx.ita.vitalsense.ios", category: "Notifications")

// MARK: - Notification Types

enum VitalNotificationType: String {
    case healthAlert       = "health_alert"
    case diseaseRisk       = "disease_risk"
    case trendWarning      = "trend_warning"
    case dailyInsight      = "daily_insight"
    case medicationReminder = "medication_reminder"

    var categoryId: String { rawValue }

    var threadId: String {
        switch self {
        case .healthAlert, .diseaseRisk, .trendWarning: return "vitalsense.health"
        case .dailyInsight:                              return "vitalsense.insights"
        case .medicationReminder:                        return "vitalsense.medications"
        }
    }

    var interruptionLevel: UNNotificationInterruptionLevel {
        switch self {
        case .healthAlert:   return .critical
        case .diseaseRisk:   return .timeSensitive
        default:             return .active
        }
    }
}

// MARK: - NotificationService

class NotificationService: NSObject, ObservableObject {
    static let shared = NotificationService()

    @Published var isAuthorized = false

    private let center = UNUserNotificationCenter.current()
    private var authCheckTask: Task<Void, Never>?

    private override init() {
        super.init()
        center.delegate = self
    }

    // MARK: - Authorization

    func requestAuthorization() async {
        do {
            let granted = try await center.requestAuthorization(
                options: [.alert, .sound, .badge, .criticalAlert]
            )
            await MainActor.run { isAuthorized = granted }
            if granted { registerCategories() }
        } catch {
            logger.error("Auth error: \(error.localizedDescription)")
        }
    }

    func checkAuthorizationStatus() async {
        let settings = await center.notificationSettings()
        let granted = settings.authorizationStatus == .authorized ||
                      settings.authorizationStatus == .provisional
        await MainActor.run { isAuthorized = granted }
    }

    // MARK: - Categories & Actions

    private func registerCategories() {
        let viewAction = UNNotificationAction(
            identifier: "VIEW_DETAILS",
            title: "Ver detalles",
            options: [.foreground]
        )
        let dismissAction = UNNotificationAction(
            identifier: "DISMISS",
            title: "Ignorar",
            options: [.destructive]
        )

        let categories: [UNNotificationCategory] = [
            UNNotificationCategory(
                identifier: VitalNotificationType.healthAlert.categoryId,
                actions: [viewAction, dismissAction],
                intentIdentifiers: [], options: [.customDismissAction]
            ),
            UNNotificationCategory(
                identifier: VitalNotificationType.diseaseRisk.categoryId,
                actions: [viewAction, dismissAction],
                intentIdentifiers: [], options: []
            ),
            UNNotificationCategory(
                identifier: VitalNotificationType.trendWarning.categoryId,
                actions: [viewAction],
                intentIdentifiers: [], options: []
            ),
            UNNotificationCategory(
                identifier: VitalNotificationType.dailyInsight.categoryId,
                actions: [viewAction],
                intentIdentifiers: [], options: []
            ),
            UNNotificationCategory(
                identifier: VitalNotificationType.medicationReminder.categoryId,
                actions: [
                    UNNotificationAction(identifier: "TAKEN", title: "Tomado ✓", options: []),
                    UNNotificationAction(identifier: "SNOOZE", title: "Recordar en 15 min", options: [])
                ],
                intentIdentifiers: [], options: []
            ),
            // SOS / caída — registrado aquí para no sobrescribir las demás categorías
            UNNotificationCategory(
                identifier: "SOS_ALERT",
                actions: [
                    UNNotificationAction(identifier: "SOS_OK",   title: "Estoy bien",     options: [.foreground]),
                    UNNotificationAction(identifier: "SOS_HELP", title: "Necesito ayuda", options: [.foreground, .destructive])
                ],
                intentIdentifiers: [],
                options: [.customDismissAction]
            ),
        ]
        center.setNotificationCategories(Set(categories))
    }

    // MARK: - Schedule notification

    func scheduleNotification(
        type: VitalNotificationType,
        title: String,
        body: String,
        delay: TimeInterval = 1,
        userInfo: [String: Any] = [:]
    ) async {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = type == .healthAlert ? .defaultCritical : .default
        content.categoryIdentifier = type.categoryId
        content.threadIdentifier = type.threadId
        content.interruptionLevel = type.interruptionLevel
        content.userInfo = userInfo.merging(["notifType": type.rawValue]) { _, new in new }

        // Badge increment (iOS only)
        #if os(iOS)
        let current = await UIApplication.shared.applicationIconBadgeNumber
        content.badge = NSNumber(value: current + 1)
        #endif

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(delay, 0.5), repeats: false)
        let id = "\(type.rawValue)-\(UUID().uuidString.prefix(8))"
        let request = UNNotificationRequest(identifier: id, content: content, trigger: trigger)

        do {
            try await center.add(request)
            logger.debug("Programada: \(title) en \(delay)s")
        } catch {
            logger.error("Error al programar: \(error.localizedDescription)")
        }
    }

    // MARK: - Simulation (para ver cómo se verán)

    /// Simula todas las notificaciones posibles en secuencia
    func simulateAllNotifications() async {
        await scheduleNotification(
            type: .healthAlert,
            title: "🚨 Alerta Crítica — BioMetric AI",
            body: "SpO₂ bajo detectado: 88%. Consulta a tu médico de inmediato.",
            delay: 1
        )
        await scheduleNotification(
            type: .diseaseRisk,
            title: "⚠️ Riesgo: Prediabetes detectado",
            body: "Tu glucosa promedio (138 mg/dL) supera el umbral normal. Revisa el análisis IA.",
            delay: 4
        )
        await scheduleNotification(
            type: .trendWarning,
            title: "📈 Tendencia preocupante",
            body: "Tu frecuencia cardíaca aumentó 15% esta semana. Considera una evaluación médica.",
            delay: 7
        )
        await scheduleNotification(
            type: .dailyInsight,
            title: "💡 Resumen de salud — BioMetric AI",
            body: "Buen día. Tus vitales de hoy están estables. Sueño: 78%. FC: 72 bpm. SpO₂: 97%.",
            delay: 10
        )
        await scheduleNotification(
            type: .medicationReminder,
            title: "💊 Recordatorio de medicamento",
            body: "Hora de tomar Metformina 500mg. ¿Lo tomaste?",
            delay: 13
        )
    }

    /// Simula notificaciones basadas en un análisis real
    #if os(iOS)
    func simulateFromAnalysis(_ analysis: HealthAnalysis) async {
        if analysis.immediateAlert, let msg = analysis.alertMessage {
            await scheduleNotification(type: .healthAlert, title: "🚨 Alerta Médica — BioMetric AI", body: msg, delay: 1)
        }

        for (i, pred) in analysis.predictions.prefix(3).enumerated() {
            let delay = Double(i) * 3 + 2
            switch pred.riskLevel {
            case .critico, .alto:
                await scheduleNotification(
                    type: .diseaseRisk,
                    title: "\(pred.riskLevel.emoji) Riesgo \(pred.riskLevel.label): \(pred.disease)",
                    body: pred.description,
                    delay: delay
                )
            case .moderado:
                await scheduleNotification(
                    type: .trendWarning,
                    title: "⚠️ Patrón detectado: \(pred.disease)",
                    body: pred.description,
                    delay: delay
                )
            case .bajo:
                await scheduleNotification(
                    type: .dailyInsight,
                    title: "✅ Sin riesgos en: \(pred.disease)",
                    body: pred.description,
                    delay: delay
                )
            }
        }

        await scheduleNotification(
            type: .dailyInsight,
            title: "📊 Análisis completo",
            body: "Riesgo general: \(analysis.overallRisk.label). Toca para ver tu análisis detallado.",
            delay: Double(min(analysis.predictions.count, 3)) * 3 + 3
        )
    }
    #endif

    // MARK: - Medication Reminders

    /// Programa notificaciones repetidas para un medicamento.
    /// - Parameters:
    ///   - id: UUID del medicamento (usado como prefijo de ID para poder cancelarlas)
    ///   - name: Nombre del medicamento (ej. "Metformina 500mg")
    ///   - intervalHours: Intervalo en horas entre dosis (4, 6, 8, 12 o 24)
    func scheduleMedicationReminder(id: String, name: String, dosis: String, intervalHours: Int) async {
        guard isAuthorized else { return }

        let content = UNMutableNotificationContent()
        content.title = "💊 Recordatorio — VitalSense"
        content.body = "Hora de tomar \(name)\(dosis.isEmpty ? "" : " \(dosis)"). ¿Lo tomaste?"
        content.sound = .default
        content.categoryIdentifier = VitalNotificationType.medicationReminder.categoryId
        content.threadIdentifier = VitalNotificationType.medicationReminder.threadId
        content.interruptionLevel = .timeSensitive
        content.userInfo = ["medId": id, "medName": name, "notifType": VitalNotificationType.medicationReminder.rawValue]

        let intervalSeconds = TimeInterval(intervalHours * 3600)
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: intervalSeconds, repeats: true)
        let request = UNNotificationRequest(
            identifier: "med-\(id)",
            content: content,
            trigger: trigger
        )

        do {
            try await center.add(request)
            print("[Notifications] Recordatorio de \(name) cada \(intervalHours)h programado.")
        } catch {
            print("[Notifications] Error al programar medicamento: \(error.localizedDescription)")
        }
    }

    /// Cancela los recordatorios de un medicamento específico.
    func cancelMedicationReminder(id: String) {
        center.removePendingNotificationRequests(withIdentifiers: ["med-\(id)"])
    }

    // MARK: - Pending management

    func clearBadge() {
        #if os(iOS)
        UIApplication.shared.applicationIconBadgeNumber = 0
        #endif
    }

    func cancelAll() {
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
        clearBadge()
    }

    func getPendingCount() async -> Int {
        let pending = await center.pendingNotificationRequests()
        return pending.count
    }
}

// MARK: - UNUserNotificationCenterDelegate

extension NotificationService: UNUserNotificationCenterDelegate {
    // Mostrar notificaciones aunque la app esté en primer plano
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        return [.banner, .sound, .badge, .list]
    }

    // Manejar tap en notificación
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let actionId = response.actionIdentifier
        let userInfo = response.notification.request.content.userInfo

        switch actionId {
        case "TAKEN":
            let medName = userInfo["medName"] as? String ?? ""
            logger.info("Medicamento tomado: \(medName)")
        case "SNOOZE":
            if let type = userInfo["notifType"] as? String,
               let vType = VitalNotificationType(rawValue: type) {
                let content = response.notification.request.content
                Task {
                    await NotificationService.shared.scheduleNotification(
                        type: vType, title: content.title, body: content.body, delay: 900
                    )
                }
            }
        default:
            #if os(iOS)
            if actionId == "VIEW_DETAILS" {
                NotificationCenter.default.post(name: .openAIInsights, object: nil, userInfo: userInfo)
            } else if actionId == "SOS_OK" {
                if let sosId = userInfo["sosId"] as? String {
                    SOSMonitorService.shared.dismissAlert(sosId: sosId)
                }
            } else if actionId == "SOS_HELP" || userInfo["sosId"] != nil {
                NotificationCenter.default.post(name: .openSOSAlert, object: nil, userInfo: userInfo)
            } else {
                NotificationCenter.default.post(name: .openAIInsights, object: nil, userInfo: userInfo)
            }
            #endif
        }
    }
}

