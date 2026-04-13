// QRCodeGenerator.swift - Utilidad para generar deep links para QR
// NOTA: watchOS no soporta CoreImage ni CoreGraphics para generación de QR.
// El QR real debe generarse en el iPhone via WatchConnectivity.
// En el watch solo se muestra el deep link como texto.

import Foundation

class QRCodeGenerator {

    /// Deep link para SOS: vitalsense://sos/{userId}/{sosId}
    static func generateSosDeepLink(userId: String, sosId: String) -> String {
        return "vitalsense://sos/\(userId)/\(sosId)"
    }

    /// Deep link para emergencia: vitalsense://emergency/{tokenId}
    static func generateEmergencyDeepLink(tokenId: String) -> String {
        return "vitalsense://emergency/\(tokenId)"
    }

    /// Returns nil — watchOS cannot generate QR images.
    /// Callers should display the deep link string as a fallback.
    static func generateSosQR(userId: String, sosId: String) -> String? {
        let link = generateSosDeepLink(userId: userId, sosId: sosId)
        print("[QRCodeGenerator] watchOS: show deep link instead of QR: \(link)")
        return link
    }

    /// Returns nil — watchOS cannot generate QR images.
    /// Callers should display the deep link string as a fallback.
    static func generateEmergencyQR(tokenId: String) -> String? {
        let link = generateEmergencyDeepLink(tokenId: tokenId)
        print("[QRCodeGenerator] watchOS: show deep link instead of QR: \(link)")
        return link
    }
}
