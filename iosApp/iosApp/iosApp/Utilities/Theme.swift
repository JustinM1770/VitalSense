#if os(iOS)
import SwiftUI

// MARK: - VitalSense Theme
// Unified color tokens matching Android Color.kt
// UI/UX principles applied:
//   • Consistency & Standards — single source of truth for all visual tokens
//   • Visibility of System Status — skeleton & animated feedback components
//   • Sensory Feedback — haptics wired into interactive components

extension Color {
    // MARK: - Brand
    static let primaryBlue      = Color(hex: "#1169FF")   // botones, bottom nav, links activos
    static let primaryBlueDark  = Color(hex: "#225FFF")   // pressed state / títulos de sección
    static let primaryBlueLight = Color(hex: "#E5EBFF")   // fondos suaves azules

    // MARK: - Backgrounds
    static let dashBg           = Color(hex: "#93EDFC")   // header azul claro del Dashboard (Figma)
    static let dashBgAlt        = Color(hex: "#95C3F8")   // variante azul header / calendario activo
    static let surfaceWhite     = Color(hex: "#FFFFFF")   // tarjetas y superficies
    static let surfaceGray      = Color(hex: "#F9F9FB")   // fondo campos y secciones
    static let inputBg          = Color(hex: "#F9F9FB")   // fondo de campos de texto auth

    // MARK: - Splash / Onboarding
    static let logoNavy         = Color(hex: "#0A2540")
    static let logoTeal         = Color(hex: "#52A2C4")
    static let onboardingBlue   = Color(hex: "#126AFF")
    static let onboardingButtonText  = Color(hex: "#B6D8FF")
    static let onboardingDotInactive = Color(hex: "#E3E1E8")
    static let gradientStart    = Color(hex: "#FFFFFF")
    static let gradientEnd      = Color(hex: "#B6D8FF")

    // MARK: - Text
    static let textPrimary      = Color(hex: "#040415")   // texto principal más oscuro (Figma)
    static let textNavy         = Color(hex: "#03314B")   // subtítulos y headers (Figma)
    static let textSecondary    = Color(hex: "#8C8C8C")   // texto secundario gris (Figma)
    static let textHint         = Color(hex: "#AEAEAE")   // placeholders y hints
    static let textDark         = Color(hex: "#221F1F")   // texto oscuro en auth
    static let textLink         = Color(hex: "#1169FF")

    // MARK: - Metric accents
    static let heartRateRed     = Color(hex: "#EB4B62")   // corazón (Figma: #EB4B62)
    static let heartRateSoft    = Color(hex: "#FDE8E8")
    static let glucoseOrange    = Color(hex: "#FF9800")
    static let glucoseSoft      = Color(hex: "#FFF3E0")
    static let spO2Green        = Color(hex: "#34A853")   // verde Figma
    static let spO2Soft         = Color(hex: "#E8F5E9")
    static let sleepGreen       = Color(hex: "#00C48C")
    static let chartRed         = Color(hex: "#EB4B62")

    // MARK: - Status / Semantic
    static let successGreen     = Color(hex: "#34A853")
    static let successSoft      = Color(hex: "#E8F5E9")
    static let errorRed         = Color(hex: "#EA4335")
    static let errorSoft        = Color(hex: "#FDECEA")
    static let warningAmber     = Color(hex: "#FF9800")
    static let warningSoft      = Color(hex: "#FFF3E0")
    static let infoBlueSoft     = Color(hex: "#E5EBFF")

    // MARK: - Alerts
    static let alertBackground  = Color(hex: "#FFF8E1")
    static let alertBorder      = Color(hex: "#FFB300")
    static let alertText        = Color(hex: "#795548")
    static let alertWarning     = Color(hex: "#FFF3CD")
    static let sosRed           = Color(hex: "#EA4335")   // SOS / errores (Figma)

    // MARK: - Surface / Dividers
    static let divider          = Color(hex: "#EFEFEF")
    static let borderGray       = Color(hex: "#E0E0E0")
    static let borderFocused    = Color(hex: "#1169FF")

    // MARK: - Aliases (legacy compatibility)
    static let dashCard         = Color(hex: "#FFFFFF")
    static let dashBlue         = Color(hex: "#1169FF")
}

// MARK: - Spacing Scale
// UI/UX: Consistent spacing grid (4pt base) — Nielsen #4 Consistency & Standards.
// Use these constants instead of hardcoded padding values across all views.
enum Spacing {
    static let xs:  CGFloat = 4
    static let sm:  CGFloat = 8
    static let md:  CGFloat = 12
    static let lg:  CGFloat = 16
    static let xl:  CGFloat = 20
    static let xxl: CGFloat = 24
    static let xxxl: CGFloat = 32
    static let section: CGFloat = 40
}

