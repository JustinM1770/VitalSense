import SwiftUI

struct OnboardingPage {
    let title: String
    let body: String
    let imageName: String?  // optional iOS image (if added to Assets)
    let systemImage: String // fallback SF Symbol
}

struct OnboardingView: View {
    let onGetStarted: () -> Void
    let onSkip: () -> Void

    private let pages: [OnboardingPage] = [
        OnboardingPage(
            title: "Bienvenido a VitalSense",
            body: "Conectamos tecnología y cuidado medico para proteger a quienes mas quieres. No solo medimos, predecimos para actuar a tiempo",
            imageName: "illus_stethoscope",  // Try to use asset if available
            systemImage: "stethoscope"
        ),
        OnboardingPage(
            title: "Actua con ventaja.",
            body: "Ante una crisis, la app te guia. Gestiona alertas automaticas a servicios de emergencia y comparte el historial medico mediante un QR dinamico para atencion medica precisa",
            imageName: "illus_qr",
            systemImage: "qrcode"
        ),
        OnboardingPage(
            title: "Prevencion basada en datos.",
            body: "No necesitas estar pegado a la pantalla. Nuestra IA analiza el rito cardiaco y niveles de glucosa por ti, notificandote de inmediato solo si detecta un patron de riesgo.",
            imageName: "corazon",
            systemImage: "heart.circle.fill"
        ),
    ]

    @State private var currentPage = 0

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.gradientStart.opacity(0.5), Color.gradientEnd.opacity(0.9)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // Skip button
                HStack {
                    Spacer()
                    Button(action: onSkip) {
                        Text("Omitir")
                            .font(.manrope(size: 14))
                            .foregroundColor(.black)
                            .padding(.top, 52)
                            .padding(.trailing, 16)
                    }
                }

                // Pages
                TabView(selection: $currentPage) {
                    ForEach(pages.indices, id: \.self) { i in
                        OnboardingPageView(page: pages[i]).tag(i)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))

                // Dots indicator
                HStack(spacing: 8) {
                    ForEach(pages.indices, id: \.self) { i in
                        Capsule()
                            .fill(i == currentPage ? Color.onboardingBlue : Color.onboardingDotInactive)
                            .frame(width: i == currentPage ? 16 : 8, height: 8)
                            .animation(.spring(), value: currentPage)
                    }
                }
                .padding(.bottom, 28)

                // Next/Comenzar button
                Button(action: {
                    if currentPage < pages.count - 1 {
                        withAnimation { currentPage += 1 }
                    } else {
                        onGetStarted()
                    }
                }) {
                    Text(currentPage == pages.count - 1 ? "Comenzar" : "Siguiente")
                        .font(.manropeSemiBold(size: 16))
                        .foregroundColor(Color.onboardingButtonText)
                        .frame(width: 325, height: 59)
                        .background(Color.onboardingBlue)
                        .cornerRadius(32)
                }
                .padding(.bottom, 52)
            }
        }
        .navigationBarHidden(true)
    }
}

struct OnboardingPageView: View {
    let page: OnboardingPage

    var body: some View {
        VStack(spacing: 0) {
            // Illustration area - top 55%
            ZStack {
                if let imageName = page.imageName, let _ = UIImage(named: imageName) {
                    // Use custom asset if available
                    Image(imageName)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 249, height: 213)
                } else {
                    // Fallback to SF Symbol
                    Image(systemName: page.systemImage)
                        .resizable()
                        .scaledToFit()
                        .frame(height: 120)
                        .foregroundColor(Color.primaryBlue)
                }
            }
            .frame(maxHeight: .infinity)
            .padding(.top, 60)

            // Text area - bottom 45%
            VStack(spacing: 12) {
                Text(page.title)
                    .font(.manropeBold(size: 20))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)

                Text(page.body)
                    .font(.manrope(size: 15))
                    .foregroundColor(.black.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
            .frame(maxHeight: .infinity)
        }
    }
}
