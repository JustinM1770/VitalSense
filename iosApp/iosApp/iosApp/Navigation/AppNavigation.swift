#if os(iOS)
import SwiftUI
import Combine
import FirebaseAuth
import FirebaseDatabase

struct AppNavigation: View {
    @State private var isAuthenticated    = Auth.auth().currentUser != nil
    @State private var needsCuestionario  = false
    @State private var showSplash         = true
    @ObservedObject private var sosMonitor = SOSMonitorService.shared

    var body: some View {
        Group {
            if showSplash {
                SplashView {
                    checkAuthState()
                    withAnimation(.easeInOut(duration: 0.4)) { showSplash = false }
                }
            } else if isAuthenticated {
                if needsCuestionario {
                    NavigationStack {
                        CuestionarioView(
                            onBack: {
                                try? Auth.auth().signOut()
                                isAuthenticated = false
                                needsCuestionario = false
                            },
                            onNext: { needsCuestionario = false }
                        )
                    }
                } else {
                    MainTabView(
                        onSignOut: {
                            isAuthenticated   = false
                            needsCuestionario = false
                        }
                    )
                }
            } else {
                AuthFlow(onAuthenticated: {
                    isAuthenticated = true
                    checkCuestionario()
                })
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .AuthStateChanged)) { _ in
            isAuthenticated = Auth.auth().currentUser != nil
            if isAuthenticated {
                checkCuestionario()
                SOSMonitorService.shared.startListening()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .openSOSAlert)) { _ in
            // El usuario tocó "Necesito ayuda" desde el banner — la alerta ya está en el monitor,
            // la hoja se presentará automáticamente porque activeAlert sigue siendo != nil.
            // Si por alguna razón se limpió, no hacemos nada.
            _ = sosMonitor.activeAlert
        }
        .sheet(item: $sosMonitor.activeAlert) { alert in
            SOSAlertView(alert: alert)
        }
    }

    private func checkAuthState() {
        isAuthenticated = Auth.auth().currentUser != nil
        if isAuthenticated {
            checkCuestionario()
            SOSMonitorService.shared.startListening()
        }
    }

    private func checkCuestionario() {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        let completed = UserDefaults.standard.bool(forKey: "cuestionario_completed_\(uid)")
        if completed { needsCuestionario = false; return }
        // Check Firebase as fallback
        Database.database().reference()
            .child("patients/\(uid)/profile/cuestionarioCompleted")
            .observeSingleEvent(of: .value) { snap in
                let done = snap.value as? Bool ?? false
                if done { UserDefaults.standard.set(true, forKey: "cuestionario_completed_\(uid)") }
                needsCuestionario = !done
            }
    }
}

// MARK: - AuthFlow (con ForgotPassword)
enum AuthScreen: Hashable { case login, register, forgotPassword }

struct AuthFlow: View {
    let onAuthenticated: () -> Void
    @State private var showingOnboarding = true
    @State private var navigationPath    = NavigationPath()

    var body: some View {
        NavigationStack(path: $navigationPath) {
            Group {
                if showingOnboarding {
                    OnboardingView(
                        onGetStarted: { showingOnboarding = false; navigationPath.append(AuthScreen.login) },
                        onSkip:       { showingOnboarding = false; navigationPath.append(AuthScreen.login) }
                    )
                } else {
                    Color.clear.onAppear {
                        if navigationPath.count == 0 { navigationPath.append(AuthScreen.login) }
                    }
                }
            }
            .navigationDestination(for: AuthScreen.self) { screen in
                switch screen {
                case .login:
                    LoginView(
                        onLogin:    { onAuthenticated() },
                        onRegister: { navigationPath.append(AuthScreen.register) },
                        onBack:     { navigationPath.removeLast(); showingOnboarding = true },
                        onForgotPassword: { navigationPath.append(AuthScreen.forgotPassword) }
                    )
                case .register:
                    RegisterView(
                        onRegister: { onAuthenticated() },
                        onBack:     { navigationPath.removeLast() },
                        onLogin:    { navigationPath.removeLast() }
                    )
                case .forgotPassword:
                    ForgotPasswordView(
                        onBack: { navigationPath.removeLast() }
                    )
                }
            }
        }
    }
}

// MARK: - Tabs
enum AppTab: Int, CaseIterable {
    case home, salud, ia, chat, perfil
    var label: String {
        switch self {
        case .home:  return "Inicio"
        case .salud: return "Salud"
        case .ia:    return "IA"
        case .chat:  return "Chat"
        case .perfil: return "Perfil"
        }
    }
    var icon: String {
        switch self {
        case .home:  return "house.fill"
        case .salud: return "heart.fill"
        case .ia:    return "brain.filled.head.profile"
        case .chat:  return "bubble.left.and.bubble.right.fill"
        case .perfil: return "person.fill"
        }
    }
}

