#if os(iOS)
// SOSAlertView.swift — Panel de alerta SOS/caída que aparece en iPhone
// Se presenta como sheet sobre cualquier pantalla cuando llega una alerta del Watch

import SwiftUI

struct SOSAlertView: View {
    let alert: SOSAlert
    @ObservedObject var contactsService = EmergencyContactsService.shared
    @ObservedObject var monitor         = SOSMonitorService.shared
    @State private var notifiedIds: Set<String> = []

    private var isFall: Bool { alert.isFall }
    private var hasLocation: Bool { alert.lat != 0 }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.errorRed, Color(hex: "#C62828")],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    Spacer().frame(height: Spacing.xxl)

                    // Icono pulsante
                    PulsingIcon(isFall: isFall)

                    // Título
                    Text(isFall ? "CAÍDA DETECTADA" : "SOS ACTIVADO")
                        .font(.system(size: 24, weight: .black))
                        .foregroundColor(.white)
                        .tracking(1.5)

                    Text(isFall
                         ? "Tu Apple Watch detectó una posible caída."
                         : "Tu Apple Watch envió una alerta de emergencia.")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.85))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, Spacing.xxl)

                    // Datos vitales + ubicación
                    VitalsCard(alert: alert)

                    // Contactos de emergencia
                    if !contactsService.contacts.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Notificar contactos")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.white.opacity(0.85))
                                .padding(.horizontal, 4)

                            ForEach(contactsService.contacts) { contact in
                                ContactAlertRow(
                                    contact: contact,
                                    sosId: alert.id,
                                    lat: alert.lat,
                                    lng: alert.lng,
                                    notified: notifiedIds.contains(contact.id)
                                ) {
                                    notifiedIds.insert(contact.id)
                                }
                            }
                        }
                        .padding(.horizontal, Spacing.xl)
                    }

                    // Botón "Estoy bien"
                    // UI/UX: Large tap target for emergency use (elderly, stress state)
                    // Nielsen #3 User Control: always give the user a clear exit from alerts.
                    Button {
                        HapticFeedback.success()
                        monitor.dismissAlert(sosId: alert.id)
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 18))
                                .foregroundColor(Color.errorRed)
                            Text("Estoy bien — Cancelar alerta")
                                .font(.vsH3())
                                .foregroundColor(Color.errorRed)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 58)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                    .padding(.horizontal, Spacing.xxl)
                    .accessibilityLabel("Cancelar alerta de emergencia — estoy bien")
                    .accessibilityHint("Doble toque para confirmar que estás bien y cancelar la alerta")

                    Spacer().frame(height: 32)
                }
            }
        }
        .onAppear {
            contactsService.loadContacts()
            // UI/UX: Critical alerts need immediate sensory confirmation.
            // Heavy haptic on SOS appearance ensures the user knows the alert is active
            // even if they can't see the screen clearly (accessibility, elderly users).
            HapticFeedback.error()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { HapticFeedback.heavy() }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { HapticFeedback.heavy() }
        }
    }
}

// MARK: - Pulsing icon

private struct PulsingIcon: View {
    let isFall: Bool
    @State private var scale: CGFloat = 1.0

    var body: some View {
        Image(systemName: isFall ? "figure.fall" : "heart.fill")
            .font(.system(size: 56))
            .foregroundColor(.white)
            .scaleEffect(scale)
            .onAppear {
                withAnimation(.easeInOut(duration: 0.7).repeatForever()) { scale = 1.2 }
            }
    }
}

// MARK: - VitalsCard

private struct VitalsCard: View {
    let alert: SOSAlert

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 24) {
                VitalPill(icon: "heart.fill", value: alert.heartRate > 0 ? "\(Int(alert.heartRate))" : "—", unit: "bpm", color: .white)
                if alert.lat != 0 {
                    VitalPill(icon: "location.fill", value: "GPS", unit: "activo", color: .white)
                }
            }
            if alert.lat != 0 {
                Link("Ver ubicación en Maps",
                     destination: URL(string: "https://maps.google.com/?q=\(alert.lat),\(alert.lng)")!)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.white.opacity(0.9))
                    .underline()
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.15))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .padding(.horizontal, Spacing.xxl)
    }
}

private struct VitalPill: View {
    let icon: String; let value: String; let unit: String; let color: Color
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon).font(.system(size: 14)).foregroundColor(color)
            Text(value).font(.system(size: 18, weight: .bold)).foregroundColor(color)
            Text(unit).font(.system(size: 11)).foregroundColor(color.opacity(0.8))
        }
    }
}

// MARK: - ContactAlertRow

private struct ContactAlertRow: View {
    let contact: EmergencyContact
    let sosId:   String
    let lat:     Double
    let lng:     Double
    let notified: Bool
    let onSMS:   () -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(contact.name)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                Text(contact.relation)
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.7))
            }

            Spacer()

            // Llamar
            if let url = EmergencyContactsService.shared.callURL(for: contact) {
                Link(destination: url) {
                    Label("Llamar", systemImage: "phone.fill")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color.errorRed)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(Color.white)
                        .clipShape(Capsule())
                }
            }

            // SMS
            if let url = EmergencyContactsService.shared.smsURL(for: contact, sosId: sosId, lat: lat, lng: lng) {
                Link(destination: url) {
                    Label(notified ? "Enviado" : "SMS", systemImage: notified ? "checkmark" : "message.fill")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(notified ? .white : Color.errorRed)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(notified ? Color.white.opacity(0.3) : Color.white)
                        .clipShape(Capsule())
                }
                .simultaneousGesture(TapGesture().onEnded { onSMS() })
            }
        }
        .padding(12)
        .background(Color.white.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

#endif