// MARK: - Typography Scale
// UI/UX: Clear visual hierarchy — Nielsen #8 Aesthetic & Minimalist Design.
// All text uses Manrope as the sole typeface to ensure brand consistency.
extension Font {
    // Display
    static func vsDisplay()    -> Font { .manropeBold(size: 32) }
    // Headings
    static func vsH1()         -> Font { .manropeBold(size: 24) }
    static func vsH2()         -> Font { .manropeBold(size: 20) }
    static func vsH3()         -> Font { .manropeSemiBold(size: 17) }
    // Body
    static func vsBody()       -> Font { .manrope(size: 15) }
    static func vsBodySm()     -> Font { .manrope(size: 13) }
    // Label / Caption
    static func vsLabel()      -> Font { .manropeSemiBold(size: 13) }
    static func vsCaption()    -> Font { .manrope(size: 12) }
    static func vsCaptionBold() -> Font { .manropeBold(size: 12) }
    // Button
    static func vsButton()     -> Font { .manropeSemiBold(size: 16) }
    static func vsButtonSm()   -> Font { .manropeSemiBold(size: 14) }
}

// MARK: - Shadow Tokens
struct ShadowStyle {
    let color: Color
    let radius: CGFloat
    let x: CGFloat
    let y: CGFloat

    static let small  = ShadowStyle(color: .black.opacity(0.05), radius: 4,  x: 0, y: 2)
    static let medium = ShadowStyle(color: .black.opacity(0.08), radius: 8,  x: 0, y: 4)
    static let large  = ShadowStyle(color: .black.opacity(0.10), radius: 16, x: 0, y: 6)
}

extension View {
    func vsShadow(_ style: ShadowStyle = .small) -> some View {
        shadow(color: style.color, radius: style.radius, x: style.x, y: style.y)
    }
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
            .font(.manropeSemiBold(size: 16))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(isEnabled ? Color.primaryBlue : Color.primaryBlue.opacity(0.4))
            .cornerRadius(32)
            .scaleEffect(configuration.isPressed ? 0.97 : 1.0)
            .opacity(configuration.isPressed ? 0.92 : 1.0)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
            .onChange(of: configuration.isPressed) { pressed in
                if pressed { HapticFeedback.medium() }
            }
    }
}

extension View {
    func cardStyle(cornerRadius: CGFloat = 12) -> some View {
        modifier(CardStyle(cornerRadius: cornerRadius))
    }

    // MARK: - Skeleton Loading
    // UI/UX: Visibility of System Status (Nielsen #1).
    // Skeleton placeholders communicate that content is loading without a blank screen,
    // reducing perceived wait time and keeping the user oriented in the layout.
    func skeleton(isLoading: Bool, cornerRadius: CGFloat = 8) -> some View {
        modifier(SkeletonModifier(isLoading: isLoading, cornerRadius: cornerRadius))
    }

    // MARK: - Minimum Tap Target
    // UI/UX: Accessibility & Fitts' Law.
    // Elderly users need larger touch targets. Apple HIG recommends min 44×44 pt.
    func minimumTapTarget(size: CGFloat = 44) -> some View {
        frame(minWidth: size, minHeight: size)
            .contentShape(Rectangle())
    }
}

// MARK: - Skeleton Modifier
struct SkeletonModifier: ViewModifier {
    let isLoading: Bool
    let cornerRadius: CGFloat
    @State private var phase: CGFloat = 0

    func body(content: Content) -> some View {
        if isLoading {
            content
                .hidden()
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRadius)
                        .fill(skeletonGradient)
                )
                .onAppear {
                    withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) {
                        phase = 1
                    }
                }
        } else {
            content
        }
    }

    private var skeletonGradient: LinearGradient {
        LinearGradient(
            stops: [
                .init(color: Color(hex: "#E8E8E8"), location: max(0, phase - 0.3)),
                .init(color: Color(hex: "#F4F4F4"), location: phase),
                .init(color: Color(hex: "#E8E8E8"), location: min(1, phase + 0.3))
            ],
            startPoint: .leading,
            endPoint: .trailing
        )
    }
}

// MARK: - Secondary Button Style
struct SecondaryButtonStyle: ButtonStyle {
    var isEnabled: Bool = true
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.vsButton())
            .foregroundColor(isEnabled ? .primaryBlue : .textHint)
            .frame(maxWidth: .infinity, minHeight: 50)
            .background(Color.infoBlueSoft)
            .cornerRadius(32)
            .scaleEffect(configuration.isPressed ? 0.97 : 1.0)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

// MARK: - Destructive Button Style
struct DestructiveButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.vsButtonSm())
            .foregroundColor(.errorRed)
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(Color.errorSoft)
            .cornerRadius(12)
            .scaleEffect(configuration.isPressed ? 0.97 : 1.0)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

