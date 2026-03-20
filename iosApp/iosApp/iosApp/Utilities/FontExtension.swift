import SwiftUI

// MARK: - Manrope Font Extension
// Custom font extension matching Android typography (Manrope Medium/SemiBold/Bold)

extension Font {
    /// Manrope Medium - 500 weight (default text)
    static func manrope(size: CGFloat) -> Font {
        return Font.custom("Manrope-Medium", size: size)
    }

    /// Manrope SemiBold - 600 weight (buttons, emphasis)
    static func manropeSemiBold(size: CGFloat) -> Font {
        return Font.custom("Manrope-SemiBold", size: size)
    }

    /// Manrope Bold - 700 weight (titles, headers)
    static func manropeBold(size: CGFloat) -> Font {
        return Font.custom("Manrope-Bold", size: size)
    }
}

// MARK: - UIFont Extension (for SwiftUI TextField compatibility)
extension UIFont {
    static func manrope(size: CGFloat) -> UIFont {
        return UIFont(name: "Manrope-Medium", size: size) ?? .systemFont(ofSize: size)
    }

    static func manropeSemiBold(size: CGFloat) -> UIFont {
        return UIFont(name: "Manrope-SemiBold", size: size) ?? .systemFont(ofSize: size, weight: .semibold)
    }

    static func manropeBold(size: CGFloat) -> UIFont {
        return UIFont(name: "Manrope-Bold", size: size) ?? .systemFont(ofSize: size, weight: .bold)
    }
}
