#if os(iOS)
import SwiftUI
import Combine
import FirebaseAuth
import FirebaseDatabase

// MARK: - Colors
private let emergencyRed     = Color(red: 0.827, green: 0.184, blue: 0.184)
private let emergencyDarkRed = Color(red: 0.714, green: 0.110, blue: 0.110)
private let onEmergency      = Color(red: 1, green: 0.922, blue: 0.933)
private let yellowWarning    = Color(red: 1, green: 0.839, blue: 0)

// MARK: - Model

enum EmergencyQrState {
    case loading
    case active(pin: String, publicQR: UIImage?, anomalyType: String, tokenId: String, profile: PublicPatientProfile)
    case expired(anomalyType: String)
    case error(String)
}

struct PublicPatientProfile {
    let nombre:       String
    let tipoSangre:   String
    let alergias:     String
    let padecimientos: String
}

// MARK: - ViewModel

class EmergencyQrViewModel: ObservableObject {
    @Published var state: EmergencyQrState = .loading
    @Published var remainingSecs: Int = 0
    @Published var whatsappStatus: WhatsappStatus = .pending

    enum WhatsappStatus {
        case pending, sent, noContacts
    }

    var anomalyType = ""
    var heartRate   = 0
    private var countdownTimer: Timer?
    private let db = Database.database().reference()

    func onAnomalyDetected(_ type: String, _ hr: Int) {
        anomalyType = type
        heartRate   = hr
        state       = .loading
        whatsappStatus = .pending
        fetchProfileAndActivate()
    }

    // MARK: - Step 1: fetch public profile, then create token

    private func fetchProfileAndActivate() {
        guard let uid = Auth.auth().currentUser?.uid else {
            state = .error("Sin sesión activa"); return
        }

        db.child("patients/\(uid)/profile").observeSingleEvent(of: .value) { [weak self] snap in
            guard let self else { return }
            let d = snap.value as? [String: Any] ?? [:]

            let profile = PublicPatientProfile(
                nombre:        "\(d["nombre"] as? String ?? "") \(d["apellidos"] as? String ?? "")".trimmingCharacters(in: .whitespaces),
                tipoSangre:    d["tipoSangre"]    as? String ?? "Desconocido",
                alergias:      d["alergias"]      as? String ?? "Sin alergias conocidas",
                padecimientos: d["padecimientos"] as? String ?? "Sin antecedentes"
            )

            DispatchQueue.main.async {
                self.createEmergencyToken(uid: uid, profile: profile)
            }
        }
    }

    // MARK: - Step 2: write token, generate QR with public data only

    private func createEmergencyToken(uid: String, profile: PublicPatientProfile) {
        let pin       = String(format: "%04d", Int.random(in: 1000...9999))
        let tokenId   = UUID().uuidString
        let expiresAt = Date().timeIntervalSince1970 * 1000 + 30 * 60 * 1000

        // Firebase token — datos completos protegidos con PIN (reglas: solo Cloud Function)
        let tokenData: [String: Any] = [
            "pin":         pin,
            "userId":      uid,
            "anomalyType": anomalyType,
            "heartRate":   heartRate,
            "expiresAt":   expiresAt,
            "createdAt":   Int(Date().timeIntervalSince1970 * 1000),
            "status":      "active"
        ]
        db.child("emergency_tokens/\(tokenId)").setValue(tokenData)

        // Datos públicos — nodo separado sin PIN, legibles por cualquiera.
        // Solo contiene información no sensible necesaria para la atención inmediata.
        // Firebase Rules: emergency_public/$tokenId → .read: true, .write: false (server only)
        let publicData: [String: Any] = [
            "nombre":        profile.nombre,
            "tipoSangre":    profile.tipoSangre,
            "alergias":      profile.alergias,
            "padecimientos": profile.padecimientos,
            "anomalyType":   anomalyType,
            "heartRate":     heartRate,
            "expiresAt":     expiresAt
        ]
        db.child("emergency_public/\(tokenId)").setValue(publicData)

        db.child("patients/\(uid)/activeEmergency").setValue([
            "pin": pin, "anomalyType": anomalyType,
            "expiresAt": expiresAt, "tokenId": tokenId
        ])

        // QR contiene SOLO datos públicos (sin PIN, sin historial)
        // Un paramédico puede escanearlo sin autenticación para datos críticos inmediatos
        let publicCardText = buildPublicQRCard(profile: profile, tokenId: tokenId)
        let publicQR = generateQR(from: publicCardText)

        remainingSecs  = 30 * 60
        state = .active(pin: pin, publicQR: publicQR, anomalyType: anomalyType, tokenId: tokenId, profile: profile)
        startCountdown(expiresAt: expiresAt)

        // Step 3: enviar expediente + PIN vía WhatsApp al contacto de confianza
        sendWhatsApp(pin: pin, tokenId: tokenId, profile: profile)
    }