struct MainTabView: View {
    let onSignOut: () -> Void
    @State private var selectedTab: AppTab = .home
    @StateObject private var emergencyVm = EmergencyQrViewModel()
    @State private var showEmergency = false

    var body: some View {
        ZStack(alignment: .bottom) {
            // Full-screen background — covers status bar + home indicator
            Color.dashBg.ignoresSafeArea()

            // Cada tab tiene su propio NavigationStack independiente para que
            // la pila de navegación de un tab no afecte a los demás.
            ZStack {
                if selectedTab == .home {
                    NavigationStack {
                        DashboardView(onEmergency: { type, hr in
                            emergencyVm.onAnomalyDetected(type, hr)
                            showEmergency = true
                        })
                    }
                }
                if selectedTab == .salud {
                    NavigationStack { DailyReportView() }
                }
                if selectedTab == .ia {
                    NavigationStack { AIInsightsView() }
                }
                if selectedTab == .chat {
                    NavigationStack { ChatBotView() }
                }
                if selectedTab == .perfil {
                    NavigationStack { ProfileView() }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            BiometricBottomNav(selectedTab: $selectedTab)
                .padding(.horizontal, 16)
                .padding(.bottom, max((UIApplication.shared.connectedScenes.first as? UIWindowScene)?.windows.first?.safeAreaInsets.bottom ?? 0, 8))
        }
        .ignoresSafeArea(edges: .bottom)
        .fullScreenCover(isPresented: $showEmergency) {
            EmergencyQrView(vm: emergencyVm, onResolve: { showEmergency = false })
        }
        .onReceive(NotificationCenter.default.publisher(for: .AuthStateChanged)) { _ in
            if Auth.auth().currentUser == nil { onSignOut() }
        }
        .onReceive(NotificationCenter.default.publisher(for: .openAIInsights)) { _ in
            withAnimation { selectedTab = .ia }
        }
        .onReceive(NotificationCenter.default.publisher(for: .triggerIDENTIMEX)) { notif in
            let type = notif.userInfo?["anomalyType"] as? String ?? "Anomalía crítica detectada por BioMetric AI"
            let hr   = notif.userInfo?["heartRate"]   as? Int    ?? 92
            emergencyVm.onAnomalyDetected(type, hr)
            showEmergency = true
        }
    }
}

// MARK: - Custom Bottom Nav
// UI/UX: Recognition over Recall (Nielsen #6) — labels under icons remove the need
// for users to memorize what each icon means, critical for elderly users.
// Animated selection indicator provides immediate visual confirmation of tab change.
struct BiometricBottomNav: View {
    @Binding var selectedTab: AppTab

    var body: some View {
        HStack(spacing: 0) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                TabButton(tab: tab, isSelected: selectedTab == tab) {
                    HapticFeedback.selection()
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                        selectedTab = tab
                    }
                }
            }
        }
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Color.primaryBlue)
                .shadow(color: Color.primaryBlue.opacity(0.4), radius: 16, x: 0, y: 6)
        )
    }
}

private struct TabButton: View {
    let tab: AppTab
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 3) {
                ZStack {
                    // Selection background pill
                    if isSelected {
                        Capsule()
                            .fill(Color.white.opacity(0.18))
                            .frame(width: 44, height: 32)
                            .transition(.scale.combined(with: .opacity))
                    }

                    Image(systemName: tab.icon)
                        .font(.system(size: isSelected ? 20 : 19, weight: isSelected ? .bold : .regular))
                        .foregroundColor(.white)
                        .scaleEffect(isSelected ? 1.05 : 1.0)
                }
                .frame(height: 32)
                .animation(.spring(response: 0.3, dampingFraction: 0.65), value: isSelected)

                // Label — only shows when selected with a spring slide-in
                Text(tab.label)
                    .font(.manropeSemiBold(size: 9))
                    .foregroundColor(.white)
                    .opacity(isSelected ? 1 : 0)
                    .scaleEffect(isSelected ? 1 : 0.7)
                    .frame(height: isSelected ? 12 : 0)
                    .animation(.spring(response: 0.3, dampingFraction: 0.65), value: isSelected)
            }
            .frame(maxWidth: .infinity)
            .frame(minHeight: 44) // Accessibility: min tap target
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

extension Notification.Name {
    static let AuthStateChanged  = Notification.Name("AuthStateDidChangeNotification")
    static let openAIInsights    = Notification.Name("openAIInsights")
    static let openSOSAlert      = Notification.Name("openSOSAlert")
    static let triggerIDENTIMEX  = Notification.Name("triggerIDENTIMEX")
}

#endif
