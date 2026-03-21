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
            if showingOnboarding {
                OnboardingView(
                    onGetStarted: {
                        // User completed onboarding → go to login
                        showingOnboarding = false
                        navigationPath.append(AuthScreen.login)
                    },
                    onSkip: {
                        // User skipped onboarding → go to login
                        showingOnboarding = false
                        navigationPath.append(AuthScreen.login)
                    }
                )
            } else {
                // Placeholder view - navigation will handle the rest
                Color.clear
                    .onAppear {
                        // Ensure we navigate to login if path is empty
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
                    onLogin: {
                        onAuthenticated()
                    },
                    onRegister: {
                        navigationPath.append(AuthScreen.register)
                    },
                    onBack: {
                        // Go back to onboarding
                        if navigationPath.count > 0 {
                            navigationPath.removeLast()
                        }
                        showingOnboarding = true
                    }
                )

            case .register:
                RegisterView(
                    onRegister: {
                        onAuthenticated()
                    },
                    onBack: {
                        // Go back to login
                        if navigationPath.count > 0 {
                            navigationPath.removeLast()
                        }
                    },
                    onLogin: {
                        // Go back to login
                        if navigationPath.count > 0 {
                            navigationPath.removeLast()
                        }
                    }
                )
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