    /// Texto codificado en el QR — datos públicos no sensibles.
    /// El PIN nunca aparece aquí.
    private func buildPublicQRCard(profile: PublicPatientProfile, tokenId: String) -> String {
        let url = "https://vitalsenseai-1cb9f.web.app/emergency.html?t=\(tokenId)"
        return """
        🛡️ IDENTIMEX · BioMetric AI
        Paciente: \(profile.nombre.isEmpty ? "No disponible" : profile.nombre)
        Tipo de sangre: \(profile.tipoSangre)
        Alergias: \(profile.alergias)
        Condiciones: \(profile.padecimientos)
        Anomalía: \(anomalyType)
        Expediente completo: \(url)
        """
    }

    // MARK: - WhatsApp (cifrado E2E — NOM-024-SSA3)

    private func sendWhatsApp(pin: String, tokenId: String, profile: PublicPatientProfile) {
        EmergencyContactsService.shared.loadContacts()

        // Pequeño delay para que loadContacts() termine
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { [weak self] in
            guard let self else { return }
            guard let contact = EmergencyContactsService.shared.contacts.first else {
                self.whatsappStatus = .noContacts; return
            }
            guard let url = EmergencyContactsService.shared.whatsAppEmergencyURL(
                for: contact,
                tokenId: tokenId,
                pin: pin,
                patientName: profile.nombre,
                anomalyType: self.anomalyType
            ) else {
                self.whatsappStatus = .noContacts; return
            }

            UIApplication.shared.open(url)
            self.whatsappStatus = .sent
        }
    }

    // MARK: - Timer

    private func startCountdown(expiresAt: Double) {
        countdownTimer?.invalidate()
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self else { return }
            let diff = expiresAt / 1000 - Date().timeIntervalSince1970
            if diff <= 0 {
                self.countdownTimer?.invalidate()
                if case .active(_, _, let type, _, _) = self.state {
                    self.state = .expired(anomalyType: type)
                }
            } else {
                self.remainingSecs = Int(diff)
            }
        }
    }

    func resolveEmergency() {
        countdownTimer?.invalidate()
        guard let uid = Auth.auth().currentUser?.uid else { return }
        db.child("patients/\(uid)/activeEmergency").removeValue()
    }

    // MARK: - QR generator

    private func generateQR(from string: String) -> UIImage? {
        guard let data = string.data(using: .utf8),
              let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        filter.setValue(data, forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        return UIImage(ciImage: scaled)
    }
}

// MARK: - Main Screen

struct EmergencyQrView: View {
    @ObservedObject var vm: EmergencyQrViewModel
    let onResolve: () -> Void

