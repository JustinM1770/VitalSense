#if os(iOS)
import Foundation
import Combine
import StoreKit
import FirebaseAuth
import FirebaseDatabase

// MARK: - Plan de suscripción
enum SubscriptionPlan: String {
    case free    = "free"
    case premium = "premium"

    var deviceLimit: Int {
        switch self {
        case .free:    return 1
        case .premium: return 999
        }
    }

    var displayName: String {
        switch self {
        case .free:    return "Free"
        case .premium: return "Premium"
        }
    }
}

// MARK: - Dispositivo vinculado
struct PairedDevice: Identifiable, Codable {
    let id: String          // UUID generado al vincular
    let name: String        // "Apple Watch de Justin"
    let platform: String    // "watchOS", "wearOS", "custom"
    let code: String        // código de vinculación usado
    let pairedAt: TimeInterval

    var pairedDate: Date { Date(timeIntervalSince1970: pairedAt) }
}

// MARK: - SubscriptionService
@MainActor
class SubscriptionService: ObservableObject {
    static let shared = SubscriptionService()

    @Published var plan: SubscriptionPlan = .free
    @Published var pairedDevices: [PairedDevice] = []
    @Published var products: [StoreKit.Product] = []
    @Published var isPurchasing = false

    var canAddDevice: Bool { pairedDevices.count < plan.deviceLimit }
    var devicesUsed: Int  { pairedDevices.count }
    var deviceLimit: Int  { plan.deviceLimit }

    // StoreKit product ID — configura en App Store Connect con este ID
    static let premiumMonthlyID = "ai.biometric.vitalsense.premium.monthly"
    static let premiumYearlyID  = "ai.biometric.vitalsense.premium.yearly"

    private let db = Database.database().reference()
    private var uid: String { Auth.auth().currentUser?.uid ?? "" }
    private var devicesHandle: DatabaseHandle?

    private init() {}

    // MARK: - Setup (llamar al hacer login)

    func setup() async {
        await loadProducts()
        loadDevices()
        checkPremiumStatus()
    }

    func teardown() {
        if let h = devicesHandle, !uid.isEmpty {
            db.child("patients/\(uid)/devices").removeObserver(withHandle: h)
        }
        devicesHandle = nil
        pairedDevices = []
        plan = .free
    }

    // MARK: - Dispositivos

    func loadDevices() {
        guard !uid.isEmpty else { return }
        devicesHandle = db.child("patients/\(uid)/devices").observe(.value) { [weak self] snapshot in
            guard let self else { return }
            var devices: [PairedDevice] = []
            for child in snapshot.children {
                guard let snap = child as? DataSnapshot,
                      let dict = snap.value as? [String: Any] else { continue }
                let device = PairedDevice(
                    id:        snap.key,
                    name:      dict["name"]      as? String        ?? "Wearable",
                    platform:  dict["platform"]  as? String        ?? "unknown",
                    code:      dict["code"]       as? String        ?? "",
                    pairedAt:  dict["pairedAt"]  as? TimeInterval  ?? 0
                )
                devices.append(device)
            }
            devices.sort { $0.pairedAt < $1.pairedAt }
            Task { @MainActor in self.pairedDevices = devices }
        }
    }

    func addDevice(name: String, platform: String, code: String) async -> Bool {
        guard canAddDevice else { return false }
        guard !uid.isEmpty else { return false }

        let id = UUID().uuidString
        let data: [String: Any] = [
            "name":     name,
            "platform": platform,
            "code":     code,
            "pairedAt": Date().timeIntervalSince1970
        ]
        try? await db.child("patients/\(uid)/devices/\(id)").setValue(data)
        return true
    }

    func removeDevice(_ device: PairedDevice) {
        guard !uid.isEmpty else { return }
        db.child("patients/\(uid)/devices/\(device.id)").removeValue()
        // También limpiar el pairing_code en Firebase
        db.child("patients/pairing_codes/\(device.code)").removeValue()
    }

    // MARK: - Estado premium (Firebase-backed, StoreKit confirmado)

    private func checkPremiumStatus() {
        guard !uid.isEmpty else { return }
        db.child("patients/\(uid)/subscription/plan").observeSingleEvent(of: .value) { [weak self] snap in
            guard let self else { return }
            let value = snap.value as? String ?? "free"
            Task { @MainActor in
                self.plan = SubscriptionPlan(rawValue: value) ?? .free
            }
        }

        // También verificar transacciones activas de StoreKit
        Task {
            for await result in Transaction.currentEntitlements {
                if case .verified(let tx) = result {
                    if tx.productID == Self.premiumMonthlyID || tx.productID == Self.premiumYearlyID {
                        await upgradeToPremium()
                    }
                }
            }
        }
    }

    // MARK: - StoreKit: cargar productos

    func loadProducts() async {
        do {
            products = try await StoreKit.Product.products(for: [Self.premiumMonthlyID, Self.premiumYearlyID])
            products.sort { $0.price < $1.price }
        } catch {
            print("[Subscription] Error cargando productos: \(error)")
        }
    }

    // MARK: - Comprar

    func purchase(_ product: StoreKit.Product) async -> Bool {
        isPurchasing = true
        defer { isPurchasing = false }
        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                if case .verified(let tx) = verification {
                    await tx.finish()
                    await upgradeToPremium()
                    return true
                }
            case .userCancelled:
                break
            case .pending:
                break
            @unknown default:
                break
            }
        } catch {
            print("[Subscription] Error de compra: \(error)")
        }
        return false
    }

    func restorePurchases() async {
        try? await AppStore.sync()
        for await result in Transaction.currentEntitlements {
            if case .verified(let tx) = result {
                if tx.productID == Self.premiumMonthlyID || tx.productID == Self.premiumYearlyID {
                    await upgradeToPremium()
                    return
                }
            }
        }
    }

    private func upgradeToPremium() async {
        plan = .premium
        guard !uid.isEmpty else { return }
        db.child("patients/\(uid)/subscription").setValue([
            "plan":      "premium",
            "updatedAt": Date().timeIntervalSince1970
        ]) { _, _ in }
    }
}
#endif
