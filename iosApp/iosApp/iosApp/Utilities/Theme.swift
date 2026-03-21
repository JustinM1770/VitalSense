import SwiftUI

// MARK: - VitalSense Theme
// Unified color tokens matching Android Color.kt

extension Color {
    // MARK: - Brand
    static let primaryBlue      = Color(hex: "#1169FF")   // botones, bottom nav, links activos
    static let primaryBlueDark  = Color(hex: "#0A4FCC")   // pressed state
    static let primaryBlueLight = Color(hex: "#EBF2FF")   // fondos de pantallas blancas

    // MARK: - Backgrounds
    static let dashBg           = Color(hex: "#BDD9F2")   // fondo azul medio del Dashboard
    static let surfaceWhite     = Color(hex: "#FFFFFF")   // tarjetas y superficies
    static let inputBg          = Color(hex: "#F0F2F5")   // fondo de campos de texto

    // MARK: - Splash / Onboarding
    static let logoNavy         = Color(hex: "#0A2540")
    static let logoTeal         = Color(hex: "#52A2C4")
    static let onboardingBlue   = Color(hex: "#126AFF")
    static let onboardingButtonText  = Color(hex: "#B6D8FF")
    static let onboardingDotInactive = Color(hex: "#E3E1E8")
    static let gradientStart    = Color(hex: "#FFFFFF")
    static let gradientEnd      = Color(hex: "#B6D8FF")

    // MARK: - Text
    static let textPrimary      = Color(hex: "#0D1B2A")
    static let textSecondary    = Color(hex: "#6B7A8D")
    static let textHint         = Color(hex: "#B0B8C4")
    static let textDark         = Color(hex: "#221F1F")
    static let textLink         = primaryBlue

    // MARK: - Metric accents
    static let heartRateRed     = Color(hex: "#FF4560")
    static let heartRateSoft    = Color(hex: "#FDE8E8")
    static let glucoseOrange    = Color(hex: "#FF9800")
    static let glucoseSoft      = Color(hex: "#FFF3E0")
    static let spO2Green        = Color(hex: "#4CAF50")
    static let spO2Soft         = Color(hex: "#E8F5E9")
    static let sleepGreen       = Color(hex: "#00C48C")
    static let chartRed         = Color(hex: "#FF4560")

    // MARK: - Alerts
    static let alertBackground  = Color(hex: "#FFF8E1")
    static let alertBorder      = Color(hex: "#FFB300")
    static let alertText        = Color(hex: "#795548")
    static let alertWarning     = Color(hex: "#FFF3CD")

    // MARK: - Aliases (legacy compatibility)
    static let dashCard         = surfaceWhite
    static let dashBlue         = primaryBlue
}

// MARK: - Custom View Modifiers
struct CardStyle: ViewModifier {
    var cornerRadius: CGFloat = 12

    func body(content: Content) -> some View {
        content
            .background(Color.surfaceWhite)
            .cornerRadius(cornerRadius)
            .shadow(color: Color.black.opacity(0.05), radius: 4, x: 0, y: 2)
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    var isEnabled: Bool = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 16, weight: .semibold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(isEnabled ? Color.primaryBlue : Color.primaryBlue.opacity(0.4))
            .cornerRadius(32)
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.easeInOut(duration: 0.1), value: configuration.isPressed)
    }
}

extension View {
    func cardStyle(cornerRadius: CGFloat = 12) -> some View {
        modifier(CardStyle(cornerRadius: cornerRadius))
    }
}
