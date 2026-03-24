import SwiftUI
import FirebaseAuth

struct AppNavigation: View {
    @State private var isAuthenticated = Auth.auth().currentUser != nil
    @State private var showSplash = true

    var body: some View {
        Group {
            if showSplash {
                SplashView {
                    withAnimation(.easeInOut(duration: 0.4)) { showSplash = false }
                }
            } else if isAuthenticated {
                MainTabView(onSignOut: { isAuthenticated = false })
            } else {
                AuthFlow(onAuthenticated: { isAuthenticated = true })
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .AuthStateChanged)) { _ in
            isAuthenticated = Auth.auth().currentUser != nil
        }
    }
}

// MARK: - AuthFlow with proper navigation
enum AuthScreen: Hashable {
    case login
    case register
}

struct AuthFlow: View {
    let onAuthenticated: () -> Void

    @State private var showingOnboarding = true
    @State private var navigationPath = NavigationPath()

    var body: some View {
        NavigationStack(path: $navigationPath) {
            Group {
                if showingOnboarding {
                    OnboardingView(
                        onGetStarted: {
                            showingOnboarding = false
                            navigationPath.append(AuthScreen.login)
                        },
                        onSkip: {
                            showingOnboarding = false
                            navigationPath.append(AuthScreen.login)
                        }
                    )
                } else {
                    Color.clear
                        .onAppear {
                            if navigationPath.count == 0 {
                                navigationPath.append(AuthScreen.login)
                            }
                        }
                }
            }
            .navigationDestination(for: AuthScreen.self) { screen in
                switch screen {
                case .login:
                    LoginView(
                        onLogin: { onAuthenticated() },
                        onRegister: { navigationPath.append(AuthScreen.register) },
                        onBack: {
                            navigationPath.removeLast()
                            showingOnboarding = true
                        }
                    )
                case .register:
                    RegisterView(
                        onRegister: { onAuthenticated() },
                        onBack: { navigationPath.removeLast() },
                        onLogin: { navigationPath.removeLast() }
                    )
                }
            }
        }
    }
}

struct MainTabView: View {
    let onSignOut: () -> Void

    var body: some View {
        TabView {
            DashboardView()
                .tabItem { Label("Inicio", systemImage: "heart.fill") }

            WearableView()
                .tabItem { Label("Wearable", systemImage: "applewatch") }

            ChatBotView()
                .tabItem { Label("IA", systemImage: "brain") }

            ProfileView()
                .tabItem { Label("Perfil", systemImage: "person.fill") }
        }
        .accentColor(Color.primaryBlue)
        .onReceive(NotificationCenter.default.publisher(for: .AuthStateChanged)) { _ in
            if Auth.auth().currentUser == nil { onSignOut() }
        }
    }
}

extension Notification.Name {
    static let AuthStateChanged = Notification.Name("AuthStateDidChangeNotification")
}