    var body: some View {
        ZStack {
            LinearGradient(colors: [emergencyDarkRed, emergencyRed], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()

            switch vm.state {
            case .loading:
                LoadingContent()

            case .active(let pin, let publicQR, let anomalyType, let tokenId, let profile):
                ActiveContent(
                    pin:          pin,
                    publicQR:     publicQR,
                    anomalyType:  anomalyType,
                    tokenId:      tokenId,
                    profile:      profile,
                    remainingSecs: vm.remainingSecs,
                    whatsappStatus: vm.whatsappStatus,
                    onResolve: {
                        vm.resolveEmergency()
                        onResolve()
                    },
                    onResendWhatsApp: {
                        if case .active(let p, _, _, let tid, let prof) = vm.state {
                            if let contact = EmergencyContactsService.shared.contacts.first,
                               let url = EmergencyContactsService.shared.whatsAppEmergencyURL(
                                   for: contact, tokenId: tid, pin: p,
                                   patientName: prof.nombre, anomalyType: anomalyType) {
                                UIApplication.shared.open(url)
                            }
                        }
                    }
                )

            case .expired(let type):
                ExpiredContent(anomalyType: type, onClose: onResolve)

            case .error(let msg):
                ErrorContent(message: msg, onRetry: {
                    vm.onAnomalyDetected(vm.anomalyType, vm.heartRate)
                }, onClose: onResolve)
            }
        }
        .navigationBarHidden(true)
    }
}

// MARK: - Loading

struct LoadingContent: View {
    @State private var scale: CGFloat = 1.0
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 56))
                .foregroundColor(onEmergency)
                .scaleEffect(scale)
                .onAppear {
                    withAnimation(.easeInOut(duration: 0.6).repeatForever()) { scale = 1.25 }
                }
            Text("Activando alerta de emergencia...")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(onEmergency)
            Text("Notificando contactos de confianza")
                .font(.system(size: 13))
                .foregroundColor(onEmergency.opacity(0.75))
            ProgressView().tint(onEmergency)
        }
    }
}

// MARK: - Active (simplified iPhone view — QR is on Apple Watch)

struct ActiveContent: View {
    let pin:           String
    let publicQR:      UIImage?      // retained by VM, not used on iPhone
    let anomalyType:   String
    let tokenId:       String
    let profile:       PublicPatientProfile
    let remainingSecs: Int
    let whatsappStatus: EmergencyQrViewModel.WhatsappStatus
    let onResolve:     () -> Void
    let onResendWhatsApp: () -> Void

    @State private var pulse: CGFloat = 1.0

    private var timeString: String {
        let m = remainingSecs / 60; let s = remainingSecs % 60
        return String(format: "%02d:%02d", m, s)
    }
    private var timerColor: Color { remainingSecs < 300 ? yellowWarning : onEmergency }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 24) {
                Spacer().frame(height: Spacing.xxl)

                // Pulsing emergency icon
                ZStack {
                    Circle()
                        .fill(Color.white.opacity(0.12))
                        .frame(width: 120, height: 120)
                        .scaleEffect(pulse)
                        .onAppear {
                            withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                                pulse = 1.15
                            }
                        }
                    Circle()
                        .fill(Color.white.opacity(0.18))
                        .frame(width: 90, height: 90)
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 44))
                        .foregroundColor(onEmergency)
                }

                // Title
                VStack(spacing: 6) {
                    Text("EMERGENCIA ACTIVA")
                        .font(.system(size: 20, weight: .black))
                        .foregroundColor(onEmergency)
                        .tracking(2)
                    Text("BioMetric AI — Protocolo iniciado")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(onEmergency.opacity(0.75))
                }

                // Anomaly type
                Text(anomalyType)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20).padding(.vertical, 10)
                    .background(Color.white.opacity(0.2))
                    .clipShape(Capsule())

                // Patient info pills
                HStack(spacing: 12) {
                    EmergencyInfoPill(icon: "person.fill",    label: profile.nombre.isEmpty ? "Paciente" : profile.nombre)
                    EmergencyInfoPill(icon: "drop.fill",      label: profile.tipoSangre)
                }
                .padding(.horizontal, Spacing.xl)

                // Watch QR notice
                HStack(spacing: 10) {
                    Image(systemName: "applewatch")
                        .font(.system(size: 18))
                        .foregroundColor(onEmergency)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("QR disponible en tu Apple Watch")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(onEmergency)
                        Text("Muestra la pantalla del reloj al paramédico para acceso inmediato")
                            .font(.system(size: 11))
                            .foregroundColor(onEmergency.opacity(0.75))
                    }
                }
                .padding(16)
                .background(Color.white.opacity(0.15))
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .padding(.horizontal, Spacing.xl)

                // WhatsApp status
                WhatsAppStatusPanel(status: whatsappStatus, onResend: onResendWhatsApp)
                    .padding(.horizontal, Spacing.xl)

                // Countdown timer
                VStack(spacing: 4) {
                    Text(timeString)
                        .font(.system(size: 36, weight: .black, design: .monospaced))
                        .foregroundColor(timerColor)
                    Text("Sesión de emergencia activa")
                        .font(.system(size: 12))
                        .foregroundColor(onEmergency.opacity(0.7))
                }
                .padding(.vertical, 8)

                // Resolve button
                Button(action: onResolve) {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 18))
                        Text("Estoy bien — Resolver emergencia")
                            .font(.system(size: 16, weight: .semibold))
                    }
                    .foregroundColor(emergencyRed)
                    .frame(maxWidth: .infinity).frame(height: 56)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .shadow(color: .black.opacity(0.12), radius: 8, x: 0, y: 4)
                }
                .padding(.horizontal, Spacing.xl)

                Spacer().frame(height: 32)
            }
        }
    }
}