// MARK: - Empty State Component
// UI/UX: Nielsen #1 Visibility of System Status — always show meaningful content,
// never leave the user looking at a blank screen. EmptyState gives context + a clear action.
struct VSEmptyState: View {
    let icon: String
    let title: String
    let message: String
    var actionLabel: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: Spacing.lg) {
            ZStack {
                Circle()
                    .fill(Color.infoBlueSoft)
                    .frame(width: 80, height: 80)
                Image(systemName: icon)
                    .font(.system(size: 32))
                    .foregroundColor(.primaryBlue)
            }
            VStack(spacing: Spacing.sm) {
                Text(title)
                    .font(.vsH3())
                    .foregroundColor(.textPrimary)
                    .multilineTextAlignment(.center)
                Text(message)
                    .font(.vsBodySm())
                    .foregroundColor(.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, Spacing.xxxl)
            }
            if let label = actionLabel, let action = action {
                Button(action: action) {
                    Text(label)
                        .font(.vsButton())
                        .foregroundColor(.white)
                        .frame(height: 48)
                        .padding(.horizontal, Spacing.xxxl)
                        .background(Color.primaryBlue)
                        .clipShape(Capsule())
                }
                .minimumTapTarget()
            }
        }
        .padding(.vertical, Spacing.section)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Error State Component
// UI/UX: Nielsen #9 Help Users Recognize, Diagnose and Recover from Errors.
// Show what went wrong + a clear Retry action so the user is never stuck.
struct VSErrorState: View {
    let message: String
    var retryAction: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: Spacing.lg) {
            ZStack {
                Circle()
                    .fill(Color.errorSoft)
                    .frame(width: 72, height: 72)
                Image(systemName: "wifi.exclamationmark")
                    .font(.system(size: 28))
                    .foregroundColor(.errorRed)
            }
            VStack(spacing: Spacing.sm) {
                Text("Algo salió mal")
                    .font(.vsH3())
                    .foregroundColor(.textPrimary)
                Text(message)
                    .font(.vsBodySm())
                    .foregroundColor(.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, Spacing.xxxl)
            }
            if let retry = retryAction {
                Button(action: retry) {
                    Label("Reintentar", systemImage: "arrow.clockwise")
                        .font(.vsButton())
                        .foregroundColor(.white)
                        .frame(height: 48)
                        .padding(.horizontal, Spacing.xxxl)
                        .background(Color.primaryBlue)
                        .clipShape(Capsule())
                }
                .minimumTapTarget()
            }
        }
        .padding(.vertical, Spacing.section)
    }
}

// MARK: - Success Banner
// UI/UX: Nielsen #1 — Communicate successful outcomes clearly and briefly.
struct VSSuccessBanner: View {
    let message: String
    var body: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(.successGreen)
                .font(.system(size: 18))
            Text(message)
                .font(.vsLabel())
                .foregroundColor(.textPrimary)
            Spacer()
        }
        .padding(Spacing.md)
        .background(Color.successSoft)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, Spacing.xxl)
    }
}

// MARK: - Section Header
struct VSSectionHeader: View {
    let title: String
    var action: String? = nil
    var onAction: (() -> Void)? = nil

    var body: some View {
        HStack {
            Text(title)
                .font(.vsH3())
                .foregroundColor(.textPrimary)
            Spacer()
            if let actionLabel = action, let handler = onAction {
                Button(action: handler) {
                    Text(actionLabel)
                        .font(.vsLabel())
                        .foregroundColor(.primaryBlue)
                }
            }
        }
        .padding(.horizontal, Spacing.xxl)
    }
}

// MARK: - Metric Pill
// Reusable vital sign display pill used across Dashboard, Reports, Patient Detail
struct VSMetricPill: View {
    let icon: String
    let value: String
    let unit: String
    let color: Color
    let softColor: Color

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(color)
            VStack(alignment: .leading, spacing: 0) {
                Text(value)
                    .font(.vsH3())
                    .foregroundColor(color)
                Text(unit)
                    .font(.vsCaption())
                    .foregroundColor(.textSecondary)
            }
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm)
        .background(softColor)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Animated Metric Number
// UI/UX: Delight & Feedback — numbers that count up when first shown
// communicate that data is live and dynamic, not static.
struct AnimatedMetricNumber: View {
    let value: Double
    let format: String
    let font: Font
    let color: Color

    @State private var displayed: Double = 0

    init(value: Double, format: String = "%.0f", font: Font = .manropeBold(size: 32), color: Color = .textPrimary) {
        self.value  = value
        self.format = format
        self.font   = font
        self.color  = color
    }

    var body: some View {
        Text(String(format: format, displayed))
            .font(font)
            .foregroundColor(color)
            .contentTransition(.numericText())
            .onAppear {
                withAnimation(.spring(response: 0.8, dampingFraction: 0.7).delay(0.1)) {
                    displayed = value
                }
            }
            .onChange(of: value) { newVal in
                withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                    displayed = newVal
                }
            }
    }
}
#endif
