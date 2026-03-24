package mx.ita.vitalsense.data.sos

/**
 * En iOS, el SOS se gestiona desde Swift usando Firebase iOS SDK directamente.
 * Esta implementación es un stub para que el módulo compartido compile.
 */
actual class SosManager actual constructor() {
    actual fun enviarSOS(userId: String, patientName: String) {
        // iOS: handled natively in Swift (see SosService.swift)
    }
    actual fun escucharSOS(userId: String, onAlerta: (String) -> Unit) {
        // iOS: handled natively in Swift (see SosService.swift)
    }
}