// MARK: - Emergency Info Pill

private struct EmergencyInfoPill: View {
    let icon: String; let label: String
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 12))
                .foregroundColor(onEmergency)
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(.white)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(Color.white.opacity(0.15))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: - WhatsApp Status Panel

struct WhatsAppStatusPanel: View {
    let status: EmergencyQrViewModel.WhatsappStatus
    let onResend: () -> Void

    private var icon: String {
        switch status {
        case .pending:    return "clock.fill"
        case .sent:       return "checkmark.circle.fill"
        case .noContacts: return "person.crop.circle.badge.exclamationmark"
        }
    }
    private var color: Color {
        switch status {
        case .pending:    return yellowWarning
        case .sent:       return Color(red: 0.24, green: 0.73, blue: 0.42)   // WhatsApp green
        case .noContacts: return onEmergency.opacity(0.6)
        }
    }
    private var title: String {
        switch status {
        case .pending:    return "Enviando expediente + PIN..."
        case .sent:       return "Expediente y PIN enviados por WhatsApp"
        case .noContacts: return "Sin contacto de confianza configurado"
        }
    }
    private var subtitle: String {
        switch status {
        case .pending:    return "Cifrado E2E — el PIN viaja solo por canal privado"
        case .sent:       return "Cifrado E2E • NOM-024-SSA3 • Solo el destinatario puede verlo"
        case .noContacts: return "Agrega un contacto de emergencia en tu perfil"
        }
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 22))
                .foregroundColor(color)

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(onEmergency)
                Text(subtitle)
                    .font(.system(size: 10))
                    .foregroundColor(onEmergency.opacity(0.7))
            }

            Spacer()

            if status == .sent || status == .noContacts {
                Button(action: onResend) {
                    Image(systemName: "arrow.counterclockwise")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(emergencyDarkRed)
                        .frame(width: 32, height: 32)
                        .background(Color.white)
                        .clipShape(Circle())
                }
            }
        }
        .padding(14)
        .background(Color.white.opacity(0.15))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

// MARK: - Expired

struct ExpiredContent: View {
    let anomalyType: String; let onClose: () -> Void
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "clock.badge.exclamationmark").font(.system(size: 56)).foregroundColor(onEmergency)
            Text("Sesión expirada").font(.system(size: 20, weight: .bold)).foregroundColor(onEmergency)
            Text(anomalyType).font(.system(size: 14)).foregroundColor(onEmergency.opacity(0.8))
            Button(action: onClose) {
                Text("Cerrar").font(.system(size: 16, weight: .semibold)).foregroundColor(emergencyRed)
                    .frame(width: 200, height: 48).background(Color.white).clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

// MARK: - Error

struct ErrorContent: View {
    let message: String; let onRetry: () -> Void; let onClose: () -> Void
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "wifi.slash").font(.system(size: 48)).foregroundColor(onEmergency)
            Text("Error de conexión").font(.system(size: 18, weight: .bold)).foregroundColor(onEmergency)
            Text(message).font(.system(size: 13)).foregroundColor(onEmergency.opacity(0.8))
            HStack(spacing: 12) {
                Button(action: onClose) {
                    Text("Cancelar").foregroundColor(onEmergency)
                        .frame(width: 120, height: 44).overlay(RoundedRectangle(cornerRadius: 10).stroke(onEmergency, lineWidth: 1))
                }
                Button(action: onRetry) {
                    Text("Reintentar").foregroundColor(emergencyRed)
                        .frame(width: 120, height: 44).background(Color.white).clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
        }
        .padding(.horizontal, 32)
    }
}

#endif
