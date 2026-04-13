#if os(iOS)
import SwiftUI

struct OnboardingPage {
    let title: String
    let body: String
    let imageName: String?
    let systemImage: String
    let accentColor: Color
}

// MARK: - OnboardingView
// UI/UX principles:
//  • Progressive disclosure — introduces features one at a time
//  • Visual continuity — smooth spring transitions between pages
//  • Accessibility — minimum tap targets, high contrast text
//  • User control — skip option always visible
struct OnboardingView: View {
    let onGetStarted: () -> Void
    let onSkip: () -> Void

    private let pages: [OnboardingPage] = [
        OnboardingPage(
            title: "Bienvenido a VitalSense",
            body: "Conectamos tecnología y cuidado médico para proteger a quienes más quieres. No solo medimos, predecimos para actuar a tiempo.",
            imageName: "illus_stethoscope",
            systemImage: "stethoscope",
            accentColor: Color.primaryBlue
        ),
        OnboardingPage(
            title: "Actúa con ventaja.",
            body: "Ante una crisis, la app te guía. Gestiona alertas automáticas a servicios de emergencia y comparte el historial médico mediante un QR dinámico para atención precisa.",
            imageName: "illus_qr",
            systemImage: "qrcode",
            accentColor: Color.primaryBlueDark
        ),
        OnboardingPage(
            title: "Prevención basada en datos.",
            body: "Nuestra IA analiza el ritmo cardíaco y niveles de glucosa en tiempo real, notificándote solo si detecta un patrón de riesgo clínico.",
            imageName: "corazon",
            systemImage: "heart.circle.fill",
            accentColor: Color.heartRateRed
        ),
    ]

    @State private var currentPage = 0
    @State private var dragOffset: CGFloat = 0

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.gradientStart.opacity(0.5), Color.gradientEnd.opacity(0.9)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // Skip button — always visible (user control & freedom, Nielsen #3)
                HStack {
                    Spacer()
                    Button(action: {
                        HapticFeedback.light()
                        onSkip()
                    }) {
                        Text("Omitir")
                            .font(.manrope(size: 14))
                            .foregroundColor(Color.textPrimary.opacity(0.6))
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(Color.white.opacity(0.4))
                            .clipShape(Capsule())
                    }
                    .minimumTapTarget()
                    .padding(.top, 52)
                    .padding(.trailing, 20)
                    .opacity(currentPage == pages.count - 1 ? 0 : 1)
                    .animation(.easeInOut(duration: 0.2), value: currentPage)
                }

                // Pages with swipe gesture
                TabView(selection: $currentPage) {
                    ForEach(pages.indices, id: \.self) { i in
                        OnboardingPageView(page: pages[i])
                            .tag(i)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .onChange(of: currentPage) { _ in
                    HapticFeedback.selection()
                }

                // Dot indicator — animated capsule pill
                HStack(spacing: 8) {
                    ForEach(pages.indices, id: \.self) { i in
                        Capsule()
                            .fill(i == currentPage ? Color.onboardingBlue : Color.onboardingDotInactive)
                            .frame(width: i == currentPage ? 24 : 8, height: 8)
                            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: currentPage)
                    }
                }
                .padding(.bottom, 28)

                // Primary action button
                Button(action: {
                    if currentPage < pages.count - 1 {
                        HapticFeedback.light()
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.75)) {
                            currentPage += 1
                        }
                    } else {
                        HapticFeedback.success()
                        onGetStarted()
                    }
                }) {
                    HStack(spacing: 8) {
                        Text(currentPage == pages.count - 1 ? "Comenzar" : "Siguiente")
                            .font(.manropeSemiBold(size: 16))
                            .foregroundColor(Color.onboardingButtonText)
                        if currentPage < pages.count - 1 {
                            Image(systemName: "arrow.right")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(Color.onboardingButtonText)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 59)
                    .background(Color.onboardingBlue)
                    .cornerRadius(32)
                }
                .padding(.horizontal, Spacing.xxl)
                .padding(.bottom, 52)
            }
        }
        .navigationBarHidden(true)
    }
}

// MARK: - OnboardingPageView
struct OnboardingPageView: View {
    let page: OnboardingPage
    @State private var appeared = false

    var body: some View {
        VStack(spacing: 0) {
            // Illustration area
            ZStack {
                // Soft accent circle behind illustration
                Circle()
                    .fill(page.accentColor.opacity(0.08))
                    .frame(width: 220, height: 220)
                    .scaleEffect(appeared ? 1 : 0.6)
                    .animation(.spring(response: 0.6, dampingFraction: 0.7).delay(0.1), value: appeared)

                if let imageName = page.imageName, UIImage(named: imageName) != nil {
                    Image(imageName)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 220, height: 180)
                        .scaleEffect(appeared ? 1 : 0.8)
                        .opacity(appeared ? 1 : 0)
                        .animation(.spring(response: 0.5, dampingFraction: 0.7).delay(0.15), value: appeared)
                } else {
                    Image(systemName: page.systemImage)
                        .resizable()
                        .scaledToFit()
                        .frame(height: 110)
                        .foregroundColor(page.accentColor)
                        .scaleEffect(appeared ? 1 : 0.7)
                        .opacity(appeared ? 1 : 0)
                        .animation(.spring(response: 0.5, dampingFraction: 0.65).delay(0.15), value: appeared)
                }
            }
            .frame(maxHeight: .infinity)
            .padding(.top, 40)

            // Text area
            VStack(spacing: 14) {
                Text(page.title)
                    .font(.manropeBold(size: 22))
                    .foregroundColor(Color.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                    .offset(y: appeared ? 0 : 12)
                    .opacity(appeared ? 1 : 0)
                    .animation(.spring(response: 0.45, dampingFraction: 0.75).delay(0.2), value: appeared)

                Text(page.body)
                    .font(.manrope(size: 15))
                    .foregroundColor(Color.textSecondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
                    .padding(.horizontal, 32)
                    .offset(y: appeared ? 0 : 10)
                    .opacity(appeared ? 1 : 0)
                    .animation(.spring(response: 0.45, dampingFraction: 0.75).delay(0.28), value: appeared)
            }
            .padding(.bottom, 24)
            .frame(maxHeight: .infinity)
        }
        .onAppear {
            appeared = false
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                appeared = true
            }
        }
        .onDisappear { appeared = false }
    }
}
#endif
