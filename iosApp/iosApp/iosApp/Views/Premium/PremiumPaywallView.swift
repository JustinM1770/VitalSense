#if os(iOS)
import SwiftUI
import StoreKit

struct PremiumPaywallView: View {
    @ObservedObject private var sub = SubscriptionService.shared
    @Environment(\.dismiss) private var dismiss

    // Precio a mostrar mientras cargan los productos reales de StoreKit
    private let fallbackMonthly = "$4.99"
    private let fallbackYearly  = "$39.99"

    var body: some View {
        let monthlyProduct = sub.products.first(where: { $0.productID == SubscriptionService.premiumMonthlyID })
        let yearlyProduct  = sub.products.first(where: { $0.productID == SubscriptionService.premiumYearlyID })
        ZStack {
            // Fondo degradado — mismo azul del branding
            LinearGradient(
                colors: [Color(hex: "#0D47A1"), Color.primaryBlue],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 0) {

                    // ── Header ────────────────────────────────────────────────
                    HStack {
                        Spacer()
                        Button { dismiss() } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 26))
                                .foregroundColor(.white.opacity(0.7))
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, 20)

                    Spacer().frame(height: 12)

                    // Ícono central
                    ZStack {
                        Circle()
                            .fill(Color.white.opacity(0.15))
                            .frame(width: 90, height: 90)
                        Image(systemName: "crown.fill")
                            .font(.system(size: 40))
                            .foregroundColor(.yellow)
                    }

                    Spacer().frame(height: 20)

                    Text("BioMetric AI Premium")
                        .font(.manropeBold(size: 26))
                        .foregroundColor(.white)

                    Spacer().frame(height: 8)

                    Text("Monitorea más de un wearable\ny desbloquea todo el poder de la IA.")
                        .font(.manrope(size: 15))
                        .foregroundColor(.white.opacity(0.85))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)

                    Spacer().frame(height: 36)

                    // ── Beneficios ─────────────────────────────────────────────
                    VStack(spacing: 0) {
                        PremiumFeatureRow(icon: "applewatch",            text: "Wearables ilimitados vinculados")
                        Divider().background(Color.white.opacity(0.08))
                        PremiumFeatureRow(icon: "brain.filled.head.profile", text: "Análisis IA sin límite diario")
                        Divider().background(Color.white.opacity(0.08))
                        PremiumFeatureRow(icon: "chart.line.uptrend.xyaxis", text: "Historial completo (365 días)")
                        Divider().background(Color.white.opacity(0.08))
                        PremiumFeatureRow(icon: "person.2.fill",         text: "Compartir datos con tu médico")
                        Divider().background(Color.white.opacity(0.08))
                        PremiumFeatureRow(icon: "bell.badge.fill",       text: "Alertas inteligentes prioritarias")
                    }
                    .background(Color.white.opacity(0.1))
                    .cornerRadius(20)
                    .padding(.horizontal, 24)

                    Spacer().frame(height: 32)

                    // ── Planes ─────────────────────────────────────────────────
                    VStack(spacing: 12) {
                        // Anual — destacado
                        PlanCard(
                            title: "Anual",
                            price: yearlyProduct?.displayPrice ?? fallbackYearly,
                            period: "/ año",
                            badge: "AHORRA 33%",
                            isHighlighted: true,
                            isLoading: sub.isPurchasing
                        ) {
                            Task {
                                if let p = yearlyProduct {
                                    _ = await sub.purchase(p)
                                    if sub.plan == .premium { dismiss() }
                                }
                            }
                        }

                        // Mensual
                        PlanCard(
                            title: "Mensual",
                            price: monthlyProduct?.displayPrice ?? fallbackMonthly,
                            period: "/ mes",
                            badge: nil,
                            isHighlighted: false,
                            isLoading: sub.isPurchasing
                        ) {
                            Task {
                                if let p = monthlyProduct {
                                    _ = await sub.purchase(p)
                                    if sub.plan == .premium { dismiss() }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 24)

                    Spacer().frame(height: 20)

                    // Restaurar compras
                    Button {
                        Task {
                            await sub.restorePurchases()
                            if sub.plan == .premium { dismiss() }
                        }
                    } label: {
                        Text("Restaurar compras")
                            .font(.manrope(size: 13))
                            .foregroundColor(.white.opacity(0.6))
                            .underline()
                    }

                    Spacer().frame(height: 8)

                    Text("Cancela cuando quieras. Sin compromisos.")
                        .font(.manrope(size: 11))
                        .foregroundColor(.white.opacity(0.45))

                    Spacer().frame(height: 32)
                }
            }
        }
        .task { await sub.loadProducts() }
    }
}

// MARK: - Componentes

private struct PremiumFeatureRow: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 17))
                .foregroundColor(.yellow)
                .frame(width: 24)
            Text(text)
                .font(.manrope(size: 14))
                .foregroundColor(.white)
            Spacer()
            Image(systemName: "checkmark")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.spO2Green)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
    }
}

private struct PlanCard: View {
    let title: String
    let price: String
    let period: String
    let badge: String?
    let isHighlighted: Bool
    let isLoading: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(title)
                            .font(.manropeBold(size: 16))
                            .foregroundColor(isHighlighted ? .primaryBlue : .white)
                        if let badge {
                            Text(badge)
                                .font(.manropeSemiBold(size: 9))
                                .foregroundColor(.white)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(Color.spO2Green)
                                .cornerRadius(8)
                        }
                    }
                    if isHighlighted {
                        Text("Mejor valor")
                            .font(.manrope(size: 11))
                            .foregroundColor(.primaryBlue.opacity(0.7))
                    }
                }

                Spacer()

                if isLoading {
                    ProgressView()
                        .tint(isHighlighted ? .primaryBlue : .white)
                } else {
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        Text(price)
                            .font(.manropeBold(size: 20))
                            .foregroundColor(isHighlighted ? .primaryBlue : .white)
                        Text(period)
                            .font(.manrope(size: 12))
                            .foregroundColor(isHighlighted ? .primaryBlue.opacity(0.7) : .white.opacity(0.7))
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 18)
            .background(isHighlighted ? Color.white : Color.white.opacity(0.12))
            .cornerRadius(16)
            .overlay(
                isHighlighted
                    ? RoundedRectangle(cornerRadius: 16).stroke(Color.yellow, lineWidth: 2)
                    : nil
            )
        }
        .buttonStyle(.plain)
        .disabled(isLoading)
    }
}
#endif
