#if os(iOS)
#if canImport(UIKit)
import UIKit

// MARK: - HapticFeedback
// Centralized haptic feedback manager.
// UI/UX principle: Sensory confirmation of interactions reduces uncertainty
// and communicates system state without relying solely on visuals.

enum HapticFeedback {
    // MARK: Impact
    static func light()    { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    static func medium()   { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
    static func heavy()    { UIImpactFeedbackGenerator(style: .heavy).impactOccurred() }
    static func soft()     { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
    static func rigid()    { UIImpactFeedbackGenerator(style: .rigid).impactOccurred() }

    // MARK: Notification
    /// Use for successful async operations (login, save, send)
    static func success()  { UINotificationFeedbackGenerator().notificationOccurred(.success) }
    /// Use for recoverable errors (validation fail, wrong input)
    static func warning()  { UINotificationFeedbackGenerator().notificationOccurred(.warning) }
    /// Use for critical failures or SOS activations
    static func error()    { UINotificationFeedbackGenerator().notificationOccurred(.error) }

    // MARK: Selection
    /// Use when navigating between tabs, pages or picker items
    static func selection() { UISelectionFeedbackGenerator().selectionChanged() }
}
#endif

#endif
